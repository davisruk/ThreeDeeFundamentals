package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.OrderType;

/** Full-day candidate ranking: existing centre/pharmacy order, with eligible ADAPTED first. */
public final class AdaptedFirstPharmacyGroupedSourceSequenceRankingPolicy
        implements OperationalCandidateRankingPolicy {
    private final PharmacyGroupedSourceSequenceRankingPolicy base =
            new PharmacyGroupedSourceSequenceRankingPolicy();

    @Override
    public List<OperationalReleaseSelection> rank(
            List<OperationalReleaseSelection> eligibleCandidates,
            DspOperationalReleaseSnapshot snapshot) {
        List<OperationalReleaseSelection> ranked = base.rank(eligibleCandidates, snapshot);
        List<OperationalReleaseSelection> adaptedFirst = new ArrayList<>(ranked.size());
        for (OperationalReleaseSelection selection : ranked) {
            if (selection.candidate().logicalOrderState().order().orderType() == OrderType.ADAPTED) {
                adaptedFirst.add(selection);
            }
        }
        for (OperationalReleaseSelection selection : ranked) {
            if (selection.candidate().logicalOrderState().order().orderType() != OrderType.ADAPTED) {
                adaptedFirst.add(selection);
            }
        }
        return List.copyOf(adaptedFirst);
    }
}
