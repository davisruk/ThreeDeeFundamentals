package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

public record AdaptingAreaAdmissionSnapshot(
        List<AdaptingBenchAdmissionSnapshot> benchAdmissions,
        AdaptingBenchSelection selection) {

    public AdaptingAreaAdmissionSnapshot {
        if (benchAdmissions == null) {
            throw new IllegalArgumentException("benchAdmissions must not be null");
        }
        if (selection == null) {
            throw new IllegalArgumentException("selection must not be null");
        }
        benchAdmissions = List.copyOf(benchAdmissions);
    }

    public boolean admissionOpen() {
        return selection.accepted();
    }
}
