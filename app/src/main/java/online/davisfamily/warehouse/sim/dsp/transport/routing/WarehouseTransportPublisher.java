package online.davisfamily.warehouse.sim.dsp.transport.routing;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

public interface WarehouseTransportPublisher {
    boolean contains(PhysicalToteId physicalToteId);

    /**
     * Publishes the exact routed tote as one acceptance operation. Implementations must
     * complete expected validation before mutating simulation or render collections.
     */
    void publish(RoutedPhysicalTote routedTote);
}
