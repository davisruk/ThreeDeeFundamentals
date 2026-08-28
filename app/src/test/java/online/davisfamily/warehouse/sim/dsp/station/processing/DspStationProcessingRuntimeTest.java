package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;

class DspStationProcessingRuntimeTest {

    @Test
    void shouldExposeOrderedImmutableValueSnapshots() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        BindingFixture first = binding(StationType.P2P, "p2p-a", coordinator);
        BindingFixture second = binding(StationType.P2P, "p2p-b", coordinator);
        RecordingSimulationWorld world = new RecordingSimulationWorld();
        DspStationProcessingRuntime runtime = new DspStationProcessingRuntimeFactory().create(
                world,
                coordinator,
                List.of(second.binding(), first.binding()),
                List.of());

        assertEquals(List.of(first.destination(), second.destination()), runtime.destinations());
        assertThrows(UnsupportedOperationException.class,
                () -> runtime.destinations().add(first.destination()));
        assertThrows(UnsupportedOperationException.class,
                () -> runtime.claimantSnapshots().clear());
        StationProcessingSnapshot before = runtime.coordinatorSnapshot();
        assertTrue(before.activeClaims().isEmpty());
        assertTrue(before.pendingDispositions().isEmpty());

        RoutedPhysicalTote routed = StationProcessingTestFixtures.routedTote(
                "runtime-snapshot", first.destination());
        first.sourceQueue().enqueue(routed);
        world.update(0d);

        StationProcessingSnapshot after = runtime.coordinatorSnapshot();
        assertEquals(1, after.activeClaims().size());
        assertEquals(routed.physicalToteId(), after.activeClaims().getFirst().physicalToteId());
        assertEquals(List.of(0, 0), runtime.claimantSnapshots().stream()
                .map(StationArrivalClaimControllerSnapshot::sourceOccupancy).toList());
        assertTrue(before.activeClaims().isEmpty());
        assertTrue(before.pendingDispositions().isEmpty());
    }

    @Test
    void shouldExposeClaimCompletionAndConsumeSequenceThroughWorldUpdate() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        BindingFixture fixture = binding(StationType.THIRD_PARTY, "third-party", coordinator);
        RoutedPhysicalTote routed = StationProcessingTestFixtures.routedTote(
                "runtime-completion", fixture.destination());
        fixture.sourceQueue().enqueue(routed);
        RecordingCompletionController completion = new RecordingCompletionController(
                "completion", Set.of(fixture.destination()), coordinator);
        completion.completeOne(StationProcessingDispositionType.CONSUME);
        RecordingSimulationWorld world = new RecordingSimulationWorld();
        DspStationProcessingRuntime runtime = new DspStationProcessingRuntimeFactory().create(
                world, coordinator, List.of(fixture.binding()), List.of(completion));

        world.update(0d);

        assertEquals(0, runtime.claimantSnapshots().getFirst().sourceOccupancy());
        StationProcessingSnapshot snapshot = runtime.coordinatorSnapshot();
        assertTrue(snapshot.activeClaims().isEmpty());
        assertEquals(1, snapshot.pendingDispositions().size());
        assertEquals(routed.physicalToteId(), snapshot.pendingDispositions().getFirst().physicalToteId());
        assertEquals(StationProcessingDispositionType.CONSUME,
                snapshot.pendingDispositions().getFirst().type());
        assertEquals(1, completion.updateCount());
        assertFalse(routed.renderable().isVisible());
        assertFalse(routed.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.HELD, routed.tote().getInteractionMode());
        assertEquals(List.of(
                StationArrivalClaimController.class,
                RecordingCompletionController.class,
                StationConsumedToteController.class),
                world.addedControllers().stream().map(Object::getClass).toList());
    }

    @Test
    void shouldCloseIdempotentlyWithoutUnregisteringOrMutatingState() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        BindingFixture fixture = binding(StationType.P2P, "p2p", coordinator);
        RoutedPhysicalTote routed = StationProcessingTestFixtures.routedTote(
                "runtime-close", fixture.destination());
        fixture.sourceQueue().enqueue(routed);
        RecordingSimulationWorld world = new RecordingSimulationWorld();
        DspStationProcessingRuntime runtime = new DspStationProcessingRuntimeFactory().create(
                world, coordinator, List.of(fixture.binding()), List.of());
        var queueBefore = fixture.sourceQueue().snapshot();
        var coordinatorBefore = runtime.coordinatorSnapshot();
        var evaluatedBefore = fixture.target().evaluatedTotes();
        var acceptedBefore = fixture.target().acceptedTotes();

        assertFalse(runtime.isClosed());
        runtime.close();
        runtime.close();

        assertTrue(runtime.isClosed());
        assertEquals(queueBefore, fixture.sourceQueue().snapshot());
        assertEquals(coordinatorBefore, runtime.coordinatorSnapshot());
        assertEquals(evaluatedBefore, fixture.target().evaluatedTotes());
        assertEquals(acceptedBefore, fixture.target().acceptedTotes());
        assertEquals(2, world.addedControllers().size());

        world.update(0d);

        assertEquals(0, fixture.sourceQueue().snapshot().occupancy());
        assertEquals(1, runtime.coordinatorSnapshot().activeClaims().size());
        assertEquals(1, fixture.target().acceptedTotes().size());
    }

    @Test
    void shouldStartFreshWhenASecondRuntimeIsConstructed() {
        StationProcessingCoordinator firstCoordinator = new StationProcessingCoordinator();
        BindingFixture first = binding(StationType.P2P, "first", firstCoordinator);
        RecordingSimulationWorld firstWorld = new RecordingSimulationWorld();
        DspStationProcessingRuntime firstRuntime = new DspStationProcessingRuntimeFactory().create(
                firstWorld, firstCoordinator,
                List.of(first.binding()), List.of());
        first.sourceQueue().enqueue(StationProcessingTestFixtures.routedTote(
                "first-runtime", first.destination()));
        firstWorld.update(0d);
        assertEquals(1, firstRuntime.coordinatorSnapshot().activeClaims().size());

        StationProcessingCoordinator secondCoordinator = new StationProcessingCoordinator();
        BindingFixture second = binding(StationType.P2P, "second", secondCoordinator);
        DspStationProcessingRuntime secondRuntime = new DspStationProcessingRuntimeFactory().create(
                new RecordingSimulationWorld(), secondCoordinator,
                List.of(second.binding()), List.of());

        assertFalse(firstRuntime.isClosed());
        assertFalse(secondRuntime.isClosed());
        assertTrue(secondRuntime.coordinatorSnapshot().activeClaims().isEmpty());
        assertTrue(secondRuntime.coordinatorSnapshot().pendingDispositions().isEmpty());
        assertEquals(List.of(0), secondRuntime.claimantSnapshots().stream()
                .map(StationArrivalClaimControllerSnapshot::sourceOccupancy).toList());
    }

    private static BindingFixture binding(
            StationType stationType,
            String targetId,
            StationProcessingCoordinator coordinator) {
        OperationalRouteDestination destination = new OperationalRouteDestination(
                stationType, targetId);
        StationRoutedToteArrivalQueue sourceQueue = new StationRoutedToteArrivalQueue(destination, 2);
        StationProcessingTestTarget target = new StationProcessingTestTarget(destination, coordinator);
        return new BindingFixture(
                new StationProcessingBinding(sourceQueue, target), destination, sourceQueue, target);
    }

    private record BindingFixture(
            StationProcessingBinding binding,
            OperationalRouteDestination destination,
            StationRoutedToteArrivalQueue sourceQueue,
            StationProcessingTestTarget target) {
    }

    private static final class RecordingCompletionController
            implements StationProcessingCompletionController {
        private final String id;
        private final Set<OperationalRouteDestination> destinations;
        private final StationProcessingCoordinator coordinator;
        private StationProcessingDispositionType completionType;
        private int updateCount;

        private RecordingCompletionController(
                String id,
                Set<OperationalRouteDestination> destinations,
                StationProcessingCoordinator coordinator) {
            this.id = id;
            this.destinations = new LinkedHashSet<>(destinations);
            this.coordinator = coordinator;
        }

        private void completeOne(StationProcessingDispositionType type) {
            completionType = type;
        }

        @Override
        public String processingControllerId() {
            return id;
        }

        @Override
        public Set<OperationalRouteDestination> destinations() {
            return destinations;
        }

        @Override
        public StationProcessingCoordinator coordinator() {
            return coordinator;
        }

        @Override
        public void update(SimulationContext context, double dtSeconds) {
            updateCount++;
            if (completionType == null) {
                return;
            }
            StationProcessingSnapshot.ActiveClaim activeClaim = coordinator.snapshot().activeClaims()
                    .stream()
                    .filter(claim -> destinations.contains(claim.destination()))
                    .findFirst()
                    .orElse(null);
            if (activeClaim == null) {
                return;
            }
            StationProcessingClaim claim = coordinator.requireActiveClaim(activeClaim.physicalToteId());
            coordinator.complete(
                    activeClaim.physicalToteId(),
                    completionType,
                    claim.routedTote().loadPlan(),
                    Duration.ofNanos(Math.round(context.getSimulationTimeSeconds() * 1_000_000_000L)));
            completionType = null;
        }

        private int updateCount() {
            return updateCount;
        }
    }

    private static final class RecordingSimulationWorld extends SimulationWorld {
        private final List<SimulationController> addedControllers = new ArrayList<>();

        @Override
        public void addController(SimulationController controller) {
            addedControllers.add(controller);
            super.addController(controller);
        }

        private List<SimulationController> addedControllers() {
            return List.copyOf(addedControllers);
        }
    }
}
