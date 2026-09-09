package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.analysis.DspCompletionMilestone;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayTerminationReason;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayOccupancySample;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspP2pLineMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;

/**
 * Immutable, renderer-independent report for one full-day DSP analysis.
 *
 * <p>The configuration map is deliberately restricted to JSON-safe values.  It therefore cannot
 * retain a profile, loader, scheduler, or any other live domain owner.</p>
 */
public record DspFullDayAnalysisReport(
        String profileId,
        String calibrationStatus,
        DspCompletionMilestone completionMilestone,
        DspFullDayTerminationReason terminationReason,
        DspFullDayRuntimeState state,
        DspFullDayMetricsSnapshot metrics,
        DspFullDayAnalysisRuntimeSnapshot runtimeSnapshot,
        Map<String, Object> configuration,
        DspDatasetLoadReport loadReport,
        List<DspServiceCentreAnalysisResult> serviceCentres,
        List<DspP2pLineMetricsSnapshot> p2pLines,
        List<DspFullDayOccupancySample> occupancySamples,
        List<DspFullDayMetricsSnapshot.ElasticInfeasibilityEvent> elasticInfeasibilityHistory,
        List<String> warnings,
        List<String> unsupportedWork,
        List<String> unfinishedIdentities) {

    public DspFullDayAnalysisReport {
        profileId = requireValue(profileId, "profileId");
        calibrationStatus = requireValue(calibrationStatus, "calibrationStatus");
        if (completionMilestone == null || terminationReason == null || state == null
                || metrics == null || runtimeSnapshot == null || loadReport == null) {
            throw new IllegalArgumentException("report values must not be null");
        }
        if (state != metrics.state() || state != runtimeSnapshot.state()) {
            throw new IllegalArgumentException("report state must match metrics and runtime state");
        }
        if (!profileId.equals(metrics.profileId())
                || !calibrationStatus.equals(metrics.calibrationStatus())) {
            throw new IllegalArgumentException("report identity must match metrics identity");
        }
        if (!completionMilestone.name().equals(metrics.completionMilestone())) {
            throw new IllegalArgumentException("report milestone must match metrics milestone");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        configuration = immutableJsonObject(configuration, "configuration");
        serviceCentres = copyValues(serviceCentres, "serviceCentres");
        p2pLines = copyValues(p2pLines, "p2pLines");
        occupancySamples = copyValues(occupancySamples, "occupancySamples");
        elasticInfeasibilityHistory = copyValues(
                elasticInfeasibilityHistory, "elasticInfeasibilityHistory");
        warnings = copyStrings(warnings, "warnings");
        unsupportedWork = copyStrings(unsupportedWork, "unsupportedWork");
        unfinishedIdentities = copyStrings(unfinishedIdentities, "unfinishedIdentities");
    }

    /** Compatibility alias for callers that call the terminal reason the termination. */
    public DspFullDayTerminationReason termination() {
        return terminationReason;
    }

    public List<DspServiceCentreAnalysisResult> results() {
        return serviceCentres;
    }

    public List<DspFullDayOccupancySample> occupancyHistory() {
        return occupancySamples;
    }

    public List<String> unsupported() {
        return unsupportedWork;
    }

    private static <T> List<T> copyValues(List<T> values, String fieldName) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not be null or contain null");
        }
        return List.copyOf(values);
    }

    private static List<String> copyStrings(List<String> values, String fieldName) {
        if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(fieldName + " must not contain blank values");
        }
        return values.stream().map(String::trim).toList();
    }

    private static Map<String, Object> immutableJsonObject(
            Map<String, Object> source,
            String fieldName) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException(fieldName + " contains a blank key");
            }
            copy.put(entry.getKey().trim(), immutableJsonValue(entry.getValue(), fieldName));
        }
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object immutableJsonValue(Object value, String fieldName) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) {
            if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                    || value instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw new IllegalArgumentException(fieldName + " contains a non-finite number");
            }
            return value;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(immutableJsonValue(item, fieldName));
            }
            return List.copyOf(copy);
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw new IllegalArgumentException(fieldName + " contains a non-string key");
                }
                copy.put(key.trim(), immutableJsonValue(entry.getValue(), fieldName));
            }
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException(
                fieldName + " contains a value that is not JSON-safe: " + value.getClass());
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
