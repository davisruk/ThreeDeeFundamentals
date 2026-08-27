package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JRootPane;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DspWarehouseTransportDebugRigTest {

    @Test
    void shouldRouteMixedTotesAndRecoverFromTerminalBackpressure() {
        SimulationWorld sim = new SimulationWorld();
        List<RenderableObject> objects = new ArrayList<>();
        SelectionInspectionRegistry inspectionRegistry = new SelectionInspectionRegistry();
        DspWarehouseTransportDebugRig rig = new DspWarehouseTransportDebugRig(
                renderer(), sim, objects, inspectionRegistry);

        for (int i = 0; i < 500; i++) {
            sim.update(0.10d);
        }

        assertTrue(rig.observedP2pBackpressure());
        assertTrue(rig.observedSeparatedP2pBackpressure());
        assertTrue(rig.p2pCapacityReturned());
        assertEquals(List.of("transport-p2p-1"), rig.consumedP2pToteIds());
        assertEquals(2L, rig.acceptedP2pArrivalCount());
        assertEquals(1, rig.p2pInputOccupancy());
        assertEquals(4, rig.runtime().arrivalController().snapshot().successfulArrivalCount());
        assertEquals(0, rig.runtime().inFlightSnapshot().occupancy());
        assertEquals(4, rig.expectedPhysicalToteIds().stream()
                .filter(id -> objects.stream().anyMatch(object -> object.id.equals(id)))
                .count());
        assertEquals(4, rig.expectedPhysicalToteIds().stream()
                .mapToLong(id -> objects.stream().filter(object -> object.id.equals(id)).count())
                .sum());
        assertEquals(List.of(
                DspWarehouseTransportDebugRig.THIRD_PARTY_TARGET_ID,
                DspWarehouseTransportDebugRig.ADAPTING_TARGET_ID,
                DspWarehouseTransportDebugRig.P2P_TARGET_ID,
                "warehouse-p2p-placeholder-2",
                "warehouse-p2p-placeholder-3",
                "warehouse-p2p-placeholder-4",
                "warehouse-p2p-placeholder-5"), rig.operationalLaunchTargetIds());
        assertEquals(List.of(
                DspWarehouseTransportDebugRig.THIRD_PARTY_TARGET_ID,
                DspWarehouseTransportDebugRig.ADAPTING_TARGET_ID,
                DspWarehouseTransportDebugRig.P2P_TARGET_ID),
                rig.warehouseRouteTargetIds());

        List<String> transportInspection = inspectionRegistry.describe(
                rig.transportInspectionMarker());
        assertTrue(transportInspection.stream().anyMatch(line -> line.startsWith("Launch:")));
        assertTrue(transportInspection.stream().anyMatch(line -> line.startsWith("Transport:")));
        assertTrue(transportInspection.stream().anyMatch(line -> line.startsWith("In flight:")));
        assertTrue(transportInspection.stream().anyMatch(line -> line.startsWith("Pending arrivals:")));
        assertTrue(transportInspection.stream()
                .anyMatch(line -> line.startsWith("P2P station arrival:")));
        assertTrue(transportInspection.stream()
                .anyMatch(line -> line.startsWith("P2P tipper input:")));
        assertEquals(5, transportInspection.stream()
                .filter(line -> line.startsWith("P2P lease warehouse-p2p-line-"))
                .count());
        assertTrue(transportInspection.stream().anyMatch(line ->
                line.startsWith("P2P lease warehouse-p2p-line-1:")
                        && line.contains("owner=104")
                        && line.contains("pharmacy=pharmacy-104-1")
                        && line.contains("transport-p2p-1")
                        && line.contains("blockers=[")
                        && line.contains("closure=none")
                        && line.contains("transition=ASSIGNMENT_COMMITTED")));
        assertTrue(transportInspection.stream().anyMatch(line ->
                line.startsWith("Elastic fixture:")
                        && line.contains("non-rendered P2P activity/output placeholders")
                        && line.contains("not a production-day execution")));
        assertTrue(transportInspection.contains(
                "Elastic profile: DEADLINE_AWARE_ELASTIC_STICKY_LEASES"));
        assertTrue(transportInspection.contains("Elastic calibration: UNCALIBRATED"));
        assertTrue(transportInspection.stream().anyMatch(line ->
                line.startsWith("SC 104 deadline:") && line.contains("slack=PT10H")));
        assertTrue(transportInspection.stream().anyMatch(line ->
                line.startsWith("SC 104 lines:")
                        && line.contains("required=2")
                        && line.contains("desired=2")
                        && line.contains("owned=1")
                        && line.contains("additional=1")));
        assertTrue(transportInspection.contains(
                "SC 104 feeding: [warehouse-p2p-line-1]"));
        assertTrue(transportInspection.contains("SC 104 draining: []"));

        for (String targetId : List.of(
                DspWarehouseTransportDebugRig.THIRD_PARTY_TARGET_ID,
                DspWarehouseTransportDebugRig.ADAPTING_TARGET_ID,
                DspWarehouseTransportDebugRig.P2P_TARGET_ID)) {
            List<String> queueInspection = inspectionRegistry.describe(rig.queueMarker(targetId));
            assertTrue(queueInspection.stream().anyMatch(line -> line.startsWith("Target: " + targetId)));
            assertTrue(queueInspection.stream().anyMatch(line -> line.startsWith("Queue:")));
            if (DspWarehouseTransportDebugRig.P2P_TARGET_ID.equals(targetId)) {
                assertTrue(queueInspection.stream()
                        .anyMatch(line -> line.startsWith("Tipper input:")));
                assertTrue(queueInspection.stream()
                        .anyMatch(line -> line.startsWith("Tipper totes:")));
            }
        }

        rig.close();
    }

    @Test
    void shouldAllocateAndHydrateAv02EmptyThroughSharedTransportRuntime() {
        SimulationWorld sim = new SimulationWorld();
        List<RenderableObject> objects = new ArrayList<>();
        SelectionInspectionRegistry inspectionRegistry = new SelectionInspectionRegistry();
        DspWarehouseTransportDebugRig rig = new DspWarehouseTransportDebugRig(
                renderer(), sim, objects, inspectionRegistry);

        assertEquals(1, rig.av02Capacity());
        assertEquals(0, rig.av02Occupancy());
        assertTrue(rig.av02AllocationSnapshot().command().isPresent());
        assertNull(rig.loadPlan("av02-000001"));
        assertFalse(rig.hasRenderable("av02-000001"));
        assertEquals(List.of("transport-p2p-1"), rig.pendingLaunchPhysicalToteIds());
        assertEquals(0, rig.successfulHydrationCount());

        sim.update(0.10d);

        assertEquals(1, rig.av02Occupancy());
        assertEquals(List.of("av02-000001"), rig.av02WaitingPhysicalToteIds());
        assertEquals("av02-000001", rig.activeAv02PhysicalToteId());
        ToteLoadPlan av02LoadPlan = rig.loadPlan("av02-000001");
        assertNotNull(av02LoadPlan);
        assertEquals(List.of(), av02LoadPlan.getPackPlans());
        assertFalse(rig.hasRenderable("av02-000001"));
        assertEquals(List.of(), rig.av02DepartedPhysicalToteIds());
        assertFalse(rig.pendingLaunchPhysicalToteIds().contains("av02-000001"));
        assertEquals(1, rig.successfulHydrationCount());
        assertTrue(rig.operationalReleaseSnapshot().lastEvaluation().orElseThrow()
                .releaseDecision().isEmpty());
        assertEquals(
                "transport-p2p-1",
                rig.runtime().routeLaunchController().snapshot()
                        .lastHydratedPhysicalToteId().orElseThrow().value());

        for (int i = 0; i < 88; i++) {
            sim.update(0.10d);
        }

        assertEquals(1, rig.av02Occupancy());
        assertEquals(List.of("av02-000001"), rig.av02WaitingPhysicalToteIds());
        assertEquals(List.of(), rig.av02DepartedPhysicalToteIds());
        assertEquals("av02-000001", rig.activeAv02PhysicalToteId());
        assertFalse(rig.hasRenderable("av02-000001"));
        assertEquals(3, rig.successfulHydrationCount());
        assertEquals(
                "transport-adapting",
                rig.runtime().routeLaunchController().snapshot()
                        .lastHydratedPhysicalToteId().orElseThrow().value());
        assertFalse(rig.pendingLaunchPhysicalToteIds().contains("av02-000001"));

        sim.update(0.11d);

        assertEquals(0, rig.av02Occupancy());
        assertEquals(List.of("av02-000001"), rig.av02DepartedPhysicalToteIds());
        assertEquals("av02-000001", rig.activeAv02PhysicalToteId());
        assertEquals(4, rig.successfulHydrationCount());
        assertEquals(
                "av02-000001",
                rig.runtime().routeLaunchController().snapshot()
                        .lastHydratedPhysicalToteId().orElseThrow().value());
        assertFalse(rig.pendingLaunchPhysicalToteIds().contains("av02-000001"));
        assertEquals(1, objects.stream()
                .filter(object -> object.id.equals("av02-000001"))
                .count());

        List<String> inspection = inspectionRegistry.describe(rig.transportInspectionMarker());
        assertTrue(inspection.stream().anyMatch(line ->
                line.startsWith("AV02 capacity: 0 / 1")));
        assertTrue(inspection.stream().anyMatch(line ->
                line.contains("AV02 waiting: []")));
        assertTrue(inspection.stream().anyMatch(line ->
                line.contains("AV02 departed: [av02-000001")));
        assertTrue(inspection.stream().anyMatch(line ->
                line.startsWith("AV02 last allocation: av02-000001")
                        && line.contains("pharmacy-104-1")));
        assertTrue(inspection.contains("Launch source: AV02"));
        assertTrue(inspection.contains("Selected first destination: P2P/warehouse-p2p"));
        assertTrue(inspection.contains("Pinned P2P line: warehouse-p2p-line-1"));

        rig.close();
    }

    @Test
    void shouldParseAndReconstructWarehouseTransportSceneOnReset() {
        assertEquals(
                DebugSceneKind.DSP_WAREHOUSE_TRANSPORT,
                DebugSceneKind.fromCliValue("dsp-warehouse-transport"));
        InspectableTestScene scene = new InspectableTestScene(
                new JRootPane(),
                new ViewDimensions(120, 100, 0, 0, 120, 100),
                new DebugSceneOptions(DebugSceneKind.DSP_WAREHOUSE_TRANSPORT));
        SimulationWorld originalWorld = scene.simulationWorld();
        List<RenderableObject> originalObjects = scene.sceneObjects();

        scene.resetActiveScene();

        assertNotSame(originalWorld, scene.simulationWorld());
        assertEquals(originalObjects.size(), scene.sceneObjects().size());
        assertFalse(scene.sceneObjects().stream().anyMatch(originalObjects::contains));
        assertEquals(1, scene.sceneObjects().stream()
                .filter(object -> object.id.equals("warehouse_transport_state"))
                .count());
        assertFalse(scene.sceneObjects().stream()
                .anyMatch(object -> object.id.equals("av02-000001")));
    }

    private static TriangleRenderer renderer() {
        int[] pixels = new int[100 * 100];
        return new TriangleRenderer(
                pixels,
                100,
                0,
                0,
                99,
                99,
                null,
                new InputState(),
                null);
    }

    private static final class InspectableTestScene extends TestScene {
        private InspectableTestScene(
                JRootPane pane,
                ViewDimensions dimensions,
                DebugSceneOptions options) {
            super(pane, dimensions, options);
        }

        private SimulationWorld simulationWorld() {
            return sim;
        }

        private List<RenderableObject> sceneObjects() {
            return List.copyOf(objects);
        }
    }
}
