package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.Optional;

@FunctionalInterface
public interface OperationalServiceCentreReleasePolicy {
    Optional<OperationalReleaseBlock> blockFor(
            DspOperationalReleaseCandidate candidate, DspOperationalReleaseSnapshot snapshot);

    static OperationalServiceCentreReleasePolicy allowAll() {
        return AllowAll.INSTANCE;
    }

    final class AllowAll {
        private static final OperationalServiceCentreReleasePolicy INSTANCE =
                (candidate, snapshot) -> Optional.empty();

        private AllowAll() { }
    }
}
