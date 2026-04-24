package online.davisfamily.warehouse.testing;

public interface DebugSceneRuntime {
    void syncVisuals();

    default void syncVisuals(double dtSeconds) {
        syncVisuals();
    }

    static DebugSceneRuntime noop() {
        return () -> {
        };
    }
}
