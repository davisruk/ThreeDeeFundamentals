package online.davisfamily.warehouse.testing;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSection;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;

public class TipperToReceiverDebugRig implements DebugSceneRuntime {
    private final TipperInstallation tipperInstallation;
    private final SimplePackReceiverTarget receiverTarget;
    private final ToteTrackTipperFlowController flowController;
    private final TipperToReceiverPackVisuals packVisuals;

    public TipperToReceiverDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        TipperTrackSection trackSection = new TipperTrackSectionInstaller().install(
                tr,
                objects,
                TipperEntryLayoutSpec.debugDefaults());
        TipperDemoFixtures.DemoTipperFeed demoTipperFeed = TipperDemoFixtures.createDemoTipperFeed(tr, sim, trackSection);
        tipperInstallation = new TipperSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                trackSection,
                demoTipperFeed.totePayload(),
                demoTipperFeed.toteLoadPlanProvider());

        Vec3 receiverWorld = trackSection.localToWorld(
                tipperInstallation.getTipperModule().sorterIntakeMountLocalPoint().x + 0.35f,
                tipperInstallation.getTipperModule().sorterIntakeMountLocalPoint().y - 0.08f,
                tipperInstallation.getTipperModule().sorterIntakeMountLocalPoint().z - 0.04f);
        receiverTarget = new SimplePackReceiverTarget(
                tr,
                objects,
                inspectionRegistry,
                receiverWorld,
                trackSection.rigYaw());

        flowController = new ToteTrackTipperFlowController(
                tipperInstallation.getTote(),
                tipperInstallation.getToteLoadPlanProvider(),
                trackSection.getTipperSegment(),
                trackSection.getTipperStopDistance(),
                trackSection.getTippedAngleRadians(),
                tipperInstallation.getTippingMachine(),
                new ImmediatePackReceiveDownstreamFlow(receiverTarget),
                0.55d);
        sim.addController(flowController);

        TipperToReceiverDebugSeam dischargeSeam = new TipperToReceiverDebugSeam();
        packVisuals = new TipperToReceiverPackVisuals(
                tr,
                objects,
                inspectionRegistry,
                tipperInstallation.getTotePayload().getToteRenderable(),
                tipperInstallation.getToteLoadPlanProvider().getLoadPlanFor(tipperInstallation.getTote().getId()),
                tipperInstallation.getTotePayload().getContainedPackLayoutById(),
                trackSection.rigYaw(),
                dischargeSeam);
    }

    @Override
    public void syncVisuals() {
        tipperInstallation.getTipperTrackRuntimeState().setRunning(!flowController.isToteCaptured());
        tipperInstallation.getTipperModule().syncVisuals(flowController.getVisualTipProgress());
        packVisuals.sync(
                flowController,
                tipperInstallation.getTipperModule(),
                receiverTarget.handoffPoint().worldPosition());
        receiverTarget.syncVisuals(packVisuals::getPackRenderable);
    }
}
