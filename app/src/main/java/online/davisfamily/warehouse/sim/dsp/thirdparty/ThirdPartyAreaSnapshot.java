package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.List;

public record ThirdPartyAreaSnapshot(
        ThirdPartyAreaConfig config,
        List<String> waitingOrderIds,
        List<String> waitingNotionalToteIds,
        List<ThirdPartyVisitState> activeVisits) {

    public ThirdPartyAreaSnapshot {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (waitingOrderIds == null) {
            throw new IllegalArgumentException("waitingOrderIds must not be null");
        }
        if (waitingNotionalToteIds == null) {
            throw new IllegalArgumentException("waitingNotionalToteIds must not be null");
        }
        if (activeVisits == null) {
            throw new IllegalArgumentException("activeVisits must not be null");
        }
        waitingOrderIds = List.copyOf(waitingOrderIds);
        waitingNotionalToteIds = List.copyOf(waitingNotionalToteIds);
        activeVisits = List.copyOf(activeVisits);
        if (waitingOrderIds.size() != waitingNotionalToteIds.size()) {
            throw new IllegalArgumentException("waiting order and tote ids must have equal sizes");
        }
    }

    public int waitingCount() {
        return waitingOrderIds.size();
    }

    public int activeCount() {
        return activeVisits.size();
    }

    public int admittedCount() {
        return waitingCount() + activeCount();
    }
}
