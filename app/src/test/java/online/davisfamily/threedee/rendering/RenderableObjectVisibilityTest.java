package online.davisfamily.threedee.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;

class RenderableObjectVisibilityTest {

    @Test
    void shouldBeVisibleByDefault() {
        RenderableObject renderable = renderable("visible-default");

        assertTrue(renderable.isVisible());
    }

    @Test
    void shouldNotUpdateBehaviourWhenHidden() {
        CountingBehaviour behaviour = new CountingBehaviour();
        RenderableObject renderable = renderableWithBehaviour("hidden-self", behaviour);
        renderable.setVisible(false);

        renderable.update(1.0d);

        assertEquals(0, behaviour.updateCount);
    }

    @Test
    void shouldNotUpdateChildBehaviourWhenParentIsHidden() {
        CountingBehaviour childBehaviour = new CountingBehaviour();
        RenderableObject parent = renderable("hidden-parent");
        RenderableObject child = renderableWithBehaviour("child", childBehaviour);
        parent.addChild(child);
        parent.setVisible(false);

        parent.update(1.0d);

        assertEquals(0, childBehaviour.updateCount);
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                triangleMesh(),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                true);
    }

    private static RenderableObject renderableWithBehaviour(String id, Behaviour behaviour) {
        return RenderableObject.createWithBehaviours(
                id,
                null,
                triangleMesh(),
                new ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                true,
                behaviour);
    }

    private static Mesh triangleMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(1f, 0f, 0f, 1f),
                        new Vec4(0f, 1f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "triangle");
    }

    private static final class CountingBehaviour implements Behaviour {
        private int updateCount;

        @Override
        public void update(RenderableObject object, double dtSeconds) {
            updateCount++;
        }
    }
}
