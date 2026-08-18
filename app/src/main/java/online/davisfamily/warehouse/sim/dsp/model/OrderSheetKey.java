package online.davisfamily.warehouse.sim.dsp.model;

public record OrderSheetKey(String orderId, int sheetNumber) {

    public OrderSheetKey {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (sheetNumber < 1) {
            throw new IllegalArgumentException("sheetNumber must be >= 1");
        }
        orderId = orderId.trim();
    }
}
