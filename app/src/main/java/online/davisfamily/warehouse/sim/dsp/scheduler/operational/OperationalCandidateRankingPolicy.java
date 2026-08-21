package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.List;

public interface OperationalCandidateRankingPolicy {
    List<OperationalReleaseSelection> rank(
            List<OperationalReleaseSelection> eligibleCandidates,
            DspOperationalReleaseSnapshot snapshot);
}
