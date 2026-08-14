package online.davisfamily.warehouse.sim.dsp.thirdparty;

public record ThirdPartyVisitState(
        String orderId,
        String notionalToteId,
        int lineCount,
        int outstandingPackCount,
        double remainingProcessingSeconds) {

    public ThirdPartyVisitState {
        orderId = requireValue(orderId, "orderId");
        notionalToteId = requireValue(notionalToteId, "notionalToteId");
        if (lineCount <= 0) {
            throw new IllegalArgumentException("lineCount must be > 0");
        }
        if (outstandingPackCount <= 0) {
            throw new IllegalArgumentException("outstandingPackCount must be > 0");
        }
        if (!Double.isFinite(remainingProcessingSeconds) || remainingProcessingSeconds < 0d) {
            throw new IllegalArgumentException("remainingProcessingSeconds must be finite and >= 0");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
