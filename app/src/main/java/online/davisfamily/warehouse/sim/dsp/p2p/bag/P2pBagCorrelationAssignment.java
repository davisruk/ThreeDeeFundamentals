package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.Objects;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

/**
 * Immutable audit value recording the P2P line that owns one bag correlation.
 */
public final class P2pBagCorrelationAssignment {
    private final String correlationId;
    private final P2pLineId lineId;
    private final P2pPhysicalToteAssignment p2pAssignment;

    public P2pBagCorrelationAssignment(
            String correlationId,
            P2pPhysicalToteAssignment p2pAssignment) {
        this.correlationId = requireValue(correlationId, "correlationId");
        this.p2pAssignment = Objects.requireNonNull(
                p2pAssignment, "p2pAssignment must not be null");
        this.lineId = p2pAssignment.lineId();
    }

    public P2pBagCorrelationAssignment(
            String correlationId,
            P2pLineId lineId) {
        this.correlationId = requireValue(correlationId, "correlationId");
        this.lineId = Objects.requireNonNull(lineId, "lineId must not be null");
        this.p2pAssignment = null;
    }

    public P2pBagCorrelationAssignment(
            String correlationId,
            P2pLineId lineId,
            PhysicalToteId physicalToteId,
            String serviceCentreId) {
        this.correlationId = requireValue(correlationId, "correlationId");
        this.lineId = Objects.requireNonNull(lineId, "lineId must not be null");
        this.p2pAssignment = physicalToteId == null || serviceCentreId == null
                ? null
                : new P2pPhysicalToteAssignment(
                        physicalToteId,
                        serviceCentreId,
                        lineId,
                        new OperationalRouteDestination(StationType.P2P, lineId.value()));
    }

    public String correlationId() {
        return correlationId;
    }

    public P2pPhysicalToteAssignment p2pAssignment() {
        return p2pAssignment;
    }

    public P2pPhysicalToteAssignment assignment() {
        return p2pAssignment;
    }

    public P2pLineId lineId() {
        return lineId;
    }

    public PhysicalToteId physicalToteId() {
        return p2pAssignment == null ? null : p2pAssignment.physicalToteId();
    }

    public String serviceCentreId() {
        return p2pAssignment == null ? null : p2pAssignment.serviceCentreId();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof P2pBagCorrelationAssignment that)) {
            return false;
        }
        return correlationId.equals(that.correlationId)
                && Objects.equals(p2pAssignment, that.p2pAssignment)
                && Objects.equals(lineId, that.lineId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, p2pAssignment, lineId);
    }

    @Override
    public String toString() {
        return "P2pBagCorrelationAssignment[correlationId=" + correlationId
                + ", p2pAssignment=" + p2pAssignment + "]";
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
