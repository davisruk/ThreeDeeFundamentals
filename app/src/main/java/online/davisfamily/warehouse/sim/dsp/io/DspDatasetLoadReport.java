package online.davisfamily.warehouse.sim.dsp.io;

import java.util.List;

public record DspDatasetLoadReport(
        int ignoredManualMessageCount,
        int ignoredManualLineCount,
        int omittedOrderCount,
        List<UnresolvedProductLine> unresolvedProductLines,
        List<InboundToteIdSubstitution> inboundToteIdSubstitutions) {

    public DspDatasetLoadReport(
            int ignoredManualMessageCount,
            int ignoredManualLineCount,
            int omittedOrderCount,
            List<UnresolvedProductLine> unresolvedProductLines) {
        this(
                ignoredManualMessageCount,
                ignoredManualLineCount,
                omittedOrderCount,
                unresolvedProductLines,
                List.of());
    }

    public DspDatasetLoadReport {
        if (ignoredManualMessageCount < 0) {
            throw new IllegalArgumentException("ignoredManualMessageCount must be >= 0");
        }
        if (ignoredManualLineCount < 0) {
            throw new IllegalArgumentException("ignoredManualLineCount must be >= 0");
        }
        if (omittedOrderCount < 0) {
            throw new IllegalArgumentException("omittedOrderCount must be >= 0");
        }
        if (unresolvedProductLines == null) {
            throw new IllegalArgumentException("unresolvedProductLines must not be null");
        }
        if (unresolvedProductLines.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("unresolvedProductLines must not contain null");
        }
        if (inboundToteIdSubstitutions == null) {
            throw new IllegalArgumentException("inboundToteIdSubstitutions must not be null");
        }
        if (inboundToteIdSubstitutions.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(
                    "inboundToteIdSubstitutions must not contain null");
        }
        unresolvedProductLines = List.copyOf(unresolvedProductLines);
        inboundToteIdSubstitutions = List.copyOf(inboundToteIdSubstitutions);
    }

    public static DspDatasetLoadReport empty() {
        return new DspDatasetLoadReport(0, 0, 0, List.of(), List.of());
    }
}
