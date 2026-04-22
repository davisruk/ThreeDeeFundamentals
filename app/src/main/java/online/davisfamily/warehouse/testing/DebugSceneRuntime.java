package online.davisfamily.warehouse.testing;

public interface DebugSceneRuntime {
    void syncVisuals();

    static DebugSceneRuntime noop() {
        return () -> {
        };
    }
}
