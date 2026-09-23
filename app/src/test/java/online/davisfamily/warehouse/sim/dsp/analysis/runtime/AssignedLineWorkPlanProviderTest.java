package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagWorkPlanProvider;

class AssignedLineWorkPlanProviderTest {

    @Test
    void shouldPublishAssignedCorrelationsWithoutScanningAllWork() {
        P2pLineId firstLine = new P2pLineId("line-a");
        P2pLineId secondLine = new P2pLineId("line-b");
        P2pBagCorrelationAssignmentSnapshot initial = snapshot(
                assignment("correlation-a-1", firstLine),
                assignment("correlation-b-1", secondLine));
        AtomicReference<P2pBagCorrelationAssignmentSnapshot> published =
                new AtomicReference<>(initial);
        AtomicInteger snapshotReads = new AtomicInteger();
        CountingWorkPlan allWork = new CountingWorkPlan(Map.of(
                "correlation-a-1", 3,
                "correlation-b-1", 2));
        AssignedLineWorkPlanProvider provider = new AssignedLineWorkPlanProvider(
                firstLine,
                allWork,
                () -> {
                    snapshotReads.incrementAndGet();
                    return published.get();
                });

        Set<String> initialIds = provider.expectedCorrelationIds();
        assertEquals(List.of("correlation-a-1"), initialIds.stream().toList());
        assertSame(initial.correlationIdsFor(firstLine), initialIds);
        assertEquals(1, snapshotReads.get());
        assertEquals(OptionalInt.of(3), provider.expectedPackCount("correlation-a-1"));
        assertEquals(2, snapshotReads.get());
        assertEquals(1, allWork.packCountReads);
        assertTrue(provider.expectedPackCount("correlation-b-1").isEmpty());
        assertEquals(3, snapshotReads.get());
        assertEquals(1, allWork.packCountReads);

        P2pBagCorrelationAssignmentSnapshot replacement = snapshot(
                assignment("correlation-a-1", firstLine),
                assignment("correlation-a-2", firstLine));
        published.set(replacement);
        Set<String> replacementIds = provider.expectedCorrelationIds();
        assertEquals(List.of("correlation-a-1", "correlation-a-2"),
                replacementIds.stream().toList());
        assertSame(replacement.correlationIdsFor(firstLine), replacementIds);
        assertEquals(4, snapshotReads.get());
    }

    @Test
    void shouldRejectInvalidConstructionAndNullPublishedSnapshots() {
        P2pLineId line = new P2pLineId("line-a");
        CountingWorkPlan allWork = new CountingWorkPlan(Map.of());
        P2pBagCorrelationAssignmentSnapshot snapshot = snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> new AssignedLineWorkPlanProvider(null, allWork, () -> snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new AssignedLineWorkPlanProvider(line, null, () -> snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new AssignedLineWorkPlanProvider(line, allWork, null));

        AssignedLineWorkPlanProvider provider = new AssignedLineWorkPlanProvider(
                line, allWork, () -> null);
        assertThrows(IllegalStateException.class, provider::expectedCorrelationIds);
    }

    private static P2pBagCorrelationAssignmentSnapshot snapshot(
            P2pBagCorrelationAssignment... assignments) {
        return new P2pBagCorrelationAssignmentSnapshot(List.of(assignments));
    }

    private static P2pBagCorrelationAssignment assignment(
            String correlationId,
            P2pLineId lineId) {
        return new P2pBagCorrelationAssignment(correlationId, lineId);
    }

    private static final class CountingWorkPlan implements ToteToBagWorkPlanProvider {
        private final Map<String, Integer> packCounts;
        private int packCountReads;

        private CountingWorkPlan(Map<String, Integer> packCounts) {
            this.packCounts = packCounts;
        }

        @Override
        public OptionalInt expectedPackCount(String correlationId) {
            packCountReads++;
            Integer count = packCounts.get(correlationId);
            return count == null ? OptionalInt.empty() : OptionalInt.of(count);
        }

        @Override
        public Set<String> expectedCorrelationIds() {
            throw new AssertionError("assigned provider must not scan all work");
        }
    }
}
