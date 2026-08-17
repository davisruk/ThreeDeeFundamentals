package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JRootPane;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class ThirdPartyDebugRigTest {

    @Test
    void shouldRunScheduledThirdPartyScenariosAndExposeInspection() {
        SimulationWorld sim = new SimulationWorld();
        List<RenderableObject> objects = new java.util.ArrayList<>();
        SelectionInspectionRegistry inspectionRegistry = new SelectionInspectionRegistry();
        ThirdPartyDebugRig rig = new ThirdPartyDebugRig(
                renderer(),
                sim,
                objects,
                inspectionRegistry);

        boolean observedWaitingVisit = false;
        List<String> waitingInspection = List.of();
        for (int i = 0; i < 400; i++) {
            sim.update(0.25d);
            if (!observedWaitingVisit && rig.areaSnapshot().waitingCount() > 0) {
                observedWaitingVisit = true;
                waitingInspection = inspectionRegistry.describe(rig.areaRenderable());
            }
        }

        assertTrue(observedWaitingVisit);
        assertTrue(waitingInspection.stream().anyMatch(line -> line.startsWith("Waiting totes: tote-")));
        assertTrue(waitingInspection.stream().anyMatch(line -> line.startsWith("Active order/tote:")));
        assertTrue(waitingInspection.stream().anyMatch(line -> line.startsWith("Work:")));
        assertTrue(waitingInspection.stream().anyMatch(line -> line.contains(" bin Y7")));

        assertEquals(
                List.of("pack-adapted-third-party-line-1"),
                packIds(rig, "tote-third-party-adapted"));
        assertEquals(
                List.of("pack-associated-adapted-ready", "pack-associated-direct-line-1"),
                packIds(rig, "tote-third-party-associated"));
        assertEquals(
                List.of("pack-regular-existing"),
                packIds(rig, "tote-regular-full-pack"));
        assertEquals(
                List.of("pack-later-third-party-line-1"),
                packIds(rig, "tote-third-party-later"));

        assertTrue(rig.completedThirdPartyToteIds().containsAll(List.of(
                "tote-third-party-adapted",
                "tote-third-party-associated",
                "tote-third-party-later")));
        assertFalse(rig.completedThirdPartyToteIds().contains("tote-regular-full-pack"));
        assertTrue(rig.schedulerSnapshot().orderStates().stream()
                .allMatch(orderState -> orderState.status() == DspOrderStatus.RELEASED));

        List<String> finalInspection = inspectionRegistry.describe(rig.areaRenderable());
        assertTrue(finalInspection.stream().anyMatch(line -> line.startsWith("Last completion:")));
        assertTrue(finalInspection.stream().anyMatch(line -> line.contains("Line: later-third-party-line")));
        assertTrue(objects.stream().anyMatch(object -> object.id.equals("third_party_source")));
        assertTrue(objects.stream().anyMatch(object -> object.id.equals("third_party_area")));
        assertTrue(objects.stream().anyMatch(object -> object.id.equals("third_party_adapting_boundary")));
        assertTrue(objects.stream().anyMatch(object -> object.id.equals("third_party_p2p_boundary")));

        rig.close();
    }

    @Test
    void shouldParseAndInstallThirdPartySceneAgainOnReset() {
        assertEquals(DebugSceneKind.THIRD_PARTY, DebugSceneKind.fromCliValue("third-party"));
        InspectableTestScene scene = new InspectableTestScene(
                new JRootPane(),
                new ViewDimensions(120, 100, 0, 0, 120, 100),
                new DebugSceneOptions(DebugSceneKind.THIRD_PARTY));
        List<RenderableObject> originalObjects = scene.sceneObjects();
        SimulationWorld originalWorld = scene.simulationWorld();

        scene.resetActiveScene();

        assertNotSame(originalWorld, scene.simulationWorld());
        assertEquals(originalObjects.size(), scene.sceneObjects().size());
        assertFalse(scene.sceneObjects().stream().anyMatch(originalObjects::contains));
        assertEquals(1, scene.sceneObjects().stream()
                .filter(object -> object.id.equals("third_party_area"))
                .count());
    }

    private static List<String> packIds(ThirdPartyDebugRig rig, String toteId) {
        return rig.loadPlanFor(toteId).getPackPlans().stream().map(PackPlan::packId).toList();
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
