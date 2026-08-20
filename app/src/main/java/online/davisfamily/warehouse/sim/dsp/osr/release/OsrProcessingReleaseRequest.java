package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;

public record OsrProcessingReleaseRequest(
        InboundToteManifest manifest,
        Duration releaseTime) {

    public OsrProcessingReleaseRequest {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest must not be null");
        }
        if (releaseTime == null) {
            throw new IllegalArgumentException("releaseTime must not be null");
        }
        if (releaseTime.isNegative()) {
            throw new IllegalArgumentException("releaseTime must not be negative");
        }
    }
}
