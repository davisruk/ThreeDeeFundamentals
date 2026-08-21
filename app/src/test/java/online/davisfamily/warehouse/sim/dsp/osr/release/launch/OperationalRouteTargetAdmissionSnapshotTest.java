package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

class OperationalRouteTargetAdmissionSnapshotTest {

    @Test
    void shouldNormalizeIdentityAndExposeDerivedCapacity() {
        OperationalRouteTargetAdmissionSnapshot open =
                new OperationalRouteTargetAdmissionSnapshot(
                        StationType.P2P, "  p2p-1  ", 3, 1);

        assertEquals(StationType.P2P, open.stationType());
        assertEquals("p2p-1", open.targetId());
        assertEquals(2, open.remainingCapacity());
        assertTrue(open.canAccept());

        OperationalRouteTargetAdmissionSnapshot full =
                new OperationalRouteTargetAdmissionSnapshot(
                        StationType.ADAPTING, "adapting-1", 1, 1);
        assertEquals(0, full.remainingCapacity());
        assertFalse(full.canAccept());

        OperationalRouteTargetAdmissionSnapshot zeroCapacity =
                new OperationalRouteTargetAdmissionSnapshot(
                        StationType.THIRD_PARTY, "third-party-1", 0, 0);
        assertFalse(zeroCapacity.canAccept());
    }

    @Test
    void shouldRejectInvalidIdentityAndOccupancy() {
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteTargetAdmissionSnapshot(null, "target", 1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteTargetAdmissionSnapshot(StationType.P2P, null, 1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteTargetAdmissionSnapshot(StationType.P2P, " ", 1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteTargetAdmissionSnapshot(StationType.P2P, "target", -1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteTargetAdmissionSnapshot(StationType.P2P, "target", 1, -1));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteTargetAdmissionSnapshot(StationType.P2P, "target", 1, 2));
    }
}
