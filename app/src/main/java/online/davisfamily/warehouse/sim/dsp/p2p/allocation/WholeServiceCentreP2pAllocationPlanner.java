package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentrePolicySnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

/** Release-order decisions are physical; deadlines and normalized costs are report-only. */
public final class WholeServiceCentreP2pAllocationPlanner {
    private final ServiceCentreDeadlineSnapshotFactory deadlineFactory =
            new ServiceCentreDeadlineSnapshotFactory();
    private WholeServiceCentrePolicySnapshot lastPolicy;

    public P2pElasticAllocationSnapshot create(
            DspOperationalClockSnapshot clock,
            DspSupplySnapshot supply,
            P2pWorkloadSnapshot workload,
            DspServiceCentreTimetable timetable,
            P2pLineLeaseCatalogSnapshot leases,
            WholeServiceCentreReleaseSnapshot releases,
            Duration downstreamHandlingDuration) {
        if (clock == null || supply == null || workload == null || timetable == null
                || leases == null || releases == null || downstreamHandlingDuration == null
                || downstreamHandlingDuration.isZero() || downstreamHandlingDuration.isNegative()
                || leases.lines().isEmpty()) {
            throw new IllegalArgumentException("allocation planner inputs must be valid and nonnull");
        }
        Optional<String> current = releases.releaseServiceCentreId();
        var currentSupply = current.flatMap(id -> supply.serviceCentres().stream()
                .filter(centre -> centre.serviceCentreId().equals(id)).findFirst());
        if (current.isPresent() && currentSupply.isEmpty()) {
            throw new IllegalArgumentException("release centre is absent from supply");
        }
        boolean authorized = currentSupply.filter(centre ->
                centre.authorizationState() == ServiceCentreAuthorizationState.PRELOADED
                || centre.authorizationState() == ServiceCentreAuthorizationState.AUTHORIZED
                || centre.authorizationState() == ServiceCentreAuthorizationState.SUPPLY_COMPLETE)
                .isPresent();
        if (authorized && currentSupply.orElseThrow().authorizationElapsedTime().isEmpty()) {
            throw new IllegalArgumentException("authorized release centre requires an authorization time");
        }
        List<P2pLineId> feeding = new ArrayList<>();
        int availableCount = 0;
        boolean sameAvailable = lastPolicy != null;
        for (var line : leases.lines()) {
            if (current.isPresent() && line.serviceCentreId().equals(current)) {
                feeding.add(line.definition().lineId());
            }
            if (!line.leased() && line.activity().quiescent()) {
                if (sameAvailable && (availableCount >= lastPolicy.availableUnleasedLineIds().size()
                        || !line.definition().lineId().equals(
                                lastPolicy.availableUnleasedLineIds().get(availableCount)))) {
                    sameAvailable = false;
                }
                availableCount++;
            }
        }
        sameAvailable = sameAvailable
                && availableCount == lastPolicy.availableUnleasedLineIds().size();
        Optional<String> eligible = authorized && (!feeding.isEmpty() || availableCount > 0)
                ? current : Optional.empty();
        WholeServiceCentrePolicySnapshot policy;
        if (sameAvailable && lastPolicy.releases() == releases
                && lastPolicy.eligibleServiceCentreId().equals(eligible)) {
            policy = lastPolicy;
        } else {
            List<P2pLineId> available = sameAvailable ? lastPolicy.availableUnleasedLineIds()
                    : leases.lines().stream()
                            .filter(line -> !line.leased() && line.activity().quiescent())
                            .map(line -> line.definition().lineId()).toList();
            policy = new WholeServiceCentrePolicySnapshot(releases, eligible, available);
        }
        List<P2pServiceCentreLineDemandSnapshot> demands = List.of();
        if (authorized) {
            String id = current.orElseThrow();
            var centreSupply = currentSupply.orElseThrow();
            var schedule = timetable.require(id);
            if (schedule.priority() != centreSupply.priority()) {
                throw new IllegalArgumentException("supply and timetable priorities must match");
            }
            var centreWork = workload.find(id).orElse(null);
            if (centreWork != null && centreWork.hasEstimatedWork()) {
                int count = leases.lines().size();
                int desired = eligible.isPresent() ? feeding.size() + availableCount : 0;
                demands = List.of(new P2pServiceCentreLineDemandSnapshot(
                        id, centreSupply.priority(), centreSupply.authorizationElapsedTime().orElseThrow(),
                        deadlineFactory.create(schedule, clock, downstreamHandlingDuration),
                        centreWork, centreWork.estimatedSingleLineWork(), count, count, desired,
                        feeding, List.of(), Math.max(0, desired - feeding.size()),
                        Math.max(0, count - desired), true, List.of()));
            }
        }
        var allocation = new P2pElasticAllocationSnapshot(
                DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER.name(),
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED, clock.businessDateTime(),
                leases.lines().stream().map(line -> line.definition().lineId()).toList(),
                1, demands, List.of(), Optional.of(policy));
        lastPolicy = policy;
        return allocation;
    }
}
