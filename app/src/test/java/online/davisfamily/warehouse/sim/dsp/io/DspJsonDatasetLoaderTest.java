package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductCategory;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class DspJsonDatasetLoaderTest {
    private final DspJsonDatasetLoader loader = new DspJsonDatasetLoader(
            new ProductMasterJsonLoader(),
            new TwelveNMessageKindMapper(),
            new TwelveNOrderMapper(new TwelveNMessageKindMapper()),
            new TwelveNPreparedLineMapper(new TwelveNMessageKindMapper()),
            new DspOrderValidator());

    @Test
    void shouldLoadProductsAndOneFullPackDispatchOrder() {
        LoadedDspData data = loader.load(
                List.of(new ProductMasterRecord("9114", ProductCategory.AUTOMATED, false)),
                List.of(message("""
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
        assertEquals(1, data.dispatchOrders().size());
        assertEquals(OrderType.FULL_PACK, data.dispatchOrders().getFirst().orderType());
        assertTrue(data.preparedLines().isEmpty());
        assertTrue(data.loadedPreparedLineKeys().isEmpty());
        assertTrue(data.startupReadyPreparedLineKeys().isEmpty());
    }

    @Test
    void shouldLoadAdaptedPreparationLinesAndLoadedPreparedLineKeysWithoutDispatchOrder() {
        LoadedDspData data = loader.load(
                List.of(new ProductMasterRecord("36550", ProductCategory.SORTABLE, false)),
                List.of(message("""
                        {
                          "header": {"orderId":"TOTE0007168406","sheetNumber":"022"},
                          "toteIdentifier": {"payload":"02"},
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

        assertTrue(data.dispatchOrders().isEmpty());
        assertEquals(2, data.preparedLines().size());
        assertEquals(Set.of(
                new PreparedLineKey("TOTE0007168519", "000243449262"),
                new PreparedLineKey("TOTE0007168489", "000243450449")),
                data.loadedPreparedLineKeys());
        assertTrue(data.startupReadyPreparedLineKeys().isEmpty());
    }

    @Test
    void shouldRejectMixedPharmacyAssociatedDispatchOrder() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(
                        List.of(
                                new ProductMasterRecord("1809", ProductCategory.MANUAL, false),
                                new ProductMasterRecord("19959", ProductCategory.AUTOMATED, false)),
                        List.of(message("""
                                {
                                  "header": {"orderId":"TOTE0007170299","sheetNumber":"001"},
                                  "toteIdentifier": {"payload":"04"},
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
                                """))));

        assertTrue(exception.getMessage().contains("must be pharmacy-pure"));
    }

    @Test
    void shouldPreserveStableDispatchSequenceNumbersAcrossMixedInput() {
        LoadedDspData data = loader.load(
                List.of(
                        new ProductMasterRecord("36550", ProductCategory.SORTABLE, false),
                        new ProductMasterRecord("9114", ProductCategory.AUTOMATED, false),
                        new ProductMasterRecord("19959", ProductCategory.AUTOMATED, false)),
                List.of(
                        message("""
                                {
                                  "header": {"orderId":"TOTE0007168406","sheetNumber":"022"},
                                  "toteIdentifier": {"payload":"02"},
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

        assertEquals(2, data.dispatchOrders().size());
        assertEquals(0L, data.dispatchOrders().get(0).sequenceNumber());
        assertEquals(1L, data.dispatchOrders().get(1).sequenceNumber());
        assertEquals(1, data.preparedLines().size());
    }

    @Test
    void shouldKeepManualMessagesOutOfDispatchOrders() {
        LoadedDspData data = loader.load(
                List.of(new ProductMasterRecord("6881", ProductCategory.MANUAL, false)),
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

        assertTrue(data.dispatchOrders().isEmpty());
        assertEquals(1, data.preparedLines().size());
        assertEquals(DspOrderLineType.MANUAL, data.preparedLines().getFirst().lineType());
    }

    private static TwelveNMessageJson message(String json) {
        return JsonLoaderSupport.readString(json, TwelveNMessageJson.class);
    }
}
