package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TwelveNDatasetLoaderTest {
    private final TwelveNDatasetLoader loader = new TwelveNDatasetLoader();

    @Test
    void shouldLoadStringsInSourceOrder() {
        List<TwelveNMessageJson> messages = loader.loadStrings(List.of(
                messageJson("order-1", "001"),
                messageJson("order-2", "002")));

        assertEquals(List.of("order-1", "order-2"), messages.stream()
                .map(message -> message.header().orderId())
                .toList());
    }

    @Test
    void shouldLoadPathsInSourceOrder(@TempDir Path directory) throws Exception {
        Path first = directory.resolve("first.json");
        Path second = directory.resolve("second.json");
        Files.writeString(first, messageJson("order-1", "001"));
        Files.writeString(second, messageJson("order-2", "002"));

        List<TwelveNMessageJson> messages = loader.load(List.of(first, second));

        assertEquals(List.of("order-1", "order-2"), messages.stream()
                .map(message -> message.header().orderId())
                .toList());
    }

    @Test
    void shouldRejectNullInputsAndEntries() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
        assertThrows(IllegalArgumentException.class, () -> loader.load(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> loader.loadStrings(null));
        assertThrows(IllegalArgumentException.class, () -> loader.loadStrings(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString(null));
    }

    private static String messageJson(String orderId, String sheetNumber) {
        return """
                {
                  "header": {"orderId":"%s","sheetNumber":"%s"},
                  "toteIdentifier": {"payload":"05"},
                  "orderPriority": {"payload":"999"},
                  "serviceCentre": {"payload":"104"},
                  "orderDetail": {"numberOfOrderLines":0,"orderLines":[]}
                }
                """.formatted(orderId, sheetNumber);
    }
}
