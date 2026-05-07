package online.davisfamily.warehouse.sim.dsp.model;

public enum DspOrderLineType {
    MANUAL("01"),
    ADAPTED("02"),
    FULL_PACK("05");

    private final String code;

    DspOrderLineType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static DspOrderLineType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }

        String trimmedCode = code.trim();
        for (DspOrderLineType lineType : values()) {
            if (lineType.code.equals(trimmedCode)) {
                return lineType;
            }
        }
        throw new IllegalArgumentException("Unknown DspOrderLineType code: " + trimmedCode);
    }
}
