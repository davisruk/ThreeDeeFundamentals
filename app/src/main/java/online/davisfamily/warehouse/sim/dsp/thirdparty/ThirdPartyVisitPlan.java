package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

public record ThirdPartyVisitPlan(
        OrderSheetKey orderSheetKey,
        OrderType orderType,
        List<ThirdPartyLineWork> lineWork) {

    public ThirdPartyVisitPlan {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (lineWork == null || lineWork.isEmpty()) {
            throw new IllegalArgumentException("lineWork must not be empty");
        }
        if (lineWork.stream().anyMatch(work -> work == null)) {
            throw new IllegalArgumentException("lineWork must not contain null");
        }
        lineWork = List.copyOf(lineWork);
    }

    public int outstandingPackCount() {
        return lineWork.stream().mapToInt(ThirdPartyLineWork::outstandingQuantity).sum();
    }
}
