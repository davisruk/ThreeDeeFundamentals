package online.davisfamily.warehouse.sim.totebag.assembly;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;

class PackRenderableVisibilityTest {

    @Test
    void shouldShowRenderable() {
        RenderableObject renderable = renderable();
        renderable.setVisible(false);

        PackRenderableVisibility.show(renderable);

        assertTrue(renderable.isVisible());
    }

    @Test
    void shouldHideRenderable() {
        RenderableObject renderable = renderable();

        PackRenderableVisibility.hide(renderable);

        assertFalse(renderable.isVisible());
    }

    @Test
    void shouldHideAndResetPose() {
        RenderableObject renderable = renderable();
        renderable.transformation.xTranslation = 1f;
        renderable.transformation.yTranslation = 2f;
        renderable.transformation.zTranslation = 3f;
        renderable.transformation.angleX = 4f;
        renderable.transformation.angleY = 5f;
        renderable.transformation.angleZ = 6f;

        PackRenderableVisibility.hideAndResetPose(renderable);

        assertFalse(renderable.isVisible());
        assertTrue(renderable.transformation.xTranslation == 0f);
        assertTrue(renderable.transformation.yTranslation == 0f);
        assertTrue(renderable.transformation.zTranslation == 0f);
        assertTrue(renderable.transformation.angleX == 0f);
        assertTrue(renderable.transformation.angleY == 0f);
        assertTrue(renderable.transformation.angleZ == 0f);
    }

    @Test
    void shouldRejectNullRenderable() {
        assertThrows(IllegalArgumentException.class, () -> PackRenderableVisibility.show(null));
        assertThrows(IllegalArgumentException.class, () -> PackRenderableVisibility.hide(null));
        assertThrows(IllegalArgumentException.class, () -> PackRenderableVisibility.hideAndResetPose(null));
    }

    private static RenderableObject renderable() {
        return RenderableObject.create(
                "pack",
                null,
                new Mesh(
                        new Vec4[] {
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(1f, 0f, 0f, 1f),
                                new Vec4(0f, 1f, 0f, 1f)
                        },
                        new int[][] { {0, 1, 2} },
                        "triangle"),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                true);
    }
}
