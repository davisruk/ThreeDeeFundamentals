package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;

class DspStationRouteContinuationRuntimeTest {

    @Test
    void shouldExposeFreshSnapshotsAndContinueAfterIdempotentClose() {
        DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture fixture =
                DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture.create(true);
        DspStationRouteContinuationRuntime runtime =
                new DspStationRouteContinuationRuntimeFactory().create(
                        fixture.world,
                        fixture.coordinator,
                        fixture.orderCatalog,
                        fixture.loadPlanRegistry,
                        fixture.routeDeriver,
                        fixture.selector,
                        fixture.targetResolver,
                        fixture.routeCatalog,
                        fixture.transportQueue,
                        fixture.publisher);

        StationRouteContinuationControllerSnapshot before = runtime.snapshot();
        assertTrue(before.headPhysicalToteId().isPresent());
        assertEquals(0, before.continuedCount());
        assertFalse(runtime.isClosed());

        runtime.close();
        runtime.close();
        assertTrue(runtime.isClosed());

        fixture.world.update(0d);

        StationRouteContinuationControllerSnapshot after = runtime.snapshot();
        assertNotSame(before, after);
        assertTrue(before.headPhysicalToteId().isPresent());
        assertEquals(0, before.continuedCount());
        assertTrue(after.headPhysicalToteId().isEmpty());
        assertEquals(1, after.continuedCount());
        assertEquals(fixture.nextDestination, after.lastHandledNextDestination().orElseThrow());
    }

    @Test
    void shouldAcknowledgeTerminalDispositionThroughTheSameWorldController() {
        DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture fixture =
                DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture.create(true);
        DspStationRouteContinuationRuntime runtime =
                new DspStationRouteContinuationRuntimeFactory().create(
                        fixture.world,
                        fixture.coordinator,
                        fixture.orderCatalog,
                        fixture.loadPlanRegistry,
                        fixture.routeDeriver,
                        fixture.selector,
                        fixture.targetResolver,
                        fixture.routeCatalog,
                        fixture.transportQueue,
                        fixture.publisher);

        fixture.world.update(0d);
        RoutedPhysicalTote continued = fixture.transportQueue.dequeue().orElseThrow();
        continued.tote().closeLids();
        continued.tote().setInteractionMode(Tote.ToteMotionState.HELD);
        continued.renderable().setVisible(false);
        fixture.coordinator.claim(continued, Duration.ofSeconds(2));
        fixture.coordinator.complete(
                continued.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                continued.loadPlan(),
                Duration.ofSeconds(3));

        fixture.world.update(0d);

        StationRouteContinuationControllerSnapshot snapshot = runtime.snapshot();
        assertTrue(snapshot.headPhysicalToteId().isEmpty());
        assertEquals(1, snapshot.continuedCount());
        assertEquals(1, snapshot.consumedAcknowledgementCount());
        assertEquals(StationProcessingDispositionType.CONSUME,
                snapshot.lastHandledDispositionType().orElseThrow());
        assertTrue(snapshot.lastHandledNextDestination().isEmpty());
        assertTrue(fixture.coordinator.pendingDispositions().isEmpty());
    }

    @Test
    void shouldResetDiagnosticsByReconstructingAFreshRuntime() {
        DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture firstFixture =
                DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture.create(true);
        DspStationRouteContinuationRuntime first =
                new DspStationRouteContinuationRuntimeFactory().create(
                        firstFixture.world,
                        firstFixture.coordinator,
                        firstFixture.orderCatalog,
                        firstFixture.loadPlanRegistry,
                        firstFixture.routeDeriver,
                        firstFixture.selector,
                        firstFixture.targetResolver,
                        firstFixture.routeCatalog,
                        firstFixture.transportQueue,
                        firstFixture.publisher);
        firstFixture.world.update(0d);
        assertEquals(1, first.snapshot().continuedCount());

        DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture secondFixture =
                DspStationRouteContinuationRuntimeFactoryTest.ContinuationRuntimeFixture.create(false);
        DspStationRouteContinuationRuntime second =
                new DspStationRouteContinuationRuntimeFactory().create(
                        secondFixture.world,
                        secondFixture.coordinator,
                        secondFixture.orderCatalog,
                        secondFixture.loadPlanRegistry,
                        secondFixture.routeDeriver,
                        secondFixture.selector,
                        secondFixture.targetResolver,
                        secondFixture.routeCatalog,
                        secondFixture.transportQueue,
                        secondFixture.publisher);

        assertFalse(second.isClosed());
        assertEquals(0, second.snapshot().continuedCount());
        assertEquals(0, second.snapshot().consumedAcknowledgementCount());
        assertTrue(second.snapshot().headPhysicalToteId().isEmpty());
        assertEquals(1, first.snapshot().continuedCount());
    }
}
