package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

public record P2pPhysicalToteAssignment(
        PhysicalToteId physicalToteId,
        String serviceCentreId,
        P2pLineId lineId,
        OperationalRouteDestination destination) {

    public P2pPhysicalToteAssignment {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (destination.stationType() != StationType.P2P) {
            throw new IllegalArgumentException("destination must identify a P2P station");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
