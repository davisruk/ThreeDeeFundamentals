package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record ThirdPartyVisit(
        PhysicalToteId physicalToteId,
        ThirdPartyVisitPlan plan) {

    public ThirdPartyVisit {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
    }

    public OrderSheetKey orderSheetKey() {
        return plan.orderSheetKey();
    }

    public OrderType orderType() {
        return plan.orderType();
    }

    public String serviceCentreId() {
        return plan.serviceCentreId();
    }

    public List<ThirdPartyLineWork> lineWork() {
        return plan.lineWork();
    }

    public int outstandingPackCount() {
        return plan.outstandingPackCount();
    }
}
