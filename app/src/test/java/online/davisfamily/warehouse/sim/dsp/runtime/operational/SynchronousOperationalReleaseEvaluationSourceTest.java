package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.ServiceCentrePharmacyGroup;

class SynchronousOperationalReleaseEvaluationSourceTest {

    @Test
    void shouldEvaluateOperationalSnapshotSynchronously() {
        DspOperationalReleaseSnapshot snapshot = snapshot("tote-1", "order-1");
        SynchronousOperationalReleaseEvaluationSource source =
                new SynchronousOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler());

        assertTrue(source.canSubmit());
        assertEquals("sync", source.modeLabel());

        source.submit(snapshot);

        assertFalse(source.canSubmit());
        OperationalReleaseEvaluationResult result = source.pollResult().orElseThrow();
        assertEquals(0L, result.sequence());
        assertSame(snapshot, result.snapshot());
        assertEquals(
                new PhysicalToteId("tote-1"),
                result.evaluation().releaseDecision().orElseThrow()
                        .command().physicalToteId());
        assertTrue(source.canSubmit());
        assertTrue(source.pollResult().isEmpty());
    }

    @Test
    void shouldPreserveSnapshotAndMonotonicSequence() {
        DspOperationalReleaseSnapshot firstSnapshot = snapshot("tote-1", "order-1");
        DspOperationalReleaseSnapshot secondSnapshot = snapshot("tote-2", "order-2");
        SynchronousOperationalReleaseEvaluationSource source =
                new SynchronousOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler());

        source.submit(firstSnapshot);
        OperationalReleaseEvaluationResult first = source.pollResult().orElseThrow();
        source.submit(secondSnapshot);
        OperationalReleaseEvaluationResult second = source.pollResult().orElseThrow();

        assertEquals(0L, first.sequence());
        assertEquals(1L, second.sequence());
        assertSame(firstSnapshot, first.snapshot());
        assertSame(secondSnapshot, second.snapshot());

        source.close();
        assertTrue(source.canSubmit());
    }

    @Test
    void shouldRejectSubmissionWhileResultIsPending() {
        DspOperationalReleaseSnapshot firstSnapshot = snapshot("tote-1", "order-1");
        DspOperationalReleaseSnapshot secondSnapshot = snapshot("tote-2", "order-2");
        SynchronousOperationalReleaseEvaluationSource source =
                new SynchronousOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler());

        source.submit(firstSnapshot);

        assertThrows(IllegalStateException.class, () -> source.submit(secondSnapshot));
        assertThrows(IllegalArgumentException.class, () -> source.submit(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SynchronousOperationalReleaseEvaluationSource(null));
    }

    private static DspOperationalReleaseSnapshot snapshot(
            String physicalToteId,
            String orderId) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                "product-" + orderId,
                1,
                "pharmacy-1",
                "patient-" + orderId,
                "prescription-" + orderId,
                DspOrderLineType.FULL_PACK,
                orderId,
                1,
                1);
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(item),
                999,
                1);
        DspSchedulerOrderState logicalState = new DspSchedulerOrderState(
                order,
                new RouteRequirements(
                        false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                OrderType.FULL_PACK,
                "sc-1",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate candidate = new DspOperationalReleaseCandidate(
                physicalCandidate, logicalState, List.of("pharmacy-1"));
        StationAdmissionSnapshot admission = new StationAdmissionSnapshot(
                StationType.P2P,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0),
                true,
                "",
                Optional.of("p2p-1"));
        return new DspOperationalReleaseSnapshot(
                List.of(candidate),
                List.of(new ServiceCentrePharmacyGroup(
                        "sc-1", "pharmacy-1", 0, 1)),
                Map.of(StationType.P2P, admission),
                Set.of());
    }
}
