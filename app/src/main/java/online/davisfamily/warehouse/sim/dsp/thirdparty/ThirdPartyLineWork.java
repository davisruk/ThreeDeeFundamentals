package online.davisfamily.warehouse.sim.dsp.thirdparty;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;

public record ThirdPartyLineWork(
        DspOrderItem line,
        String binLocation,
        ThirdPartyWorkType workType) {

    public ThirdPartyLineWork {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        binLocation = requireValue(binLocation, "binLocation");
        if (workType == null) {
            throw new IllegalArgumentException("workType must not be null");
        }
    }

    public String lineReference() {
        return line.lineReference();
    }

    public String productId() {
        return line.productId();
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
