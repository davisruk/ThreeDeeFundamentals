package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record InboundToteManifest(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        OrderType orderType,
        String serviceCentreId,
        List<DspOrderItem> items,
        long sourceSequenceNumber) {

    public InboundToteManifest {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (orderType == OrderType.EMPTY) {
            throw new IllegalArgumentException("EMPTY orders do not have inbound tote manifests");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        if (sourceSequenceNumber < 0) {
            throw new IllegalArgumentException("sourceSequenceNumber must be >= 0");
        }

        Set<String> lineReferences = new LinkedHashSet<>();
        for (DspOrderItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items must not contain null");
            }
            if (!lineReferences.add(item.lineReference())) {
                throw new IllegalArgumentException("Duplicate lineReference in inbound tote manifest: "
                        + item.lineReference());
            }
        }
        items = List.copyOf(items);
        serviceCentreId = serviceCentreId.trim();
    }

    public InboundToteManifest withItems(List<DspOrderItem> retainedItems) {
        return new InboundToteManifest(
                physicalToteId,
                orderSheetKey,
                orderType,
                serviceCentreId,
                retainedItems,
                sourceSequenceNumber);
    }
}
