package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class JsonLoaderSupportTest {

    @Test
    void shouldReadValidJsonString() {
        TestJsonRecord record = JsonLoaderSupport.readString("{\"name\":\"alpha\",\"count\":3}", TestJsonRecord.class);

        assertEquals("alpha", record.name());
        assertEquals(3, record.count());
    }

    @Test
    void shouldRejectInvalidJsonString() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonLoaderSupport.readString("{\"name\":", TestJsonRecord.class));

        assertTrue(exception.getMessage().contains(TestJsonRecord.class.getName()));
    }

    @Test
    void shouldRejectMissingFile() {
        Path missingPath = Path.of("build", "tmp", "missing-json-loader-support-test.json");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonLoaderSupport.read(missingPath, TestJsonRecord.class));

        assertTrue(exception.getMessage().contains(missingPath.toString()));
        assertTrue(exception.getMessage().contains(TestJsonRecord.class.getName()));
    }

    private record TestJsonRecord(String name, int count) {
    }
}
