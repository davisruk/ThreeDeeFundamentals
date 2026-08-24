package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;

public record DspP2pElasticAllocationRuntimeSnapshot(
        P2pLineLeaseCatalogSnapshot leases,
        P2pElasticAllocationSnapshot allocation) {

    public DspP2pElasticAllocationRuntimeSnapshot {
        if (leases == null || allocation == null) {
            throw new IllegalArgumentException("elastic runtime snapshots must not be null");
        }
        if (!allocation.configuredLineIds().equals(leases.lines().stream()
                .map(line -> line.definition().lineId())
                .toList())) {
            throw new IllegalArgumentException(
                    "elastic allocation must describe the same lease snapshot lines");
        }
    }
}
