package online.davisfamily.warehouse.testing;

import online.davisfamily.warehouse.sim.totebag.assembly.SortingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.BaggingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSection;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSection;
import online.davisfamily.warehouse.sim.totebag.assembly.ToteToBagSubsystem;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;

public class IntegratedToteToBagDebugInstallation {
    private final ToteToBagSubsystem subsystem;
    private final TipperTrackSection trackSection;
    private final TipperInstallation tipperInstallation;
    private final SortingInstallation sortingInstallation;
    private final BaggingInstallation baggingInstallation;
    private final TipperToSorterSection tipperToSorterSection;
    private final ToteToBagFlowController flowController;

    public IntegratedToteToBagDebugInstallation(
            ToteToBagSubsystem subsystem,
            TipperTrackSection trackSection,
            TipperInstallation tipperInstallation,
            SortingInstallation sortingInstallation,
            BaggingInstallation baggingInstallation,
            TipperToSorterSection tipperToSorterSection,
            ToteToBagFlowController flowController) {
        if (subsystem == null
                || trackSection == null
                || tipperInstallation == null
                || sortingInstallation == null
                || baggingInstallation == null
                || tipperToSorterSection == null
                || flowController == null) {
            throw new IllegalArgumentException("Integrated tote-to-bag installation inputs must not be null");
        }
        this.subsystem = subsystem;
        this.trackSection = trackSection;
        this.tipperInstallation = tipperInstallation;
        this.sortingInstallation = sortingInstallation;
        this.baggingInstallation = baggingInstallation;
        this.tipperToSorterSection = tipperToSorterSection;
        this.flowController = flowController;
    }

    public ToteToBagSubsystem getSubsystem() {
        return subsystem;
    }

    public TipperTrackSection getTrackSection() {
        return trackSection;
    }

    public TipperInstallation getTipperInstallation() {
        return tipperInstallation;
    }

    public SortingInstallation getSortingInstallation() {
        return sortingInstallation;
    }

    public BaggingInstallation getBaggingInstallation() {
        return baggingInstallation;
    }

    public TipperToSorterSection getTipperToSorterSection() {
        return tipperToSorterSection;
    }

    public ToteToBagFlowController getFlowController() {
        return flowController;
    }
}
