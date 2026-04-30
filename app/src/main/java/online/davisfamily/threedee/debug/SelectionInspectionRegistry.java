package online.davisfamily.threedee.debug;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.rendering.RenderableObject;

public class SelectionInspectionRegistry {
    private final Map<RenderableObject, Inspectable> inspectablesByTarget = new IdentityHashMap<>();

    public void register(RenderableObject renderable, Inspectable inspectable) {
        if (renderable == null) {
            throw new IllegalArgumentException("renderable must not be null");
        }
        if (inspectable == null) {
            throw new IllegalArgumentException("inspectable must not be null");
        }
        inspectablesByTarget.put(renderable.getSelectionTarget(), inspectable);
    }

    public List<String> describe(RenderableObject renderable) {
        if (renderable == null) {
            return List.of();
        }
        Inspectable inspectable = inspectablesByTarget.get(renderable.getSelectionTarget());
        return inspectable != null ? inspectable.describe() : List.of();
    }
}
