package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspDatasetAssemblerTest {
    private final DspDatasetAssembler assembler = new DspDatasetAssembler(
            new TwelveNMessageKindMapper(),
            new TwelveNOrderMapper(new TwelveNMessageKindMapper()),
            new DspOrderValidator());

    @Test
    void shouldRetainInboundManifestAlongsideLogicalOrder() {
        LoadedDspData data = assembler.assemble(
                List.of(product("9114")),
                List.of(message("""
                        {
                          "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"05"},
                          "transportContainer": {"payload":"tote-full-pack"},
                          "serviceCentre": {"payload":"104"},
                          "orderDetail": {
                            "numberOfOrderLines": 1,
                            "orderLines": [
                              {
                                "orderLineNumber":"000243548241",
                                "orderLineType":"05",
                                "pharmacyId":"0006461",
                                "productId":"        9114",
                                "numberOfPacks":"0001",
                                "referenceSheetNumber":"001",
                                "numberOfPacksPicked":"0001",
                                "referenceOrderId":"TOTE0007170720"
                              }
                            ]
                          }
                        }
                        """)));

        assertEquals(1, data.products().size());
        assertEquals(1, data.orders().size());
        assertEquals(OrderType.FULL_PACK, data.orders().getFirst().orderType());
        assertTrue(data.preparedLines().isEmpty());
        assertTrue(data.loadedPreparedLineKeys().isEmpty());
        assertTrue(data.startupReadyPreparedLineKeys().isEmpty());
        assertEquals(1, data.inboundToteManifests().size());
        assertEquals("tote-full-pack", data.inboundToteManifests().getFirst().physicalToteId().value());
        assertEquals(data.orders().getFirst().orderSheetKey(),
                data.inboundToteManifests().getFirst().orderSheetKey());
    }

    @Test
    void shouldLoadAdaptedPreparationLinesAndLoadedPreparedLineKeysWithoutDispatchOrder() {
        LoadedDspData data = assembler.assemble(
                List.of(product("36550")),
                List.of(message("""
                        {
                          "header": {"orderId":"TOTE0007168406","sheetNumber":"022"},
                          "toteIdentifier": {"payload":"02"},
                          "transportContainer": {"payload":"tote-adapted"},
                          "serviceCentre": {"payload":"116"},
                          "orderDetail": {
                            "numberOfOrderLines": 2,
                            "orderLines": [
                              {
                                "orderLineNumber":"000243449262",
                                "orderLineType":"02",
                                "pharmacyId":"0000310",
                                "productId":"       36550",
                                "numberOfPacks":"0001",
                                "referenceSheetNumber":"001",
                                "numberOfPacksPicked":"0000",
                                "referenceOrderId":"TOTE0007168519"
                              },
                              {
                                "orderLineNumber":"000243450449",
                                "orderLineType":"02",
                                "pharmacyId":"0000388",
                                "productId":"       36550",
                                "numberOfPacks":"0001",
                                "referenceSheetNumber":"001",
                                "numberOfPacksPicked":"0000",
                                "referenceOrderId":"TOTE0007168489"
                              }
                            ]
                          }
                        }
                        """)));

        assertEquals(1, data.orders().size());
        assertEquals(OrderType.ADAPTED, data.orders().getFirst().orderType());
        assertEquals(2, data.preparedLines().size());
        assertEquals(Set.of(
                new PreparedLineKey("TOTE0007168519", "000243449262"),
                new PreparedLineKey("TOTE0007168489", "000243450449")),
                data.loadedPreparedLineKeys());
        assertTrue(data.startupReadyPreparedLineKeys().isEmpty());
    }

    @Test
    void shouldGroupSeveralInboundManifestsIntoOneLogicalSheet() {
        LoadedDspData data = assembler.assemble(
                List.of(product("product-1"), product("product-2")),
                List.of(
                        physicalMessage(
                                "order-1", "001", "05", "tote-1", "104",
                                line("line-1", "05", "pharmacy-1", "product-1")),
                        physicalMessage(
                                "order-1", "001", "05", "tote-2", "104",
                                line("line-2", "05", "pharmacy-1", "product-2"))));

        assertEquals(1, data.orders().size());
        assertEquals(2, data.inboundToteManifests().size());
        assertEquals(List.of("tote-1", "tote-2"), data.inboundToteManifests().stream()
                .map(manifest -> manifest.physicalToteId().value())
                .toList());
        assertEquals(0L, data.orders().getFirst().sequenceNumber());
        assertEquals(List.of(0L, 1L), data.inboundToteManifests().stream()
                .map(manifest -> manifest.sourceSequenceNumber())
                .toList());
    }

    @Test
    void shouldPreserveManifestSpecificItemsAndCombinedLogicalItems() {
        LoadedDspData data = assembler.assemble(
                List.of(product("product-1"), product("product-2")),
                List.of(
                        physicalMessage(
                                "order-1", "001", "05", "tote-1", "104",
                                line("line-1", "05", "pharmacy-1", "product-1")),
                        physicalMessage(
                                "order-1", "001", "05", "tote-2", "104",
                                line("line-2", "05", "pharmacy-1", "product-2"))));

        assertEquals(List.of("line-1", "line-2"), data.orders().getFirst().items().stream()
                .map(DspOrderItem::lineReference)
                .toList());
        assertEquals(List.of("line-1"), data.inboundToteManifests().get(0).items().stream()
                .map(DspOrderItem::lineReference)
                .toList());
        assertEquals(List.of("line-2"), data.inboundToteManifests().get(1).items().stream()
                .map(DspOrderItem::lineReference)
                .toList());
    }

    @Test
    void shouldRejectConflictingMetadataForSameLogicalSheet() {
        TwelveNMessageJson first = physicalMessage(
                "order-1", "001", "05", "tote-1", "104",
                line("line-1", "05", "pharmacy-1", "product-1"));
        TwelveNMessageJson conflictingServiceCentre = physicalMessage(
                "order-1", "001", "05", "tote-2", "116",
                line("line-2", "05", "pharmacy-1", "product-2"));
        TwelveNMessageJson conflictingOrderType = physicalMessage(
                "order-1", "001", "04", "tote-2", "104",
                line("line-2", "05", "pharmacy-1", "product-2"));

        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(
                        List.of(product("product-1"), product("product-2")),
                        List.of(first, conflictingServiceCentre)));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(
                        List.of(product("product-1"), product("product-2")),
                        List.of(first, conflictingOrderType)));
    }

    @Test
    void shouldRejectDuplicateLineReferencesAcrossManifests() {
        List<TwelveNMessageJson> messages = List.of(
                physicalMessage(
                        "order-1", "001", "05", "tote-1", "104",
                        line("line-1", "05", "pharmacy-1", "product-1")),
                physicalMessage(
                        "order-1", "001", "05", "tote-2", "104",
                        line("line-1", "05", "pharmacy-1", "product-2")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assemble(
                        List.of(product("product-1"), product("product-2")),
                        messages));

        assertTrue(exception.getMessage().contains("Duplicate lineReference"));
    }

    @Test
    void shouldRejectDuplicatePhysicalToteIds() {
        List<TwelveNMessageJson> messages = List.of(
                physicalMessage(
                        "order-1", "001", "05", "tote-1", "104",
                        line("line-1", "05", "pharmacy-1", "product-1")),
                physicalMessage(
                        "order-2", "001", "05", "tote-1", "104",
                        line("line-2", "05", "pharmacy-1", "product-2")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assemble(
                        List.of(product("product-1"), product("product-2")),
                        messages));

        assertTrue(exception.getMessage().contains("Duplicate physical tote ID"));
    }

    @Test
    void shouldKeepEmptyOrderManifestFree() {
        LoadedDspData data = assembler.assemble(
                List.of(product("product-1")),
                List.of(physicalMessage(
                        "order-1", "001", "03", null, "104",
                        line("line-1", "05", "pharmacy-1", "product-1"))));

        assertEquals(1, data.orders().size());
        assertEquals(OrderType.EMPTY, data.orders().getFirst().orderType());
        assertTrue(data.inboundToteManifests().isEmpty());
    }

    @Test
    void shouldRemoveIgnoredManualLinesFromManifestAndLogicalOrder() {
        String mixedOrderJson = """
                                {
                                  "header": {"orderId":"TOTE0007170299","sheetNumber":"001"},
                                  "toteIdentifier": {"payload":"04"},
                                  "transportContainer": {"payload":"tote-associated"},
                                  "serviceCentre": {"payload":"104"},
                                  "orderDetail": {
                                    "numberOfOrderLines": 2,
                                    "orderLines": [
                                      {
                                        "orderLineNumber":"000243560310",
                                        "orderLineType":"01",
                                        "pharmacyId":"0006515",
                                        "productId":"        1809",
                                        "numberOfPacks":"0001",
                                        "referenceSheetNumber":"001",
                                        "numberOfPacksPicked":"0000",
                                        "referenceOrderId":"TOTE0007170299"
                                      },
                                      {
                                        "orderLineNumber":"000243567994",
                                        "orderLineType":"05",
                                        "pharmacyId":"0006461",
                                        "productId":"       19959",
                                        "numberOfPacks":"0002",
                                        "referenceSheetNumber":"001",
                                        "numberOfPacksPicked":"0001",
                                        "referenceOrderId":"TOTE0007170299"
                                      }
                                    ]
                                  }
                                }
                                """;
        LoadedDspData data = assembler.assemble(
                List.of(product("1809"), product("19959")),
                List.of(message(mixedOrderJson)));

        assertEquals(1, data.orders().size());
        assertEquals(1, data.orders().getFirst().items().size());
        assertEquals("0006461", data.orders().getFirst().items().getFirst().pharmacyId());
        assertEquals(1, data.inboundToteManifests().size());
        assertEquals(data.orders().getFirst().items(), data.inboundToteManifests().getFirst().items());
        assertEquals(1, data.report().ignoredManualLineCount());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assemble(
                        List.of(product("1809"), product("19959")),
                        List.of(message(mixedOrderJson.replace(
                                "\"orderLineType\":\"01\"",
                                "\"orderLineType\":\"05\"")))));

        assertTrue(exception.getMessage().contains("must be pharmacy-pure"));
    }

    @Test
    void shouldPreserveStableOrderSequenceNumbersAcrossMixedInput() {
        LoadedDspData data = assembler.assemble(
                List.of(
                        product("36550"),
                        product("9114"),
                        product("19959")),
                List.of(
                        message("""
                                {
                                  "header": {"orderId":"TOTE0007168406","sheetNumber":"022"},
                                  "toteIdentifier": {"payload":"02"},
                                  "transportContainer": {"payload":"tote-adapted"},
                                  "serviceCentre": {"payload":"116"},
                                  "orderDetail": {
                                    "numberOfOrderLines": 1,
                                    "orderLines": [
                                      {
                                        "orderLineNumber":"000243449262",
                                        "orderLineType":"02",
                                        "pharmacyId":"0000310",
                                        "productId":"       36550",
                                        "numberOfPacks":"0001",
                                        "referenceSheetNumber":"001",
                                        "numberOfPacksPicked":"0000",
                                        "referenceOrderId":"TOTE0007168519"
                                      }
                                    ]
                                  }
                                }
                                """),
                        message("""
                                {
                                  "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                                  "toteIdentifier": {"payload":"05"},
                                  "transportContainer": {"payload":"tote-full-pack"},
                                  "serviceCentre": {"payload":"104"},
                                  "orderDetail": {
                                    "numberOfOrderLines": 1,
                                    "orderLines": [
                                      {
                                        "orderLineNumber":"000243548241",
                                        "orderLineType":"05",
                                        "pharmacyId":"0006461",
                                        "productId":"        9114",
                                        "numberOfPacks":"0001",
                                        "referenceSheetNumber":"001",
                                        "numberOfPacksPicked":"0001",
                                        "referenceOrderId":"TOTE0007170720"
                                      }
                                    ]
                                  }
                                }
                                """),
                        message("""
                                {
                                  "header": {"orderId":"TOTE0007170299","sheetNumber":"001"},
                                  "toteIdentifier": {"payload":"04"},
                                  "transportContainer": {"payload":"tote-associated"},
                                  "serviceCentre": {"payload":"104"},
                                  "orderDetail": {
                                    "numberOfOrderLines": 1,
                                    "orderLines": [
                                      {
                                        "orderLineNumber":"000243567994",
                                        "orderLineType":"05",
                                        "pharmacyId":"0006515",
                                        "productId":"       19959",
                                        "numberOfPacks":"0002",
                                        "referenceSheetNumber":"001",
                                        "numberOfPacksPicked":"0001",
                                        "referenceOrderId":"TOTE0007170299"
                                      }
                                    ]
                                  }
                                }
                                """)));

        assertEquals(3, data.orders().size());
        assertEquals(0L, data.orders().get(0).sequenceNumber());
        assertEquals(1L, data.orders().get(1).sequenceNumber());
        assertEquals(2L, data.orders().get(2).sequenceNumber());
        assertEquals(1, data.preparedLines().size());
    }

    @Test
    void shouldIgnoreManualPreparationMessages() {
        LoadedDspData data = assembler.assemble(
                List.of(product("6881")),
                List.of(message("""
                        {
                          "header": {"orderId":"TOTE0007171306","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"01"},
                          "serviceCentre": {"payload":"104"},
                          "orderDetail": {
                            "numberOfOrderLines": 1,
                            "orderLines": [
                              {
                                "orderLineNumber":"000243560347",
                                "orderLineType":"01",
                                "pharmacyId":"0005984",
                                "productId":"        6881",
                                "numberOfPacks":"0001",
                                "referenceSheetNumber":"001",
                                "numberOfPacksPicked":"0001",
                                "referenceOrderId":"TOTE0007170196"
                              }
                            ]
                          }
                        }
                        """)));

        assertTrue(data.orders().isEmpty());
        assertTrue(data.preparedLines().isEmpty());
        assertTrue(data.inboundToteManifests().isEmpty());
        assertEquals(1, data.report().ignoredManualMessageCount());
        assertEquals(1, data.report().ignoredManualLineCount());
        assertEquals(0, data.report().omittedOrderCount());
    }

    @Test
    void shouldOmitDispatchOrderWhenOnlyManualLinesRemain() {
        LoadedDspData data = assembler.assemble(
                List.of(product("6881")),
                List.of(message("""
                        {
                          "header": {"orderId":"TOTE0007170196","sheetNumber":"003"},
                          "toteIdentifier": {"payload":"04"},
                          "transportContainer": {"payload":"tote-associated"},
                          "serviceCentre": {"payload":"104"},
                          "orderDetail": {
                            "numberOfOrderLines": 1,
                            "orderLines": [
                              {
                                "orderLineNumber":"000243560347",
                                "orderLineType":"01",
                                "pharmacyId":"0005984",
                                "productId":"6881",
                                "numberOfPacks":"0001",
                                "referenceSheetNumber":"001",
                                "numberOfPacksPicked":"0000",
                                "referenceOrderId":"TOTE0007170196"
                              }
                            ]
                          }
                        }
                        """)));

        assertTrue(data.orders().isEmpty());
        assertTrue(data.inboundToteManifests().isEmpty());
        assertEquals(0, data.report().ignoredManualMessageCount());
        assertEquals(1, data.report().ignoredManualLineCount());
        assertEquals(1, data.report().omittedOrderCount());
    }

    @Test
    void shouldRetainAndReportLinesMissingFromProductMaster() {
        LoadedDspData data = assembler.assemble(
                List.of(),
                List.of(message("""
                        {
                          "header": {"orderId":"order-missing","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"05"},
                          "transportContainer": {"payload":"tote-full-pack"},
                          "serviceCentre": {"payload":"104"},
                          "orderDetail": {
                            "numberOfOrderLines": 1,
                            "orderLines": [
                              {
                                "orderLineNumber":"line-missing",
                                "orderLineType":"05",
                                "pharmacyId":"0005984",
                                "productId":"missing-product",
                                "numberOfPacks":"0001",
                                "referenceSheetNumber":"001",
                                "numberOfPacksPicked":"0000",
                                "referenceOrderId":"order-missing"
                              }
                            ]
                          }
                        }
                        """)));

        assertEquals(1, data.orders().size());
        assertEquals(
                List.of(new UnresolvedProductLine("order-missing", "line-missing", "missing-product")),
                data.report().unresolvedProductLines());
    }

    private static TwelveNMessageJson message(String json) {
        return JsonLoaderSupport.readString(json, TwelveNMessageJson.class);
    }

    private static TwelveNMessageJson physicalMessage(
            String orderId,
            String sheetNumber,
            String toteType,
            String physicalToteId,
            String serviceCentreId,
            TwelveNOrderLineJson... lines) {
        return new TwelveNMessageJson(
                new TwelveNHeaderJson(null, null, orderId, sheetNumber),
                new TwelveNFieldJson(null, null, toteType),
                physicalToteId == null ? null : new TwelveNFieldJson(null, null, physicalToteId),
                null,
                null,
                new TwelveNFieldJson(null, null, serviceCentreId),
                new TwelveNOrderDetailJson(
                        lines.length,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(lines)));
    }

    private static TwelveNOrderLineJson line(
            String lineReference,
            String lineType,
            String pharmacyId,
            String productId) {
        return new TwelveNOrderLineJson(
                lineReference,
                lineType,
                pharmacyId,
                null,
                null,
                productId,
                "0001",
                null,
                "reference-" + lineReference,
                "001",
                "0000");
    }

    private static ProductMasterRecord product(String productId) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                Optional.empty(),
                Optional.of(new PackDimensions(0.20f, 0.10f, 0.08f)));
    }
}
