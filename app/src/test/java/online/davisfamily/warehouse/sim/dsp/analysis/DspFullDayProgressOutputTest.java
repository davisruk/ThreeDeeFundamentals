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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocation;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

class DspFullDayProgressOutputTest {

    @Test
    void shouldMirrorCorrectedSharedOutboundSummaryAndPreserveWallClockLines(@TempDir Path directory)
            throws Exception {
        var sheet = new OrderSheetKey("order", 1);
        var toteId = new PhysicalToteId("outbound-line-1-1");
        var plannedBag = new PlannedBag(new BagKey("prescription", 1), "104", "pharmacy",
                "patient", "prescription", List.of("pack"), List.of(sheet));
        var bag = new AllocatedOutboundBag(plannedBag, toteId,
                List.of(new OutputSheetAllocation(sheet, sheet)));
        var tote = new OutboundToteSnapshot(toteId, new P2pLineId("line-1"),
                Optional.of("104"), Optional.of("pharmacy"), 2, List.of(bag),
                Optional.of(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE));
        var shared = new OutboundAllocationSnapshot(Map.of(), List.of(tote), List.of(bag));
        String summary = DspFullDayAnalysisRunner.closedOutboundTotesByServiceCentre(
                List.of(shared, shared, shared, shared, shared), List.of("108", "104"));
        var bytes = new ByteArrayOutputStream();
        Path log = directory.resolve("outbound-summary.log");
        try (var output = DspFullDayProgressOutput.open(
                new PrintStream(bytes, true, StandardCharsets.UTF_8), Optional.of(log), false)) {
            output.print("progress=PT1M", List.of(
                    "WallClock: sincePreviousProgress=PT2S", "WallClock: sinceStart=PT4S", summary));
            assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(log));
            assertEquals(List.of("[dsp-full-day:progress=PT1M]",
                    "WallClock: sincePreviousProgress=PT2S", "WallClock: sinceStart=PT4S",
                    "ClosedOutboundTotesByServiceCentre: 108=0 104=1"
                            + " | AllocatedBagsByServiceCentre: 108=0 104=1"
                            + " | MissingPacksByServiceCentre: 108=0 104=0"), Files.readAllLines(log));
        }
    }

    @Test
    void shouldFlushStationSettingsWithStartBeforeLaterProgress(@TempDir Path directory) throws Exception {
        var bytes = new ByteArrayOutputStream();
        Path log = directory.resolve("station-settings.log");
        List<String> settings = List.of(
                "StationProcessing: thirdPartySeconds=20.0 adaptingPositions=18 adaptingWaiting=18",
                "AdaptingConfig[bench-1]: storeSeconds=60.0 collectSeconds=10.0 positions=3 waitingCapacity=3");
        try (var output = DspFullDayProgressOutput.open(
                new PrintStream(bytes, true, StandardCharsets.UTF_8), Optional.of(log), false)) {
            output.print("start", settings);
            assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(log));
            output.print("progress=PT1M", List.of("Remaining: totes=1"));
            assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(log));
            assertEquals(settings, Files.readAllLines(log).subList(1, 3));
            assertEquals(1, Files.readAllLines(log).stream()
                    .filter(line -> line.startsWith("StationProcessing:")).count());
        }
    }

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
