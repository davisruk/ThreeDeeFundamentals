package online.davisfamily.warehouse.rendering.model.tote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.rendering.RenderableObject;

class RenderableToteFactoryTest {

    @Test
    void shouldCreateToteWithClosedStaticLids() {
        RenderableObject tote = RenderableToteFactory.createRenderableTote(
                "tote",
                null,
                new ToteGeometry(),
                true);

        RenderableObject leftLid = findChild(tote, "tote_LeftLid");
        RenderableObject rightLid = findChild(tote, "tote_RightLid");

        assertEquals(0f, leftLid.transformation.angleZ, 0.0001f);
        assertEquals(0f, rightLid.transformation.angleZ, 0.0001f);
        assertTrue(leftLid.behaviours.isEmpty());
        assertTrue(rightLid.behaviours.isEmpty());
    }

    private RenderableObject findChild(RenderableObject parent, String id) {
        return parent.children.stream()
                .filter(child -> child.id.equals(id))
                .findFirst()
                .orElseThrow();
    }
}
