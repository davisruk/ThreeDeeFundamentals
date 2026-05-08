package online.davisfamily.warehouse.sim.dsp.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonLoaderSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonLoaderSupport() {
    }

    static <T> T read(Path path, Class<T> type) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }

        try {
            return OBJECT_MAPPER.readValue(Files.readString(path), type);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to read JSON from path " + path + " into " + type.getName(),
                    e);
        }
    }

    static <T> T readString(String json, Class<T> type) {
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }

        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to read JSON string into " + type.getName(),
                    e);
        }
    }
}
