package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

class AdaptingSchedulerIntegrationTest {

    @Test
    void shouldReleaseCollectingOrderWithDeterministicSelectedBenchIdAfterStoreCompletes() {
        AdaptingStorageMap storageMap = storageMap();
        storageMap.assignPharmacyToBench("0000310", new AdaptingBenchId("bench-2"));
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap));
        AdaptingBench bench1 = new AdaptingBench("bench-1", store, 1d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", store, 1d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 0, storageMap);
        DspSchedulerRuntimeState runtimeState = runtimeState(collectingOrderState("dispatch-1"));
        AdaptingAreaController controller = new AdaptingAreaController(area, runtimeState);
        DspReleaseScheduler scheduler = scheduler(area);

        DspOrderItem stagedLine = adaptedPreparedLine("line-1", "dispatch-1");
        area.submitVisit(AdaptingVisit.store(new PhysicalToteId("store-tote-1"), List.of(stagedLine)));
        bench2.startProcessing();
        bench2.tick(1d);
        controller.applyBenchCompletion(new AdaptingBenchId("bench-2")).orElseThrow();

        WarehouseSchedulerSnapshot snapshot = snapshotWithAdaptingAdmission(runtimeState.snapshot(), area);
        var evaluation = scheduler.evaluate(snapshot);

        assertTrue(evaluation.releaseDecision().isPresent());
        assertEquals("dispatch-1", evaluation.releaseDecision().get().orderId());
        assertEquals(Optional.of("bench-2"),
                evaluation.releaseDecision().get().selectedStationTargets().selectedTargetIdFor(StationType.ADAPTING));
    }

    @Test
    void shouldBlockCollectingOrderBeforeStoreCompletionAndWhenAllBenchesAreFull() {
        AdaptingStorageMap storageMap = storageMap();
        AdaptedLineStore store = new AdaptedLineStore(new AdaptingStorageLayout(
                AdaptingStorageConfig.defaults(),
                storageMap));
        AdaptingBench bench1 = new AdaptingBench("bench-1", store, 1d);
        AdaptingBench bench2 = new AdaptingBench("bench-2", store, 1d);
        AdaptingArea area = new AdaptingArea(List.of(bench1, bench2), 0, storageMap);
        DspSchedulerRuntimeState runtimeState = runtimeState(collectingOrderState("dispatch-2"));
        DspReleaseScheduler scheduler = scheduler(area);

        WarehouseSchedulerSnapshot missingReadySnapshot = snapshotWithAdaptingAdmission(runtimeState.snapshot(), area);
        var blockedByDependency = scheduler.evaluate(missingReadySnapshot);
        assertFalse(blockedByDependency.releaseDecision().isPresent());
        assertTrue(blockedByDependency.blockedDecision().isPresent());
        assertTrue(blockedByDependency.blockedDecision().get().blockReasons().stream()
                .anyMatch(reason -> reason.contains("Adapted work is not complete")));

        bench1.acceptVisit(AdaptingVisit.store(
                new PhysicalToteId("busy-1"),
                List.of(adaptedPreparedLine("busy-line-1", "other-order-1"))));
        bench2.acceptVisit(AdaptingVisit.store(
                new PhysicalToteId("busy-2"),
                List.of(adaptedPreparedLine("busy-line-2", "other-order-2"))));
        runtimeState.addPreparedLineKey(online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey.forDispatchLine(
                collectingOrderState("dispatch-2").order(),
                collectingOrderState("dispatch-2").order().items().getFirst()));

        WarehouseSchedulerSnapshot fullAreaSnapshot = snapshotWithAdaptingAdmission(runtimeState.snapshot(), area);
        var blockedByCapacity = scheduler.evaluate(fullAreaSnapshot);
        assertFalse(blockedByCapacity.releaseDecision().isPresent());
        assertTrue(blockedByCapacity.blockedDecision().isPresent());
        assertTrue(blockedByCapacity.blockedDecision().get().blockReasons().stream()
                .anyMatch(reason -> reason.contains("No adapting bench has queue or processing capacity")));
    }

    private static DspReleaseScheduler scheduler(AdaptingArea area) {
        return new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("SC-1"))),
                new DspDependencyEvaluator(),
                new AdaptingStationAdmissionResolver(
                        (stationType, candidate, snapshot) -> snapshot.stationAdmissions().get(stationType),
                        area,
                        new StationCapacity(2, 0)));
    }

    private static DspSchedulerRuntimeState runtimeState(DspSchedulerOrderState orderState) {
        return new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(orderState),
                Map.of(
                        StationType.ADAPTING, new StationAdmissionSnapshot(
                                StationType.ADAPTING,
                                new StationCapacity(2, 0),
                                new StationSnapshot(StationType.ADAPTING, 0, 0),
                                true,
                                ""),
                        StationType.P2P, new StationAdmissionSnapshot(
                                StationType.P2P,
                                new StationCapacity(1, 1),
                                new StationSnapshot(StationType.P2P, 0, 0),
                                true,
                                "")),
                Set.of(),
                Optional.empty()));
    }

    private static WarehouseSchedulerSnapshot snapshotWithAdaptingAdmission(
            WarehouseSchedulerSnapshot baseSnapshot,
            AdaptingArea area) {
        DspSchedulerOrderState candidate = baseSnapshot.orderStates().getFirst();
        StationAdmissionSnapshot adaptingAdmission = new AdaptingStationAdmissionAdapter(area, new StationCapacity(2, 0))
                .admissionFor(candidate);
        return new WarehouseSchedulerSnapshot(
                baseSnapshot.orderStates(),
                Map.of(
                        StationType.ADAPTING, adaptingAdmission,
                        StationType.P2P, baseSnapshot.stationAdmissions().get(StationType.P2P)),
                baseSnapshot.preparedLineKeys(),
                baseSnapshot.activeServiceCentreId());
    }

    private static DspSchedulerOrderState collectingOrderState(String orderId) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "SC-1",
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem(
                        "line-1",
                        "product-line-1",
                        1,
                        "0000310",
                        DspOrderLineType.ADAPTED,
                        orderId,
                        1,
                        0)),
                0L);

        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, true, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static DspOrderItem adaptedPreparedLine(String lineId, String targetOrderId) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                1,
                "0000310",
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0);
    }

    private static AdaptingStorageMap storageMap() {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(new AdaptingBenchId("bench-1"), new AdaptingBenchId("bench-2")));
        return storageMap;
    }
}
