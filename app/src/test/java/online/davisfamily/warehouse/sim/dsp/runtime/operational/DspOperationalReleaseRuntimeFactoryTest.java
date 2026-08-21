package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetDefinition;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;

class DspOperationalReleaseRuntimeFactoryTest {

    @Test
    void shouldWireFreshCandidateAdmissionAndApplyThroughExactQueueRegistry() {
        Fixture fixture = new Fixture();
        fixture.queue.enqueue(new OsrProcessingReleaseRequest(
                fixture.blockerManifest, Duration.ZERO));

        try (DspOperationalReleaseRuntime runtime = fixture.create(
                new SynchronousOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler()))) {
            assertSame(fixture.registry, runtime.routeTargetRegistry());

            runtime.controller().update(new SimulationContext(), 0.1d);

            assertTrue(fixture.inventory.snapshot().findStored(
                    fixture.manifest.physicalToteId()).isPresent());
            assertEquals(1, runtime.routeEntryQueueSnapshots().get(0).occupancy());
            assertEquals(1, runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().blockedCandidates().size());

            fixture.queue.dequeue();
            runtime.controller().update(new SimulationContext(), 0.1d);

            assertFalse(fixture.inventory.snapshot().findStored(
                    fixture.manifest.physicalToteId()).isPresent());
            assertEquals(
                    List.of(fixture.manifest.physicalToteId()),
                    runtime.routeEntryQueueSnapshots().get(0).physicalToteIds());
            assertEquals(
                    DspOrderStatus.WAITING,
                    fixture.runtimeState.snapshot().orderStates().get(0).status());
        }
    }

    @Test
    void shouldAcceptSuppliedThreadedEvaluationSourceWithoutCreatingAnother() {
        Fixture fixture = new Fixture();
        ThreadedOperationalReleaseEvaluationSource source =
                new ThreadedOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler(), "route-release-test");

        DspOperationalReleaseRuntime runtime = fixture.create(source);

        assertEquals("threaded", runtime.controller().snapshot().evaluationMode());
        runtime.close();
    }

    @Test
    void shouldRejectNullDependenciesAndNullLogicalSnapshotResult() {
        Fixture fixture = new Fixture();
        DspOperationalReleaseRuntimeFactory factory = new DspOperationalReleaseRuntimeFactory();
        SynchronousOperationalReleaseEvaluationSource source =
                new SynchronousOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler());

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        null,
                        fixture.inventory,
                        fixture.lifecycleController,
                        fixture.catalog,
                        fixture.runtimeState::snapshot,
                        fixture.clock::initialSnapshot,
                        fixture::admission,
                        fixture.registry));
        DspOperationalReleaseRuntime runtime = factory.create(
                source,
                fixture.inventory,
                fixture.lifecycleController,
                fixture.catalog,
                () -> null,
                fixture.clock::initialSnapshot,
                fixture::admission,
                fixture.registry);
        try (runtime) {
            assertThrows(
                    IllegalStateException.class,
                    () -> runtime.controller().update(new SimulationContext(), 0.1d));
        }
    }

    private static final class Fixture {
        private final InboundToteManifest manifest = manifest(
                "tote-1", "order-1", "pharmacy-1", 1);
        private final InboundToteManifest blockerManifest = manifest(
                "blocker-tote", "blocker-order", "pharmacy-2", 2);
        private final InboundToteManifestCatalog catalog =
                new InboundToteManifestCatalog(List.of(manifest, blockerManifest));
        private final OsrPhysicalInventory inventory = new OsrPhysicalInventory(
                new OsrInventoryConfig(2, List.of()));
        private final InboundToteLifecycleController lifecycleController =
                new InboundToteLifecycleController(
                        new PhysicalToteLifecycleLedger(), catalog);
        private final DspSchedulerRuntimeState runtimeState =
                new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                        List.of(logicalState(manifest)), Map.of(), Set.of(), Optional.empty()));
        private final OperationalRouteEntryQueue queue = new OperationalRouteEntryQueue(
                new OperationalRouteTargetDefinition(StationType.P2P, "p2p-1", 1));
        private final OperationalRouteTargetRegistry registry =
                new OperationalRouteTargetRegistry(List.of(queue));
        private final DspOperationalClock clock = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 8, 21)));

        private Fixture() {
            inventory.store(manifest);
        }

        private DspOperationalReleaseRuntime create(
                OperationalReleaseEvaluationSource evaluationSource) {
            return new DspOperationalReleaseRuntimeFactory().create(
                    evaluationSource,
                    inventory,
                    lifecycleController,
                    catalog,
                    runtimeState::snapshot,
                    clock::initialSnapshot,
                    this::admission,
                    registry);
        }

        private StationAdmissionSnapshot admission(
                StationType stationType,
                DspSchedulerOrderState candidate,
                WarehouseSchedulerSnapshot snapshot) {
            return new StationAdmissionSnapshot(
                    stationType,
                    new StationCapacity(1, 1),
                    new StationSnapshot(stationType, 0, 0),
                    true,
                    "",
                    Optional.of("p2p-1"));
        }
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String orderId,
            String pharmacyId,
            long sequence) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                "product-" + orderId,
                1,
                pharmacyId,
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
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                order.items(),
                sequence);
    }

    private static DspSchedulerOrderState logicalState(InboundToteManifest manifest) {
        NotionalToteOrder order = new NotionalToteOrder(
                manifest.orderSheetKey().orderId(),
                "notional-" + manifest.orderSheetKey().orderId(),
                manifest.serviceCentreId(),
                manifest.orderSheetKey().sheetNumber(),
                manifest.orderType(),
                manifest.items(),
                999,
                1);
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(
                        false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }
}
