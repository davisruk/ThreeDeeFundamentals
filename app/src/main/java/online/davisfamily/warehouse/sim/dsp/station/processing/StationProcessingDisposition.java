package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/**
 * Immutable completion handoff for one exact station processing claim.
 */
public record StationProcessingDisposition(
        StationProcessingClaim claim,
        StationProcessingDispositionType type,
        ToteLoadPlan currentLoadPlan,
        Duration completedAt) {

    public StationProcessingDisposition {
        if (claim == null) {
            throw new IllegalArgumentException("claim must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (currentLoadPlan == null) {
            throw new IllegalArgumentException("currentLoadPlan must not be null");
        }
        if (!claim.physicalToteId().equals(currentLoadPlan.physicalToteId())) {
            throw new IllegalArgumentException(
                    "currentLoadPlan physical tote ID must match claim: "
                            + claim.physicalToteId().value());
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        if (completedAt.isNegative()) {
            throw new IllegalArgumentException("completedAt must not be negative");
        }
        if (completedAt.compareTo(claim.claimedAt()) < 0) {
            throw new IllegalArgumentException(
                    "completedAt must not precede claimedAt");
        }
    }

    public PhysicalToteId physicalToteId() {
        return claim.physicalToteId();
    }
}
