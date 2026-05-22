package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class AdaptingCollectVisitFactory {

    public AdaptingVisit create(String toteId, NotionalToteOrder order) {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be blank");
        }
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
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

        return AdaptingVisit.collect(toteId.trim(), requestedLineKeys, pharmacyIds);
    }
}
