package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;

class DspP2pArrivalConsumerRuntimeFactoryTest {

    @Test
    void shouldComposeZeroOneAndFiveBindingsInSuppliedOrder() {
        DspP2pArrivalConsumerRuntimeFactory factory =
                new DspP2pArrivalConsumerRuntimeFactory();
        RecordingSimulationWorld emptyWorld = new RecordingSimulationWorld();

        DspP2pArrivalConsumerRuntime emptyRuntime = factory.create(emptyWorld, List.of());

        assertEquals(List.of(), emptyRuntime.controllerSnapshots());
        assertEquals(List.of(), emptyRuntime.targetSnapshots());
        assertEquals(0, emptyWorld.addedControllers().size());

        RecordingSimulationWorld oneWorld = new RecordingSimulationWorld();
        P2pArrivalRuntimeTestFixtures.BindingFixture single =
                P2pArrivalRuntimeTestFixtures.binding("p2p-1");
        DspP2pArrivalConsumerRuntime oneRuntime = factory.create(
                oneWorld, List.of(single.binding()));

        assertEquals(List.of("p2p-1"), targetIds(oneRuntime.controllerSnapshots()));
        assertEquals(1, oneWorld.addedControllers().size());

        RecordingSimulationWorld fiveWorld = new RecordingSimulationWorld();
        List<P2pArrivalRuntimeTestFixtures.BindingFixture> fixtures = List.of(
                P2pArrivalRuntimeTestFixtures.binding("p2p-1"),
                P2pArrivalRuntimeTestFixtures.binding("p2p-2"),
                P2pArrivalRuntimeTestFixtures.binding("p2p-3"),
                P2pArrivalRuntimeTestFixtures.binding("p2p-4"),
                P2pArrivalRuntimeTestFixtures.binding("p2p-5"));
        DspP2pArrivalConsumerRuntime fiveRuntime = factory.create(
                fiveWorld,
                fixtures.stream().map(fixture -> fixture.binding()).toList());

        List<String> expectedIds = List.of("p2p-1", "p2p-2", "p2p-3", "p2p-4", "p2p-5");
        assertEquals(expectedIds, targetIds(fiveRuntime.controllerSnapshots()));
        assertEquals(expectedIds, fiveRuntime.targetSnapshots().stream()
                .map(snapshot -> snapshot.destination().targetId())
                .toList());
        assertEquals(5, fiveWorld.addedControllers().size());
        for (int index = 0; index < expectedIds.size(); index++) {
            P2pArrivalConsumerController controller =
                    (P2pArrivalConsumerController) fiveWorld.addedControllers().get(index);
            assertEquals(expectedIds.get(index),
                    controller.snapshot().sourceDestination().targetId());
        }
    }

    @Test
    void shouldRejectDuplicateTargetIdAndSourceQueueInstances() {
        DspP2pArrivalConsumerRuntimeFactory factory =
                new DspP2pArrivalConsumerRuntimeFactory();
        P2pArrivalRuntimeTestFixtures.BindingFixture first =
                P2pArrivalRuntimeTestFixtures.binding("p2p-1");
        P2pArrivalRuntimeTestFixtures.BindingFixture sameId =
                P2pArrivalRuntimeTestFixtures.binding("p2p-1");

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        new RecordingSimulationWorld(),
                        List.of(first.binding(), sameId.binding())));

        P2pTipperArrivalTarget replacementTarget = new P2pTipperArrivalTarget(
                first.binding().destination(),
                new TipperInputQueue("replacement-input", 1));
        P2pArrivalConsumerBinding duplicateSource = new P2pArrivalConsumerBinding(
                first.sourceQueue(),
                first.binding().admissionPolicy(),
                first.binding().routeBinding(),
                first.binding().payloadFactory(),
                replacementTarget);

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        new RecordingSimulationWorld(),
                        List.of(first.binding(), duplicateSource)));
    }

    @Test
    void shouldRejectDuplicateTargetQueueBeforeRegisteringAnyController() {
        DspP2pArrivalConsumerRuntimeFactory factory =
                new DspP2pArrivalConsumerRuntimeFactory();
        P2pArrivalRuntimeTestFixtures.BindingFixture first =
                P2pArrivalRuntimeTestFixtures.binding("p2p-1");
        P2pArrivalRuntimeTestFixtures.BindingFixture second =
                P2pArrivalRuntimeTestFixtures.binding(
                        "p2p-2",
                        new AllowAllP2pArrivalAdmissionPolicy(),
                        first.inputQueue());
        RecordingSimulationWorld world = new RecordingSimulationWorld();

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(world, List.of(first.binding(), second.binding())));

        assertEquals(0, world.addedControllers().size());
    }

    @Test
    void shouldValidateFactoryAndBindingDependencies() {
        DspP2pArrivalConsumerRuntimeFactory factory =
                new DspP2pArrivalConsumerRuntimeFactory();
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-1");

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(new SimulationWorld(), null));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        new SimulationWorld(),
                        Arrays.asList((P2pArrivalConsumerBinding) null)));

        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerBinding(
                        null,
                        fixture.binding().admissionPolicy(),
                        fixture.binding().routeBinding(),
                        fixture.binding().payloadFactory(),
                        fixture.target()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerBinding(
                        fixture.sourceQueue(), null,
                        fixture.binding().routeBinding(),
                        fixture.binding().payloadFactory(),
                        fixture.target()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerBinding(
                        fixture.sourceQueue(),
                        fixture.binding().admissionPolicy(), null,
                        fixture.binding().payloadFactory(),
                        fixture.target()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerBinding(
                        fixture.sourceQueue(),
                        fixture.binding().admissionPolicy(),
                        fixture.binding().routeBinding(), null,
                        fixture.target()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerBinding(
                        fixture.sourceQueue(),
                        fixture.binding().admissionPolicy(),
                        fixture.binding().routeBinding(),
                        fixture.binding().payloadFactory(), null));

        StationRoutedToteArrivalQueue adaptingSource =
                new StationRoutedToteArrivalQueue(
                        new OperationalRouteDestination(StationType.ADAPTING, "bench-1"), 1);
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerBinding(
                        adaptingSource,
                        fixture.binding().admissionPolicy(),
                        fixture.binding().routeBinding(),
                        fixture.binding().payloadFactory(),
                        fixture.target()));

        P2pArrivalRuntimeTestFixtures.BindingFixture other =
                P2pArrivalRuntimeTestFixtures.binding("p2p-2");
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalConsumerBinding(
                        fixture.sourceQueue(),
                        fixture.binding().admissionPolicy(),
                        fixture.binding().routeBinding(),
                        fixture.binding().payloadFactory(),
                        other.target()));
    }

    private static List<String> targetIds(
            List<P2pArrivalConsumerControllerSnapshot> snapshots) {
        return snapshots.stream()
                .map(snapshot -> snapshot.sourceDestination().targetId())
                .toList();
    }

    private static final class RecordingSimulationWorld extends SimulationWorld {
        private final List<SimulationController> addedControllers = new ArrayList<>();

        @Override
        public void addController(SimulationController controller) {
            addedControllers.add(controller);
            super.addController(controller);
        }

        List<SimulationController> addedControllers() {
            return addedControllers;
        }
    }
}
