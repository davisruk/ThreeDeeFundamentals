package online.davisfamily.warehouse.sim.dsp.lifecycle;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

@FunctionalInterface
public interface PhysicalToteIdAllocator {
    PhysicalToteId nextPhysicalToteId();
}
