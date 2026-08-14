package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.OrderType;

public record ThirdPartyVisit(
        String orderId,
        String notionalToteId,
        OrderType orderType,
        List<ThirdPartyLineWork> lineWork) {

    public ThirdPartyVisit {
        orderId = requireValue(orderId, "orderId");
        notionalToteId = requireValue(notionalToteId, "notionalToteId");
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (lineWork == null || lineWork.isEmpty()) {
            throw new IllegalArgumentException("lineWork must not be empty");
        }
        lineWork = List.copyOf(lineWork);
    }

    public int outstandingPackCount() {
        return lineWork.stream().mapToInt(ThirdPartyLineWork::outstandingQuantity).sum();
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
