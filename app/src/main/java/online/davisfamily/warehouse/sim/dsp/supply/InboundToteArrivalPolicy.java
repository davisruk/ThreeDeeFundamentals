package online.davisfamily.warehouse.sim.dsp.supply;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;

public interface InboundToteArrivalPolicy {
    String policyId();

    Duration intervalBeforeNextTote(
            InboundToteManifest nextManifest,
            long previouslyAdmittedToteCount);
}
