package online.davisfamily.warehouse.sim.dsp.supply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record ServiceCentreSupplyBatch(
        String serviceCentreId,
        int priority,
        long firstSourceSequenceNumber,
        boolean preloadedAtStart,
        List<InboundToteManifest> physicalManifests,
        Set<OrderSheetKey> emptyOrderSheetKeys) {

    public ServiceCentreSupplyBatch {
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (priority <= 0) {
            throw new IllegalArgumentException("priority must be positive");
        }
        if (firstSourceSequenceNumber < 0) {
            throw new IllegalArgumentException("firstSourceSequenceNumber must be >= 0");
        }
        if (physicalManifests == null) {
            throw new IllegalArgumentException("physicalManifests must not be null");
        }
        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        List<InboundToteManifest> copiedManifests = new ArrayList<>();
        for (InboundToteManifest manifest : physicalManifests) {
            if (manifest == null) {
                throw new IllegalArgumentException("physicalManifests must not contain null");
            }
            if (!serviceCentreId.equals(manifest.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "Physical manifest serviceCentreId must match batch: "
                                + manifest.physicalToteId().value());
            }
            if (!physicalToteIds.add(manifest.physicalToteId())) {
                throw new IllegalArgumentException(
                        "Duplicate physical tote ID in supply batch: "
                                + manifest.physicalToteId().value());
            }
            copiedManifests.add(manifest);
        }
        if (emptyOrderSheetKeys == null) {
            throw new IllegalArgumentException("emptyOrderSheetKeys must not be null");
        }
        Set<OrderSheetKey> copiedEmptyKeys = new LinkedHashSet<>();
        for (OrderSheetKey orderSheetKey : emptyOrderSheetKeys) {
            if (orderSheetKey == null) {
                throw new IllegalArgumentException("emptyOrderSheetKeys must not contain null");
            }
            if (!copiedEmptyKeys.add(orderSheetKey)) {
                throw new IllegalArgumentException(
                        "Duplicate EMPTY order sheet key: " + orderSheetKey);
            }
        }
        physicalManifests = List.copyOf(copiedManifests);
        emptyOrderSheetKeys = Collections.unmodifiableSet(new LinkedHashSet<>(copiedEmptyKeys));
    }
}
