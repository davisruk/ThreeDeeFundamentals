package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.threedee.rendering.RenderableObject;

public final class PackRenderableVisibility {

    private PackRenderableVisibility() {
    }

    public static void show(RenderableObject renderable) {
        requireRenderable(renderable).setVisible(true);
    }

    public static void hide(RenderableObject renderable) {
        requireRenderable(renderable).setVisible(false);
    }

    public static void hideAndResetPose(RenderableObject renderable) {
        RenderableObject requiredRenderable = requireRenderable(renderable);
        requiredRenderable.setVisible(false);
        requiredRenderable.transformation.xTranslation = 0f;
        requiredRenderable.transformation.yTranslation = 0f;
        requiredRenderable.transformation.zTranslation = 0f;
        requiredRenderable.transformation.angleX = 0f;
        requiredRenderable.transformation.angleY = 0f;
        requiredRenderable.transformation.angleZ = 0f;
    }

    private static RenderableObject requireRenderable(RenderableObject renderable) {
        if (renderable == null) {
            throw new IllegalArgumentException("Renderable must not be null");
        }
        return renderable;
    }
}
