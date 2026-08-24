package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

class P2pLineLeaseRegistryTest {

    @Test
    void shouldValidateAndPreserveConfiguredLineOrder() {
        P2pLineDefinition first = definition("line-1", "target-1");
        P2pLineDefinition second = definition("line-2", "target-2");
        List<P2pLineDefinition> source = new ArrayList<>(List.of(second, first));

        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(source);
        source.clear();

        assertEquals(List.of(second, first), registry.definitions());
        assertThrows(UnsupportedOperationException.class, () -> registry.definitions().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseRegistry(null));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseRegistry(java.util.Arrays.asList(first, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseRegistry(List.of(first, first)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseRegistry(List.of(
                        first,
                        new P2pLineDefinition(new P2pLineId("line-3"), first.destination()))));
    }

    @Test
    void shouldAcquireOnlyAnUnleasedQuiescentLine() {
        P2pLineDefinition line = definition("line-1", "target-1");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(line));

        assertThrows(IllegalStateException.class,
                () -> registry.acquireLease(
                        line.lineId(), "SC-104", busyInputActivity()));
        assertEquals(Optional.empty(), registry.ownerFor(line.lineId()));

        registry.acquireLease(line.lineId(), " SC-104 ", P2pLineActivitySnapshot.idle());
        registry.acquireLease(line.lineId(), "SC-104", busyInputActivity());

        assertEquals(Optional.of("SC-104"), registry.ownerFor(line.lineId()));
        assertThrows(IllegalStateException.class,
                () -> registry.acquireLease(
                        line.lineId(), "SC-108", P2pLineActivitySnapshot.idle()));
        assertEquals(Optional.of("SC-104"), registry.ownerFor(line.lineId()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquireLease(null, "SC-104", P2pLineActivitySnapshot.idle()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquireLease(
                        new P2pLineId("missing"), "SC-104", P2pLineActivitySnapshot.idle()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquireLease(line.lineId(), " ", P2pLineActivitySnapshot.idle()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquireLease(line.lineId(), "SC-104", null));
    }

    @Test
    void shouldCommitOnlyExactOwnerAndLineAssignments() {
        P2pLineDefinition first = definition("line-1", "target-1");
        P2pLineDefinition second = definition("line-2", "target-2");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(first, second));
        registry.acquireLease(first.lineId(), "SC-104", P2pLineActivitySnapshot.idle());
        P2pPhysicalToteAssignment assignment = assignment("physical-1", "SC-104", first);

        registry.commitAssignment(assignment);
        registry.commitAssignment(assignment);

        assertEquals(Optional.of(assignment),
                registry.findAssignment(assignment.physicalToteId()));
        assertEquals(1, registry.snapshot(idleActivities(first, second))
                .findLine(first.lineId()).orElseThrow().physicalAssignments().size());
        assertThrows(IllegalStateException.class,
                () -> registry.commitAssignment(new P2pPhysicalToteAssignment(
                        assignment.physicalToteId(),
                        "SC-108",
                        first.lineId(),
                        first.destination())));
        assertThrows(IllegalStateException.class,
                () -> registry.commitAssignment(new P2pPhysicalToteAssignment(
                        assignment.physicalToteId(),
                        "SC-104",
                        second.lineId(),
                        second.destination())));
        assertThrows(IllegalStateException.class,
                () -> registry.commitAssignment(assignment("physical-2", "SC-108", first)));
        assertThrows(IllegalStateException.class,
                () -> registry.commitAssignment(assignment("physical-3", "SC-104", second)));
        assertThrows(IllegalArgumentException.class,
                () -> registry.commitAssignment(new P2pPhysicalToteAssignment(
                        new PhysicalToteId("physical-4"),
                        "SC-104",
                        first.lineId(),
                        new OperationalRouteDestination(StationType.P2P, "wrong-target"))));
        assertThrows(IllegalArgumentException.class, () -> registry.commitAssignment(null));
        assertThrows(IllegalArgumentException.class, () -> registry.findAssignment(null));
    }

    @Test
    void shouldReleaseOnlyExpectedOwnerFromAQuiescentLine() {
        P2pLineDefinition line = definition("line-1", "target-1");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(line));
        registry.acquireLease(line.lineId(), "SC-104", P2pLineActivitySnapshot.idle());
        P2pPhysicalToteAssignment historicalAssignment = assignment(
                "physical-1", "SC-104", line);
        registry.commitAssignment(historicalAssignment);

        assertThrows(IllegalStateException.class,
                () -> registry.releaseLease(
                        line.lineId(), "SC-108", P2pLineActivitySnapshot.idle()));
        assertThrows(IllegalStateException.class,
                () -> registry.releaseLease(line.lineId(), "SC-104", busyInputActivity()));
        assertThrows(IllegalStateException.class,
                () -> registry.releaseLease(
                        line.lineId(), "SC-104", activityWithOpenTote(line, "SC-104")));

        registry.releaseLease(line.lineId(), " SC-104 ", P2pLineActivitySnapshot.idle());

        assertEquals(Optional.empty(), registry.ownerFor(line.lineId()));
        P2pLineLeaseSnapshot released = registry.snapshot(
                Map.of(line.lineId(), P2pLineActivitySnapshot.idle()))
                .findLine(line.lineId())
                .orElseThrow();
        assertEquals(List.of(historicalAssignment), released.physicalAssignments());
        assertTrue(released.serviceCentreId().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> registry.releaseLease(
                        line.lineId(), "SC-104", P2pLineActivitySnapshot.idle()));

        registry.acquireLease(line.lineId(), "SC-108", P2pLineActivitySnapshot.idle());
        registry.commitAssignment(historicalAssignment);
        assertEquals(Optional.of(historicalAssignment),
                registry.findAssignment(historicalAssignment.physicalToteId()));
    }

    @Test
    void shouldPublishDetachedOrderedSnapshotsFromExactActivitySet() {
        P2pLineDefinition first = definition("line-1", "target-1");
        P2pLineDefinition second = definition("line-2", "target-2");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(second, first));
        registry.acquireLease(first.lineId(), "SC-104", P2pLineActivitySnapshot.idle());
        P2pPhysicalToteAssignment assignment = assignment("physical-1", "SC-104", first);
        registry.commitAssignment(assignment);
        Map<P2pLineId, P2pLineActivitySnapshot> source = new LinkedHashMap<>();
        source.put(first.lineId(), busyInputActivity());
        source.put(second.lineId(), P2pLineActivitySnapshot.idle());

        P2pLineLeaseCatalogSnapshot snapshot = registry.snapshot(source);
        source.clear();

        assertEquals(List.of(second, first), snapshot.lines().stream()
                .map(P2pLineLeaseSnapshot::definition)
                .toList());
        assertEquals(Optional.of(assignment),
                snapshot.findAssignment(assignment.physicalToteId()));
        assertEquals(busyInputActivity(),
                snapshot.findLine(first.lineId()).orElseThrow().activity());
        assertThrows(IllegalArgumentException.class, () -> registry.snapshot(null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.snapshot(Map.of(first.lineId(), P2pLineActivitySnapshot.idle())));

        Map<P2pLineId, P2pLineActivitySnapshot> withUnknown = new LinkedHashMap<>();
        withUnknown.put(first.lineId(), P2pLineActivitySnapshot.idle());
        withUnknown.put(new P2pLineId("unknown"), P2pLineActivitySnapshot.idle());
        assertThrows(IllegalArgumentException.class, () -> registry.snapshot(withUnknown));

        Map<P2pLineId, P2pLineActivitySnapshot> withNull = new LinkedHashMap<>();
        withNull.put(first.lineId(), P2pLineActivitySnapshot.idle());
        withNull.put(second.lineId(), null);
        assertThrows(IllegalArgumentException.class, () -> registry.snapshot(withNull));
    }

    private static P2pLineDefinition definition(String lineId, String targetId) {
        return new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, targetId));
    }

    private static P2pPhysicalToteAssignment assignment(
            String physicalToteId,
            String serviceCentreId,
            P2pLineDefinition definition) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalToteId),
                serviceCentreId,
                definition.lineId(),
                definition.destination());
    }

    private static Map<P2pLineId, P2pLineActivitySnapshot> idleActivities(
            P2pLineDefinition... definitions) {
        Map<P2pLineId, P2pLineActivitySnapshot> activities = new LinkedHashMap<>();
        for (P2pLineDefinition definition : definitions) {
            activities.put(definition.lineId(), P2pLineActivitySnapshot.idle());
        }
        return activities;
    }

    private static P2pLineActivitySnapshot busyInputActivity() {
        return new P2pLineActivitySnapshot(
                new P2pInputActivitySnapshot(1, 0, false, 0),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.empty());
    }

    private static P2pLineActivitySnapshot activityWithOpenTote(
            P2pLineDefinition definition,
            String serviceCentreId) {
        return new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.of(new OutboundToteSnapshot(
                        new PhysicalToteId("outbound-1"),
                        definition.lineId(),
                        Optional.of(serviceCentreId),
                        Optional.of("pharmacy-1"),
                        10,
                        List.of(),
                        Optional.empty())));
    }
}
