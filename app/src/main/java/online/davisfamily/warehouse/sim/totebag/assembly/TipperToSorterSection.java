package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.handoff.MachineHandoffPointId;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPointProvider;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class TipperToSorterSection implements PackHandoffPointProvider {
    private final TipperInstallation tipperInstallation;
    private final SortingInstallation sortingInstallation;
    private final ToteTrackTipperFlowController flowController;
    private final TipperToSorterDischargeSeam dischargeSeam;
    private final TipperToSorterPackVisuals packVisuals;

    public TipperToSorterSection(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            TipperInstallation tipperInstallation,
            SortingInstallation sortingInstallation,
            ToteLoadPlan toteLoadPlan,
            ToteTrackTipperFlowController flowController) {
        if (tr == null
                || sim == null
                || objects == null
                || inspectionRegistry == null
                || tipperInstallation == null
                || sortingInstallation == null
                || toteLoadPlan == null
                || flowController == null) {
            throw new IllegalArgumentException("Tipper-to-sorter section inputs must not be null");
        }
        this.tipperInstallation = tipperInstallation;
        this.sortingInstallation = sortingInstallation;
        this.flowController = flowController;
        this.dischargeSeam = new TipperToSorterDischargeSeam();
        this.packVisuals = new TipperToSorterPackVisuals(
                tr,
                objects,
                inspectionRegistry,
                tipperInstallation.getTotePayload().getToteRenderable(),
                toteLoadPlan,
                tipperInstallation.getTotePayload().getContainedPackLayoutById(),
                tipperInstallation.getTrackSection().rigYaw(),
                dischargeSeam);
    }

    public TipperInstallation getTipperInstallation() {
        return tipperInstallation;
    }

    public SortingInstallation getSortingInstallation() {
        return sortingInstallation;
    }

    public ToteTrackTipperFlowController getFlowController() {
        return flowController;
    }

    public void syncVisuals() {
        tipperInstallation.getTipperTrackRuntimeState().setRunning(!flowController.isToteCaptured());
        tipperInstallation.getTipperModule().syncVisuals(flowController.getVisualTipProgress());
        packVisuals.sync(
                flowController,
                tipperInstallation.getTipperModule(),
                sortingInstallation.getSortingModule());
    }

    public RenderableObject getPackRenderable(String packId) {
        return packVisuals.getPackRenderable(packId);
    }

    @Override
    public PackHandoffPoint resolveHandoffPoint(MachineHandoffPointId pointId) {
        if (pointId == null) {
            throw new IllegalArgumentException("pointId must not be null");
        }
        return switch (pointId) {
            case TIPPER_PACK_DISCHARGE -> tipperInstallation.getTipperModule().dischargePoint();
            case SORTER_PACK_INTAKE -> sortingInstallation.getSortingModule().intakePoint();
            case SORTER_PACK_OUTFEED -> sortingInstallation.getSortingModule().outfeedPoint();
        };
    }
}
