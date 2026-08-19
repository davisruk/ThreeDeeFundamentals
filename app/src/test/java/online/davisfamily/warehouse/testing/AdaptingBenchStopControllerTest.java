package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageMap;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class AdaptingBenchStopControllerTest {

    @Test
    void shouldStopToteAtAssignedBenchSensor() {
        RouteSegment benchLane = segment("bench-lane", new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 6f));
        RouteSegment completion = segment("completion", new Vec3(0f, 0f, 6f), new Vec3(0f, 0f, 8f));
        benchLane.connectTo(completion);
        Tote tote = createTote("tote-a", benchLane, 0f, 4.0d);
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");
        AdaptingVisit visit = AdaptingVisit.store(
                new PhysicalToteId("tote-a"),
                new OrderSheetKey("adapted-source-a", 1),
                "104",
                List.of(adaptedLine("line-1", "collect-1", "0000310")));
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingArea area = area(new AdaptingBench("bench-1", store, 2d), storageMap("0000310", "bench-1"));
        area.submitVisit(visit);

        Map<String, AdaptingBenchJourney> journeys = new LinkedHashMap<>();
        journeys.put(tote.getId(), new AdaptingBenchJourney(
                "order-1",
                tote,
                visit,
                benchId,
                benchLane,
                AdaptingJourneyPhase.TO_BENCH));

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addController(new AdaptingBenchStopController(
                area,
                new AdaptingAreaController(area, emptyRuntimeState()),
                journeys,
                Map.of(benchId, new AdaptingBenchStop(benchId, benchLane, 1.0f)),
                completion,
                AdaptingBenchStopControllerTest::hideTote));

        sim.update(0.30d);

        assertEquals(ToteMotionState.HELD, tote.getInteractionMode());
        assertTrue(tote.areLidsOpen());
        assertEquals(1.0f, tote.getLastSnapshot().distanceAlongSegment(), 0.0001f);
        assertEquals("tote-a", area.bench(benchId).snapshot().activeToteId());
    }

    @Test
    void shouldNotStopAtAnotherBenchSensorOnSameLane() {
        RouteSegment benchLane = segment("bench-lane", new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 6f));
        RouteSegment completion = segment("completion", new Vec3(0f, 0f, 6f), new Vec3(0f, 0f, 8f));
        benchLane.connectTo(completion);
        Tote tote = createTote("tote-b", benchLane, 0f, 4.0d);
        AdaptingBenchId bench2 = new AdaptingBenchId("bench-2");
        AdaptingVisit visit = AdaptingVisit.store(
                new PhysicalToteId("tote-b"),
                new OrderSheetKey("adapted-source-b", 1),
                "104",
                List.of(adaptedLine("line-2", "collect-2", "0000311")));
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingArea area = area(
                List.of(new AdaptingBench("bench-1", store, 2d), new AdaptingBench("bench-2", store, 2d)),
                storageMap("0000311", "bench-2"));
        area.submitVisit(visit);

        Map<String, AdaptingBenchJourney> journeys = new LinkedHashMap<>();
        journeys.put(tote.getId(), new AdaptingBenchJourney(
                "order-2",
                tote,
                visit,
                bench2,
                benchLane,
                AdaptingJourneyPhase.TO_BENCH));

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addController(new AdaptingBenchStopController(
                area,
                new AdaptingAreaController(area, emptyRuntimeState()),
                journeys,
                Map.of(
                        new AdaptingBenchId("bench-1"), new AdaptingBenchStop(new AdaptingBenchId("bench-1"), benchLane, 1.0f),
                        bench2, new AdaptingBenchStop(bench2, benchLane, 2.5f)),
                completion,
                AdaptingBenchStopControllerTest::hideTote));

        sim.update(0.30d);

        assertEquals(ToteMotionState.MOVING, tote.getInteractionMode());
        assertFalse(tote.areLidsOpen());
        assertEquals(1.2f, tote.getLastSnapshot().distanceAlongSegment(), 0.0001f);
        assertEquals("tote-b", area.bench(bench2).snapshot().activeToteId());
    }

    @Test
    void shouldHideStoreToteAfterBenchProcessingCompletes() {
        RouteSegment benchLane = segment("bench-lane", new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 6f));
        RouteSegment completion = segment("completion", new Vec3(0f, 0f, 6f), new Vec3(0f, 0f, 8f));
        benchLane.connectTo(completion);
        Tote tote = createTote("tote-store", benchLane, 0f, 4.0d);
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");
        DspOrderItem line = adaptedLine("line-3", "collect-3", "0000310");
        AdaptingVisit visit = AdaptingVisit.store(
                new PhysicalToteId("tote-store"),
                new OrderSheetKey("adapted-source-store", 1),
                "104",
                List.of(line));
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingArea area = area(new AdaptingBench("bench-1", store, 0d), storageMap("0000310", "bench-1"));
        DspSchedulerRuntimeState runtimeState = emptyRuntimeState();
        area.submitVisit(visit);

        Map<String, AdaptingBenchJourney> journeys = new LinkedHashMap<>();
        journeys.put(tote.getId(), new AdaptingBenchJourney(
                "order-3",
                tote,
                visit,
                benchId,
                benchLane,
                AdaptingJourneyPhase.TO_BENCH));

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addController(new AdaptingBenchStopController(
                area,
                new AdaptingAreaController(area, runtimeState),
                journeys,
                Map.of(benchId, new AdaptingBenchStop(benchId, benchLane, 1.0f)),
                completion,
                AdaptingBenchStopControllerTest::hideTote));

        sim.update(0.30d);
        sim.update(0.10d);

        assertFalse(journeys.containsKey(tote.getId()));
        assertFalse(tote.getRenderable().isVisible());
        assertEquals(-50f, tote.getRenderable().transformation.xTranslation, 0.0001f);
        assertTrue(runtimeState.snapshot().preparedLineKeys().contains(PreparedLineKey.forPreparedLine(line)));
    }

    @Test
    void shouldReleaseCollectToteBackToMovingAfterBenchProcessingCompletes() {
        RouteSegment benchLane = segment("bench-lane", new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 6f));
        RouteSegment completion = segment("completion", new Vec3(0f, 0f, 6f), new Vec3(0f, 0f, 8f));
        benchLane.connectTo(completion);
        Tote tote = createTote("tote-collect", benchLane, 0f, 4.0d);
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");
        DspOrderItem line = adaptedLine("line-4", "collect-4", "0000310");
        AdaptedLineStore store = new AdaptedLineStore();
        store.stage(line, new OrderSheetKey("adapted-source-collect", 1), "104");
        AdaptingVisit visit = AdaptingVisit.collect(
                new PhysicalToteId("tote-collect"),
                new OrderSheetKey("associated-collect", 1),
                "104",
                List.of(PreparedLineKey.forPreparedLine(line)),
                List.of("0000310"));
        AdaptingArea area = area(new AdaptingBench("bench-1", store, 0d), storageMap("0000310", "bench-1"));
        area.submitVisit(visit);

        Map<String, AdaptingBenchJourney> journeys = new LinkedHashMap<>();
        journeys.put(tote.getId(), new AdaptingBenchJourney(
                "order-4",
                tote,
                visit,
                benchId,
                benchLane,
                AdaptingJourneyPhase.TO_BENCH));

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addController(new AdaptingBenchStopController(
                area,
                new AdaptingAreaController(
                        area,
                        emptyRuntimeState(),
                        new online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry(),
                        new online.davisfamily.warehouse.sim.dsp.adapting.DefaultCollectedPackPlanFactory(
                                new online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory(
                                        new online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry()))),
                journeys,
                Map.of(benchId, new AdaptingBenchStop(benchId, benchLane, 1.0f)),
                completion,
                AdaptingBenchStopControllerTest::hideTote));

        sim.update(0.30d);
        sim.update(0.10d);

        assertTrue(journeys.containsKey(tote.getId()));
        assertEquals(ToteMotionState.MOVING, tote.getInteractionMode());
        assertFalse(tote.areLidsOpen());
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(line)));
    }

    private static AdaptingArea area(AdaptingBench bench, AdaptingStorageMap storageMap) {
        return new AdaptingArea(List.of(bench), 0, storageMap);
    }

    private static AdaptingArea area(List<AdaptingBench> benches, AdaptingStorageMap storageMap) {
        return new AdaptingArea(benches, 0, storageMap);
    }

    private static AdaptingStorageMap storageMap(String pharmacyId, String benchId) {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        storageMap.configureAvailableBenches(List.of(
                new AdaptingBenchId("bench-1"),
                new AdaptingBenchId("bench-2")));
        storageMap.assignPharmacyToBench(pharmacyId, new AdaptingBenchId(benchId));
        return storageMap;
    }

    private static DspOrderItem adaptedLine(String lineId, String targetOrderId, String pharmacyId) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                1,
                pharmacyId,
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0);
    }

    private static RouteSegment segment(String label, Vec3 start, Vec3 end) {
        return new RouteSegment(label, new LinearSegment3(start, end, false));
    }

    private static Tote createTote(String toteId, RouteSegment segment, float distance, double speed) {
        RenderableObject renderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        Tote tote = new Tote(
                toteId,
                new RouteFollower(toteId, segment, distance, speed),
                renderable,
                new Vec3(),
                0f);
        tote.snapToRouteDistance(distance);
        tote.closeLids();
        return tote;
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "anchor");
    }

    private static DspSchedulerRuntimeState emptyRuntimeState() {
        return new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(),
                Map.of(),
                Set.of(),
                Optional.empty()));
    }

    private static void hideTote(Tote tote) {
        tote.setInteractionMode(ToteMotionState.HELD);
        tote.closeLids();
        tote.getRenderable().setVisible(false);
        tote.getRenderable().transformation.xTranslation = -50f;
        tote.getRenderable().transformation.yTranslation = -50f;
        tote.getRenderable().transformation.zTranslation = -50f;
    }
}
