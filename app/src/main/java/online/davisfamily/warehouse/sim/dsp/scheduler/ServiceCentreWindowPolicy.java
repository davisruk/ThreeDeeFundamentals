package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.Optional;

public class ServiceCentreWindowPolicy {
    private final ServiceCentrePriority priority;

    public ServiceCentreWindowPolicy(ServiceCentrePriority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("priority must not be null");
        }
        this.priority = priority;
    }

    public Optional<String> activeWindowFor(WarehouseSchedulerSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        String currentActive = snapshot.activeServiceCentreId().orElse(null);
        if (currentActive != null && hasUnreleasedWork(currentActive, snapshot)) {
            return Optional.of(currentActive);
        }

        for (String serviceCentreId : priority.serviceCentreIds()) {
            if (hasUnreleasedWork(serviceCentreId, snapshot)) {
                return Optional.of(serviceCentreId);
            }
        }
        return Optional.empty();
    }

    public boolean isOrderInActiveWindow(DspSchedulerOrderState orderState, WarehouseSchedulerSnapshot snapshot) {
        if (orderState == null) {
            throw new IllegalArgumentException("orderState must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return activeWindowFor(snapshot)
                .map(activeServiceCentre -> activeServiceCentre.equals(orderState.order().serviceCentreId()))
                .orElse(false);
    }

    private boolean hasUnreleasedWork(String serviceCentreId, WarehouseSchedulerSnapshot snapshot) {
        return snapshot.orderStates().stream()
                .filter(orderState -> serviceCentreId.equals(orderState.order().serviceCentreId()))
                .anyMatch(orderState -> orderState.status() == DspOrderStatus.WAITING
                        || orderState.status() == DspOrderStatus.BLOCKED);
    }
}
