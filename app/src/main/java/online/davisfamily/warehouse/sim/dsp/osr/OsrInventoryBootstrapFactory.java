package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OsrInventoryBootstrapFactory {

    public OsrBootstrapState create(LoadedDspData data, OsrInventoryConfig config) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        Set<String> preloadServiceCentreIds = new LinkedHashSet<>(
                config.preloadServiceCentreIds());
        List<InboundToteManifest> preloadManifests = data.inboundToteManifests().stream()
                .filter(manifest -> preloadServiceCentreIds.contains(manifest.serviceCentreId()))
                .toList();

        Set<PhysicalToteId> selectedPhysicalToteIds = new LinkedHashSet<>();
        for (InboundToteManifest manifest : preloadManifests) {
            if (manifest == null) {
                throw new IllegalArgumentException("preload manifests must not contain null");
            }
            if (!selectedPhysicalToteIds.add(manifest.physicalToteId())) {
                throw new IllegalArgumentException(
                        "Duplicate startup preload physical tote ID: "
                                + manifest.physicalToteId().value());
            }
        }

        int initialCount = Math.min(preloadManifests.size(), config.capacity());
        List<InboundToteManifest> initialManifests = new ArrayList<>(
                preloadManifests.subList(0, initialCount));
        List<PhysicalToteId> overflowPhysicalToteIds =
                preloadManifests.subList(initialCount, preloadManifests.size()).stream()
                        .map(InboundToteManifest::physicalToteId)
                        .toList();

        Set<OrderSheetKey> authorizedEmptyOrderSheetKeys = new LinkedHashSet<>();
        data.orders().stream()
                .filter(order -> order.orderType() == OrderType.EMPTY)
                .filter(order -> preloadServiceCentreIds.contains(order.serviceCentreId()))
                .map(order -> order.orderSheetKey())
                .forEach(authorizedEmptyOrderSheetKeys::add);

        OsrPhysicalInventory inventory = new OsrPhysicalInventory(config);
        inventory.storeAll(initialManifests);
        return new OsrBootstrapState(
                inventory,
                authorizedEmptyOrderSheetKeys,
                overflowPhysicalToteIds);
    }
}
