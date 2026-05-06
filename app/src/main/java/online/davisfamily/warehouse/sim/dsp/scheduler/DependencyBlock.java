package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.DependencyType;

public record DependencyBlock(DependencyType type, String reason) {
    public DependencyBlock {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
