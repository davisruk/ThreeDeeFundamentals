package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TwelveNMessageJsonTest {

    @Test
    void shouldParseMinimalFullPackLikeMessage() {
        TwelveNMessageJson message = JsonLoaderSupport.readString("""
                {
                  "header": {
                    "orderIdLength": 14,
                    "sheetNumberLength": 3,
                    "orderId": "TOTE0007170720",
                    "sheetNumber": "001"
                  },
                  "toteIdentifier": {
                    "identifier": "T",
                    "payloadLength": 2,
                    "payload": "05"
                  },
                  "transportContainer": {
                    "identifier": "C",
                    "payloadLength": 8,
                    "payload": "90784872"
                  },
                  "orderPriority": {
                    "identifier": "U",
                    "payloadLength": 3,
                    "payload": "999"
                  },
                  "departureTime": {
                    "identifier": "e",
                    "payloadLength": 14,
                    "payload": "20250626185913"
                  },
                  "serviceCentre": {
                    "identifier": "E",
                    "payloadLength": 3,
                    "payload": "104"
                  },
                  "orderDetail": {
                    "numberOfOrderLines": 2,
                    "orderLineReferenceNumberLength": 12,
                    "orderLineTypeLength": 2,
                    "pharmacyIdLength": 7,
                    "patientIdLength": 30,
                    "prescriptionIdLength": 20,
                    "productIdLength": 12,
                    "numPacksLength": 4,
                    "numPillsLength": 4,
                    "refOrderIdLength": 14,
                    "refSheetNumLength": 3,
                    "packsPickedLength": 4,
                    "orderLines": [
                      {
                        "orderLineNumber": "000243548241",
                        "orderLineType": "05",
                        "pharmacyId": "0006461",
                        "patientId": "                   NP253177242",
                        "prescriptionId": "   20018720000140736",
                        "productId": "        9114",
                        "numberOfPacks": "0001",
                        "numberOfPills": "0000",
                        "referenceSheetNumber": "001",
                        "numberOfPacksPicked": "0001",
                        "referenceOrderId": "TOTE0007170720"
                      },
                      {
                        "orderLineNumber": "000243548242",
                        "orderLineType": "05",
                        "pharmacyId": "0006461",
                        "patientId": "                   NP253177242",
                        "prescriptionId": "   20018720000140736",
                        "productId": "        9114",
                        "numberOfPacks": "0001",
                        "numberOfPills": "0000",
                        "referenceSheetNumber": "001",
                        "numberOfPacksPicked": "0001",
                        "referenceOrderId": "TOTE0007170720"
                      }
                    ]
                  }
                }
                """, TwelveNMessageJson.class);

        assertEquals("TOTE0007170720", message.header().orderId());
        assertEquals("001", message.header().sheetNumber());
        assertEquals("05", message.toteIdentifier().payload());
        assertEquals("104", message.serviceCentre().payload());
        assertEquals(2, message.orderDetail().numberOfOrderLines());
        assertEquals(2, message.orderDetail().orderLines().size());
        assertEquals("000243548241", message.orderDetail().orderLines().getFirst().orderLineNumber());
        assertEquals("05", message.orderDetail().orderLines().getFirst().orderLineType());
        assertEquals("TOTE0007170720", message.orderDetail().orderLines().getFirst().referenceOrderId());
    }
}
