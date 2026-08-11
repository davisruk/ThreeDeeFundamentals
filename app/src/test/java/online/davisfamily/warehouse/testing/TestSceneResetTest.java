package online.davisfamily.warehouse.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import javax.swing.JRootPane;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;

class TestSceneResetTest {
    @Test
    void shouldReinstallSameSceneWithFreshWorldAndRenderableInstances() {
        InspectableTestScene scene = createScene();
        SimulationWorld originalWorld = scene.simulationWorld();
        List<RenderableObject> originalObjects = scene.sceneObjects();
        List<String> originalIds = ids(originalObjects);
        RenderableObject originalSelection = originalObjects.getFirst();
        scene.select(originalSelection);
        scene.registerInspection(originalSelection, List.of("old scene"));

        scene.resetActiveScene();

        assertNotSame(originalWorld, scene.simulationWorld());
        assertEquals(DebugSceneKind.STRAIGHT_CONVEYOR, scene.activeSceneKind());
        assertEquals(originalIds, ids(scene.sceneObjects()));
        assertEquals(originalObjects.size(), scene.sceneObjects().size());
        assertFalse(scene.sceneObjects().stream().anyMatch(originalObjects::contains));
        assertNull(scene.getSelectedObject());
        assertEquals(List.of(), scene.describe(originalSelection));
    }

    @Test
    void shouldSafelyResetSceneRepeatedlyWithoutDuplicatingRenderables() {
        InspectableTestScene scene = createScene();
        int initialObjectCount = scene.sceneObjects().size();

        scene.resetActiveScene();
        SimulationWorld firstResetWorld = scene.simulationWorld();
        scene.resetActiveScene();

        assertNotSame(firstResetWorld, scene.simulationWorld());
        assertEquals(initialObjectCount, scene.sceneObjects().size());
    }

    private static InspectableTestScene createScene() {
        return new InspectableTestScene(
                new JRootPane(),
                new ViewDimensions(100, 100, 0, 0, 100, 100),
                new DebugSceneOptions(DebugSceneKind.STRAIGHT_CONVEYOR));
    }

    private static List<String> ids(List<RenderableObject> objects) {
        return objects.stream().map(renderable -> renderable.id).toList();
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

        private void select(RenderableObject renderable) {
            selectionManager.setSelected(renderable);
        }

        private void registerInspection(RenderableObject renderable, List<String> description) {
            inspectionRegistry.register(renderable, () -> description);
        }

        private List<String> describe(RenderableObject renderable) {
            return inspectionRegistry.describe(renderable);
        }
    }
}
