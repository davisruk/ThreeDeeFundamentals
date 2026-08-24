package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.Locale;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteIdAllocator;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class DeterministicAv02PhysicalToteIdAllocator implements PhysicalToteIdAllocator {
    private long lastOrdinal;

    @Override
    public PhysicalToteId nextPhysicalToteId() {
        lastOrdinal = Math.incrementExact(lastOrdinal);
        return new PhysicalToteId(String.format(Locale.ROOT, "av02-%06d", lastOrdinal));
    }
}
