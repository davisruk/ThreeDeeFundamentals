package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixtures.Candidate;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixtures.Fixture;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe.BenchmarkCase;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe.Kernel;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe.Mode;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe.ProbeConfig;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe.Sample;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe.Settings;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe.Summary;

class P2pHistoryScalingProbeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldParseAllSupportedModesAndRunIndexesWithoutWriting() {
        Path output = temporaryDirectory.resolve("reports with space=valid");
        for (Mode mode : Mode.values()) {
            for (int runIndex = 1; runIndex <= 3; runIndex++) {
                var options = P2pHistoryScalingProbe.parseArgs(new String[] {
                        "--output-dir=" + output, "--run-index=" + runIndex,
                        "--mode=" + (mode == Mode.MATRIX ? "matrix" : "smoke") });
                assertEquals(new ProbeConfig(mode, runIndex), options.config());
                assertEquals(output, options.outputDirectory());
            }
        }
        assertFalse(Files.exists(output));
    }

    @Test
    void shouldRejectInvalidArgumentsAndDirectConfigsBeforeWriting() {
        Path output = temporaryDirectory.resolve("must-not-exist");
        String outputArg = "--output-dir=" + output;
        List<String[]> invalid = List.of(
                new String[] {},
                new String[] { "--mode=smoke", outputArg },
                new String[] { "--mode=smoke", "--run-index=1" },
                new String[] { "--run-index=1", outputArg },
                new String[] { "--mode=smoke", "--mode=matrix", "--run-index=1", outputArg },
                new String[] { "--mode=smoke", "--run-index=1", "--run-index=2", outputArg },
                new String[] { "--mode=smoke", "--run-index=1", outputArg, outputArg },
                new String[] { "--mode=smoke", "--run-index=1", outputArg, "--extra=value" },
                new String[] { "--mode=SMOKE", "--run-index=1", outputArg },
                new String[] { "--mode=smoke", "--run-index=0", outputArg },
                new String[] { "--mode=smoke", "--run-index=4", outputArg },
                new String[] { "--mode=smoke", "--run-index=01", outputArg },
                new String[] { "--mode=smoke", "--run-index=one", outputArg },
                new String[] { "--mode=smoke", "--run-index=1", "--output-dir= " },
                new String[] { "--mode=", "--run-index=1", outputArg },
                new String[] { "--mode", "smoke", "--run-index=1", outputArg },
                new String[] { null, "--mode=smoke", "--run-index=1", outputArg });
        for (String[] arguments : invalid) {
            assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingProbe.main(arguments));
            assertFalse(Files.exists(output));
        }
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingProbe.parseArgs(null));
        assertThrows(IllegalArgumentException.class, () -> new ProbeConfig(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProbeConfig(Mode.SMOKE, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProbeConfig(Mode.SMOKE, 4));
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingProbe.run(null, output));
        assertThrows(IllegalArgumentException.class,
                () -> P2pHistoryScalingProbe.run(new ProbeConfig(Mode.SMOKE, 1), null));
        assertThrows(IllegalArgumentException.class,
                () -> P2pHistoryScalingProbe.run(new ProbeConfig(Mode.SMOKE, 1), Path.of("")));
        assertFalse(Files.exists(output));
    }

    @Test
    void shouldLeaveExistingOutputDirectoryUntouched() throws IOException {
        Path output = Files.createDirectory(temporaryDirectory.resolve("existing"));
        Path sentinel = output.resolve("sentinel.txt");
        Files.writeString(sentinel, "keep this result");

        assertThrows(IllegalArgumentException.class,
                () -> P2pHistoryScalingProbe.run(new ProbeConfig(Mode.SMOKE, 1), output));

        assertEquals("keep this result", Files.readString(sentinel));
        try (var files = Files.list(output)) {
            assertEquals(List.of(sentinel), files.toList());
        }
    }

    @Test
    void shouldWriteExactlyTheSmokeSamplesAndSummariesThroughTheRealRunner() throws IOException {
        ProbeConfig config = new ProbeConfig(Mode.SMOKE, 1);
        Path output = temporaryDirectory.resolve("smoke");
        P2pHistoryScalingProbe.run(config, output);

        try (var files = Files.list(output)) {
            assertEquals(Set.of("metadata.txt", "samples.csv", "summary.csv"),
                    new HashSet<>(files.map(path -> path.getFileName().toString()).toList()));
        }
        String metadata = Files.readString(output.resolve("metadata.txt"));
        for (String expected : List.of("schemaVersion=1", "mode=SMOKE", "runIndex=1", "status=COMPLETE",
                "budgetNanos=120000000000", "historySizes=[0, 10]", "pendingSizes=[2]",
                "warmupRounds=1", "measuredRounds=3", "candidatePasses=1", "catalogConstructions=3",
                "startedAt=", "endedAt=", "jvmName=", "javaVersion=", "jvmVersion=", "jvmVendor=",
                "jvmArguments=", "os=", "architecture=", "processorCount=", "maximumHeapBytes=",
                "allocationCounterAvailable=", "gcCollectionCounterAvailable=", "gcTimeCounterAvailable=",
                "Index construction/memory excluded", "Volatile sink overhead included", "failure=none")) {
            assertTrue(metadata.contains(expected), expected);
        }
        List<String> raw = Files.readAllLines(output.resolve("samples.csv"));
        List<String> summary = Files.readAllLines(output.resolve("summary.csv"));
        assertEquals(P2pHistoryScalingProbe.SAMPLE_HEADER, raw.getFirst());
        assertEquals(P2pHistoryScalingProbe.SUMMARY_HEADER, summary.getFirst());
        assertEquals(25, raw.size());
        assertEquals(9, summary.size());
        Map<BenchmarkCase, Set<Integer>> indexes = new LinkedHashMap<>();
        Map<BenchmarkCase, List<Double>> times = new LinkedHashMap<>();
        for (String row : raw.subList(1, raw.size())) {
            String[] values = row.split(",", -1);
            assertEquals(14, values.length);
            BenchmarkCase benchmarkCase = parseCase(values);
            assertEquals(1, Integer.parseInt(values[0]));
            assertEquals(benchmarkCase.historicalCount() + 40, Integer.parseInt(values[3]));
            assertTrue(indexes.computeIfAbsent(benchmarkCase, ignored -> new HashSet<>())
                    .add(Integer.parseInt(values[5])));
            long operations = Long.parseLong(values[6]);
            assertEquals(benchmarkCase.kernel() == Kernel.CATALOG_REBUILD ? 3 : 2, operations);
            long nanos = Long.parseLong(values[7]);
            assertTrue(nanos >= 0);
            double nanosPerOperation = Double.parseDouble(values[8]);
            assertEquals((double) nanos / operations, nanosPerOperation, 0.000001);
            times.computeIfAbsent(benchmarkCase, ignored -> new ArrayList<>()).add(nanosPerOperation);
            if (values[9].equals("NA")) {
                assertEquals("NA", values[10]);
            } else {
                long bytes = Long.parseLong(values[9]);
                assertTrue(bytes >= 0);
                assertEquals((double) bytes / operations, Double.parseDouble(values[10]), 0.000001);
            }
            assertTrue(values[11].equals("NA") || Long.parseLong(values[11]) >= 0);
            assertTrue(values[12].equals("NA") || Long.parseLong(values[12]) >= 0);
            long expectedChecksum = switch (benchmarkCase.kernel()) {
                case REQUEST_FACTORY -> operations;
                case CATALOG_REBUILD -> operations * 5;
                case LOOKUP_LINEAR, LOOKUP_INDEX_CONTROL -> 0;
            };
            assertEquals(expectedChecksum, Long.parseLong(values[13]));
        }
        assertEquals(new HashSet<>(P2pHistoryScalingProbe.cases(config)), indexes.keySet());
        indexes.values().forEach(value -> assertEquals(Set.of(0, 1, 2), value));
        Set<BenchmarkCase> summarized = new HashSet<>();
        for (String row : summary.subList(1, summary.size())) {
            String[] values = row.split(",", -1);
            assertEquals(10, values.length);
            BenchmarkCase benchmarkCase = parseCase(values);
            assertTrue(summarized.add(benchmarkCase));
            assertEquals(1, Integer.parseInt(values[0]));
            assertEquals(benchmarkCase.historicalCount() + 40, Integer.parseInt(values[3]));
            assertEquals(3, Integer.parseInt(values[5]));
            List<Double> sorted = new ArrayList<>(times.get(benchmarkCase));
            sorted.sort(Double::compareTo);
            assertEquals(sorted.get(1), Double.parseDouble(values[6]), 0.000001);
            assertEquals(sorted.getFirst(), Double.parseDouble(values[7]), 0.000001);
            assertEquals(sorted.getLast(), Double.parseDouble(values[8]), 0.000001);
            assertTrue(values[9].equals("NA") || Double.parseDouble(values[9]) >= 0);
        }
        assertEquals(indexes.keySet(), summarized);
    }

    @Test
    void shouldExcludeWarmupAndIncompleteCasesAndPreserveUnavailableAllocation() {
        ProbeConfig config = new ProbeConfig(Mode.SMOKE, 1);
        BenchmarkCase benchmarkCase = P2pHistoryScalingProbe.cases(config).getFirst();
        List<Sample> samples = List.of(
                sample(benchmarkCase, -1, 100_000, 100_000L),
                sample(benchmarkCase, 0, 900, 9000L),
                sample(benchmarkCase, 1, 100, 1000L),
                sample(benchmarkCase, 2, 500, 5000L));
        Summary summary = P2pHistoryScalingProbe.summarize(config, samples).getFirst();
        assertEquals(3, summary.sampleCount());
        assertEquals(50d, summary.medianNanosPerOperation());
        assertEquals(10d, summary.minNanosPerOperation());
        assertEquals(90d, summary.maxNanosPerOperation());
        assertEquals(500d, summary.medianBytesPerOperation());
        assertTrue(P2pHistoryScalingProbe.summarize(config, samples.subList(0, 3)).isEmpty());
        assertNull(P2pHistoryScalingProbe.summarize(config, List.of(
                sample(benchmarkCase, 0, 900, 9000L), sample(benchmarkCase, 1, 100, null),
                sample(benchmarkCase, 2, 500, 5000L))).getFirst().medianBytesPerOperation());
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingProbe.summarize(config,
                List.of(samples.get(1), samples.get(1), samples.get(2))));
        assertEquals(2d, P2pHistoryScalingProbe.median(List.of(3d, 1d, 2d)));
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingProbe.median(List.of()));
    }

    @Test
    void shouldPreserveUnavailableDecreasingAndOverflowingCountersAsUnavailable() {
        assertEquals(0L, P2pHistoryScalingProbe.counterDelta(0, 0));
        assertEquals(5L, P2pHistoryScalingProbe.counterDelta(3, 8));
        assertNull(P2pHistoryScalingProbe.counterDelta(-1, 5));
        assertNull(P2pHistoryScalingProbe.counterDelta(0, -1));
        assertNull(P2pHistoryScalingProbe.counterDelta(8, 3));
        assertEquals(0L, P2pHistoryScalingProbe.aggregateCounters(0, 0));
        assertEquals(7L, P2pHistoryScalingProbe.aggregateCounters(2, 5));
        assertEquals(-1L, P2pHistoryScalingProbe.aggregateCounters());
        assertEquals(-1L, P2pHistoryScalingProbe.aggregateCounters(2, -1));
        assertEquals(-1L, P2pHistoryScalingProbe.aggregateCounters(Long.MAX_VALUE, 1));
    }

    @Test
    void shouldKeepMatrixWorkAndOrderingFixedWithoutExecutingIt() {
        ProbeConfig matrix = new ProbeConfig(Mode.MATRIX, 1);
        Settings settings = P2pHistoryScalingProbe.settings(matrix);
        assertEquals(List.of(0, 500, 2000, 5000), settings.historySizes());
        assertEquals(List.of(100, 400, 800), settings.pendingSizes());
        assertEquals(5, settings.warmupRounds());
        assertEquals(7, settings.measuredRounds());
        assertEquals(20, settings.candidatePasses());
        assertEquals(200, settings.catalogConstructions());
        List<BenchmarkCase> cases = P2pHistoryScalingProbe.cases(matrix);
        assertEquals(40, cases.size());
        assertEquals(new BenchmarkCase(Kernel.REQUEST_FACTORY, 0, 100), cases.getFirst());
        assertEquals(new BenchmarkCase(Kernel.REQUEST_FACTORY, 5000, 800), cases.get(11));
        assertEquals(new BenchmarkCase(Kernel.LOOKUP_LINEAR, 0, 100), cases.get(12));
        assertEquals(new BenchmarkCase(Kernel.LOOKUP_INDEX_CONTROL, 0, 100), cases.get(24));
        assertEquals(new BenchmarkCase(Kernel.CATALOG_REBUILD, 0, 0), cases.get(36));
        assertEquals(new BenchmarkCase(Kernel.CATALOG_REBUILD, 5000, 0), cases.getLast());
        for (int runIndex = 1; runIndex <= 3; runIndex++) {
            for (int round = 0; round < 12; round++) {
                List<BenchmarkCase> ordered = P2pHistoryScalingProbe.orderedCases(cases, round, runIndex);
                assertEquals(new HashSet<>(cases), new HashSet<>(ordered));
                for (int position = 0; position < 40; position++) {
                    int rotatedPosition = round % 2 == 0 ? position : 39 - position;
                    int expectedIndex = (rotatedPosition + round * 7 + runIndex * 11) % 40;
                    assertEquals(cases.get(expectedIndex), ordered.get(position));
                }
            }
        }
        assertEquals(16_000L, P2pHistoryScalingProbe.operations(cases.get(11), settings));
        assertEquals(200L, P2pHistoryScalingProbe.operations(cases.getLast(), settings));
        assertThrows(UnsupportedOperationException.class, () -> cases.clear());
    }

    @Test
    void shouldCheckCooperativeBudgetAtSafeBoundariesWithoutWaiting() {
        assertFalse(P2pHistoryScalingProbe.budgetExceeded(0));
        assertFalse(P2pHistoryScalingProbe.budgetExceeded(119_999_999_999L));
        assertTrue(P2pHistoryScalingProbe.budgetExceeded(120_000_000_000L));
        assertTrue(P2pHistoryScalingProbe.budgetExceeded(120_000_000_001L));
        ProbeConfig config = new ProbeConfig(Mode.SMOKE, 1);
        Fixture fixture = P2pHistoryScalingFixtures.create(0, 2);
        for (BenchmarkCase benchmarkCase : P2pHistoryScalingProbe.cases(config)) {
            assertThrows(IllegalStateException.class, () -> P2pHistoryScalingProbe.executeKernel(
                    benchmarkCase, fixture, P2pHistoryScalingProbe.settings(config),
                    System.nanoTime() - 120_000_000_000L));
        }
    }

    @Test
    void shouldConsumeRealKernelResultsWithoutMutatingFixtureState() {
        ProbeConfig config = new ProbeConfig(Mode.SMOKE, 1);
        Settings settings = P2pHistoryScalingProbe.settings(config);
        Fixture fixture = P2pHistoryScalingFixtures.create(10, 2);
        var beforeIndex = new LinkedHashMap<>(fixture.indexedAssignments());
        var beforeCandidates = new ArrayList<>(fixture.candidates());
        var beforeAssignments = fixture.lines().stream()
                .map(line -> new ArrayList<>(line.physicalAssignments())).toList();
        for (Kernel kernel : Kernel.values()) {
            BenchmarkCase benchmarkCase = new BenchmarkCase(kernel, 10, kernel == Kernel.CATALOG_REBUILD ? 0 : 2);
            long checksum = P2pHistoryScalingProbe.executeKernel(benchmarkCase, fixture, settings, System.nanoTime());
            switch (kernel) {
                case REQUEST_FACTORY -> {
                    assertEquals(2L, checksum);
                    P2pLineAllocationRequest result = assertInstanceOf(P2pLineAllocationRequest.class,
                            P2pHistoryScalingProbe.lastResult());
                    assertSame(fixture.catalog(), result.lineCatalog());
                    assertSame(fixture.candidates().getLast().toteId(), result.physicalToteId());
                }
                case LOOKUP_LINEAR, LOOKUP_INDEX_CONTROL -> {
                    assertEquals(0L, checksum);
                    assertEquals(Optional.empty(), P2pHistoryScalingProbe.lastResult());
                }
                case CATALOG_REBUILD -> {
                    assertEquals(15L, checksum);
                    P2pLineLeaseCatalogSnapshot result = assertInstanceOf(P2pLineLeaseCatalogSnapshot.class,
                            P2pHistoryScalingProbe.lastResult());
                    assertNotSame(fixture.catalog(), result);
                    for (int index = 0; index < 5; index++) {
                        assertSame(fixture.lines().get(index), result.lines().get(index));
                    }
                }
            }
        }
        assertEquals(beforeIndex, fixture.indexedAssignments());
        assertEquals(beforeCandidates, fixture.candidates());
        for (int index = 0; index < 5; index++) {
            assertEquals(beforeAssignments.get(index), fixture.lines().get(index).physicalAssignments());
        }

        P2pPhysicalToteAssignment assigned = fixture.lines().getLast().physicalAssignments().getLast();
        Candidate candidate = new Candidate(fixture.candidates().getFirst().source(), assigned.physicalToteId(),
                fixture.candidates().getFirst().sheetKey());
        Fixture assignedCandidate = new Fixture(10, fixture.lines(), fixture.catalog(), fixture.requestFactory(),
                fixture.indexedAssignments(), List.of(candidate));
        for (Kernel kernel : List.of(Kernel.LOOKUP_LINEAR, Kernel.LOOKUP_INDEX_CONTROL)) {
            assertEquals(1L, P2pHistoryScalingProbe.executeKernel(
                    new BenchmarkCase(kernel, 10, 1), assignedCandidate, settings, System.nanoTime()));
            Optional<?> result = assertInstanceOf(Optional.class, P2pHistoryScalingProbe.lastResult());
            assertSame(assigned, result.orElseThrow());
        }
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingProbe.executeKernel(
                new BenchmarkCase(Kernel.REQUEST_FACTORY, 10, 1), assignedCandidate, settings, System.nanoTime()));
    }

    private static BenchmarkCase parseCase(String[] values) {
        return new BenchmarkCase(Kernel.valueOf(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[4]));
    }

    private static Sample sample(BenchmarkCase benchmarkCase, int index, long nanos, Long allocatedBytes) {
        return new Sample(1, benchmarkCase, index, 10, nanos, allocatedBytes, null, null, 10);
    }
}
