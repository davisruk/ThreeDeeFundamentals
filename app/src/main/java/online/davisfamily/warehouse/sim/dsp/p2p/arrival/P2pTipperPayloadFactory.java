package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

@FunctionalInterface
public interface P2pTipperPayloadFactory {
    TipperTotePayload create(RoutedPhysicalTote routedTote);
}
