package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public final class AdaptingVisitFactory {

    public AdaptingVisitProfile profileFor(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (order.orderType() == OrderType.ADAPTED) {
            return AdaptingVisitProfile.store(
                    order.orderSheetKey(), order.serviceCentreId(), order.items());
        }
        if (order.orderType() == OrderType.FULL_PACK) {
            throw new IllegalArgumentException("FULL_PACK orders do not collect adapted lines");
        }
        if (order.orderType() != OrderType.ASSOCIATED && order.orderType() != OrderType.EMPTY) {
            throw new IllegalArgumentException("Only ASSOCIATED and EMPTY orders can collect adapted lines");
        }

        List<PreparedLineKey> requestedLineKeys = order.items().stream()
                .filter(line -> line.lineType() == DspOrderLineType.ADAPTED)
                .map(line -> PreparedLineKey.forDispatchLine(order, line))
                .toList();
        List<String> pharmacyIds = order.items().stream()
                .filter(line -> line.lineType() == DspOrderLineType.ADAPTED)
                .map(line -> line.pharmacyId())
                .toList();
        if (requestedLineKeys.isEmpty()) {
            throw new IllegalArgumentException("Collecting order must contain at least one ADAPTED line");
        }
        return AdaptingVisitProfile.collect(
                order.orderSheetKey(), order.serviceCentreId(), requestedLineKeys, pharmacyIds);
    }

    public AdaptingVisit create(PhysicalToteId physicalToteId, NotionalToteOrder order) {
        return new AdaptingVisit(physicalToteId, profileFor(order));
    }
}
