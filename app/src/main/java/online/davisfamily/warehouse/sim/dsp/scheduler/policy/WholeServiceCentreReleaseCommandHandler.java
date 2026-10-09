package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandHandler;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;

/** Simulation-thread guard; the delegate remains the sole physical release/assignment owner. */
public final class WholeServiceCentreReleaseCommandHandler implements SchedulerCommandHandler {
    private final SchedulerCommandHandler delegate;
    private final WholeServiceCentreReleaseLedger ledger;
    private final Supplier<P2pElasticAllocationSnapshot> liveAllocationSupplier;

    public WholeServiceCentreReleaseCommandHandler(SchedulerCommandHandler delegate,
            WholeServiceCentreReleaseLedger ledger,
            Supplier<P2pElasticAllocationSnapshot> liveAllocationSupplier) {
        if (delegate == null || ledger == null || liveAllocationSupplier == null) {
            throw new IllegalArgumentException("release guard collaborators must not be null");
        }
        this.delegate = delegate;
        this.ledger = ledger;
        this.liveAllocationSupplier = liveAllocationSupplier;
    }

    @Override
    public SchedulerCommandApplicationResult apply(SchedulerCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (!(command instanceof ReleasePhysicalToteFromOsrCommand)
                && !(command instanceof ReleasePhysicalToteFromAv02Command)) {
            return SchedulerCommandApplicationResult.rejectedResult("Unsupported physical release command");
        }
        var release = (OperationalPhysicalToteReleaseCommand) command;
        var allocation = liveAllocationSupplier.get();
        if (allocation == null || !allocation.profileId().equals(
                DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER.name())) {
            throw new IllegalStateException("release guard requires a live whole-service-centre allocation");
        }
        var policy = allocation.wholeServiceCentrePolicy().orElseThrow();
        if (policy.releases() != ledger.snapshot()) {
            throw new IllegalStateException("live allocation must capture the shared current release ledger");
        }
        if (!policy.eligibleServiceCentreId().filter(release.serviceCentreId()::equals).isPresent()) {
            return SchedulerCommandApplicationResult.deferredResult("Service-centre release barrier: "
                    + release.serviceCentreId() + " is not the current eligible centre");
        }
        try {
            // Includes exact source/sheet/tote, configured destination and counter overflow checks.
            ledger.validateUnreleased(release);
        } catch (IllegalArgumentException invalid) {
            return SchedulerCommandApplicationResult.rejectedResult(invalid.getMessage());
        }
        if (release.proposedP2pAssignment().isPresent()) {
            var assignment = release.proposedP2pAssignment().orElseThrow();
            boolean feeding = allocation.find(release.serviceCentreId())
                    .filter(demand -> demand.feedingOwnedLineIds().contains(assignment.lineId())).isPresent();
            if (!feeding && !policy.availableUnleasedLineIds().contains(assignment.lineId())) {
                return SchedulerCommandApplicationResult.deferredResult(
                        "Proposed P2P line is no longer feeding-current or available unleased");
            }
            int outstanding = policy.releases().outstandingToteCount(assignment.lineId());
            int watermark = policy.releases().p2pOutstandingToteWatermark();
            if (outstanding >= watermark) {
                return SchedulerCommandApplicationResult.deferredResult(
                        "OUTSTANDING_TOTE_WATERMARK: line " + assignment.lineId()
                                + " has " + outstanding + " outstanding totes (limit " + watermark + ")");
            }
        }
        var result = delegate.apply(command);
        if (result == null) {
            throw new IllegalStateException("physical release delegate returned null");
        }
        if (result.applied()) {
            ledger.recordApplied(release);
        }
        return result;
    }
}
