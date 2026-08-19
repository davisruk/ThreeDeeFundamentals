package online.davisfamily.warehouse.sim.dsp.osr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class OsrInventoryConfigTest {

    @Test
    void shouldProvideProductionCapacityAndPreloadServiceCentres() {
        OsrInventoryConfig config = OsrInventoryConfig.productionBaseline();

        assertEquals(1200, config.capacity());
        assertEquals(List.of("104", "108"), config.preloadServiceCentreIds());
    }

    @Test
    void shouldNormalizeAndPreserveConfiguredServiceCentreOrder() {
        List<String> sourceIds = new ArrayList<>(List.of(" 108 ", "104", " 116"));
        OsrInventoryConfig config = new OsrInventoryConfig(20, sourceIds);
        sourceIds.clear();

        assertEquals(List.of("108", "104", "116"), config.preloadServiceCentreIds());
        assertEquals(List.of(), new OsrInventoryConfig(1, List.of()).preloadServiceCentreIds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> config.preloadServiceCentreIds().add("110"));
    }

    @Test
    void shouldRejectInvalidCapacityBlankOrDuplicateServiceCentres() {
        assertThrows(IllegalArgumentException.class, () -> new OsrInventoryConfig(0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new OsrInventoryConfig(-1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new OsrInventoryConfig(1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventoryConfig(1, java.util.Arrays.asList("104", null)));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventoryConfig(1, List.of("104", "  ")));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrInventoryConfig(1, List.of("104", " 104 ")));
    }
}
