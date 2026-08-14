package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

class TwelveNOrderMapperTest {
    private final TwelveNMessageKindMapper messageKindMapper = new TwelveNMessageKindMapper();
    private final TwelveNOrderMapper orderMapper = new TwelveNOrderMapper(messageKindMapper);
    private final TwelveNPreparedLineMapper preparedLineMapper = new TwelveNPreparedLineMapper(messageKindMapper);

    @Test
    void shouldConvertFullPackDispatchMessage() {
        NotionalToteOrder order = orderMapper.toOrder(message("""
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
                """), 7L);

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
    }

    @Test
    void shouldConvertAssociatedDispatchMessageWithMixedLineTypes() {
        NotionalToteOrder order = orderMapper.toOrder(message("""
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

        assertEquals(OrderType.ASSOCIATED, order.orderType());
        assertEquals(List.of(DspOrderLineType.MANUAL, DspOrderLineType.FULL_PACK),
                order.items().stream().map(DspOrderItem::lineType).toList());
        assertEquals(2, order.items().get(1).quantity());
        assertEquals(1, order.items().get(1).numberOfPacksPicked());
    }

    @Test
    void shouldConvertEmptyDispatchMessage() {
        NotionalToteOrder order = orderMapper.toOrder(message("""
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

        assertEquals(OrderType.EMPTY, order.orderType());
        assertEquals(2, order.sheetNumber());
        assertEquals("116", order.serviceCentreId());
    }

    @Test
    void shouldConvertAdaptedPreparationLines() {
        TwelveNMessageJson adaptedMessage = message("""
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
                """);
        List<DspOrderItem> preparedLines = preparedLineMapper.toPreparedLines(adaptedMessage);
        NotionalToteOrder order = orderMapper.toOrder(adaptedMessage, 4L);

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
                () -> orderMapper.toOrder(message("""
                        {
                          "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"05"},
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
                () -> orderMapper.toOrder(message("""
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
                () -> orderMapper.toOrder(message("""
                        {
                          "header": {"orderId":"TOTE0007170720","sheetNumber":"001"},
                          "toteIdentifier": {"payload":"05"},
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
}
