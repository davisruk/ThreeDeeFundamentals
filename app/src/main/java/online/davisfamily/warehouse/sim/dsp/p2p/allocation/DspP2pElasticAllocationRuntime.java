package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.List;
import java.util.function.Function;

import online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalP2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommitter;

public final class DspP2pElasticAllocationRuntime implements AutoCloseable {

    private final DspP2pStickyLeaseRuntime leaseRuntime;
    private final Function<P2pLineLeaseCatalogSnapshot, P2pElasticAllocationSnapshot>
            allocationFactory;

    DspP2pElasticAllocationRuntime(
            DspP2pStickyLeaseRuntime leaseRuntime,
            Function<P2pLineLeaseCatalogSnapshot, P2pElasticAllocationSnapshot>
                    allocationFactory) {
        if (leaseRuntime == null || allocationFactory == null) {
            throw new IllegalArgumentException("elastic runtime inputs must not be null");
        }
        this.leaseRuntime = leaseRuntime;
        this.allocationFactory = allocationFactory;
    }

    public List<P2pLineDefinition> lineDefinitions() {
        return leaseRuntime.lineDefinitions();
    }

    public P2pReleaseAssignmentCommitter releaseAssignmentCommitter() {
        return leaseRuntime.releaseAssignmentCommitter();
    }

    public OperationalP2pReleaseAssignmentCommitter operationalReleaseAssignmentCommitter() {
        return leaseRuntime.operationalReleaseAssignmentCommitter();
    }

    public P2pLineLeaseCatalogSnapshot leaseSnapshot() {
        return leaseRuntime.leaseSnapshot();
    }

    public P2pElasticAllocationSnapshot allocationSnapshot() {
        P2pLineLeaseCatalogSnapshot leases = leaseSnapshot();
        return requireAllocation(allocationFactory.apply(leases));
    }

    public DspP2pElasticAllocationRuntimeSnapshot operationalSnapshot() {
        P2pLineLeaseCatalogSnapshot leases = leaseSnapshot();
        return new DspP2pElasticAllocationRuntimeSnapshot(
                leases,
                requireAllocation(allocationFactory.apply(leases)));
    }

    public DspP2pStickyLeaseRuntimeSnapshot leaseRuntimeSnapshot() {
        return leaseRuntime.snapshot();
    }

    public boolean isClosed() {
        return leaseRuntime.isClosed();
    }

    @Override
    public void close() {
        leaseRuntime.close();
    }

    private static P2pElasticAllocationSnapshot requireAllocation(
            P2pElasticAllocationSnapshot allocation) {
        if (allocation == null) {
            throw new IllegalStateException("elastic allocation factory returned null");
        }
        return allocation;
    }
}
