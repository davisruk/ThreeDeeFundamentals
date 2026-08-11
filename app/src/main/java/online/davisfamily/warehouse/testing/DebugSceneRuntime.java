package online.davisfamily.warehouse.testing;

public interface DebugSceneRuntime extends AutoCloseable {
    void syncVisuals();

    default void syncVisuals(double dtSeconds) {
        syncVisuals();
    }

    @Override
    default void close() {
    }

    static DebugSceneRuntime noop() {
        return () -> {
        };
    }
}
