package online.davisfamily.warehouse.sim.dsp.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Package-private parser for the exact full-day command-line contract. */
final class DspFullDayAnalysisCommandParser {
    private static final String CONFIG = "config";
    private static final String PRODUCT_MASTER = "product-master";
    private static final String ORDERS = "orders";
    private static final String ORDERS_DIRECTORY = "orders-directory";
    private static final String OUTPUT = "output";
    private static final String INSPECTION_OUTPUT = "inspection-output";
    private static final String PROGRESS_LOG = "progress-log";
    private static final String PROGRESS_INTERVAL_SECONDS = "progress-interval-seconds";
    private static final String OPERATING_DATE = "operating-date";
    private static final String OSR_LOW_WATER_MARK = "osr-low-water-mark";
    private static final String INBOUND_INTERVAL_SECONDS = "inbound-interval-seconds";
    private static final String AV02_CAPACITY = "av02-capacity";
    private static final String OUTBOUND_BAG_CAPACITY = "outbound-bag-capacity";
    private static final String MAXIMUM_PACKS_PER_BAG = "maximum-packs-per-bag";
    private static final String FIXED_STEP_MILLIS = "fixed-step-millis";
    private static final String STEPS_PER_BATCH = "steps-per-batch";
    private static final String METRIC_SAMPLE_SECONDS = "metric-sample-seconds";
    private static final String OVERWRITE = "overwrite";

    DspFullDayAnalysisCommand parse(String[] arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("arguments must not be null");
        }

        Path configPath = findConfigPath(arguments);
        DspFullDayAnalysisConfigJson config = configPath == null
                ? null
                : new DspFullDayAnalysisConfigLoader().load(configPath);
        Path configBaseDirectory = configPath == null
                ? null
                : configPath.toAbsolutePath().normalize().getParent();

        Set<String> seenSingletons = new HashSet<>();
        List<Path> orderPaths = new ArrayList<>();
        Path orderDirectoryPath = null;
        Path productMasterPath = null;
        Path outputPath = null;
        Path inspectionOutputPath = null;
        Path progressLogPath = null;
        LocalDate operatingDate = null;
        int osrLowWaterMark = -1;
        Duration inboundInterval = null;
        int av02Capacity = -1;
        int outboundBagCapacity = -1;
        int maximumPacksPerBag = -1;
        Duration fixedStep = Duration.ofMillis(50);
        int stepsPerBatch = 2_000;
        Duration metricSampleInterval = Duration.ofSeconds(60);
        Duration progressInterval = Duration.ofSeconds(300);
        boolean overwrite = false;

        if (config != null) {
            productMasterPath = configuredPath(
                    config.productMaster(), configBaseDirectory, PRODUCT_MASTER);
            if (config.orders() != null) {
                for (String order : config.orders()) {
                    orderPaths.add(configuredPath(order, configBaseDirectory, ORDERS));
                }
            }
            orderDirectoryPath = configuredPath(
                    config.ordersDirectory(), configBaseDirectory, ORDERS_DIRECTORY);
            outputPath = configuredPath(config.output(), configBaseDirectory, OUTPUT);
            inspectionOutputPath = configuredPath(
                    config.inspectionOutput(), configBaseDirectory, INSPECTION_OUTPUT);
            progressLogPath = configuredPath(
                    config.progressLog(), configBaseDirectory, PROGRESS_LOG);
            if (config.operatingDate() != null) {
                operatingDate = parseDate(config.operatingDate());
            }
            if (config.osrLowWaterMark() != null) {
                osrLowWaterMark = parseNonnegativeInt(
                        Integer.toString(config.osrLowWaterMark()), OSR_LOW_WATER_MARK);
            }
            if (config.inboundIntervalSeconds() != null) {
                inboundInterval = parseSeconds(
                        config.inboundIntervalSeconds().toPlainString(), INBOUND_INTERVAL_SECONDS);
            }
            if (config.av02Capacity() != null) {
                av02Capacity = parsePositiveInt(
                        Integer.toString(config.av02Capacity()), AV02_CAPACITY);
            }
            if (config.outboundBagCapacity() != null) {
                outboundBagCapacity = parsePositiveInt(
                        Integer.toString(config.outboundBagCapacity()), OUTBOUND_BAG_CAPACITY);
            }
            if (config.maximumPacksPerBag() != null) {
                maximumPacksPerBag = parsePositiveInt(
                        Integer.toString(config.maximumPacksPerBag()), MAXIMUM_PACKS_PER_BAG);
            }
            if (config.fixedStepMillis() != null) {
                fixedStep = Duration.ofMillis(parsePositiveInt(
                        Integer.toString(config.fixedStepMillis()), FIXED_STEP_MILLIS));
            }
            if (config.stepsPerBatch() != null) {
                stepsPerBatch = parsePositiveInt(
                        Integer.toString(config.stepsPerBatch()), STEPS_PER_BATCH);
            }
            if (config.metricSampleSeconds() != null) {
                metricSampleInterval = Duration.ofSeconds(parsePositiveInt(
                        Integer.toString(config.metricSampleSeconds()), METRIC_SAMPLE_SECONDS));
            }
            if (config.progressIntervalSeconds() != null) {
                progressInterval = Duration.ofSeconds(parsePositiveInt(
                        Integer.toString(config.progressIntervalSeconds()),
                        PROGRESS_INTERVAL_SECONDS));
            }
            if (config.overwrite() != null) {
                overwrite = config.overwrite();
            }
        }

        boolean commandLineOrderModeSeen = false;
        for (String argument : arguments) {
            if (argument == null || argument.isBlank()) {
                throw new IllegalArgumentException("arguments must not contain blank values");
            }
            if (argument.equals("--overwrite")) {
                if (!seenSingletons.add(OVERWRITE)) {
                    throw new IllegalArgumentException("duplicate option: --overwrite");
                }
                overwrite = true;
                continue;
            }
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("malformed option: " + argument);
            }
            int equals = argument.indexOf('=');
            if (equals <= 2 || equals == argument.length() - 1) {
                throw new IllegalArgumentException("malformed option: " + argument);
            }
            String name = argument.substring(2, equals);
            String value = argument.substring(equals + 1);
            switch (name) {
                case CONFIG -> ensureSingleton(seenSingletons, name);
                case PRODUCT_MASTER -> {
                    ensureSingleton(seenSingletons, name);
                    productMasterPath = parsePath(value, name);
                }
                case ORDERS -> {
                    if (!commandLineOrderModeSeen) {
                        orderPaths.clear();
                        orderDirectoryPath = null;
                        commandLineOrderModeSeen = true;
                    }
                    orderPaths.add(parsePath(value, name));
                }
                case ORDERS_DIRECTORY -> {
                    ensureSingleton(seenSingletons, name);
                    if (!commandLineOrderModeSeen) {
                        orderPaths.clear();
                        orderDirectoryPath = null;
                        commandLineOrderModeSeen = true;
                    }
                    orderDirectoryPath = parsePath(value, name);
                }
                case OUTPUT -> {
                    ensureSingleton(seenSingletons, name);
                    outputPath = parsePath(value, name);
                }
                case INSPECTION_OUTPUT -> {
                    ensureSingleton(seenSingletons, name);
                    inspectionOutputPath = parsePath(value, name);
                }
                case PROGRESS_LOG -> {
                    ensureSingleton(seenSingletons, name);
                    progressLogPath = parsePath(value, name);
                }
                case OPERATING_DATE -> {
                    ensureSingleton(seenSingletons, name);
                    operatingDate = parseDate(value);
                }
                case OSR_LOW_WATER_MARK -> {
                    ensureSingleton(seenSingletons, name);
                    osrLowWaterMark = parseNonnegativeInt(value, name);
                }
                case INBOUND_INTERVAL_SECONDS -> {
                    ensureSingleton(seenSingletons, name);
                    inboundInterval = parseSeconds(value, name);
                }
                case AV02_CAPACITY -> {
                    ensureSingleton(seenSingletons, name);
                    av02Capacity = parsePositiveInt(value, name);
                }
                case OUTBOUND_BAG_CAPACITY -> {
                    ensureSingleton(seenSingletons, name);
                    outboundBagCapacity = parsePositiveInt(value, name);
                }
                case MAXIMUM_PACKS_PER_BAG -> {
                    ensureSingleton(seenSingletons, name);
                    maximumPacksPerBag = parsePositiveInt(value, name);
                }
                case FIXED_STEP_MILLIS -> {
                    ensureSingleton(seenSingletons, name);
                    fixedStep = Duration.ofMillis(parsePositiveInt(value, name));
                }
                case STEPS_PER_BATCH -> {
                    ensureSingleton(seenSingletons, name);
                    stepsPerBatch = parsePositiveInt(value, name);
                }
                case METRIC_SAMPLE_SECONDS -> {
                    ensureSingleton(seenSingletons, name);
                    metricSampleInterval = Duration.ofSeconds(parsePositiveInt(value, name));
                }
                case PROGRESS_INTERVAL_SECONDS -> {
                    ensureSingleton(seenSingletons, name);
                    progressInterval = Duration.ofSeconds(parsePositiveInt(value, name));
                }
                default -> throw new IllegalArgumentException("unknown option: --" + name);
            }
        }

        require(productMasterPath != null, "missing required option: --product-master=<csv path>");
        require(orderPaths.isEmpty() != (orderDirectoryPath == null),
                "exactly one order input mode is required: --orders=<json path> or "
                        + "--orders-directory=<directory path>");
        require(outputPath != null, "missing required option: --output=<json path>");
        require(operatingDate != null, "missing required option: --operating-date=<YYYY-MM-DD>");
        require(osrLowWaterMark >= 0, "missing required option: --osr-low-water-mark=<count>");
        require(inboundInterval != null, "missing required option: --inbound-interval-seconds=<positive decimal>");
        require(av02Capacity >= 1, "missing required option: --av02-capacity=<count>");
        require(outboundBagCapacity >= 1, "missing required option: --outbound-bag-capacity=<count>");
        require(maximumPacksPerBag >= 1, "missing required option: --maximum-packs-per-bag=<count>");

        validateRegularFile(productMasterPath, PRODUCT_MASTER);
        if (orderDirectoryPath != null) {
            orderPaths = expandOrderDirectory(orderDirectoryPath);
        }
        for (Path orderPath : orderPaths) {
            validateRegularFile(orderPath, ORDERS);
        }
        validateOutputPath(outputPath, OUTPUT);
        if (inspectionOutputPath != null) {
            validateOutputPath(inspectionOutputPath, INSPECTION_OUTPUT);
        }
        if (progressLogPath != null) {
            validateOutputPath(progressLogPath, PROGRESS_LOG);
        }
        validateDistinctOutputPaths(outputPath, inspectionOutputPath, progressLogPath);

        return new DspFullDayAnalysisCommand(
                productMasterPath,
                orderPaths,
                outputPath,
                Optional.ofNullable(inspectionOutputPath),
                operatingDate,
                osrLowWaterMark,
                inboundInterval,
                av02Capacity,
                outboundBagCapacity,
                maximumPacksPerBag,
                fixedStep,
                stepsPerBatch,
                metricSampleInterval,
                overwrite,
                Optional.ofNullable(progressLogPath),
                progressInterval);
    }

    private static Path findConfigPath(String[] arguments) {
        Path configPath = null;
        for (String argument : arguments) {
            if (argument == null
                    || (!argument.equals("--config") && !argument.startsWith("--config="))) {
                continue;
            }
            if (!argument.startsWith("--config=") || argument.length() == "--config=".length()) {
                throw new IllegalArgumentException("malformed option: " + argument);
            }
            if (configPath != null) {
                throw new IllegalArgumentException("duplicate option: --config");
            }
            configPath = parsePath(argument.substring("--config=".length()), CONFIG);
            validateRegularFile(configPath, CONFIG);
        }
        return configPath;
    }

    private static Path configuredPath(String value, Path configBaseDirectory, String name) {
        if (value == null) {
            return null;
        }
        Path parsed = parsePath(value, name);
        if (parsed.isAbsolute()) {
            return parsed.normalize();
        }
        return configBaseDirectory.resolve(parsed).normalize();
    }

    private static List<Path> expandOrderDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "--" + ORDERS_DIRECTORY + " must be an existing directory: " + path);
        }

        try (Stream<Path> entries = Files.list(path)) {
            List<Path> orderPaths = entries
                    .filter(entry -> Files.isRegularFile(entry)
                            && entry.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(
                            (Path entry) -> entry.getFileName().toString()))
                    .toList();
            if (orderPaths.isEmpty()) {
                throw new IllegalArgumentException(
                        "--" + ORDERS_DIRECTORY
                                + " must contain at least one direct regular .json file: " + path);
            }
            return orderPaths;
        } catch (IOException | UncheckedIOException exception) {
            Throwable cause = exception instanceof UncheckedIOException
                    ? exception.getCause()
                    : exception;
            throw new IllegalArgumentException(
                    "could not enumerate --" + ORDERS_DIRECTORY + ": " + path, cause);
        }
    }

    private static void ensureSingleton(Set<String> seen, String name) {
        if (!seen.add(name)) {
            throw new IllegalArgumentException("duplicate option: --" + name);
        }
    }

    private static Path parsePath(String value, String name) {
        try {
            Path path = Path.of(value);
            if (path.toString().isBlank()) {
                throw new IllegalArgumentException("--" + name + " must not be blank");
            }
            return path;
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("invalid path for --" + name, exception);
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid --operating-date: " + value, exception);
        }
    }

    private static int parsePositiveInt(String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("--" + name + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid integer for --" + name + ": " + value, exception);
        }
    }

    private static int parseNonnegativeInt(String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("--" + name + " must be nonnegative");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid integer for --" + name + ": " + value, exception);
        }
    }

    private static Duration parseSeconds(String value, String name) {
        try {
            BigDecimal seconds = new BigDecimal(value);
            if (seconds.signum() <= 0) {
                throw new IllegalArgumentException("--" + name + " must be positive");
            }
            long nanos = seconds.movePointRight(9)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
            if (nanos < 1) {
                throw new IllegalArgumentException("--" + name + " is below one nanosecond");
            }
            return Duration.ofNanos(nanos);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("invalid positive decimal for --" + name + ": " + value, exception);
        }
    }

    private static void validateRegularFile(Path path, String name) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "--" + name + " must be an existing regular file: " + path);
        }
    }

    private static void validateOutputPath(Path path, String name) {
        if (Files.exists(path) && !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "--" + name + " must not name a directory or non-file: " + path);
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && Files.exists(parent) && !Files.isDirectory(parent)) {
            throw new IllegalArgumentException(
                    "parent of --" + name + " is not a directory: " + parent);
        }
    }

    private static void validateDistinctOutputPaths(
            Path outputPath,
            Path inspectionOutputPath,
            Path progressLogPath) {
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (inspectionOutputPath != null
                && normalizedOutput.equals(inspectionOutputPath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "--output and --inspection-output must name different files");
        }
        if (progressLogPath != null
                && normalizedOutput.equals(progressLogPath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "--output and --progress-log must name different files");
        }
        if (inspectionOutputPath != null && progressLogPath != null
                && inspectionOutputPath.toAbsolutePath().normalize()
                        .equals(progressLogPath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "--inspection-output and --progress-log must name different files");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
