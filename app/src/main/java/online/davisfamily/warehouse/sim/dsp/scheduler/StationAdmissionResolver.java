package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public interface StationAdmissionResolver {
    /**
     * Returns a resolver scoped to one immutable scheduler evaluation.
     *
     * <p>The default implementation preserves the existing per-call behaviour. Resolvers that
     * obtain immutable live-state snapshots may override this method to capture them once for the
     * candidate batch.</p>
     */
    default StationAdmissionResolver forEvaluation(WarehouseSchedulerSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return this;
    }

    StationAdmissionSnapshot admissionFor(
            StationType stationType,
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot);
}
