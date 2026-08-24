package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.List;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public final class WarehouseSnapshotP2pReleaseRequirementResolver
        implements P2pReleaseRequirementResolver {
    private final Supplier<WarehouseSchedulerSnapshot> snapshotSupplier;

    public WarehouseSnapshotP2pReleaseRequirementResolver(
            Supplier<WarehouseSchedulerSnapshot> snapshotSupplier) {
        if (snapshotSupplier == null) {
            throw new IllegalArgumentException("snapshotSupplier must not be null");
        }
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public RouteRequirements resolve(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        WarehouseSchedulerSnapshot snapshot = snapshotSupplier.get();
        if (snapshot == null) {
            throw new IllegalStateException("snapshotSupplier returned null");
        }
        List<DspSchedulerOrderState> matches = snapshot.orderStates().stream()
                .filter(state -> state.order().orderSheetKey().equals(orderSheetKey))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one route definition for order sheet " + orderSheetKey
                            + " but found " + matches.size());
        }
        return matches.getFirst().routeRequirements();
    }
}
