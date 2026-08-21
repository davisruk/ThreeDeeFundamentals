package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandHandler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.ServiceCentrePharmacyGroup;

class DspOperationalReleaseControllerTest {

    @Test
    void shouldSubmitImmutableOperationalSnapshotAndApplyPhysicalCommand() {
        DspOperationalReleaseSnapshot suppliedSnapshot = snapshot("tote-1", "order-1");
        AtomicReference<SchedulerCommand> appliedCommand = new AtomicReference<>();
        SchedulerCommandApplicationResult appliedResult =
                SchedulerCommandApplicationResult.appliedResult();
        DspOperationalReleaseController controller = controller(
                () -> suppliedSnapshot,
                command -> {
                    appliedCommand.set(command);
                    return appliedResult;
                });

        controller.update(new SimulationContext(), 0.1d);

        ReleasePhysicalToteFromOsrCommand command = assertInstanceOf(
                ReleasePhysicalToteFromOsrCommand.class, appliedCommand.get());
        assertEquals(new PhysicalToteId("tote-1"), command.physicalToteId());
        DspOperationalReleaseControllerSnapshot controllerSnapshot = controller.snapshot();
        assertEquals("sync", controllerSnapshot.evaluationMode());
        assertFalse(controllerSnapshot.evaluationInFlight());
        assertEquals(Optional.of(0L), controllerSnapshot.lastCompletedEvaluationSequence());
        assertTrue(controllerSnapshot.lastEvaluation().isPresent());
        assertSame(appliedResult, controllerSnapshot.lastCommandApplicationResult().orElseThrow());
        assertEquals(
                Optional.of(new PhysicalToteId("tote-1")),
                controllerSnapshot.lastPhysicalToteId());
    }

    @Test
    void shouldApplyCommandOnCallingSimulationThread() {
        AtomicReference<Thread> applicationThread = new AtomicReference<>();
        DspOperationalReleaseController controller = controller(
                () -> snapshot("tote-1", "order-1"),
                command -> {
                    applicationThread.set(Thread.currentThread());
                    return SchedulerCommandApplicationResult.appliedResult();
                });
        Thread callingThread = Thread.currentThread();

        controller.update(new SimulationContext(), 0.1d);

        assertSame(callingThread, applicationThread.get());
    }

    @Test
    void shouldNotApplyWhenEvaluationHasNoDecision() {
        AtomicInteger applications = new AtomicInteger();
        DspOperationalReleaseController controller = controller(
                DspOperationalReleaseControllerTest::emptySnapshot,
                command -> {
                    applications.incrementAndGet();
                    return SchedulerCommandApplicationResult.appliedResult();
                });

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(0, applications.get());
        assertTrue(controller.snapshot().lastEvaluation().isPresent());
        assertTrue(controller.snapshot().lastEvaluation().orElseThrow()
                .releaseDecision().isEmpty());
        assertTrue(controller.snapshot().lastCommandApplicationResult().isEmpty());
        assertTrue(controller.snapshot().lastPhysicalToteId().isEmpty());
    }

    @Test
    void shouldRecordDeferredOrRejectedApplicationWithoutLogicalMutation() {
        for (SchedulerCommandApplicationResult result : List.of(
                SchedulerCommandApplicationResult.deferredResult("target busy"),
                SchedulerCommandApplicationResult.rejectedResult("stale candidate"))) {
            DspOperationalReleaseSnapshot suppliedSnapshot = snapshot("tote-1", "order-1");
            DspSchedulerOrderState logicalState = suppliedSnapshot.candidates().get(0)
                    .logicalOrderState();
            DspOperationalReleaseController controller = controller(
                    () -> suppliedSnapshot,
                    command -> result);

            controller.update(new SimulationContext(), 0.1d);

            assertSame(result, controller.snapshot()
                    .lastCommandApplicationResult().orElseThrow());
            assertEquals(DspOrderStatus.WAITING, logicalState.status());
            assertEquals(
                    Optional.of(new PhysicalToteId("tote-1")),
                    controller.snapshot().lastPhysicalToteId());
        }
    }

    @Test
    void shouldRebuildSnapshotAfterAppliedPhysicalDeparture() {
        DspOperationalReleaseSnapshot firstSnapshot = snapshot("tote-1", "order-1");
        AtomicInteger supplierCalls = new AtomicInteger();
        Supplier<DspOperationalReleaseSnapshot> supplier = () ->
                supplierCalls.getAndIncrement() == 0 ? firstSnapshot : emptySnapshot();
        AtomicInteger applications = new AtomicInteger();
        DspOperationalReleaseController controller = controller(
                supplier,
                command -> {
                    applications.incrementAndGet();
                    return SchedulerCommandApplicationResult.appliedResult();
                });

        controller.update(new SimulationContext(), 0.1d);
        controller.update(new SimulationContext(), 0.1d);

        assertEquals(2, supplierCalls.get());
        assertEquals(1, applications.get());
        assertEquals(Optional.of(1L), controller.snapshot().lastCompletedEvaluationSequence());
        assertTrue(controller.snapshot().lastEvaluation().orElseThrow()
                .releaseDecision().isEmpty());
        assertEquals(
                Optional.of(new PhysicalToteId("tote-1")),
                controller.snapshot().lastPhysicalToteId());
    }

    @Test
    void shouldNotMarkLegacyOrderReleased() {
        DspOperationalReleaseSnapshot operationalSnapshot = snapshot("tote-1", "order-1");
        DspSchedulerOrderState logicalState = operationalSnapshot.candidates().get(0)
                .logicalOrderState();
        DspSchedulerRuntimeState legacyRuntimeState = new DspSchedulerRuntimeState(
                new WarehouseSchedulerSnapshot(
                        List.of(logicalState), Map.of(), Set.of(), Optional.empty()));
        DspOperationalReleaseController controller = controller(
                () -> operationalSnapshot,
                command -> SchedulerCommandApplicationResult.appliedResult());

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(
                DspOrderStatus.WAITING,
                legacyRuntimeState.snapshot().orderStates().get(0).status());
        assertTrue(legacyRuntimeState.snapshot().activeServiceCentreId().isEmpty());
    }

    @Test
    void shouldPropagateBrokenCollaboratorFailure() {
        SimulationContext context = new SimulationContext();
        DspOperationalReleaseController nullSnapshotController = controller(
                () -> null,
                command -> SchedulerCommandApplicationResult.appliedResult());
        assertThrows(
                IllegalStateException.class,
                () -> nullSnapshotController.update(context, 0.1d));

        DspOperationalReleaseController brokenHandlerController = controller(
                () -> snapshot("tote-1", "order-1"),
                command -> {
                    throw new IllegalStateException("handler failed");
                });
        IllegalStateException handlerFailure = assertThrows(
                IllegalStateException.class,
                () -> brokenHandlerController.update(context, 0.1d));
        assertEquals("handler failed", handlerFailure.getMessage());

        OperationalReleaseEvaluationSource brokenSource = new CloseTrackingSource() {
            @Override
            public boolean canSubmit() {
                throw new IllegalStateException("source failed");
            }
        };
        DspOperationalReleaseController brokenSourceController =
                new DspOperationalReleaseController(
                        brokenSource,
                        DspOperationalReleaseControllerTest::emptySnapshot,
                        command -> SchedulerCommandApplicationResult.appliedResult());
        IllegalStateException sourceFailure = assertThrows(
                IllegalStateException.class,
                () -> brokenSourceController.update(context, 0.1d));
        assertEquals("source failed", sourceFailure.getMessage());

        assertThrows(
                IllegalArgumentException.class,
                () -> brokenHandlerController.update(null, 0.1d));
    }

    @Test
    void shouldCloseOperationalEvaluationSource() {
        CloseTrackingSource source = new CloseTrackingSource();
        DspOperationalReleaseController controller = new DspOperationalReleaseController(
                source,
                DspOperationalReleaseControllerTest::emptySnapshot,
                command -> SchedulerCommandApplicationResult.appliedResult());

        controller.close();

        assertTrue(source.closed.get());
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalReleaseController(
                        null,
                        DspOperationalReleaseControllerTest::emptySnapshot,
                        command -> SchedulerCommandApplicationResult.appliedResult()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalReleaseController(
                        source,
                        null,
                        command -> SchedulerCommandApplicationResult.appliedResult()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalReleaseController(
                        source,
                        DspOperationalReleaseControllerTest::emptySnapshot,
                        null));
    }

    private static DspOperationalReleaseController controller(
            Supplier<DspOperationalReleaseSnapshot> snapshotSupplier,
            SchedulerCommandHandler commandHandler) {
        return new DspOperationalReleaseController(
                new SynchronousOperationalReleaseEvaluationSource(
                        new DspOperationalReleaseScheduler()),
                snapshotSupplier,
                commandHandler);
    }

    private static DspOperationalReleaseSnapshot emptySnapshot() {
        return new DspOperationalReleaseSnapshot(List.of(), List.of(), Map.of(), Set.of());
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

    private static class CloseTrackingSource implements OperationalReleaseEvaluationSource {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public boolean canSubmit() {
            return false;
        }

        @Override
        public void submit(DspOperationalReleaseSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OperationalReleaseEvaluationResult> pollResult() {
            return Optional.empty();
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
