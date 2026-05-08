package online.davisfamily.warehouse.sim.dsp.io;

import java.util.List;

public record TwelveNOrderDetailJson(
        Integer numberOfOrderLines,
        Integer orderLineReferenceNumberLength,
        Integer orderLineTypeLength,
        Integer pharmacyIdLength,
        Integer patientIdLength,
        Integer prescriptionIdLength,
        Integer productIdLength,
        Integer numPacksLength,
        Integer numPillsLength,
        Integer refOrderIdLength,
        Integer refSheetNumLength,
        Integer packsPickedLength,
        List<TwelveNOrderLineJson> orderLines) {
}
