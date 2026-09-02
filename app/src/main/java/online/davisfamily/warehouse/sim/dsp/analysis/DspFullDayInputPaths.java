package online.davisfamily.warehouse.sim.dsp.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable paths for one product master and an ordered set of 12N messages. */
public record DspFullDayInputPaths(
        Path productMasterCsvPath,
        List<Path> twelveNJsonPaths) {

    public DspFullDayInputPaths {
        if (productMasterCsvPath == null) {
            throw new IllegalArgumentException("productMasterCsvPath must not be null");
        }
        if (twelveNJsonPaths == null || twelveNJsonPaths.isEmpty()) {
            throw new IllegalArgumentException("twelveNJsonPaths must not be null or empty");
        }

        List<Path> orderedPaths = new ArrayList<>();
        Set<Path> distinctPaths = new HashSet<>();
        Path productPathKey = normalizedKey(productMasterCsvPath, "productMasterCsvPath");
        if (!distinctPaths.add(productPathKey)) {
            throw new IllegalArgumentException("input paths must be distinct");
        }
        for (Path path : twelveNJsonPaths) {
            if (path == null) {
                throw new IllegalArgumentException("twelveNJsonPaths must not contain null");
            }
            if (!distinctPaths.add(normalizedKey(path, "twelveNJsonPath"))) {
                throw new IllegalArgumentException("input paths must be distinct: " + path);
            }
            orderedPaths.add(path);
        }
        twelveNJsonPaths = List.copyOf(orderedPaths);
    }

    /** Alias used by callers that do not include the file format in the name. */
    public Path productMasterPath() {
        return productMasterCsvPath;
    }

    /** Alias for the ordered 12N message paths. */
    public List<Path> orderJsonPaths() {
        return twelveNJsonPaths;
    }

    /** Returns all input paths in validation/load order. */
    public List<Path> allPaths() {
        List<Path> paths = new ArrayList<>(1 + twelveNJsonPaths.size());
        paths.add(productMasterCsvPath);
        paths.addAll(twelveNJsonPaths);
        return List.copyOf(paths);
    }

    private static Path normalizedKey(Path path, String fieldName) {
        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(fieldName + " is not a usable path", exception);
        }
    }
}
