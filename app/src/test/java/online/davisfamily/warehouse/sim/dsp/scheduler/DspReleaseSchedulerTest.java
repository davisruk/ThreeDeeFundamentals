package online.davisfamily.warehouse.sim.dsp.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

class DspReleaseSchedulerTest {

    @Test
    void shouldReleaseHighestPriorityEligibleOrderInActiveServiceCentre() {
        DspReleaseScheduler scheduler = scheduler(List.of("sc-1", "sc-2"));
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(order("full", "notional-full", "sc-1", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.WAITING),
                        orderState(order("assoc", "notional-assoc", "sc-1", 1, OrderType.ASSOCIATED, 0), route(false, false, false, true, false), DspOrderStatus.WAITING),
                        orderState(order("adapted", "notional-adapted", "sc-1", 1, OrderType.ADAPTED, 0), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of("notional-assoc"),
                Set.of(),
                Optional.empty());

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertTrue(evaluation.releaseDecision().isPresent());
        assertFalse(evaluation.blockedDecision().isPresent());
        assertEquals("adapted", evaluation.releaseDecision().get().orderId());
        assertEquals("adapted", evaluation.releaseDecision().get().command().orderId());
    }

    @Test
    void shouldUseSheetSequenceThenSequenceNumberThenOrderIdAsTieBreakers() {
        DspReleaseScheduler scheduler = scheduler(List.of("sc-1"));
        WarehouseSchedulerSnapshot sheetSnapshot = snapshot(
                List.of(
                        orderState(order("order-b", "notional-a", "sc-1", 2, OrderType.FULL_PACK, 5), route(false, false, false, false, false), DspOrderStatus.WAITING),
                        orderState(order("order-a", "notional-a", "sc-1", 1, OrderType.FULL_PACK, 7), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                Map.of(),
                Set.of(),
                Set.of(),
                Optional.empty());
        assertEquals("order-a", scheduler.evaluate(sheetSnapshot).releaseDecision().get().orderId());

        WarehouseSchedulerSnapshot sequenceSnapshot = snapshot(
                List.of(
                        orderState(order("order-b", "notional-a", "sc-1", 1, OrderType.FULL_PACK, 5), route(false, false, false, false, false), DspOrderStatus.WAITING),
                        orderState(order("order-a", "notional-b", "sc-1", 1, OrderType.FULL_PACK, 2), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                Map.of(),
                Set.of(),
                Set.of(),
                Optional.empty());
        assertEquals("order-a", scheduler.evaluate(sequenceSnapshot).releaseDecision().get().orderId());

        WarehouseSchedulerSnapshot orderIdSnapshot = snapshot(
                List.of(
                        orderState(order("order-b", "notional-a", "sc-1", 1, OrderType.FULL_PACK, 2), route(false, false, false, false, false), DspOrderStatus.WAITING),
                        orderState(order("order-a", "notional-b", "sc-1", 1, OrderType.FULL_PACK, 2), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                Map.of(),
                Set.of(),
                Set.of(),
                Optional.empty());
        assertEquals("order-a", scheduler.evaluate(orderIdSnapshot).releaseDecision().get().orderId());
    }

    @Test
    void shouldReturnBlockedDecisionWhenActiveServiceCentreHasOnlyBlockedWork() {
        DspReleaseScheduler scheduler = scheduler(List.of("sc-1", "sc-2"));
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(order("assoc-a", "notional-a", "sc-1", 1, OrderType.ASSOCIATED, 0), route(false, false, false, true, false), DspOrderStatus.WAITING),
                        orderState(order("assoc-b", "notional-b", "sc-1", 1, OrderType.ASSOCIATED, 0), route(false, false, false, true, false), DspOrderStatus.BLOCKED),
                        orderState(order("full-c", "notional-c", "sc-2", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of(),
                Set.of(),
                Optional.of("sc-1"));

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertFalse(evaluation.releaseDecision().isPresent());
        assertTrue(evaluation.blockedDecision().isPresent());
        assertEquals("sc-1", evaluation.blockedDecision().get().activeServiceCentreId());
        assertEquals(List.of("assoc-a", "assoc-b"), evaluation.blockedDecision().get().candidateOrderIds());
        assertTrue(evaluation.blockedDecision().get().blockReasons().stream()
                .anyMatch(reason -> reason.contains("Adapted work is not complete")));
    }

    @Test
    void shouldReturnNothingWhenNoServiceCentreHasUnreleasedWork() {
        DspReleaseScheduler scheduler = scheduler(List.of("sc-1"));
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(orderState(order("done", "notional-a", "sc-1", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.COMPLETED)),
                Map.of(),
                Set.of(),
                Set.of(),
                Optional.empty());

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertFalse(evaluation.releaseDecision().isPresent());
        assertFalse(evaluation.blockedDecision().isPresent());
    }

    @Test
    void shouldBlockWhenRequiredStationHasNoAdmissionSnapshot() {
        DspReleaseScheduler scheduler = scheduler(List.of("sc-1"));
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(orderState(order("manual", "notional-a", "sc-1", 1, OrderType.FULL_PACK, 0), route(false, false, true, false, false), DspOrderStatus.WAITING)),
                Map.of(),
                Set.of(),
                Set.of(),
                Optional.empty());

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertFalse(evaluation.releaseDecision().isPresent());
        assertTrue(evaluation.blockedDecision().isPresent());
        assertTrue(evaluation.blockedDecision().get().blockReasons().stream()
                .anyMatch(reason -> reason.contains("Missing station admission snapshot for MANUAL")));
    }

    @Test
    void shouldEmitReleaseCommandWithoutMutatingSnapshotState() {
        DspReleaseScheduler scheduler = scheduler(List.of("sc-1"));
        List<DspSchedulerOrderState> originalOrderStates = new ArrayList<>();
        DspSchedulerOrderState waitingOrder = orderState(
                order("assoc", "notional-a", "sc-1", 1, OrderType.ASSOCIATED, 0),
                route(false, false, false, true, false),
                DspOrderStatus.WAITING);
        originalOrderStates.add(waitingOrder);

        WarehouseSchedulerSnapshot snapshot = snapshot(
                originalOrderStates,
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of("notional-a"),
                Set.of(),
                Optional.empty());

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertTrue(evaluation.releaseDecision().isPresent());
        assertEquals(DspOrderStatus.WAITING, snapshot.orderStates().getFirst().status());
        assertEquals("assoc", snapshot.orderStates().getFirst().order().orderId());
        assertEquals("assoc", evaluation.releaseDecision().get().command().orderId());
        assertEquals("sc-1", evaluation.releaseDecision().get().command().serviceCentreId());
        assertEquals(StartLocation.OSR, evaluation.releaseDecision().get().command().startLocation());
    }

    private static DspReleaseScheduler scheduler(List<String> serviceCentrePriority) {
        return new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(serviceCentrePriority)),
                new DspDependencyEvaluator());
    }

    private static WarehouseSchedulerSnapshot snapshot(
            List<DspSchedulerOrderState> orderStates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<String> completedAdaptedNotionalToteIds,
            Set<String> manualReadyNotionalToteIds,
            Optional<String> activeServiceCentreId) {
        return new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                completedAdaptedNotionalToteIds,
                manualReadyNotionalToteIds,
                activeServiceCentreId);
    }

    private static Map<StationType, StationAdmissionSnapshot> stationAdmissions(StationAdmissionSnapshot... admissions) {
        Map<StationType, StationAdmissionSnapshot> result = new LinkedHashMap<>();
        for (StationAdmissionSnapshot admission : admissions) {
            result.put(admission.stationType(), admission);
        }
        return result;
    }

    private static StationAdmissionSnapshot admission(StationType stationType, boolean open, String blockedReason) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                open,
                blockedReason);
    }

    private static DspSchedulerOrderState orderState(
            NotionalToteOrder order,
            RouteRequirements routeRequirements,
            DspOrderStatus status) {
        return new DspSchedulerOrderState(order, routeRequirements, status);
    }

    private static NotionalToteOrder order(
            String orderId,
            String notionalToteId,
            String serviceCentreId,
            int sheetNumber,
            OrderType orderType,
            long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                notionalToteId,
                serviceCentreId,
                sheetNumber,
                orderType,
                List.of(new DspOrderItem("item-" + orderId, "product-1", 1)),
                sequenceNumber);
    }

    private static RouteRequirements route(
            boolean requiresThirdParty,
            boolean requiresSortable,
            boolean requiresManual,
            boolean requiresP2p,
            boolean requiresManualMerge) {
        return new RouteRequirements(
                requiresThirdParty,
                requiresSortable,
                requiresManual,
                requiresP2p,
                requiresManualMerge,
                StartLocation.OSR);
    }
}
