package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DspFullDayProgressOutputTest {

    @Test
    void shouldMirrorUtf8BlocksAndFlushBeforeClose(@TempDir Path directory) throws Exception {
        ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(consoleBytes, true, StandardCharsets.UTF_8);
        Path log = directory.resolve("nested").resolve("progress.log");

        DspFullDayProgressOutput output = DspFullDayProgressOutput.open(
                console, Optional.of(log), false);
        output.print("start", List.of("évidence", "remaining=2"));

        byte[] expected = consoleBytes.toByteArray();
        assertTrue(Files.isRegularFile(log));
        assertArrayEquals(expected, Files.readAllBytes(log));

        output.close();
        output.close();
        console.print("caller-still-open");
        console.flush();
        assertTrue(consoleBytes.toString(StandardCharsets.UTF_8).endsWith("caller-still-open"));
    }

    @Test
    void shouldRefuseExistingLogUnlessOverwriteIsExplicit(@TempDir Path directory)
            throws Exception {
        Path log = directory.resolve("progress.log");
        Files.writeString(log, "old-content", StandardCharsets.UTF_8);
        ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();

        assertThrows(FileAlreadyExistsException.class, () -> DspFullDayProgressOutput.open(
                new PrintStream(consoleBytes, true, StandardCharsets.UTF_8),
                Optional.of(log),
                false));
        assertEquals("old-content", Files.readString(log));

        try (DspFullDayProgressOutput output = DspFullDayProgressOutput.open(
                new PrintStream(consoleBytes, true, StandardCharsets.UTF_8),
                Optional.of(log),
                true)) {
            output.print("replacement", List.of("new-content"));
        }
        assertEquals(
                "[dsp-full-day:replacement]" + System.lineSeparator()
                        + "new-content" + System.lineSeparator(),
                Files.readString(log));
    }

    @Test
    void shouldRejectInvalidBlocksAndPreserveFlushedContentOnLaterFailure() throws Exception {
        ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
        FailingWriter writer = new FailingWriter();
        DspFullDayProgressOutput output = new DspFullDayProgressOutput(
                new PrintStream(consoleBytes, true, StandardCharsets.UTF_8),
                Optional.of(writer));

        assertThrows(IllegalArgumentException.class,
                () -> output.print(" ", List.of("line")));
        assertThrows(IllegalArgumentException.class,
                () -> output.print("milestone", Arrays.asList("line", null)));

        output.print("first", List.of("flushed"));
        String first = writer.content();
        writer.failFlush = true;
        assertThrows(IOException.class, () -> output.print("second", List.of("fails")));
        assertTrue(writer.content().startsWith(first));

        writer.failFlush = false;
        writer.failClose = true;
        assertThrows(IOException.class, output::close);
        output.close();
    }

    private static final class FailingWriter extends Writer {
        private final StringBuilder content = new StringBuilder();
        private boolean failFlush;
        private boolean failClose;

        @Override
        public void write(char[] cbuf, int off, int len) {
            content.append(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            if (failFlush) {
                throw new IOException("injected flush failure");
            }
        }

        @Override
        public void close() throws IOException {
            if (failClose) {
                throw new IOException("injected close failure");
            }
        }

        private String content() {
            return content.toString();
        }
    }
}
