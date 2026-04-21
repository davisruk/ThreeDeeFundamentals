package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;

public class TipperToSorterSectionInstaller {
    public TipperToSorterSection install(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            TipperInstallation tipperInstallation,
            SortingInstallation sortingInstallation,
            PackReceiveTarget sorterOutfeedTarget) {
        if (tr == null
                || sim == null
                || objects == null
                || inspectionRegistry == null
                || tipperInstallation == null
                || sortingInstallation == null) {
            throw new IllegalArgumentException("Tipper-to-sorter install inputs must not be null");
        }

        ToteTrackTipperFlowController flowController = new ToteTrackTipperFlowController(
                tipperInstallation.getTote(),
                tipperInstallation.getToteLoadPlan(),
                tipperInstallation.getTrackSection().getTipperSegment(),
                tipperInstallation.getTrackSection().getTipperStopDistance(),
                tipperInstallation.getTrackSection().getTippedAngleRadians(),
                tipperInstallation.getTippingMachine(),
                sortingInstallation.getSortingMachine(),
                0.55d,
                sorterOutfeedTarget);
        sim.addController(flowController);
        return new TipperToSorterSection(
                tr,
                sim,
                objects,
                inspectionRegistry,
                tipperInstallation,
                sortingInstallation,
                flowController);
    }
}
