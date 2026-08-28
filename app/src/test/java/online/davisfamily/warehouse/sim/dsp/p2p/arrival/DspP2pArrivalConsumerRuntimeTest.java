package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

class DspP2pArrivalConsumerRuntimeTest {

    @Test
    void shouldExposeFreshOrderedSnapshotsAndCloseIdempotently() {
        SimulationWorld world = new SimulationWorld();
        P2pArrivalRuntimeTestFixtures.BindingFixture first =
                P2pArrivalRuntimeTestFixtures.binding("p2p-1");
        P2pArrivalRuntimeTestFixtures.BindingFixture second =
                P2pArrivalRuntimeTestFixtures.binding("p2p-2");
        DspP2pArrivalConsumerRuntime runtime =
                new DspP2pArrivalConsumerRuntimeFactory().create(
                        world,
                        List.of(first.binding(), second.binding()));

        List<P2pArrivalConsumerControllerSnapshot> before = runtime.controllerSnapshots();
        assertEquals(List.of("p2p-1", "p2p-2"), before.stream()
                .map(snapshot -> snapshot.sourceDestination().targetId())
                .toList());
        assertEquals(List.of(0, 0), before.stream()
                .map(P2pArrivalConsumerControllerSnapshot::sourceOccupancy)
                .toList());
        assertThrows(UnsupportedOperationException.class,
                () -> before.add(before.get(0)));
        StationProcessingSnapshot coordinatorBefore = runtime.coordinatorSnapshot();
        assertTrue(coordinatorBefore.activeClaims().isEmpty());

        RoutedPhysicalTote routedTote = P2pArrivalRuntimeTestFixtures.routedTote(
                "tote-1", first.binding().destination(), first.terminal());
        first.sourceQueue().enqueue(routedTote);
        world.update(0d);

        List<P2pArrivalConsumerControllerSnapshot> after = runtime.controllerSnapshots();
        assertEquals(List.of(0, 0), after.stream()
                .map(P2pArrivalConsumerControllerSnapshot::sourceOccupancy)
                .toList());
        assertEquals(List.of(1, 0), after.stream()
                .map(P2pArrivalConsumerControllerSnapshot::targetOccupancy)
                .toList());
        assertEquals(List.of(0, 0), before.stream()
                .map(P2pArrivalConsumerControllerSnapshot::targetOccupancy)
                .toList());
        assertEquals(List.of("p2p-1", "p2p-2"), runtime.targetSnapshots().stream()
                .map(snapshot -> snapshot.destination().targetId())
                .toList());
        StationProcessingSnapshot coordinatorAfter = runtime.coordinatorSnapshot();
        assertEquals(1, coordinatorAfter.activeClaims().size());
        assertEquals("tote-1", coordinatorAfter.activeClaims().getFirst().physicalToteId().value());
        assertTrue(coordinatorBefore.activeClaims().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> coordinatorAfter.activeClaims().clear());

        assertFalse(runtime.isClosed());
        runtime.close();
        runtime.close();
        assertTrue(runtime.isClosed());
    }

    @Test
    void shouldRejectInvalidRuntimeDependencies() {
        P2pArrivalRuntimeTestFixtures.BindingFixture fixture =
                P2pArrivalRuntimeTestFixtures.binding("p2p-1");
        P2pArrivalConsumerController controller = new P2pArrivalConsumerController(
                fixture.sourceQueue(),
                fixture.binding().admissionPolicy(),
                fixture.binding().routeBinding(),
                fixture.binding().payloadFactory(),
                fixture.target());

        assertThrows(IllegalArgumentException.class,
                () -> new DspP2pArrivalConsumerRuntime(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DspP2pArrivalConsumerRuntime(
                        List.of(), List.of(fixture.target())));
        assertThrows(IllegalArgumentException.class,
                () -> new DspP2pArrivalConsumerRuntime(
                        Arrays.asList((P2pArrivalConsumerController) null),
                        List.of(fixture.target())));
        assertThrows(IllegalArgumentException.class,
                () -> new DspP2pArrivalConsumerRuntime(
                        List.of(controller),
                        Arrays.asList((P2pTipperArrivalTarget) null)));
    }
}
