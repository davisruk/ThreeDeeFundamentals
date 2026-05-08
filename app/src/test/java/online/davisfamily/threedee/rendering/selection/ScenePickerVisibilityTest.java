package online.davisfamily.threedee.rendering.selection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.camera.CameraPosition;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;

class ScenePickerVisibilityTest {

    @Test
    void shouldNotPickHiddenSelectableObject() {
        ScenePicker picker = new ScenePicker();
        Camera camera = new Camera(new CameraPosition());
        RenderableObject renderable = selectableTriangle("pick-hidden");
        renderable.setVisible(false);

        PickHit hit = picker.pick(List.of(renderable), camera, (float) Math.toRadians(60), 50, 50, 101, 101);

        assertNull(hit);
    }

    @Test
    void shouldPickVisibleSelectableObject() {
        ScenePicker picker = new ScenePicker();
        Camera camera = new Camera(new CameraPosition());
        RenderableObject renderable = selectableTriangle("pick-visible");

        PickHit hit = picker.pick(List.of(renderable), camera, (float) Math.toRadians(60), 50, 50, 101, 101);

        assertNotNull(hit);
    }

    private static RenderableObject selectableTriangle(String id) {
        return RenderableObject.create(
                id,
                null,
                new Mesh(
                        new Vec4[] {
                                new Vec4(-1f, -1f, -5f, 1f),
                                new Vec4(1f, -1f, -5f, 1f),
                                new Vec4(0f, 1f, -5f, 1f)
                        },
                        new int[][] { {0, 1, 2} },
                        "pick-triangle"),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                true);
    }
}
