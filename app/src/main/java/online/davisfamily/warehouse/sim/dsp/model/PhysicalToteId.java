package online.davisfamily.warehouse.sim.dsp.model;

public record PhysicalToteId(String value) {

    public PhysicalToteId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        value = value.trim();
    }
}
