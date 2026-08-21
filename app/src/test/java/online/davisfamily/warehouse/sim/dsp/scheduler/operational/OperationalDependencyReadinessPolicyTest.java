package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class OperationalDependencyReadinessPolicyTest {

    private final OperationalDependencyReadinessPolicy policy =
            new OperationalDependencyReadinessPolicy();

    @Test
    void shouldAllowAdaptedAndFullPackWithoutGlobalPreparationBarrier() {
        DspOperationalReleaseCandidate adapted = candidate(
                "adapted-tote",
                "adapted-order",
                OrderType.ADAPTED,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty(),
                List.of(line("adapted-line", DspOrderLineType.ADAPTED)));
        DspOperationalReleaseCandidate fullPack = candidate(
                "full-pack-tote",
                "full-pack-order",
                OrderType.FULL_PACK,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty(),
                List.of(line("full-pack-line", DspOrderLineType.FULL_PACK)));
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(adapted, fullPack), Set.of());

        assertTrue(policy.findBlocks(adapted, snapshot).isEmpty());
        assertTrue(policy.findBlocks(fullPack, snapshot).isEmpty());
    }

    @Test
    void shouldBlockAssociatedOnlyForItsOwnMissingAdaptedLines() {
        DspOperationalReleaseCandidate associated = candidate(
                "associated-tote",
                "associated-order",
                OrderType.ASSOCIATED,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty(),
                List.of(
                        line("adapted-line", DspOrderLineType.ADAPTED),
                        line("manual-line", DspOrderLineType.MANUAL),
                        line("full-pack-line", DspOrderLineType.FULL_PACK)));

        List<OperationalReleaseBlock> blocks = policy.findBlocks(
                associated, snapshot(List.of(associated), Set.of()));

        assertEquals(1, blocks.size());
        assertEquals(OperationalReleaseBlockType.ADAPTED_DEPENDENCY, blocks.get(0).type());
        assertTrue(blocks.get(0).reason().contains("associated-order"));
        assertTrue(blocks.get(0).reason().contains("adapted-line"));
    }

    @Test
    void shouldAllowAssociatedWhenEveryOwnDependencyIsReady() {
        DspOrderItem first = line("adapted-line-1", DspOrderLineType.ADAPTED);
        DspOrderItem second = line("adapted-line-2", DspOrderLineType.ADAPTED);
        DspOperationalReleaseCandidate associated = candidate(
                "associated-tote",
                "associated-order",
                OrderType.ASSOCIATED,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty(),
                List.of(first, second));
        Set<PreparedLineKey> preparedLineKeys = Set.of(
                PreparedLineKey.forDispatchLine(associated.logicalOrderState().order(), first),
                PreparedLineKey.forDispatchLine(associated.logicalOrderState().order(), second));

        assertTrue(policy.findBlocks(
                associated,
                snapshot(List.of(associated), preparedLineKeys)).isEmpty());
    }

    @Test
    void shouldIgnorePreparedLinesForUnrelatedOrders() {
        DspOperationalReleaseCandidate associated = candidate(
                "associated-tote",
                "associated-order",
                OrderType.ASSOCIATED,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty(),
                List.of(line("adapted-line", DspOrderLineType.ADAPTED)));
        Set<PreparedLineKey> unrelatedPreparedLines = Set.of(
                new PreparedLineKey("different-order", "adapted-line"),
                new PreparedLineKey("associated-order", "different-line"));

        List<OperationalReleaseBlock> blocks = policy.findBlocks(
                associated,
                snapshot(List.of(associated), unrelatedPreparedLines));

        assertEquals(1, blocks.size());
        assertEquals(OperationalReleaseBlockType.ADAPTED_DEPENDENCY, blocks.get(0).type());
    }

    @Test
    void shouldExposeActivePhysicalSheetBlocker() {
        DspOperationalReleaseCandidate blocked = candidate(
                "waiting-tote",
                "full-pack-order",
                OrderType.FULL_PACK,
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                Optional.of(new PhysicalToteId("active-tote")),
                List.of(line("full-pack-line", DspOrderLineType.FULL_PACK)));

        List<OperationalReleaseBlock> blocks = policy.findBlocks(
                blocked, snapshot(List.of(blocked), Set.of()));

        assertEquals(1, blocks.size());
        assertEquals(
                OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT,
                blocks.get(0).type());
        assertTrue(blocks.get(0).reason().contains("waiting-tote"));
        assertTrue(blocks.get(0).reason().contains("active-tote"));
    }

    @Test
    void shouldPreserveDeterministicDependencyBlockOrder() {
        DspOperationalReleaseCandidate blocked = candidate(
                "waiting-tote",
                "associated-order",
                OrderType.ASSOCIATED,
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                Optional.of(new PhysicalToteId("active-tote")),
                List.of(
                        line("adapted-line-b", DspOrderLineType.ADAPTED),
                        line("full-pack-line", DspOrderLineType.FULL_PACK),
                        line("adapted-line-a", DspOrderLineType.ADAPTED)));

        List<OperationalReleaseBlock> blocks = policy.findBlocks(
                blocked, snapshot(List.of(blocked), Set.of()));

        assertEquals(List.of(
                OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT,
                OperationalReleaseBlockType.ADAPTED_DEPENDENCY,
                OperationalReleaseBlockType.ADAPTED_DEPENDENCY),
                blocks.stream().map(OperationalReleaseBlock::type).toList());
        assertTrue(blocks.get(1).reason().contains("adapted-line-b"));
        assertTrue(blocks.get(2).reason().contains("adapted-line-a"));
        assertThrows(UnsupportedOperationException.class, () -> blocks.clear());
        assertThrows(IllegalArgumentException.class, () -> policy.findBlocks(null, snapshot(
                List.of(blocked), Set.of())));
        assertThrows(IllegalArgumentException.class, () -> policy.findBlocks(blocked, null));
    }

    private static DspOperationalReleaseSnapshot snapshot(
            List<DspOperationalReleaseCandidate> candidates,
            Set<PreparedLineKey> preparedLineKeys) {
        return new DspOperationalReleaseSnapshot(
                candidates,
                List.of(new ServiceCentrePharmacyGroup("sc-1", "pharmacy-1", 0, 1)),
                Map.of(),
                preparedLineKeys);
    }

    private static DspOperationalReleaseCandidate candidate(
            String physicalToteId,
            String orderId,
            OrderType orderType,
            OsrProcessingReleaseAvailability availability,
            Optional<PhysicalToteId> blockingPhysicalToteId,
            List<DspOrderItem> items) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                orderType,
                items,
                999,
                1);
        DspSchedulerOrderState logicalState = new DspSchedulerOrderState(
                order,
                new RouteRequirements(
                        false,
                        orderType == OrderType.ADAPTED,
                        false,
                        orderType != OrderType.ADAPTED,
                        false,
                        StartLocation.OSR),
                DspOrderStatus.WAITING);
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                orderType,
                "sc-1",
                1,
                availability,
                blockingPhysicalToteId);
        return new DspOperationalReleaseCandidate(
                physicalCandidate, logicalState, List.of("pharmacy-1"));
    }

    private static DspOrderItem line(
            String lineReference,
            DspOrderLineType lineType) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                "pharmacy-1",
                "patient-" + lineReference,
                "prescription-" + lineReference,
                lineType,
                "prepared-order-" + lineReference,
                1,
                1);
    }
}
