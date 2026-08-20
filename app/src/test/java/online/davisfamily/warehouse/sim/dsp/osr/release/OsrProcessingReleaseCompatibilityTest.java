package online.davisfamily.warehouse.sim.dsp.osr.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerEvaluationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SynchronousSchedulerEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseOrderCommand;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class OsrProcessingReleaseCompatibilityTest {

    @Test
    void shouldKeepLegacyOrderReleaseCommandSeparateFromPhysicalOsrCommand() {
        WarehouseSchedulerSnapshot snapshot = schedulerSnapshot();
        DspReleaseScheduler scheduler = scheduler();

        SchedulerCommand legacyCommand = scheduler.evaluate(snapshot)
                .releaseDecision()
                .orElseThrow()
                .command();
        ReleasePhysicalToteFromOsrCommand physicalCommand =
                new ReleasePhysicalToteFromOsrCommand(
                        new PhysicalToteId("tote-1"),
                        new OrderSheetKey("order-1", 1),
                        "sc-1",
                        "target-1");

        ReleaseOrderCommand orderCommand = assertInstanceOf(
                ReleaseOrderCommand.class, legacyCommand);
        assertEquals("order-1", orderCommand.orderId());
        assertEquals("sc-1", orderCommand.serviceCentreId());
        assertEquals(StartLocation.OSR, orderCommand.startLocation());
        assertFalse(legacyCommand instanceof ReleasePhysicalToteFromOsrCommand);
        assertEquals("tote-1", physicalCommand.physicalToteId().value());
        assertEquals(new OrderSheetKey("order-1", 1), physicalCommand.orderSheetKey());
    }

    @Test
    void shouldLeaveWarehouseSchedulerSnapshotContractUnchanged() {
        List<DspSchedulerOrderState> orderStates = new ArrayList<>();
        orderStates.add(orderState());
        Map<StationType, StationAdmissionSnapshot> stationAdmissions =
                new LinkedHashMap<>();
        Set<PreparedLineKey> preparedLineKeys = new LinkedHashSet<>();
        Optional<String> activeServiceCentreId = Optional.of("sc-1");

        WarehouseSchedulerSnapshot snapshot = new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                preparedLineKeys,
                activeServiceCentreId);
        orderStates.clear();
        stationAdmissions.clear();
        preparedLineKeys.clear();

        assertEquals(1, snapshot.orderStates().size());
        assertTrue(snapshot.stationAdmissions().isEmpty());
        assertTrue(snapshot.preparedLineKeys().isEmpty());
        assertEquals(activeServiceCentreId, snapshot.activeServiceCentreId());
    }

    @Test
    void shouldKeepPhysicalCommitOutsideSchedulerEvaluation() {
        InboundToteManifest manifest = manifest();
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(
                new OsrInventoryConfig(1, List.of()));
        inventory.store(manifest);
        InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                new InboundToteManifestCatalog(List.of(manifest)));
        WarehouseSchedulerSnapshot snapshot = schedulerSnapshot();

        SynchronousSchedulerEvaluationSource evaluationSource =
                new SynchronousSchedulerEvaluationSource(scheduler());
        try {
            evaluationSource.submit(snapshot);
            SchedulerEvaluationResult result = evaluationSource.pollResult().orElseThrow();

            assertSame(snapshot, result.snapshot());
            assertInstanceOf(
                    ReleaseOrderCommand.class,
                    result.evaluation().releaseDecision().orElseThrow().command());
        } finally {
            evaluationSource.close();
        }

        assertEquals(List.of(manifest), inventory.snapshot().storedTotes());
        assertTrue(inventory.snapshot().departedTotes().isEmpty());
        assertTrue(lifecycle.snapshot().assignments().isEmpty());
    }

    private static DspReleaseScheduler scheduler() {
        return new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(
                        new ServiceCentrePriority(List.of("sc-1"))),
                new DspDependencyEvaluator());
    }

    private static WarehouseSchedulerSnapshot schedulerSnapshot() {
        return new WarehouseSchedulerSnapshot(
                List.of(orderState()),
                Map.of(),
                Set.of(),
                Optional.empty());
    }

    private static DspSchedulerOrderState orderState() {
        NotionalToteOrder order = new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem("line-1", "product-1", 1)),
                0);
        RouteRequirements route = new RouteRequirements(
                false,
                false,
                false,
                false,
                false,
                StartLocation.OSR);
        return new DspSchedulerOrderState(order, route, DspOrderStatus.WAITING);
    }

    private static InboundToteManifest manifest() {
        return new InboundToteManifest(
                new PhysicalToteId("tote-1"),
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                "sc-1",
                List.of(new DspOrderItem("line-1", "product-1", 1)),
                0);
    }
}
