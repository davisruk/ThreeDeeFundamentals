package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
