package online.davisfamily.warehouse.sim.dsp.io;

public record TwelveNOrderLineJson(
        String orderLineNumber,
        String orderLineType,
        String pharmacyId,
        String patientId,
        String prescriptionId,
        String productId,
        String numberOfPacks,
        String numberOfPills,
        String referenceOrderId,
        String referenceSheetNumber,
        String numberOfPacksPicked) {
}
