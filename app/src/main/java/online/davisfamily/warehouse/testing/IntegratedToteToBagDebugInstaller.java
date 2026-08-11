package online.davisfamily.warehouse.testing;

import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.SnapshotStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.ThreadedSchedulerEvaluationSource;
import online.davisfamily.warehouse.sim.totebag.assembly.BaggingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.BaggingSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.SortingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.SortingSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSection;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueController;
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
import online.davisfamily.warehouse.testing.scheduler.DspDebugSchedulerFixtureAdapter;
import online.davisfamily.warehouse.testing.scheduler.DebugP2pAdmissionSnapshotFactory;
import online.davisfamily.warehouse.testing.scheduler.QueuedReleaseP2pAdmission;
import online.davisfamily.warehouse.testing.scheduler.QueuedTipperFlowScheduledToteReleaseTarget;
import online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorController;
import online.davisfamily.warehouse.testing.scheduler.ScheduledTipperToteReleaseCatalog;
import online.davisfamily.warehouse.testing.scheduler.SchedulerDebugInspectable;

public class IntegratedToteToBagDebugInstaller {
    private static final int DEBUG_TOTE_BAG_CAPACITY = 25;
    private static final int DEBUG_TIPPER_INPUT_QUEUE_CAPACITY = 1;
    private static final String DEBUG_SERVICE_CENTRE_ID = "SC-DEBUG";
    private static final String DEBUG_SCHEDULER_WORKER_NAME = "dsp-scheduler-worker";

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

        tipperToSorterSection.getFlowController().setToteAdmissionPredicate(flowController::canAdmit);
        TipperInputQueue tipperInputQueue = new TipperInputQueue(
                "debug-p2p-input-queue",
                DEBUG_TIPPER_INPUT_QUEUE_CAPACITY);
        TipperInputQueueController tipperInputQueueController = new TipperInputQueueController(
                tipperInputQueue,
                tipperToSorterSection.getFlowController());
        DebugToteLidController debugToteLidController = new DebugToteLidController(
                demoTipperFeedSet.demoTipperFeeds().stream()
                        .map(feed -> feed.totePayload().getTote())
                        .toList());

        DspDebugSchedulerFixtureAdapter schedulerFixtureAdapter = new DspDebugSchedulerFixtureAdapter();
        StationCapacity p2pCapacity = new StationCapacity(1, 100);
        StationAdmissionSnapshot p2pAdmission = new StationAdmissionSnapshot(
                StationType.P2P,
                p2pCapacity,
                new StationSnapshot(StationType.P2P, 0, 0),
                true,
                "");
        var runtimeState = schedulerFixtureAdapter.createRuntimeState(
                demoTipperFeedSet.additionalFeeds(),
                Map.of(StationType.P2P, p2pAdmission),
                DEBUG_SERVICE_CENTRE_ID);
        ScheduledTipperToteReleaseCatalog releaseCatalog = schedulerFixtureAdapter.createReleaseCatalog(
                demoTipperFeedSet.additionalFeeds());
        DebugP2pAdmissionSnapshotFactory p2pSnapshotFactory = new DebugP2pAdmissionSnapshotFactory(
                "debug-p2p",
                flowController);
        DspReleaseScheduler scheduler = new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of(DEBUG_SERVICE_CENTRE_ID))),
                new DspDependencyEvaluator(),
                new P2pStationAdmissionResolver(
                        new SnapshotStationAdmissionResolver(),
                        new QueuedReleaseP2pAdmission(tipperInputQueue),
                        p2pSnapshotFactory::snapshot,
                        p2pCapacity,
                        p2pSnapshotFactory::stationSnapshot));

        sim.addSimObject(pcrConveyor);
        ThreadedSchedulerEvaluationSource schedulerEvaluationSource = new ThreadedSchedulerEvaluationSource(
                scheduler,
                DEBUG_SCHEDULER_WORKER_NAME);
        ScheduledDebugToteInjectorController scheduledInjectorController = new ScheduledDebugToteInjectorController(
                schedulerEvaluationSource,
                runtimeState,
                releaseCatalog,
                new QueuedTipperFlowScheduledToteReleaseTarget(
                        sim,
                        objects,
                        tipperInputQueue));
        SchedulerDebugInspectable schedulerInspectable = new SchedulerDebugInspectable(scheduledInjectorController);
        RenderableObject schedulerInspectionTarget = RenderableObject.traverseAndExtractAllWithIdStartingWith(
                tipperInstallation.getTipperModule().getAssemblyRenderable(),
                "tipper_slide",
                new java.util.ArrayList<>()).stream()
                .findFirst()
                .orElse(tipperInstallation.getTipperModule().getAssemblyRenderable());
        inspectionRegistry.register(schedulerInspectionTarget, () -> {
            List<String> lines = new java.util.ArrayList<>();
            lines.add("Type: Tipper");
            lines.add("State: " + tipperInstallation.getTipperModule().getTippingMachine().getState());
            lines.add("Remaining packs: " + tipperInstallation.getTipperModule().getTippingMachine().getRemainingPackCount());
            lines.add("Queue: " + tipperInputQueue.snapshot().toteIds().size() + " / " + tipperInputQueue.snapshot().capacity());
            lines.add("Queued totes: " + formatQueueIds(tipperInputQueue.snapshot().toteIds()));
            lines.addAll(schedulerInspectable.describe());
            return List.copyOf(lines);
        });
        sim.addController(scheduledInjectorController);
        sim.addController(tipperInputQueueController);
        sim.addController(debugToteLidController);
        sim.addController(flowController);

        return new IntegratedToteToBagDebugInstallation(
                subsystem,
                trackSection,
                tipperInstallation,
                sortingInstallation,
                baggingInstallation,
                tipperToSorterSection,
                flowController,
                scheduledInjectorController);
    }

    private int indexOfPrl(List<PrlConveyor> prls, String prlId) {
        for (int i = 0; i < prls.size(); i++) {
            if (prls.get(i).getId().equals(prlId)) {
                return i;
            }
        }
        return 0;
    }

    private String formatQueueIds(List<String> toteIds) {
        if (toteIds.isEmpty()) {
            return "none";
        }
        return String.join(", ", toteIds);
    }
}
