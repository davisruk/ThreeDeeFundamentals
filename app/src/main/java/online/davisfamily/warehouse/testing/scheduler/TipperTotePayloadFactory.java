package online.davisfamily.warehouse.testing.scheduler;

import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

@FunctionalInterface
public interface TipperTotePayloadFactory {
    TipperTotePayload createPayload();
}
