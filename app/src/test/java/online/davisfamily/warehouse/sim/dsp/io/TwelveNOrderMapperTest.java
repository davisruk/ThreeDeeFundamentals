package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

class TwelveNOrderMapperTest {
    private final TwelveNMessageKindMapper messageKindMapper = new TwelveNMessageKindMapper();
    private final TwelveNOrderMapper orderMapper = new TwelveNOrderMapper(messageKindMapper);
    private final TwelveNPreparedLineMapper preparedLineMapper = new TwelveNPreparedLineMapper(messageKindMapper);

    @Test
    void shouldMapFullPackLogicalOrderAndPhysicalTransportContainer() {
        MappedTwelveNOrder mappedOrder = orderMapper.map(message("""
                {
                  "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"05"},
                  "transportContainer": {"payload":" 90784872 "},
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
                """), 7L);
        NotionalToteOrder order = mappedOrder.order();
        InboundToteManifest manifest = mappedOrder.inboundToteManifest().orElseThrow();

        assertEquals("TOTE0007170720", order.orderId());
        assertEquals("TOTE0007170720", order.notionalToteId());
        assertEquals("104", order.serviceCentreId());
        assertEquals(1, order.sheetNumber());
        assertEquals(OrderType.FULL_PACK, order.orderType());
        assertEquals(7L, order.sequenceNumber());
        assertEquals(1, order.items().size());
        assertEquals("9114", order.items().getFirst().productId());
        assertEquals(DspOrderLineType.FULL_PACK, order.items().getFirst().lineType());
        assertEquals(1, order.items().getFirst().quantity());
        assertEquals("90784872", manifest.physicalToteId().value());
        assertEquals(order.orderSheetKey(), manifest.orderSheetKey());
        assertEquals(order.items(), manifest.items());
        assertEquals(order.sequenceNumber(), manifest.sourceSequenceNumber());
    }

    @Test
    void shouldMapAssociatedAndAdaptedPhysicalTransportContainers() {
        MappedTwelveNOrder mappedAssociated = orderMapper.map(message("""
                {
                  "header": {"orderId":"TOTE0007170299","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"04"},
                  "transportContainer": {"payload":"90864874"},
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
                """), 3L);
        NotionalToteOrder associated = mappedAssociated.order();
        MappedTwelveNOrder mappedAdapted = orderMapper.map(adaptedMessage(), 4L);

        assertEquals(OrderType.ASSOCIATED, associated.orderType());
        assertEquals(List.of(DspOrderLineType.MANUAL, DspOrderLineType.FULL_PACK),
                associated.items().stream().map(DspOrderItem::lineType).toList());
        assertEquals(2, associated.items().get(1).quantity());
        assertEquals(1, associated.items().get(1).numberOfPacksPicked());
        assertEquals("90864874",
                mappedAssociated.inboundToteManifest().orElseThrow().physicalToteId().value());
        assertEquals(OrderType.ADAPTED, mappedAdapted.order().orderType());
        assertEquals("90864875",
                mappedAdapted.inboundToteManifest().orElseThrow().physicalToteId().value());
    }

    @Test
    void shouldMapEmptyWithoutInboundManifest() {
        MappedTwelveNOrder mappedOrder = orderMapper.map(message("""
                {
                  "header": {"orderId":"TOTE0007179999","sheetNumber":"002"},
                  "toteIdentifier": {"payload":"03"},
                  "serviceCentre": {"payload":"116"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"000243999999",
                        "orderLineType":"05",
                        "pharmacyId":"0000310",
                        "productId":"       36550",
                        "numberOfPacks":"0001",
                        "referenceSheetNumber":"002",
                        "numberOfPacksPicked":"0000",
                        "referenceOrderId":"TOTE0007179999"
                      }
                    ]
                  }
                }
                """), 11L);
        NotionalToteOrder order = mappedOrder.order();

        assertEquals(OrderType.EMPTY, order.orderType());
        assertEquals(2, order.sheetNumber());
        assertEquals("116", order.serviceCentreId());
        assertTrue(mappedOrder.inboundToteManifest().isEmpty());
    }

    @Test
    void shouldConvertAdaptedPreparationLines() {
        TwelveNMessageJson adaptedMessage = adaptedMessage();
        List<DspOrderItem> preparedLines = preparedLineMapper.toPreparedLines(adaptedMessage);
        NotionalToteOrder order = orderMapper.map(adaptedMessage, 4L).order();

        assertEquals(2, preparedLines.size());
        assertEquals(DspOrderLineType.ADAPTED, preparedLines.getFirst().lineType());
        assertEquals("0000310", preparedLines.getFirst().pharmacyId());
        assertEquals("TOTE0007168519", preparedLines.getFirst().referenceOrderId());
        assertEquals("0000388", preparedLines.get(1).pharmacyId());
        assertEquals(OrderType.ADAPTED, order.orderType());
        assertEquals(4L, order.sequenceNumber());
        assertEquals(preparedLines, order.items());
    }

    @Test
    void shouldKeepOrderIdDistinctFromTransportContainer() {
        MappedTwelveNOrder mappedOrder = orderMapper.map(message("""
                {
                  "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"05"},
                  "transportContainer": {"payload":"90784872"},
                  "serviceCentre": {"payload":"104"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"000243548241",
                        "orderLineType":"05",
                        "pharmacyId":"0006461",
                        "productId":"9114",
                        "numberOfPacks":"0001",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"0001",
                        "referenceOrderId":"TOTE0007170720"
                      }
                    ]
                  }
                }
                """), 0L);

        assertEquals("TOTE0007170720", mappedOrder.order().orderId());
        assertEquals("TOTE0007170720", mappedOrder.order().notionalToteId());
        assertEquals("90784872",
                mappedOrder.inboundToteManifest().orElseThrow().physicalToteId().value());
    }

    @Test
    void shouldIgnoreEmptyTransportContainerPlaceholder() {
        MappedTwelveNOrder mappedOrder = orderMapper.map(message("""
                {
                  "header": {"orderId":"TOTE0007179999","sheetNumber":"002"},
                  "toteIdentifier": {"payload":"03"},
                  "transportContainer": {"payload":"PLACEHOLDER"},
                  "serviceCentre": {"payload":"116"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"000243999999",
                        "orderLineType":"05",
                        "pharmacyId":"0000310",
                        "productId":"36550",
                        "numberOfPacks":"0001",
                        "referenceSheetNumber":"002",
                        "numberOfPacksPicked":"0000",
                        "referenceOrderId":"TOTE0007179999"
                      }
                    ]
                  }
                }
                """), 0L);

        assertEquals(OrderType.EMPTY, mappedOrder.order().orderType());
        assertTrue(mappedOrder.inboundToteManifest().isEmpty());
    }

    @Test
    void shouldRejectMissingTransportContainerForPhysicalInboundOrder() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderMapper.map(message("""
                        {
                          "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"05"},
                          "serviceCentre": {"payload":"104"},
                          "orderDetail": {
                            "numberOfOrderLines": 1,
                            "orderLines": [
                              {
                                "orderLineNumber":"000243548241",
                                "orderLineType":"05",
                                "pharmacyId":"0006461",
                                "productId":"9114",
                                "numberOfPacks":"0001",
                                "referenceSheetNumber":"001",
                                "numberOfPacksPicked":"0001",
                                "referenceOrderId":"TOTE0007170720"
                              }
                            ]
                          }
                        }
                        """), 0L));

        assertTrue(exception.getMessage().contains("transportContainer"));
    }

    @Test
    void shouldConvertManualPreparationLines() {
        List<DspOrderItem> preparedLines = preparedLineMapper.toPreparedLines(message("""
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
                """));

        assertEquals(1, preparedLines.size());
        assertEquals(DspOrderLineType.MANUAL, preparedLines.getFirst().lineType());
        assertEquals(1, preparedLines.getFirst().numberOfPacksPicked());
    }

    @Test
    void shouldRejectLineCountMismatch() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderMapper.map(message("""
                        {
                          "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"05"},
                          "transportContainer": {"payload":"90784872"},
                          "serviceCentre": {"payload":"104"},
                          "orderDetail": {
                            "numberOfOrderLines": 2,
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
                        """), 0L));

        assertTrue(exception.getMessage().contains("numberOfOrderLines"));
    }

    @Test
    void shouldRejectUnknownToteType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderMapper.map(message("""
                        {
                          "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"99"},
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
                        """), 0L));

        assertTrue(exception.getMessage().contains("Unknown 12N tote type code"));
    }

    @Test
    void shouldRejectUnknownOrderLineType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderMapper.map(message("""
                        {
                          "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"05"},
                          "transportContainer": {"payload":"90784872"},
                          "serviceCentre": {"payload":"104"},
                          "orderDetail": {
                            "numberOfOrderLines": 1,
                            "orderLines": [
                              {
                                "orderLineNumber":"000243548241",
                                "orderLineType":"07",
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
                        """), 0L));

        assertTrue(exception.getMessage().contains("Unknown DspOrderLineType code"));
    }

    private static TwelveNMessageJson message(String json) {
        return JsonLoaderSupport.readString(json, TwelveNMessageJson.class);
    }

    private static TwelveNMessageJson adaptedMessage() {
        return message("""
                {
                  "header": {"orderId":"TOTE0007168406","sheetNumber":"022"},
                  "toteIdentifier": {"payload":"02"},
                  "transportContainer": {"payload":"90864875"},
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
                """);
    }
}
