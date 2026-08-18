package online.davisfamily.warehouse.sim.dsp.io;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

public record MappedTwelveNOrder(
        NotionalToteOrder order,
        Optional<InboundToteManifest> inboundToteManifest) {

    public MappedTwelveNOrder {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (inboundToteManifest == null) {
            throw new IllegalArgumentException("inboundToteManifest must not be null");
        }
        if (order.orderType() == OrderType.EMPTY) {
            if (inboundToteManifest.isPresent()) {
                throw new IllegalArgumentException("EMPTY orders must not have an inbound tote manifest");
            }
        } else {
            InboundToteManifest manifest = inboundToteManifest.orElseThrow(
                    () -> new IllegalArgumentException(
                            "Physical inbound orders require an inbound tote manifest"));
            if (!manifest.orderSheetKey().equals(order.orderSheetKey())) {
                throw new IllegalArgumentException("Manifest orderSheetKey must match order");
            }
            if (manifest.orderType() != order.orderType()) {
                throw new IllegalArgumentException("Manifest orderType must match order");
            }
            if (!manifest.serviceCentreId().equals(order.serviceCentreId())) {
                throw new IllegalArgumentException("Manifest serviceCentreId must match order");
            }
            if (!manifest.items().equals(order.items())) {
                throw new IllegalArgumentException("Manifest items must match order");
            }
            if (manifest.sourceSequenceNumber() != order.sequenceNumber()) {
                throw new IllegalArgumentException("Manifest source sequence must match order");
            }
        }
    }
}
