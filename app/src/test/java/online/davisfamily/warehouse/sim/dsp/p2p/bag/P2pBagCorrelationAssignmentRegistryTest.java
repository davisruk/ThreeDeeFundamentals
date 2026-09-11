package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

class P2pBagCorrelationAssignmentRegistryTest {
    private static final P2pBagCorrelationRequirement BAG_A =
            new P2pBagCorrelationRequirement("bag-a", 2);
    private static final P2pBagCorrelationRequirement BAG_B =
            new P2pBagCorrelationRequirement("bag-b", 1);

    @Test
    void shouldPublishStableImmutableCorrelationIdsOnlyForGenuineAdditions() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        Set<String> initial = registry.correlationIdsSnapshot();

        assertSame(initial, registry.correlationIdsSnapshot());
        assertThrows(UnsupportedOperationException.class, () -> initial.clear());

        registry.commit(List.of(BAG_A), assignment("tote-1", "line-1"));
        Set<String> afterFirstAddition = registry.correlationIdsSnapshot();
        assertNotSame(initial, afterFirstAddition);
        assertEquals(Set.of("bag-a"), afterFirstAddition);
        assertSame(afterFirstAddition, registry.correlationIdsSnapshot());

        registry.commit(List.of(BAG_A), assignment("tote-2", "line-1"));
        assertSame(afterFirstAddition, registry.correlationIdsSnapshot());

        registry.commit(List.of(BAG_B), assignment("tote-3", "line-1"));
        Set<String> afterSecondAddition = registry.correlationIdsSnapshot();
        assertNotSame(afterFirstAddition, afterSecondAddition);
        assertEquals(Set.of("bag-a", "bag-b"), afterSecondAddition);
    }

    @Test
    void shouldAppendExactFirstPinAndKeepItWhenTheSameBagArrivesInAnotherTote() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        P2pPhysicalToteAssignment firstAssignment = assignment("tote-1", "line-1");

        registry.commit(List.of(BAG_A), firstAssignment);
        P2pBagCorrelationAssignmentSnapshot beforeSecondTote = registry.snapshot();
        registry.commit(List.of(BAG_A), assignment("tote-2", "line-1"));

        assertEquals(firstAssignment, registry.find("bag-a").orElseThrow().p2pAssignment());
        assertEquals(beforeSecondTote, registry.snapshot());
        assertEquals(new P2pLineId("line-1"), registry.lineFor("bag-a").orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> beforeSecondTote.assignments().clear());
    }

    @Test
    void shouldRejectMixedExistingPinsBeforeMutatingTheRegistry() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        registry.commit(List.of(BAG_A), assignment("tote-a", "line-1"));
        registry.commit(List.of(BAG_B), assignment("tote-b", "line-2"));
        P2pBagCorrelationAssignmentSnapshot before = registry.snapshot();
        Set<String> correlationIdsBefore = registry.correlationIdsSnapshot();

        assertThrows(
                IllegalStateException.class,
                () -> registry.commit(
                        List.of(BAG_A, BAG_B), assignment("tote-c", "line-1")));

        assertEquals(before, registry.snapshot());
        assertSame(correlationIdsBefore, registry.correlationIdsSnapshot());
        assertFalse(registry.compatibleWith(
                List.of(BAG_A), registry.snapshot(), new P2pLineId("line-2")));
        assertTrue(registry.compatibleWith(
                List.of(BAG_A), registry.snapshot(), new P2pLineId("line-1")));
    }

    @Test
    void shouldRejectDuplicateCorrelationRequirementsById() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.commit(
                        List.of(
                                BAG_A,
                                new P2pBagCorrelationRequirement("bag-a", 99)),
                        assignment("tote-1", "line-1")));
        assertTrue(registry.snapshot().assignments().isEmpty());
    }

    private static P2pPhysicalToteAssignment assignment(String toteId, String lineId) {
        P2pLineId line = new P2pLineId(lineId);
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(toteId),
                "SC-1",
                line,
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
    }
}
