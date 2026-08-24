package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

public final class P2pLeaseReleaseController implements SimulationController {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000d;

    private final Supplier<P2pServiceCentreWorkSnapshot> workSnapshotSupplier;
    private final List<P2pLineActivityProbe> activityProbes;
    private final P2pLineLeaseRegistry leaseRegistry;
    private final OutboundToteAllocator outboundToteAllocator;

    public P2pLeaseReleaseController(
            Supplier<P2pServiceCentreWorkSnapshot> workSnapshotSupplier,
            List<P2pLineActivityProbe> activityProbes,
            P2pLineLeaseRegistry leaseRegistry,
            OutboundToteAllocator outboundToteAllocator) {
        if (workSnapshotSupplier == null
                || activityProbes == null
                || leaseRegistry == null
                || outboundToteAllocator == null) {
            throw new IllegalArgumentException("P2P lease release controller inputs must not be null");
        }
        if (activityProbes.size() != leaseRegistry.definitions().size()
                || activityProbes.stream().anyMatch(probe -> probe == null)) {
            throw new IllegalArgumentException(
                    "activityProbes must contain one probe per configured P2P line");
        }
        this.workSnapshotSupplier = workSnapshotSupplier;
        this.activityProbes = List.copyOf(activityProbes);
        this.leaseRegistry = leaseRegistry;
        this.outboundToteAllocator = outboundToteAllocator;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and nonnegative");
        }
        P2pServiceCentreWorkSnapshot work = workSnapshotSupplier.get();
        if (work == null) {
            throw new IllegalStateException("workSnapshotSupplier returned null");
        }

        Map<P2pLineId, P2pLineActivitySnapshot> activities = new LinkedHashMap<>();
        List<P2pLineDefinition> definitions = leaseRegistry.definitions();
        for (int index = 0; index < definitions.size(); index++) {
            P2pLineActivitySnapshot activity = activityProbes.get(index).snapshot();
            if (activity == null) {
                throw new IllegalStateException("P2P line activity probe returned null");
            }
            activities.put(definitions.get(index).lineId(), activity);
        }

        P2pLineLeaseCatalogSnapshot leases = leaseRegistry.snapshot(activities);
        Duration time = simulationTime(context.getSimulationTimeSeconds());
        for (P2pLineLeaseSnapshot line : leases.lines()) {
            if (!line.leased()) {
                continue;
            }
            String owner = line.serviceCentreId().orElseThrow();
            if (work.hasRemainingWork(owner) || !line.activity().processingDrained()) {
                continue;
            }
            if (line.activity().openOutboundTote().isPresent()) {
                var closedTote = outboundToteAllocator.closeForApplicableWorkCompletion(
                        line.definition().lineId(), time)
                        .orElseThrow(() -> new IllegalStateException(
                                "P2P activity reported an open outbound tote that could not be closed"));
                leaseRegistry.recordOutboundToteClosure(
                        line.definition().lineId(), closedTote);
                return;
            }
            leaseRegistry.releaseLease(line.definition().lineId(), owner, line.activity());
            return;
        }
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
