package online.davisfamily.warehouse.testing;

import org.junit.jupiter.api.Test;

class DebugSceneRuntimeTest {
    @Test
    void shouldSafelyCloseNoopRuntime() {
        DebugSceneRuntime.noop().close();
    }
}
