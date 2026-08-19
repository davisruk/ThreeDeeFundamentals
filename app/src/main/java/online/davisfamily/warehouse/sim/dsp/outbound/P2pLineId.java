package online.davisfamily.warehouse.sim.dsp.outbound;

public record P2pLineId(String value) {

    public P2pLineId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        value = value.trim();
    }
}
