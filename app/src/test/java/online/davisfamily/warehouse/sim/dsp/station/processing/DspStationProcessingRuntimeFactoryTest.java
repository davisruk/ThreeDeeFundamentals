package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

class DspStationProcessingRuntimeFactoryTest {

    @Test
    void shouldRegisterOnlyConsumeControllerForEmptyComposition() {
        RecordingSimulationWorld world = new RecordingSimulationWorld();
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();

        DspStationProcessingRuntime runtime = new DspStationProcessingRuntimeFactory().create(
                world, coordinator, List.of(), List.of());

        assertEquals(List.of(), runtime.destinations());
        assertEquals(List.of(), runtime.claimantSnapshots());
        assertEquals(1, world.addedControllers().size());
        assertInstanceOf(StationConsumedToteController.class,
                world.addedControllers().getFirst());
    }

    @Test
    void shouldSortMixedBindingsAndRegisterOneSharedAdaptingCompletion() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        BindingFixture thirdParty = binding(StationType.THIRD_PARTY, "third-party-a", coordinator);
        BindingFixture adaptingB = binding(StationType.ADAPTING, "bench-b", coordinator);
        BindingFixture p2p = binding(StationType.P2P, "p2p-a", coordinator);
        BindingFixture adaptingA = binding(StationType.ADAPTING, "bench-a", coordinator);
        RecordingCompletionController thirdPartyCompletion = completion(
                "completion-third-party", coordinator, thirdParty.destination());
        RecordingCompletionController adaptingCompletion = completion(
                "completion-adapting", coordinator, adaptingA.destination(), adaptingB.destination());
        RecordingSimulationWorld world = new RecordingSimulationWorld();

        DspStationProcessingRuntime runtime = new DspStationProcessingRuntimeFactory().create(
                world,
                coordinator,
                List.of(thirdParty.binding(), adaptingB.binding(), p2p.binding(), adaptingA.binding()),
                List.of(thirdPartyCompletion, adaptingCompletion));

        List<OperationalRouteDestination> expectedDestinations = List.of(
                adaptingA.destination(),
                adaptingB.destination(),
                p2p.destination(),
                thirdParty.destination());
        assertEquals(expectedDestinations, runtime.destinations());
        assertEquals(expectedDestinations,
                runtime.claimantSnapshots().stream()
                        .map(StationArrivalClaimControllerSnapshot::destination)
                        .toList());

        List<SimulationController> registered = world.addedControllers();
        assertEquals(7, registered.size());
        assertTrue(registered.subList(0, 4).stream()
                .allMatch(StationArrivalClaimController.class::isInstance));
        assertEquals("completion-adapting",
                ((StationProcessingCompletionController) registered.get(4))
                        .processingControllerId());
        assertEquals("completion-third-party",
                ((StationProcessingCompletionController) registered.get(5))
                        .processingControllerId());
        assertInstanceOf(StationConsumedToteController.class, registered.get(6));
        assertSame(coordinator, adaptingA.target().coordinator());
        assertSame(coordinator, adaptingB.target().coordinator());
        assertSame(coordinator, p2p.target().coordinator());
        assertSame(coordinator, thirdParty.target().coordinator());
        assertSame(coordinator, thirdPartyCompletion.coordinator());
        assertSame(coordinator, adaptingCompletion.coordinator());
        assertEquals(Set.of(adaptingA.destination(), adaptingB.destination()),
                adaptingCompletion.destinations());
        assertFalse(registered.stream().anyMatch(
                controller -> controller.getClass().getSimpleName()
                        .equals("P2pArrivalConsumerController")));
    }

    @Test
    void shouldRunAllClaimantsBeforeCompletionAndConsumePresentation() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        BindingFixture first = binding(StationType.THIRD_PARTY, "third-party-a", coordinator);
        BindingFixture second = binding(StationType.THIRD_PARTY, "third-party-b", coordinator);
        RoutedPhysicalTote firstHead = routedTote(first, "first-head");
        RoutedPhysicalTote firstSecond = routedTote(first, "first-second");
        RoutedPhysicalTote secondHead = routedTote(second, "second-head");
        RoutedPhysicalTote secondSecond = routedTote(second, "second-second");
        first.sourceQueue().enqueue(firstHead);
        first.sourceQueue().enqueue(firstSecond);
        second.sourceQueue().enqueue(secondHead);
        second.sourceQueue().enqueue(secondSecond);
        RecordingCompletionController completion = completion(
                "completion", coordinator, first.destination(), second.destination());
        completion.completeOne(StationProcessingDispositionType.CONSUME);
        RecordingSimulationWorld world = new RecordingSimulationWorld();

        new DspStationProcessingRuntimeFactory().create(
                world,
                coordinator,
                List.of(second.binding(), first.binding()),
                List.of(completion));

        world.update(0d);

        assertEquals(List.of(firstSecond.physicalToteId()),
                first.sourceQueue().snapshot().entries().stream()
                        .map(entry -> entry.physicalToteId()).toList());
        assertEquals(List.of(secondSecond.physicalToteId()),
                second.sourceQueue().snapshot().entries().stream()
                        .map(entry -> entry.physicalToteId()).toList());
        assertEquals(List.of(firstHead.physicalToteId(), secondHead.physicalToteId()),
                completion.observations().getFirst().activeClaims().stream()
                        .map(StationProcessingSnapshot.ActiveClaim::physicalToteId).toList());
        assertEquals(List.of(secondHead.physicalToteId()),
                coordinator.snapshot().activeClaims().stream()
                        .map(StationProcessingSnapshot.ActiveClaim::physicalToteId).toList());
        assertEquals(1, coordinator.snapshot().pendingDispositions().size());
        assertEquals(StationProcessingDispositionType.CONSUME,
                coordinator.snapshot().pendingDispositions().getFirst().type());
        assertFalse(firstHead.renderable().isVisible());
        assertFalse(firstHead.tote().areLidsOpen());
        assertEquals(1, completion.updateCount());
    }

    @Test
    void shouldRejectNullFactoryValuesWithoutRegistration() {
        DspStationProcessingRuntimeFactory factory = new DspStationProcessingRuntimeFactory();
        RecordingSimulationWorld world = new RecordingSimulationWorld();
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(null, coordinator, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(world, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(world, coordinator, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(world, coordinator, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(world, coordinator,
                        Arrays.asList((StationProcessingBinding) null), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(world, coordinator, List.of(),
                        Arrays.asList((StationProcessingCompletionController) null)));
        assertEquals(0, world.addedControllers().size());
    }

    @Test
    void shouldRejectDuplicateSourceTargetAndDestinationBeforeRegistration() {
        DspStationProcessingRuntimeFactory factory = new DspStationProcessingRuntimeFactory();

        StationProcessingCoordinator sourceCoordinator = new StationProcessingCoordinator();
        BindingFixture sourceFirst = binding(StationType.P2P, "p2p-a", sourceCoordinator);
        StationProcessingTestTarget sourceReplacementTarget = new StationProcessingTestTarget(
                sourceFirst.destination(), sourceCoordinator);
        StationProcessingBinding duplicateSource = new StationProcessingBinding(
                sourceFirst.sourceQueue(), sourceReplacementTarget);
        RecordingSimulationWorld sourceWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(sourceWorld, sourceCoordinator,
                        List.of(sourceFirst.binding(), duplicateSource), List.of()));
        assertEquals(0, sourceWorld.addedControllers().size());
        assertEquals(0, sourceFirst.sourceQueue().snapshot().occupancy());

        StationProcessingCoordinator targetCoordinator = new StationProcessingCoordinator();
        BindingFixture targetFirst = binding(StationType.P2P, "p2p-target", targetCoordinator);
        StationRoutedToteArrivalQueue targetSource2 = new StationRoutedToteArrivalQueue(
                targetFirst.destination(), 2);
        StationProcessingBinding duplicateTarget = new StationProcessingBinding(
                targetSource2, targetFirst.target());
        RecordingSimulationWorld targetWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(targetWorld, targetCoordinator,
                        List.of(targetFirst.binding(), duplicateTarget), List.of()));
        assertEquals(0, targetWorld.addedControllers().size());

        StationProcessingCoordinator destinationCoordinator = new StationProcessingCoordinator();
        BindingFixture destinationFirst = binding(
                StationType.P2P, "p2p-destination", destinationCoordinator);
        StationRoutedToteArrivalQueue destinationSource2 = new StationRoutedToteArrivalQueue(
                destinationFirst.destination(), 2);
        StationProcessingTestTarget destinationTarget2 = new StationProcessingTestTarget(
                destinationFirst.destination(), destinationCoordinator);
        RecordingSimulationWorld destinationWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(destinationWorld, destinationCoordinator,
                        List.of(destinationFirst.binding(),
                                new StationProcessingBinding(destinationSource2, destinationTarget2)),
                        List.of()));
        assertEquals(0, destinationWorld.addedControllers().size());
    }

    @Test
    void shouldRejectForeignCoordinatorsAndDuplicateCompletionIds() {
        DspStationProcessingRuntimeFactory factory = new DspStationProcessingRuntimeFactory();
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        BindingFixture binding = binding(StationType.THIRD_PARTY, "third-party", coordinator);
        RecordingCompletionController foreign = completion(
                "foreign", new StationProcessingCoordinator(), binding.destination());
        RecordingSimulationWorld foreignWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(foreignWorld, coordinator,
                        List.of(binding.binding()), List.of(foreign)));
        assertEquals(0, foreignWorld.addedControllers().size());

        RecordingCompletionController first = completion("same", coordinator, binding.destination());
        RecordingCompletionController duplicate = completion("same", coordinator, binding.destination());
        RecordingSimulationWorld duplicateWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(duplicateWorld, coordinator,
                        List.of(binding.binding()), List.of(first, duplicate)));
        assertEquals(0, duplicateWorld.addedControllers().size());

        StationProcessingCoordinator foreignTargetCoordinator = new StationProcessingCoordinator();
        BindingFixture valid = binding(StationType.P2P, "valid", coordinator);
        StationProcessingTestTarget foreignTarget = new StationProcessingTestTarget(
                valid.destination(), foreignTargetCoordinator);
        StationRoutedToteArrivalQueue foreignTargetSource = new StationRoutedToteArrivalQueue(
                valid.destination(), 2);
        RecordingSimulationWorld targetWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(targetWorld, coordinator,
                        List.of(valid.binding(), new StationProcessingBinding(
                                foreignTargetSource, foreignTarget)), List.of()));
        assertEquals(0, targetWorld.addedControllers().size());
    }

    @Test
    void shouldRejectInvalidCompletionCoverageBeforeRegistration() {
        DspStationProcessingRuntimeFactory factory = new DspStationProcessingRuntimeFactory();
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        BindingFixture thirdParty = binding(StationType.THIRD_PARTY, "third-party", coordinator);
        BindingFixture adapting = binding(StationType.ADAPTING, "bench", coordinator);
        BindingFixture p2p = binding(StationType.P2P, "p2p", coordinator);

        RecordingSimulationWorld missingWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(missingWorld, coordinator,
                        List.of(thirdParty.binding(), adapting.binding()),
                        List.of(completion("third", coordinator, thirdParty.destination()))));
        assertEquals(0, missingWorld.addedControllers().size());

        RecordingSimulationWorld doubleWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(doubleWorld, coordinator,
                        List.of(thirdParty.binding(), adapting.binding()),
                        List.of(
                                completion("third-a", coordinator, thirdParty.destination(), adapting.destination()),
                                completion("third-b", coordinator, thirdParty.destination()))));
        assertEquals(0, doubleWorld.addedControllers().size());

        RecordingSimulationWorld p2pWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(p2pWorld, coordinator,
                        List.of(p2p.binding()),
                        List.of(completion("p2p", coordinator, p2p.destination()))));
        assertEquals(0, p2pWorld.addedControllers().size());

        OperationalRouteDestination absent = new OperationalRouteDestination(
                StationType.THIRD_PARTY, "absent");
        RecordingSimulationWorld absentWorld = new RecordingSimulationWorld();
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(absentWorld, coordinator,
                        List.of(thirdParty.binding()),
                        List.of(completion("absent", coordinator, absent))));
        assertEquals(0, absentWorld.addedControllers().size());

        RecordingSimulationWorld nullDestinationWorld = new RecordingSimulationWorld();
        RecordingCompletionController nullDestination = new RecordingCompletionController(
                "null-destination", Set.of(), coordinator);
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(nullDestinationWorld, coordinator,
                        List.of(thirdParty.binding()), List.of(nullDestination)));
        assertEquals(0, nullDestinationWorld.addedControllers().size());
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

    private static RoutedPhysicalTote routedTote(BindingFixture fixture, String id) {
        return StationProcessingTestFixtures.routedTote(id, fixture.destination());
    }

    private static RecordingCompletionController completion(
            String id,
            StationProcessingCoordinator coordinator,
            OperationalRouteDestination... destinations) {
        return new RecordingCompletionController(
                id,
                new LinkedHashSet<>(Arrays.asList(destinations)),
                coordinator);
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
        private final List<StationProcessingSnapshot> observations = new ArrayList<>();
        private StationProcessingDispositionType completionType;
        private int updateCount;

        private RecordingCompletionController(
                String id,
                Set<OperationalRouteDestination> destinations,
                StationProcessingCoordinator coordinator) {
            this.id = id;
            this.destinations = destinations;
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
            StationProcessingSnapshot observation = coordinator.snapshot();
            observations.add(observation);
            if (completionType == null || observation.activeClaims().isEmpty()) {
                return;
            }
            StationProcessingSnapshot.ActiveClaim activeClaim = observation.activeClaims().stream()
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

        private List<StationProcessingSnapshot> observations() {
            return List.copyOf(observations);
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
