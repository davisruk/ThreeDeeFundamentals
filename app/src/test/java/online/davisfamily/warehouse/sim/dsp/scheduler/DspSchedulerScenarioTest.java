package online.davisfamily.warehouse.sim.dsp.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

class DspSchedulerScenarioTest {

    @Test
    void shouldHoldLaterServiceCentreWhileActiveServiceCentreIsBlocked() {
        DspReleaseScheduler scheduler = scheduler();
        DspSchedulerOrderState associated = orderState(
                order(
                        "sc-a-associated",
                        "notional-a",
                        "SC-A",
                        1,
                        OrderType.ASSOCIATED,
                        1,
                        line("sc-a-associated-adapted", "0006515", DspOrderLineType.ADAPTED)),
                route(false, false, false, true, false),
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(order("sc-a-adapted", "notional-a", "SC-A", 1, OrderType.ADAPTED, 0), route(false, false, false, false, false), DspOrderStatus.WAITING),
                        associated,
                        orderState(order("sc-b-full", "notional-b", "SC-B", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of(),
                Optional.of("SC-A"));

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertTrue(evaluation.releaseDecision().isPresent());
        assertEquals("sc-a-adapted", evaluation.releaseDecision().get().orderId());

        WarehouseSchedulerSnapshot blockedSnapshot = snapshot(
                List.of(
                        orderState(order("sc-a-adapted", "notional-a", "SC-A", 1, OrderType.ADAPTED, 0), route(false, false, false, false, false), DspOrderStatus.RELEASED),
                        associated,
                        orderState(order("sc-b-full", "notional-b", "SC-B", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of(),
                Optional.of("SC-A"));

        SchedulerEvaluation blockedEvaluation = scheduler.evaluate(blockedSnapshot);

        assertFalse(blockedEvaluation.releaseDecision().isPresent());
        assertTrue(blockedEvaluation.blockedDecision().isPresent());
        assertEquals("SC-A", blockedEvaluation.blockedDecision().get().activeServiceCentreId());
        assertEquals(List.of("sc-a-associated"), blockedEvaluation.blockedDecision().get().candidateOrderIds());
    }

    @Test
    void shouldReleaseActiveServiceCentreInDependencyAndSheetOrder() {
        DspReleaseScheduler scheduler = scheduler();
        NotionalToteOrder adapted = order("sc-a-adapted", "notional-a", "SC-A", 1, OrderType.ADAPTED, 0);
        NotionalToteOrder associatedSheet1 = order(
                "sc-a-associated-1",
                "notional-a",
                "SC-A",
                1,
                OrderType.ASSOCIATED,
                1,
                line("sc-a-associated-1-adapted", "0006515", DspOrderLineType.ADAPTED));
        NotionalToteOrder associatedSheet2 = order(
                "sc-a-associated-2",
                "notional-a",
                "SC-A",
                2,
                OrderType.ASSOCIATED,
                2,
                line("sc-a-associated-2-adapted", "0006515", DspOrderLineType.ADAPTED));

        WarehouseSchedulerSnapshot step1 = snapshot(
                List.of(
                        orderState(adapted, route(false, false, false, false, false), DspOrderStatus.WAITING),
                        orderState(associatedSheet1, route(false, false, false, true, false), DspOrderStatus.WAITING),
                        orderState(associatedSheet2, route(false, false, false, true, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of(),
                Optional.of("SC-A"));
        assertEquals("sc-a-adapted", scheduler.evaluate(step1).releaseDecision().get().orderId());

        WarehouseSchedulerSnapshot step2 = snapshot(
                List.of(
                        orderState(adapted, route(false, false, false, false, false), DspOrderStatus.COMPLETED),
                        orderState(associatedSheet1, route(false, false, false, true, false), DspOrderStatus.WAITING),
                        orderState(associatedSheet2, route(false, false, false, true, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of(
                        PreparedLineKey.forDispatchLine(associatedSheet1, associatedSheet1.items().getFirst()),
                        PreparedLineKey.forDispatchLine(associatedSheet2, associatedSheet2.items().getFirst())),
                Optional.of("SC-A"));
        assertEquals("sc-a-associated-1", scheduler.evaluate(step2).releaseDecision().get().orderId());

        WarehouseSchedulerSnapshot step3 = snapshot(
                List.of(
                        orderState(adapted, route(false, false, false, false, false), DspOrderStatus.COMPLETED),
                        orderState(associatedSheet1, route(false, false, false, true, false), DspOrderStatus.RELEASED),
                        orderState(associatedSheet2, route(false, false, false, true, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of(
                        PreparedLineKey.forDispatchLine(associatedSheet1, associatedSheet1.items().getFirst()),
                        PreparedLineKey.forDispatchLine(associatedSheet2, associatedSheet2.items().getFirst())),
                Optional.of("SC-A"));
        assertEquals("sc-a-associated-2", scheduler.evaluate(step3).releaseDecision().get().orderId());
    }

    @Test
    void shouldReleaseOnlyAssociatedOrderWhosePreparedLinesAreReadyFromMixedPharmacyAdaptedWork() {
        DspReleaseScheduler scheduler = scheduler();
        NotionalToteOrder adapted = order(
                "sc-a-adapted",
                "prep-a",
                "SC-A",
                1,
                OrderType.ADAPTED,
                0,
                line("adapted-line-a", "0006515", DspOrderLineType.ADAPTED),
                line("adapted-line-b", "0006461", DspOrderLineType.ADAPTED));
        NotionalToteOrder associatedReady = order(
                "sc-a-associated-ready",
                "dispatch-a",
                "SC-A",
                1,
                OrderType.ASSOCIATED,
                1,
                line("shared-line-a", "0006515", DspOrderLineType.ADAPTED));
        NotionalToteOrder associatedBlocked = order(
                "sc-a-associated-blocked",
                "dispatch-b",
                "SC-A",
                1,
                OrderType.ASSOCIATED,
                2,
                line("shared-line-b", "0006461", DspOrderLineType.ADAPTED));

        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(adapted, route(false, false, false, false, false), DspOrderStatus.COMPLETED),
                        orderState(associatedReady, route(false, false, false, true, false), DspOrderStatus.WAITING),
                        orderState(associatedBlocked, route(false, false, false, true, false), DspOrderStatus.WAITING),
                        orderState(order("sc-b-full", "dispatch-c", "SC-B", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                stationAdmissions(admission(StationType.P2P, true, "")),
                Set.of(PreparedLineKey.forDispatchLine(associatedReady, associatedReady.items().getFirst())),
                Optional.of("SC-A"));

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertTrue(evaluation.releaseDecision().isPresent());
        assertEquals("sc-a-associated-ready", evaluation.releaseDecision().get().orderId());
        assertEquals("SC-A", evaluation.releaseDecision().get().serviceCentreId());
    }

    @Test
    void shouldMoveToNextServiceCentreAfterActiveServiceCentreCompletes() {
        DspReleaseScheduler scheduler = scheduler();
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(
                        orderState(order("sc-a-done", "notional-a", "SC-A", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.COMPLETED),
                        orderState(order("sc-b-next", "notional-b", "SC-B", 1, OrderType.FULL_PACK, 0), route(false, false, false, false, false), DspOrderStatus.WAITING)),
                stationAdmissions(),
                Set.of(),
                Optional.of("SC-A"));

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertTrue(evaluation.releaseDecision().isPresent());
        assertEquals("sc-b-next", evaluation.releaseDecision().get().orderId());
        assertEquals("SC-B", evaluation.releaseDecision().get().serviceCentreId());
    }

    @Test
    void shouldProduceCommandsWithoutApplyingSimulationSideEffects() {
        DspReleaseScheduler scheduler = scheduler();
        DspSchedulerOrderState waitingOrder = orderState(
                order("sc-a-full", "notional-a", "SC-A", 1, OrderType.FULL_PACK, 0),
                route(false, false, false, false, false),
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot snapshot = snapshot(
                List.of(waitingOrder),
                stationAdmissions(),
                Set.of(),
                Optional.of("SC-A"));

        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);

        assertTrue(evaluation.releaseDecision().isPresent());
        assertEquals("sc-a-full", evaluation.releaseDecision().get().command().orderId());
        assertEquals("SC-A", evaluation.releaseDecision().get().command().serviceCentreId());
        assertEquals(StartLocation.OSR, evaluation.releaseDecision().get().command().startLocation());
        assertEquals(DspOrderStatus.WAITING, snapshot.orderStates().getFirst().status());
        assertEquals("sc-a-full", snapshot.orderStates().getFirst().order().orderId());
    }

    private static DspReleaseScheduler scheduler() {
        return new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("SC-A", "SC-B"))),
                new DspDependencyEvaluator());
    }

    private static WarehouseSchedulerSnapshot snapshot(
            List<DspSchedulerOrderState> orderStates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            Optional<String> activeServiceCentreId) {
        return new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                preparedLineKeys,
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
            long sequenceNumber,
            DspOrderItem... items) {
        return new NotionalToteOrder(
                orderId,
                notionalToteId,
                serviceCentreId,
                sheetNumber,
                orderType,
                items.length == 0 ? List.of(new DspOrderItem("item-" + orderId, "product-1", 1)) : List.of(items),
                sequenceNumber);
    }

    private static DspOrderItem line(String itemId, String pharmacyId, DspOrderLineType lineType) {
        return new DspOrderItem(
                itemId,
                "product-" + itemId,
                1,
                pharmacyId,
                lineType,
                "prepared-" + itemId,
                1,
                0);
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
