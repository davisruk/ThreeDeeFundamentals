package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;

public final class WholeServiceCentreReleaseEligibilityPolicy
        implements OperationalServiceCentreReleasePolicy {
    @Override
    public Optional<OperationalReleaseBlock> blockFor(
            DspOperationalReleaseCandidate candidate, DspOperationalReleaseSnapshot snapshot) {
        if (candidate == null || snapshot == null) {
            throw new IllegalArgumentException("release policy inputs must not be null");
        }
        var allocation = snapshot.elasticP2pAllocation().orElseThrow(() ->
                new IllegalArgumentException("whole release policy requires allocation metadata"));
        if (!DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER.name()
                .equals(allocation.profileId())) {
            throw new IllegalArgumentException("release policy requires the whole allocation profile");
        }
        var policy = allocation.wholeServiceCentrePolicy().orElseThrow();
        if (policy.eligibleServiceCentreId()
                .filter(candidate.physicalCandidate().serviceCentreId()::equals).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new OperationalReleaseBlock(
                OperationalReleaseBlockType.SERVICE_CENTRE_SEQUENCE,
                "whole-service-centre release barrier: current="
                        + policy.releases().releaseServiceCentreId().orElse("none")
                        + ", eligible=" + policy.eligibleServiceCentreId().orElse("none")));
    }
}
