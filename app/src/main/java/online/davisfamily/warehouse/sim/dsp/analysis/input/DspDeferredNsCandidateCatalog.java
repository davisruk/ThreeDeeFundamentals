package online.davisfamily.warehouse.sim.dsp.analysis.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

/** Immutable input-line occurrences retained for future NS/Exceptions processing. */
public final class DspDeferredNsCandidateCatalog {
    private final List<UnresolvedProductLine> unresolvedProductLines;
    private final List<NotionalToteOrder> reportableOrders;
    private final Map<String, Integer> inputLineCountByServiceCentreId;

    public DspDeferredNsCandidateCatalog(DspFullDayLoadedInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        unresolvedProductLines = input.loadReport().unresolvedProductLines();
        reportableOrders = input.reportableOrders();
        Map<String, Integer> counts = new TreeMap<>();
        for (UnresolvedProductLine line : unresolvedProductLines) {
            counts.merge(line.serviceCentreId(), 1, Integer::sum);
        }
        inputLineCountByServiceCentreId = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    public List<UnresolvedProductLine> unresolvedProductLines() {
        return unresolvedProductLines;
    }

    public List<NotionalToteOrder> reportableOrders() {
        return reportableOrders;
    }

    public int inputLineCount() {
        return unresolvedProductLines.size();
    }

    public Map<String, Integer> inputLineCountByServiceCentreId() {
        return inputLineCountByServiceCentreId;
    }

    public int inputLineCountFor(String serviceCentreId) {
        return inputLineCountByServiceCentreId.getOrDefault(serviceCentreId, 0);
    }
}
