package online.davisfamily.warehouse.sim.dsp.transport.routing;

/** Publication status of a routed physical tote in the simulation world. */
public enum WarehouseTransportPublicationState {
    UNPUBLISHED,
    PUBLISHED_EXACT_OBJECTS,
    PHYSICAL_ID_CONFLICT
}
