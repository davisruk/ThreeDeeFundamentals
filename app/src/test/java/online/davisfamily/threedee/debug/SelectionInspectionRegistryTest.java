package online.davisfamily.threedee.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.rendering.RenderableObject;

class SelectionInspectionRegistryTest {
    @Test
    void shouldClearRegisteredInspections() {
        SelectionInspectionRegistry registry = new SelectionInspectionRegistry();
        RenderableObject renderable = RenderableObject.create("target", null, null, null, null, true);
        registry.register(renderable, () -> List.of("registered"));

        registry.clear();

        assertEquals(List.of(), registry.describe(renderable));
    }
}
