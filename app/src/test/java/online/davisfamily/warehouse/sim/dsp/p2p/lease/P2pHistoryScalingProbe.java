package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixtures.Candidate;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixtures.Fixture;

/** Opt-in, isolated diagnostic; not a full scheduler benchmark or a timing test. */
public final class P2pHistoryScalingProbe {
    private static final long BUDGET_NANOS = 120_000_000_000L;
    private static final List<String> PHARMACIES = List.of("diagnostic-pharmacy");
    private static volatile Object sink;

    static final String SAMPLE_HEADER = "runIndex,kernel,historicalCount,totalAssignmentCount,pendingCount,"
            + "sampleIndex,operations,elapsedNanos,nanosPerOperation,allocatedBytes,bytesPerOperation,"
            + "gcCollections,gcMillis,checksum";
    static final String SUMMARY_HEADER = "runIndex,kernel,historicalCount,totalAssignmentCount,pendingCount,"
            + "sampleCount,medianNanosPerOperation,minNanosPerOperation,maxNanosPerOperation,medianBytesPerOperation";

    private P2pHistoryScalingProbe() {
    }

    public static void main(String[] args) throws IOException {
        long enteredAt = System.nanoTime();
        Options options = parseArgs(args);
        run(options.config(), options.outputDirectory(), enteredAt);
        System.out.println("P2P history scaling reports: " + options.outputDirectory().toAbsolutePath());
    }

    static Options parseArgs(String[] args) {
        if (args == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--") || arg.indexOf('=') < 0) {
                throw new IllegalArgumentException("Use --mode=..., --run-index=... and --output-dir=...");
            }
            int separator = arg.indexOf('=');
            String key = arg.substring(2, separator);
            String value = arg.substring(separator + 1);
            if (!Set.of("mode", "run-index", "output-dir").contains(key)
                    || value.isBlank() || values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Unknown, blank or duplicate option: " + key);
            }
        }
        if (values.size() != 3) {
            throw new IllegalArgumentException("All three options are required");
        }
        Mode mode = switch (values.get("mode")) {
            case "matrix" -> Mode.MATRIX;
            case "smoke" -> Mode.SMOKE;
            default -> throw new IllegalArgumentException("Mode must be matrix or smoke");
        };
        String runIndex = values.get("run-index");
        if (!Set.of("1", "2", "3").contains(runIndex)) {
            throw new IllegalArgumentException("Run index must be 1, 2 or 3");
        }
        return new Options(new ProbeConfig(mode, Integer.parseInt(runIndex)), Path.of(values.get("output-dir")));
    }

    static void run(ProbeConfig config, Path outputDirectory) throws IOException {
        run(config, outputDirectory, System.nanoTime());
    }

    private static void run(ProbeConfig config, Path outputDirectory, long enteredAt) throws IOException {
        if (config == null || outputDirectory == null || outputDirectory.toString().isBlank()) {
            throw new IllegalArgumentException("Config and nonblank output directory are required");
        }
        Instant startedAt = Instant.now();
        Path directory = outputDirectory.toAbsolutePath().normalize();
        if (Files.exists(directory)) {
            throw new IllegalArgumentException("Output directory already exists: " + directory);
        }
        Files.createDirectories(directory.getParent());
        // Atomic reservation: a concurrent creator must not turn this into overwrite.
        Files.createDirectory(directory);
        Settings settings = settings(config);
        List<Sample> samples = new ArrayList<>();
        Counters counters = null;
        RuntimeException failure = null;
        String status = "COMPLETE";
        try {
            checkBudget(enteredAt);
            counters = Counters.discover();
            Map<FixtureKey, Fixture> fixtures = new LinkedHashMap<>();
            for (int history : settings.historySizes()) {
                for (int pending : settings.pendingSizes()) {
                    checkBudget(enteredAt);
                    Fixture fixture = P2pHistoryScalingFixtures.create(history, pending);
                    validateFixture(fixture);
                    fixtures.put(new FixtureKey(history, pending), fixture);
                }
            }
            List<BenchmarkCase> cases = cases(config);
            for (int round = 0; round < settings.warmupRounds() + settings.measuredRounds(); round++) {
                for (BenchmarkCase benchmarkCase : orderedCases(cases, round, config.runIndex())) {
                    checkBudget(enteredAt);
                    int fixturePending = benchmarkCase.kernel() == Kernel.CATALOG_REBUILD
                            ? settings.pendingSizes().getFirst() : benchmarkCase.pendingCount();
                    Fixture fixture = fixtures.get(new FixtureKey(benchmarkCase.historicalCount(), fixturePending));
                    Sample sample = measure(config, benchmarkCase, fixture, settings,
                            round - settings.warmupRounds(), enteredAt, counters);
                    if (sample.sampleIndex() >= 0) {
                        samples.add(sample);
                    }
                    checkBudget(enteredAt);
                }
            }
        } catch (RuntimeException exception) {
            failure = exception;
            status = exception instanceof BudgetExpiredException ? "BUDGET_EXCEEDED" : "FAILED";
        }
        Instant endedAt = Instant.now();
        writeReports(directory, config, settings, samples, counters, startedAt, endedAt, status, failure);
        if (failure != null) {
            throw failure;
        }
    }

    private static void validateFixture(Fixture fixture) {
        if (fixture.lines().size() != 5 || fixture.indexedAssignments().size() != fixture.historicalCount() + 40) {
            throw new IllegalStateException("Invalid fixture cardinality");
        }
        for (P2pLineLeaseSnapshot line : fixture.lines()) {
            for (P2pPhysicalToteAssignment assignment : line.physicalAssignments()) {
                if (fixture.indexedAssignments().get(assignment.physicalToteId()) != assignment) {
                    throw new IllegalStateException("Fixture index does not preserve exact assignments");
                }
            }
        }
        for (Candidate candidate : fixture.candidates()) {
            P2pLineAllocationRequest request = fixture.requestFactory().create(candidate.source(),
                    candidate.toteId(), candidate.sheetKey(), "125", PHARMACIES, true);
            if (request.physicalToteId() != candidate.toteId() || request.lineCatalog() != fixture.catalog()
                    || fixture.indexedAssignments().containsKey(candidate.toteId())
                    || !request.requiredBagCorrelations().isEmpty()) {
                throw new IllegalStateException("Invalid pending request fixture");
            }
        }
    }

    private static Sample measure(ProbeConfig config, BenchmarkCase benchmarkCase, Fixture fixture,
            Settings settings, int sampleIndex, long enteredAt, Counters counters) {
        long operations = operations(benchmarkCase, settings);
        GcReading gcBefore = counters.gc();
        long allocationBefore = counters.allocatedBytes();
        long start = System.nanoTime();
        long checksum = executeKernel(benchmarkCase, fixture, settings, enteredAt);
        long end = System.nanoTime();
        long allocationAfter = counters.allocatedBytes();
        GcReading gcAfter = counters.gc();
        long expectedChecksum = switch (benchmarkCase.kernel()) {
            case REQUEST_FACTORY -> operations;
            case CATALOG_REBUILD -> operations * 5;
            case LOOKUP_LINEAR, LOOKUP_INDEX_CONTROL -> 0;
        };
        if (checksum != expectedChecksum || end - start < 0) {
            throw new IllegalStateException("Kernel checksum or elapsed time is invalid");
        }
        return new Sample(config.runIndex(), benchmarkCase, sampleIndex, operations, end - start,
                counterDelta(allocationBefore, allocationAfter),
                counterDelta(gcBefore.collections(), gcAfter.collections()),
                counterDelta(gcBefore.millis(), gcAfter.millis()), checksum);
    }

    static long executeKernel(BenchmarkCase benchmarkCase, Fixture fixture, Settings settings, long enteredAt) {
        long checksum = 0;
        switch (benchmarkCase.kernel()) {
            case REQUEST_FACTORY -> {
                for (int pass = 0; pass < settings.candidatePasses(); pass++) {
                    checkBudget(enteredAt);
                    for (Candidate candidate : fixture.candidates()) {
                        sink = fixture.requestFactory().create(candidate.source(), candidate.toteId(),
                                candidate.sheetKey(), "125", PHARMACIES, true);
                        checksum++;
                    }
                }
            }
            case LOOKUP_LINEAR -> {
                for (int pass = 0; pass < settings.candidatePasses(); pass++) {
                    checkBudget(enteredAt);
                    for (Candidate candidate : fixture.candidates()) {
                        Optional<P2pPhysicalToteAssignment> result = fixture.catalog().findAssignment(candidate.toteId());
                        sink = result;
                        checksum += result.isPresent() ? 1 : 0;
                    }
                }
            }
            case LOOKUP_INDEX_CONTROL -> {
                for (int pass = 0; pass < settings.candidatePasses(); pass++) {
                    checkBudget(enteredAt);
                    for (Candidate candidate : fixture.candidates()) {
                        Optional<P2pPhysicalToteAssignment> result = Optional.ofNullable(
                                fixture.indexedAssignments().get(candidate.toteId()));
                        sink = result;
                        checksum += result.isPresent() ? 1 : 0;
                    }
                }
            }
            case CATALOG_REBUILD -> {
                for (int iteration = 0; iteration < settings.catalogConstructions(); iteration++) {
                    checkBudget(enteredAt);
                    P2pLineLeaseCatalogSnapshot result = new P2pLineLeaseCatalogSnapshot(fixture.lines());
                    sink = result;
                    checksum += result.lines().size();
                }
            }
        }
        return checksum;
    }

    static Object lastResult() {
        return sink;
    }

    static long operations(BenchmarkCase benchmarkCase, Settings settings) {
        return benchmarkCase.kernel() == Kernel.CATALOG_REBUILD ? settings.catalogConstructions()
                : (long) settings.candidatePasses() * benchmarkCase.pendingCount();
    }

    static Settings settings(ProbeConfig config) {
        return config.mode() == Mode.MATRIX
                ? new Settings(List.of(0, 500, 2000, 5000), List.of(100, 400, 800), 5, 7, 20, 200)
                : new Settings(List.of(0, 10), List.of(2), 1, 3, 1, 3);
    }

    static List<BenchmarkCase> cases(ProbeConfig config) {
        Settings settings = settings(config);
        List<BenchmarkCase> result = new ArrayList<>();
        for (Kernel kernel : Kernel.values()) {
            for (int history : settings.historySizes()) {
                for (int pending : kernel == Kernel.CATALOG_REBUILD ? List.of(0) : settings.pendingSizes()) {
                    result.add(new BenchmarkCase(kernel, history, pending));
                }
            }
        }
        return List.copyOf(result);
    }

    static List<BenchmarkCase> orderedCases(List<BenchmarkCase> cases, int roundIndex, int runIndex) {
        List<BenchmarkCase> ordered = new ArrayList<>(cases);
        Collections.rotate(ordered, -((roundIndex * 7 + runIndex * 11) % ordered.size()));
        if (roundIndex % 2 != 0) {
            Collections.reverse(ordered);
        }
        return List.copyOf(ordered);
    }

    static boolean budgetExceeded(long elapsedNanos) {
        return elapsedNanos >= BUDGET_NANOS;
    }

    private static void checkBudget(long enteredAt) {
        if (budgetExceeded(System.nanoTime() - enteredAt)) {
            throw new BudgetExpiredException();
        }
    }

    static Long counterDelta(long before, long after) {
        return before < 0 || after < before ? null : after - before;
    }

    static long aggregateCounters(long... values) {
        if (values.length == 0) {
            return -1;
        }
        long sum = 0;
        for (long value : values) {
            if (value < 0 || value > Long.MAX_VALUE - sum) {
                return -1;
            }
            sum += value;
        }
        return sum;
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Median requires observations");
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    static List<Summary> summarize(ProbeConfig config, List<Sample> samples) {
        Map<BenchmarkCase, List<Sample>> grouped = new LinkedHashMap<>();
        List<BenchmarkCase> cases = cases(config);
        int expectedCount = settings(config).measuredRounds();
        for (Sample sample : samples) {
            if (sample.sampleIndex() < 0) {
                continue;
            }
            if (sample.runIndex() != config.runIndex() || !cases.contains(sample.benchmarkCase())
                    || sample.sampleIndex() >= expectedCount) {
                throw new IllegalArgumentException("Sample does not belong to this experiment");
            }
            grouped.computeIfAbsent(sample.benchmarkCase(), ignored -> new ArrayList<>()).add(sample);
        }
        List<Summary> summaries = new ArrayList<>();
        for (BenchmarkCase benchmarkCase : cases) {
            List<Sample> observations = grouped.getOrDefault(benchmarkCase, List.of());
            Set<Integer> indexes = new HashSet<>();
            for (Sample observation : observations) {
                if (!indexes.add(observation.sampleIndex())) {
                    throw new IllegalArgumentException("Duplicate measured sample index");
                }
            }
            if (observations.size() != expectedCount) {
                continue;
            }
            List<Double> times = observations.stream().map(Sample::nanosPerOperation).toList();
            Double bytes = observations.stream().anyMatch(sample -> sample.allocatedBytes() == null) ? null
                    : median(observations.stream().map(Sample::bytesPerOperation).toList());
            summaries.add(new Summary(config.runIndex(), benchmarkCase, expectedCount, median(times),
                    Collections.min(times), Collections.max(times), bytes));
        }
        return List.copyOf(summaries);
    }

    private static void writeReports(Path directory, ProbeConfig config, Settings settings, List<Sample> samples,
            Counters counters, Instant startedAt, Instant endedAt, String status, RuntimeException failure)
            throws IOException {
        StringBuilder raw = new StringBuilder(SAMPLE_HEADER).append('\n');
        for (Sample sample : samples) {
            raw.append(caseColumns(sample.runIndex(), sample.benchmarkCase())).append(',')
                    .append(sample.sampleIndex()).append(',').append(sample.operations()).append(',')
                    .append(sample.elapsedNanos()).append(',').append(number(sample.nanosPerOperation())).append(',')
                    .append(number(sample.allocatedBytes())).append(',').append(number(sample.bytesPerOperation()))
                    .append(',').append(number(sample.gcCollections())).append(',').append(number(sample.gcMillis()))
                    .append(',').append(sample.checksum()).append('\n');
        }
        StringBuilder summary = new StringBuilder(SUMMARY_HEADER).append('\n');
        for (Summary row : summarize(config, samples)) {
            summary.append(caseColumns(row.runIndex(), row.benchmarkCase())).append(',').append(row.sampleCount())
                    .append(',').append(number(row.medianNanosPerOperation()))
                    .append(',').append(number(row.minNanosPerOperation()))
                    .append(',').append(number(row.maxNanosPerOperation()))
                    .append(',').append(number(row.medianBytesPerOperation())).append('\n');
        }
        String metadata = "schemaVersion=1\nmode=" + config.mode() + "\nrunIndex=" + config.runIndex()
                + "\nstatus=" + status + "\nbudgetNanos=" + BUDGET_NANOS
                + "\nhistorySizes=" + settings.historySizes() + "\npendingSizes=" + settings.pendingSizes()
                + "\nwarmupRounds=" + settings.warmupRounds() + "\nmeasuredRounds=" + settings.measuredRounds()
                + "\ncandidatePasses=" + settings.candidatePasses()
                + "\ncatalogConstructions=" + settings.catalogConstructions()
                + "\nstartedAt=" + startedAt + "\nendedAt=" + endedAt
                + "\njvmName=" + System.getProperty("java.vm.name")
                + "\njavaVersion=" + System.getProperty("java.version")
                + "\njvmVersion=" + System.getProperty("java.vm.version")
                + "\njvmVendor=" + System.getProperty("java.vm.vendor")
                + "\njvmArguments=" + ManagementFactory.getRuntimeMXBean().getInputArguments()
                + "\nos=" + System.getProperty("os.name") + "\narchitecture=" + System.getProperty("os.arch")
                + "\nprocessorCount=" + Runtime.getRuntime().availableProcessors()
                + "\nmaximumHeapBytes=" + Runtime.getRuntime().maxMemory()
                + "\nallocationCounterAvailable=" + (counters != null && counters.allocationAvailable())
                + "\ngcCollectionCounterAvailable=" + (counters != null && counters.gcCollectionsAvailable())
                + "\ngcTimeCounterAvailable=" + (counters != null && counters.gcTimeAvailable())
                + "\nsourceRevision=user-supplied git rev-parse HEAD; not collected by probe"
                + "\ncaveats=Isolated request/lease boundaries, not a full scheduler or watermark-policy run. "
                + "Volatile sink overhead included, not subtracted. Index construction/memory excluded. "
                + "Constructor reuses prebuilt line snapshots. GC counters are process-wide. "
                + "JVM/JIT/GC/order bias remains possible. Smoke mode is correctness-only."
                + "\nfailure=" + (failure == null ? "none" : failure.getClass().getName()) + "\n";
        writeNew(directory.resolve("samples.csv"), raw.toString());
        writeNew(directory.resolve("summary.csv"), summary.toString());
        // Publish completion status last, only after both data artifacts are written.
        writeNew(directory.resolve("metadata.txt"), metadata);
    }

    private static String caseColumns(int runIndex, BenchmarkCase benchmarkCase) {
        return runIndex + "," + benchmarkCase.kernel() + "," + benchmarkCase.historicalCount()
                + "," + (benchmarkCase.historicalCount() + 40) + "," + benchmarkCase.pendingCount();
    }

    private static String number(Number value) {
        return value == null ? "NA" : value instanceof Double
                ? String.format(Locale.ROOT, "%.6f", value.doubleValue()) : value.toString();
    }

    private static void writeNew(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    enum Mode { MATRIX, SMOKE }

    enum Kernel { REQUEST_FACTORY, LOOKUP_LINEAR, LOOKUP_INDEX_CONTROL, CATALOG_REBUILD }

    record ProbeConfig(Mode mode, int runIndex) {
        ProbeConfig {
            if (mode == null || runIndex < 1 || runIndex > 3) {
                throw new IllegalArgumentException("Mode and run index 1-3 are required");
            }
        }
    }

    record Options(ProbeConfig config, Path outputDirectory) { }

    record Settings(List<Integer> historySizes, List<Integer> pendingSizes, int warmupRounds,
            int measuredRounds, int candidatePasses, int catalogConstructions) { }

    record BenchmarkCase(Kernel kernel, int historicalCount, int pendingCount) { }

    private record FixtureKey(int historicalCount, int pendingCount) { }

    record Sample(int runIndex, BenchmarkCase benchmarkCase, int sampleIndex, long operations, long elapsedNanos,
            Long allocatedBytes, Long gcCollections, Long gcMillis, long checksum) {
        double nanosPerOperation() {
            return (double) elapsedNanos / operations;
        }

        Double bytesPerOperation() {
            return allocatedBytes == null ? null : (double) allocatedBytes / operations;
        }
    }

    record Summary(int runIndex, BenchmarkCase benchmarkCase, int sampleCount, double medianNanosPerOperation,
            double minNanosPerOperation, double maxNanosPerOperation, Double medianBytesPerOperation) { }

    private record GcReading(long collections, long millis) { }

    private record Counters(com.sun.management.ThreadMXBean allocationBean,
            List<GarbageCollectorMXBean> collectors, boolean allocationAvailable,
            boolean gcCollectionsAvailable, boolean gcTimeAvailable) {
        static Counters discover() {
            com.sun.management.ThreadMXBean allocationBean = null;
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                    && bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled()) {
                allocationBean = bean;
            }
            List<GarbageCollectorMXBean> collectors = List.copyOf(ManagementFactory.getGarbageCollectorMXBeans());
            long count = aggregateCounters(collectors.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).toArray());
            long time = aggregateCounters(collectors.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).toArray());
            return new Counters(allocationBean, collectors,
                    allocationBean != null && allocationBean.getCurrentThreadAllocatedBytes() >= 0,
                    count >= 0, time >= 0);
        }

        long allocatedBytes() {
            return allocationAvailable ? allocationBean.getCurrentThreadAllocatedBytes() : -1;
        }

        GcReading gc() {
            return new GcReading(
                    aggregateCounters(collectors.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).toArray()),
                    aggregateCounters(collectors.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).toArray()));
        }
    }

    private static final class BudgetExpiredException extends IllegalStateException {
        private BudgetExpiredException() {
            super("P2P history scaling cooperative 120-second work budget expired");
        }
    }
}
