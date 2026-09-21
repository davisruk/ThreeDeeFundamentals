package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldRecoverOneMalformedDocumentAndPreserveSuccessfulSourceIdentity(@TempDir Path directory)
            throws Exception {
        Path first = directory.resolve("first.json");
        Path malformed = directory.resolve("malformed.json");
        Path third = directory.resolve("third.json");
        Files.writeString(first, messageJson("order-1", "001"));
        Files.writeString(malformed, "{ not valid json");
        Files.writeString(third, messageJson("order-3", "003"));

        TwelveNLoadResult result = loader.loadRecovering(List.of(first, malformed, third));

        assertEquals(List.of(first, third), result.messages().stream()
                .map(TwelveNInputMessage::path)
                .toList());
        assertEquals(List.of(0, 2), result.messages().stream()
                .map(TwelveNInputMessage::sourceMessageEncounterIndex)
                .toList());
        assertEquals(List.of(malformed), result.rejectedMessages().stream()
                .map(TwelveNRejectedInputMessage::path)
                .toList());
        TwelveNRejectedInputMessage rejected = result.rejectedMessages().getFirst();
        assertEquals(1, rejected.sourceMessageEncounterIndex());
        assertTrue(rejected.diagnostic().contains("Failed to read JSON string"));
        assertTrue(rejected.exceptionClassName().contains("IllegalArgumentException"));
        assertFalse(rejected.stackTraceLines().isEmpty());
    }

    @Test
    void shouldKeepUnreadableFileFatal(@TempDir Path directory) {
        Path unreadable = directory.resolve("does-not-exist.json");

        assertThrows(IllegalArgumentException.class,
                () -> loader.loadRecovering(List.of(unreadable)));
    }

    @Test
    void shouldKeepStrictMethodsStrictForMalformedInput(@TempDir Path directory) throws Exception {
        Path malformed = directory.resolve("malformed.json");
        Files.writeString(malformed, "{ not valid json");

        assertThrows(IllegalArgumentException.class, () -> loader.load(List.of(malformed)));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString("{ not valid json"));
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadStrings(List.of("{ not valid json")));
    }

    @Test
    void shouldRejectNullInputsAndEntries() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
        assertThrows(IllegalArgumentException.class, () -> loader.load(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> loader.loadRecovering(null));
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadRecovering(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> loader.loadStrings(null));
        assertThrows(IllegalArgumentException.class, () -> loader.loadStrings(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString(null));
    }

    private static String messageJson(String orderId, String sheetNumber) {
        return """
                {
                  "header": {"orderId":"%s","sheetNumber":"%s"},
                  "toteIdentifier": {"payload":"05"},
                  "transportContainer": {"payload":"transport"},
                  "orderPriority": {"payload":"999"},
                  "serviceCentre": {"payload":"104"},
                  "orderDetail": {
                    "numberOfOrderLines":1,
                    "orderLines": [{
                      "orderLineNumber":"line-%s",
                      "orderLineType":"05",
                      "pharmacyId":"pharmacy",
                      "patientId":"patient",
                      "prescriptionId":"prescription",
                      "productId":"product",
                      "numberOfPacks":"0001",
                      "referenceSheetNumber":"001",
                      "numberOfPacksPicked":"0000",
                      "referenceOrderId":"%s"
                    }]
                  }
                }
                """.formatted(orderId, sheetNumber, orderId, orderId);
    }
}
