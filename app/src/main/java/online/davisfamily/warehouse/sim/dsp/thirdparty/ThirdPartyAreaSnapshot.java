package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record ThirdPartyAreaSnapshot(
        ThirdPartyAreaConfig config,
        List<OrderSheetKey> waitingOrderSheetKeys,
        List<PhysicalToteId> waitingPhysicalToteIds,
        List<ThirdPartyVisitState> activeVisits) {

    public ThirdPartyAreaSnapshot {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (waitingOrderSheetKeys == null) {
            throw new IllegalArgumentException("waitingOrderSheetKeys must not be null");
        }
        if (waitingPhysicalToteIds == null) {
            throw new IllegalArgumentException("waitingPhysicalToteIds must not be null");
        }
        if (activeVisits == null) {
            throw new IllegalArgumentException("activeVisits must not be null");
        }
        waitingOrderSheetKeys = List.copyOf(waitingOrderSheetKeys);
        waitingPhysicalToteIds = List.copyOf(waitingPhysicalToteIds);
        activeVisits = List.copyOf(activeVisits);
        if (waitingOrderSheetKeys.size() != waitingPhysicalToteIds.size()) {
            throw new IllegalArgumentException("waiting order sheets and physical tote ids must have equal sizes");
        }
    }

    public int waitingCount() {
        return waitingOrderSheetKeys.size();
    }

    public int activeCount() {
        return activeVisits.size();
    }

    public int admittedCount() {
        return waitingCount() + activeCount();
    }
}
