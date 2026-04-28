package online.davisfamily.warehouse.testing;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.assembly.BaggingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.BaggingSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.SortingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.SortingSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSection;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSection;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.ToteToBagSubsystem;
import online.davisfamily.warehouse.sim.totebag.assembly.ToteToBagSubsystemBuilder;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.handoff.SorterOutfeedToPdcReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.handoff.ToteBagReceiver;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayoutSpec;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagBatchPlan;

public class IntegratedToteToBagDebugInstaller {
    private static final int DEBUG_TOTE_BAG_CAPACITY = 10;

    public IntegratedToteToBagDebugInstallation install(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            TrackAppearance conveyorAppearance,
            ToteToBagCoreLayoutSpec layoutSpec) {
        if (tr == null
                || sim == null
                || objects == null
                || inspectionRegistry == null
                || conveyorAppearance == null
                || layoutSpec == null) {
            throw new IllegalArgumentException("Integrated tote-to-bag debug install inputs must not be null");
        }

        ToteToBagSubsystem subsystem = new ToteToBagSubsystemBuilder().buildCore(tr, conveyorAppearance, layoutSpec);
        PdcConveyor pdcConveyor = subsystem.getPdcConveyor();
        PcrConveyor pcrConveyor = subsystem.getPcrConveyor();
        List<PrlConveyor> prls = subsystem.getPrls();
        List<PdcDiversionDevice> pdcDiversionDevices = subsystem.getPdcDiversionDevices();

        TipperTrackSection trackSection = new TipperTrackSectionInstaller().install(
                tr,
                objects,
                subsystem.getLayout().resolveTipperEntryLayoutSpec());
        TipperDemoFixtures.DemoTipperFeedSet demoTipperFeedSet = TipperDemoFixtures.createDemoTipperFeedSet(tr, sim, trackSection);
        TipperDemoFixtures.DemoTipperFeed primaryFeed = demoTipperFeedSet.primaryFeed();

        TipperInstallation tipperInstallation = new TipperSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                trackSection,
                primaryFeed.totePayload(),
                demoTipperFeedSet.toteLoadPlanProvider());
        SortingInstallation sortingInstallation = new SortingSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                trackSection.getLayoutSpec(),
                tipperInstallation.getTipperModule().sorterIntakeMountLocalPoint());
        ToteBagReceiver toteBagReceiver = new ToteBagReceiver(
                "debug_output_tote_receiver",
                "debug_output_tote",
                DEBUG_TOTE_BAG_CAPACITY);
        BaggingInstallation baggingInstallation = new BaggingSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                subsystem.getLayout(),
                conveyorAppearance,
                toteBagReceiver);

        TipperToSorterSection tipperToSorterSection = new TipperToSorterSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                tipperInstallation,
                sortingInstallation,
                new SorterOutfeedToPdcReceiveTarget(
                        sortingInstallation.getSortingModule().outfeedPoint(),
                        pdcConveyor,
                        subsystem.getLayout()));

        for (TipperDemoFixtures.DemoTipperFeed additionalFeed : demoTipperFeedSet.additionalFeeds()) {
            tipperToSorterSection.registerToteSource(additionalFeed.totePayload(), additionalFeed.toteLoadPlan());
        }

        ToteLoadPlan demoToteLoadPlan = primaryFeed.toteLoadPlan();
        ToteToBagFlowController flowController = new ToteToBagFlowController(
                demoToteLoadPlan,
                ToteToBagBatchPlan.fromToteLoadPlans(
                        demoTipperFeedSet.demoTipperFeeds().stream()
                                .map(TipperDemoFixtures.DemoTipperFeed::toteLoadPlan)
                                .toList()),
                pdcConveyor,
                pcrConveyor,
                baggingInstallation.getBaggingMachine(),
                new ToteToBagAssignmentPlanner(),
                prls,
                pdcDiversionDevices,
                prlId -> subsystem.getLayout().pdcTransferDurationFor(indexOfPrl(prls, prlId)),
                (prlId, pack) -> subsystem.getLayout().pdcDiversionFrontDistanceFor(indexOfPrl(prls, prlId), pack),
                prlId -> subsystem.getLayout().prlToPcrTransferDurationFor(indexOfPrl(prls, prlId)),
                (prlId, pack) -> subsystem.getLayout().prlToPcrEntryFrontDistanceFor(indexOfPrl(prls, prlId), pack));

        sim.addSimObject(pcrConveyor);
        sim.addController(new DebugToteInjectorController(
                sim,
                objects,
                tipperToSorterSection.getFlowController(),
                demoTipperFeedSet.additionalFeeds().stream()
                        .map(TipperDemoFixtures.DemoTipperFeed::totePayload)
                        .toList()));
        sim.addController(flowController);

        return new IntegratedToteToBagDebugInstallation(
                subsystem,
                trackSection,
                tipperInstallation,
                sortingInstallation,
                baggingInstallation,
                tipperToSorterSection,
                flowController);
    }

    private int indexOfPrl(List<PrlConveyor> prls, String prlId) {
        for (int i = 0; i < prls.size(); i++) {
            if (prls.get(i).getId().equals(prlId)) {
                return i;
            }
        }
        return 0;
    }
}
