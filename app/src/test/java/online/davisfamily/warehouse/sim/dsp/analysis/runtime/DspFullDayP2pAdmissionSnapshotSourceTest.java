package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirement;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirementCatalog;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

class DspFullDayP2pAdmissionSnapshotSourceTest {
    private static final String ACTIVE_CORRELATION = "bag-active";

    @Test
    void shouldReuseSnapshotUntilTheRegistryPublishesNewCorrelationIds() {
        List<P2pBagCorrelationRequirement> knownRequirements = new ArrayList<>();
        for (int index = 0; index < 5_000; index++) {
            knownRequirements.add(new P2pBagCorrelationRequirement("bag-" + index, 1));
        }
        P2pBagCorrelationRequirementCatalog catalog =
                new P2pBagCorrelationRequirementCatalog(
                        Map.of(new PhysicalToteId("known-tote"), knownRequirements),
                        Map.of());
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        DspFullDayP2pAdmissionSnapshotSource source =
                new DspFullDayP2pAdmissionSnapshotSource(155, catalog, registry);

        P2pAdmissionSnapshot initial = source.snapshot();
        P2pAdmissionSnapshot repeated = source.snapshot();

        assertSame(initial, repeated);
        assertSame(initial.activeBagCorrelations(), repeated.activeBagCorrelations());
        assertSame(initial.admissibleKnownCorrelations(), repeated.admissibleKnownCorrelations());
        assertEquals("dsp-p2p", initial.p2pCellId());
        assertEquals(155, initial.idlePrlCount());
        assertTrue(initial.pcrAvailableForNewRelease());
        assertEquals(5_000, initial.admissibleKnownCorrelations().size());
        assertTrue(initial.admissibleKnownCorrelations().contains("bag-4999"));

        P2pBagCorrelationRequirement activeRequirement =
                new P2pBagCorrelationRequirement(ACTIVE_CORRELATION, 1);
        registry.commit(List.of(activeRequirement), assignment("tote-1", "line-1"));

        P2pAdmissionSnapshot afterAddition = source.snapshot();
        assertNotSame(initial, afterAddition);
        assertEquals(Set.of(ACTIVE_CORRELATION), afterAddition.activeBagCorrelations());
        assertEquals(initial.admissibleKnownCorrelations(),
                afterAddition.admissibleKnownCorrelations());
        assertSame(afterAddition, source.snapshot());

        registry.commit(List.of(activeRequirement), assignment("tote-2", "line-1"));
        assertSame(afterAddition, source.snapshot());

        assertThrows(
                IllegalStateException.class,
                () -> registry.commit(
                        List.of(
                                activeRequirement,
                                new P2pBagCorrelationRequirement("bag-rejected", 1)),
                        assignment("tote-3", "line-2")));
        assertSame(afterAddition, source.snapshot());
    }

    @Test
    void shouldValidateConstructorInputs() {
        P2pBagCorrelationRequirementCatalog catalog =
                P2pBagCorrelationRequirementCatalog.empty();
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();

        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayP2pAdmissionSnapshotSource(-1, catalog, registry));
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayP2pAdmissionSnapshotSource(0, null, registry));
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayP2pAdmissionSnapshotSource(0, catalog, null));
    }

    private static P2pPhysicalToteAssignment assignment(String toteId, String lineId) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(toteId),
                "SC-1",
                new P2pLineId(lineId),
                new OperationalRouteDestination(
                        online.davisfamily.warehouse.sim.dsp.model.StationType.P2P,
                        "target-" + lineId));
    }
}
