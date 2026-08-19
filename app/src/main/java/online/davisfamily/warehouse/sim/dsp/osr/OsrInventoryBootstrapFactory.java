package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

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
        if (preloadManifests.size() > config.capacity()) {
            throw new IllegalStateException(
                    "OSR initial preload exceeds capacity: selected=" + preloadManifests.size()
                            + ", capacity=" + config.capacity());
        }

        Set<OrderSheetKey> authorizedEmptyOrderSheetKeys = new LinkedHashSet<>();
        data.orders().stream()
                .filter(order -> order.orderType() == OrderType.EMPTY)
                .filter(order -> preloadServiceCentreIds.contains(order.serviceCentreId()))
                .map(order -> order.orderSheetKey())
                .forEach(authorizedEmptyOrderSheetKeys::add);

        OsrPhysicalInventory inventory = new OsrPhysicalInventory(config);
        inventory.storeAll(preloadManifests);
        return new OsrBootstrapState(inventory, authorizedEmptyOrderSheetKeys);
    }
}
