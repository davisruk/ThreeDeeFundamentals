package online.davisfamily.warehouse.sim.dsp.outbound;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public interface OutboundToteIdSource {
    PhysicalToteId nextId(P2pLineId lineId);
}
