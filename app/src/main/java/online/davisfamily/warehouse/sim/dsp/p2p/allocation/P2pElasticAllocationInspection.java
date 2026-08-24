package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.ArrayList;
import java.util.List;

public final class P2pElasticAllocationInspection {

    public List<String> describe(P2pElasticAllocationSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Elastic profile: " + snapshot.profileId());
        lines.add("Elastic time: " + snapshot.evaluatedAt());
        lines.add("Elastic calibration: " + snapshot.calibrationStatus());
        lines.add("Elastic capacity: desired=" + snapshot.totalDesiredLines()
                + "/" + snapshot.configuredLineIds().size()
                + ", unmet=" + snapshot.totalUnmetRequiredLines());

        for (int index = 0; index < snapshot.serviceCentres().size(); index++) {
            P2pServiceCentreLineDemandSnapshot demand = snapshot.serviceCentres().get(index);
            String prefix = "SC " + demand.serviceCentreId();
            lines.add(prefix + ": priority=" + demand.priority()
                    + ", authOrder=" + (index + 1)
                    + ", authorizedAt=" + demand.authorizationElapsedTime()
                    + ", activeWindow=" + demand.withinConcurrencyWindow());
            lines.add(prefix + " deadline: target=" + demand.deadline().targetCompletion()
                    + ", latest=" + demand.deadline().latestAllowedCompletion()
                    + ", slack=" + demand.deadline().availableTime());
            lines.add(prefix + " work: totes=" + demand.workload().remainingInboundToteCount()
                    + ", packs=" + demand.workload().remainingUnallocatedPackCount()
                    + ", bags=" + demand.workload().remainingUnallocatedBagCount()
                    + ", empty=" + demand.workload().unallocatedEmptyOrderCount()
                    + ", estimate=" + demand.workload().estimatedSingleLineWork()
                    + ", adjusted=" + demand.adjustedSingleLineWork());
            lines.add(prefix + " lines: raw=" + demand.rawRequiredLines()
                    + ", required=" + demand.requiredLines()
                    + ", desired=" + demand.desiredLines()
                    + ", owned=" + demand.ownedLineCount()
                    + ", additional=" + demand.additionalLineSlots()
                    + ", unmet=" + demand.unmetRequiredLines());
            lines.add(prefix + " feeding: " + demand.feedingOwnedLineIds().stream()
                    .map(lineId -> lineId.value()).toList());
            lines.add(prefix + " draining: " + demand.drainingSurplusLineIds().stream()
                    .map(lineId -> lineId.value()).toList());
            lines.add(prefix + " infeasibility: "
                    + (demand.issues().isEmpty() ? "none" : demand.issues()));
        }
        lines.add("Elastic infeasible: " + snapshot.infeasible());
        lines.add("Elastic issues: " + (snapshot.issues().isEmpty()
                ? "none"
                : snapshot.issues().stream()
                        .map(issue -> issue.serviceCentreId() + "/" + issue.type())
                        .toList()));
        return List.copyOf(lines);
    }
}
