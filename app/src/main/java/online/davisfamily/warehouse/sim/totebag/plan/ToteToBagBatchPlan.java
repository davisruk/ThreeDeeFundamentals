package online.davisfamily.warehouse.sim.totebag.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToteToBagBatchPlan {
    private final Map<String, Integer> expectedPackCountsByCorrelationId;

    public ToteToBagBatchPlan(Map<String, Integer> expectedPackCountsByCorrelationId) {
        if (expectedPackCountsByCorrelationId == null || expectedPackCountsByCorrelationId.isEmpty()) {
            throw new IllegalArgumentException("expectedPackCountsByCorrelationId must not be empty");
        }
        this.expectedPackCountsByCorrelationId = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : expectedPackCountsByCorrelationId.entrySet()) {
            String correlationId = entry.getKey();
            Integer expectedPackCount = entry.getValue();
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlationId must not be blank");
            }
            if (expectedPackCount == null || expectedPackCount <= 0) {
                throw new IllegalArgumentException("expectedPackCount must be > 0");
            }
            this.expectedPackCountsByCorrelationId.put(correlationId, expectedPackCount);
        }
    }

    public static ToteToBagBatchPlan fromToteLoadPlan(ToteLoadPlan toteLoadPlan) {
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        Map<String, Integer> expectedPackCountsByCorrelationId = new LinkedHashMap<>();
        for (String correlationId : toteLoadPlan.orderedCorrelationIds()) {
            expectedPackCountsByCorrelationId.put(correlationId, toteLoadPlan.packCountForCorrelationId(correlationId));
        }
        return new ToteToBagBatchPlan(expectedPackCountsByCorrelationId);
    }

    public static ToteToBagBatchPlan fromToteLoadPlans(List<ToteLoadPlan> toteLoadPlans) {
        if (toteLoadPlans == null || toteLoadPlans.isEmpty()) {
            throw new IllegalArgumentException("toteLoadPlans must not be empty");
        }
        Map<String, Integer> expectedPackCountsByCorrelationId = new LinkedHashMap<>();
        for (ToteLoadPlan toteLoadPlan : toteLoadPlans) {
            if (toteLoadPlan == null) {
                throw new IllegalArgumentException("toteLoadPlans must not contain null");
            }
            for (String correlationId : toteLoadPlan.orderedCorrelationIds()) {
                int totePackCount = toteLoadPlan.packCountForCorrelationId(correlationId);
                expectedPackCountsByCorrelationId.merge(correlationId, totePackCount, Integer::sum);
            }
        }
        return new ToteToBagBatchPlan(expectedPackCountsByCorrelationId);
    }

    public int expectedPackCountFor(String correlationId) {
        return expectedPackCountsByCorrelationId.getOrDefault(correlationId, 0);
    }

    public List<String> orderedCorrelationIds() {
        return new ArrayList<>(expectedPackCountsByCorrelationId.keySet());
    }

    public Map<String, Integer> expectedPackCountsByCorrelationId() {
        return Map.copyOf(expectedPackCountsByCorrelationId);
    }
}
