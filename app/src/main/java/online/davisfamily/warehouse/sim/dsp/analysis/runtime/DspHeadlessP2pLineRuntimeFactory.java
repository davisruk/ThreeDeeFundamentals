package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.P2pPlaceholderDurations;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationController;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pStationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.ToteToBagP2pLineActivityProbe;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueController;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.control.SorterTipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.transfer.PdcDiversionDistanceProvider;
import online.davisfamily.warehouse.sim.totebag.transfer.PdcTransferDurationProvider;
import online.davisfamily.warehouse.sim.totebag.transfer.PrlToPcrEntryDistanceProvider;
import online.davisfamily.warehouse.sim.totebag.transfer.PrlToPcrTransferDurationProvider;

/** Creates one production-shaped P2P line without renderer or inspection dependencies. */
public final class DspHeadlessP2pLineRuntimeFactory {
    public static final int PRL_COUNT_PER_LINE = 31;

    private static final float TIPPER_STOP_DISTANCE = 0.625f;
    private static final float TIPPER_TIPPED_ANGLE_RADIANS = -1.02f;
    private static final float PDC_LENGTH = 6.2f;
    private static final float PDC_MINIMUM_GAP = 0.015f;
    private static final float PDC_BELT_SPEED = 1.55f;
    private static final float PRL_LENGTH = 1.8f;
    private static final float PRL_MINIMUM_GAP = 0.015f;
    private static final float PRL_BELT_SPEED = 1.80f;
    private static final float PRL_INDEX_DISTANCE = 0.10f;
    private static final float PCR_LENGTH = 6.1f;
    private static final float PCR_MINIMUM_GAP = 0.015f;
    private static final float PCR_SAFETY_MARGIN = 0.15f;
    private static final double PCR_TRAVEL_DURATION_SECONDS = 2.0d;
    private static final double DIVERTER_ACTUATION_DURATION_SECONDS = 0.08d;
    private static final double DIVERTER_RESET_DURATION_SECONDS = 0.08d;
    private static final BagSpec STANDARD_BAG_SPEC = new BagSpec(0.090f, 0.160f, 0.060f);

    public DspHeadlessP2pLineRuntime create(DspHeadlessP2pLineConfig config) {
        return create(new SimulationWorld(), config);
    }

    public DspHeadlessP2pLineRuntime create(
            DspHeadlessP2pLineConfig config,
            SimulationWorld simulationWorld) {
        return create(simulationWorld, config);
    }

    public DspHeadlessP2pLineRuntime create(
            SimulationWorld simulationWorld,
            DspHeadlessP2pLineConfig config) {
        if (simulationWorld == null) {
            throw new IllegalArgumentException("simulationWorld must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        BuiltLine built = build(config);

        // Registration is intentionally the final phase. A malformed dependency must not leave
        // a partially installed line in the caller's world.
        simulationWorld.addSimObject(built.tippingMachine());
        simulationWorld.addSimObject(built.sortingMachine());
        simulationWorld.addSimObject(built.pcrConveyor());
        simulationWorld.addSimObject(built.baggingMachine());
        simulationWorld.addController(built.tipperFlowController());
        simulationWorld.addController(built.toteToBagFlowController());
        simulationWorld.addController(built.tipperInputQueueController());
        simulationWorld.addController(built.outboundToteAllocationController());

        return new DspHeadlessP2pLineRuntime(
                simulationWorld,
                config,
                config.stationArrivalQueue(),
                built.tipperInputQueue(),
                built.arrivalTarget(),
                built.stationProcessingTarget(),
                built.tippingMachine(),
                built.sortingMachine(),
                built.pcrConveyor(),
                built.baggingMachine(),
                built.bagReceiver(),
                built.prlConveyors(),
                built.pdcDiversionDevices(),
                built.sorterDownstreamFlow(),
                built.tipperFlowController(),
                built.toteToBagFlowController(),
                built.tipperInputQueueController(),
                built.outboundToteAllocationController(),
                built.activityProbe());
    }

    private BuiltLine build(DspHeadlessP2pLineConfig config) {
        String lineId = config.lineDefinition().lineId().value();
        P2pPlaceholderDurations durations = config.durations();

        TipperInputQueue tipperInputQueue = new TipperInputQueue(
                lineId + "-tipper-input",
                config.tipperInputQueueCapacity());
        P2pTipperArrivalTarget arrivalTarget = new P2pTipperArrivalTarget(
                config.lineDefinition().destination(),
                tipperInputQueue);
        P2pStationProcessingTarget stationProcessingTarget = new P2pStationProcessingTarget(
                config.admissionPolicy(),
                config.routeBinding(),
                config.payloadFactory(),
                arrivalTarget,
                config.stationProcessingCoordinator());

        TippingMachine tippingMachine = new TippingMachine(
                lineId + "-tipper",
                durations.tippingDurationSeconds(),
                durations.tipperEmitIntervalSeconds(),
                durations.tipperResetDurationSeconds());
        SortingMachine sortingMachine = new SortingMachine(
                lineId + "-sorter",
                durations.sorterReleaseIntervalSeconds());
        PdcConveyor pdcConveyor = new PdcConveyor(
                lineId + "-pdc",
                new ConveyorOccupancyModel(PDC_LENGTH, PDC_MINIMUM_GAP, 0f),
                PDC_BELT_SPEED);

        List<PrlConveyor> prls = new ArrayList<>(PRL_COUNT_PER_LINE);
        List<PdcDiversionDevice> diversionDevices = new ArrayList<>(PRL_COUNT_PER_LINE);
        for (int index = 1; index <= PRL_COUNT_PER_LINE; index++) {
            String prlId = lineId + "-prl-" + index;
            prls.add(new PrlConveyor(
                    prlId,
                    PRL_INDEX_DISTANCE,
                    new ConveyorOccupancyModel(PRL_LENGTH, PRL_MINIMUM_GAP, 0f),
                    PRL_BELT_SPEED));
            diversionDevices.add(new PdcDiversionDevice(
                    lineId + "-pdc-diverter-" + index,
                    prlId,
                    0d,
                    DIVERTER_ACTUATION_DURATION_SECONDS,
                    DIVERTER_RESET_DURATION_SECONDS));
        }

        PcrConveyor pcrConveyor = new PcrConveyor(
                lineId + "-pcr",
                new ConveyorOccupancyModel(PCR_LENGTH, PCR_MINIMUM_GAP, PCR_SAFETY_MARGIN),
                PCR_TRAVEL_DURATION_SECONDS);
        StoredBagReceiver bagReceiver = new StoredBagReceiver(lineId + "-bag-receiver");
        BaggingMachine baggingMachine = new BaggingMachine(
                lineId + "-bagger",
                STANDARD_BAG_SPEC,
                durations.bagReceivingDurationSeconds(),
                durations.bagDroppingDurationSeconds(),
                durations.bagSealingDurationSeconds(),
                durations.bagDischargingDurationSeconds(),
                bagReceiver);

        SorterTipperDownstreamFlow sorterDownstreamFlow = new SorterTipperDownstreamFlow(
                sortingMachine,
                newHeadlessPdcReceiveTarget(lineId, pdcConveyor));
        ToteTrackTipperFlowController tipperFlowController = new ToteTrackTipperFlowController(
                arrivalTarget,
                config.tipperSegment(),
                TIPPER_STOP_DISTANCE,
                TIPPER_TIPPED_ANGLE_RADIANS,
                tippingMachine,
                sorterDownstreamFlow,
                durations.tipperDischargeDurationSeconds(),
                config.toteCompletedListener());
        ToteToBagFlowController toteToBagFlowController = new ToteToBagFlowController(
                config.workPlanProvider(),
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                prls,
                diversionDevices,
                pdcTransferDurationProvider(durations),
                pdcDiversionDistanceProvider(),
                prlToPcrTransferDurationProvider(durations),
                prlToPcrEntryDistanceProvider());
        tipperFlowController.setToteAdmissionPredicate(toteToBagFlowController::canAdmit);

        TipperInputQueueController tipperInputQueueController = new TipperInputQueueController(
                tipperInputQueue,
                tipperFlowController);
        OutboundToteAllocationController outboundToteAllocationController =
                new OutboundToteAllocationController(
                        config.lineDefinition().lineId(),
                        bagReceiver,
                        config.bagPlanningResult(),
                        config.outboundToteAllocator());
        ToteToBagP2pLineActivityProbe activityProbe = new ToteToBagP2pLineActivityProbe(
                config.lineDefinition(),
                config.stationArrivalQueue(),
                tipperInputQueue,
                tipperFlowController,
                sortingMachine,
                sorterDownstreamFlow,
                toteToBagFlowController,
                pcrConveyor,
                baggingMachine,
                bagReceiver,
                config.outboundToteAllocator()::snapshot);

        return new BuiltLine(
                tipperInputQueue,
                arrivalTarget,
                stationProcessingTarget,
                tippingMachine,
                sortingMachine,
                pcrConveyor,
                baggingMachine,
                bagReceiver,
                prls,
                diversionDevices,
                sorterDownstreamFlow,
                tipperFlowController,
                toteToBagFlowController,
                tipperInputQueueController,
                outboundToteAllocationController,
                activityProbe);
    }

    private static PackReceiveTarget newHeadlessPdcReceiveTarget(
            String lineId,
            PdcConveyor pdcConveyor) {
        PackHandoffPoint handoffPoint = new PackHandoffPoint(
                lineId + "-sorter-pdc-handoff",
                new Vec3(),
                0f);
        return new PackReceiveTarget() {
            @Override
            public PackHandoffPoint handoffPoint() {
                return handoffPoint;
            }

            @Override
            public boolean canAccept(Pack pack) {
                return pack != null && pdcConveyor.canAcceptIncomingPack(pack);
            }

            @Override
            public void accept(Pack pack) {
                if (!canAccept(pack)) {
                    throw new IllegalStateException("Headless PDC cannot accept sorter pack");
                }
                pdcConveyor.acceptIncomingPack(pack);
            }
        };
    }

    private static PdcTransferDurationProvider pdcTransferDurationProvider(
            P2pPlaceholderDurations durations) {
        return ignoredPrlId -> durations.pdcTransferDurationSeconds();
    }

    private static PdcDiversionDistanceProvider pdcDiversionDistanceProvider() {
        return (prlId, pack) -> {
            int index = prlIndex(prlId);
            float spacing = (PDC_LENGTH - 0.8f) / (PRL_COUNT_PER_LINE - 1);
            float targetCenter = 0.4f + ((index - 1) * spacing);
            return targetCenter + (pack.getDimensions().length() * 0.5f);
        };
    }

    private static PrlToPcrTransferDurationProvider prlToPcrTransferDurationProvider(
            P2pPlaceholderDurations durations) {
        return ignoredPrlId -> durations.prlToPcrTransferDurationSeconds();
    }

    private static PrlToPcrEntryDistanceProvider prlToPcrEntryDistanceProvider() {
        return (ignoredPrlId, pack) -> pack.getDimensions().length();
    }

    private static int prlIndex(String prlId) {
        int separator = prlId.lastIndexOf("-prl-");
        if (separator < 0) {
            throw new IllegalArgumentException("Unexpected headless PRL ID: " + prlId);
        }
        return Integer.parseInt(prlId.substring(separator + "-prl-".length()));
    }

    private record BuiltLine(
            TipperInputQueue tipperInputQueue,
            P2pTipperArrivalTarget arrivalTarget,
            P2pStationProcessingTarget stationProcessingTarget,
            TippingMachine tippingMachine,
            SortingMachine sortingMachine,
            PcrConveyor pcrConveyor,
            BaggingMachine baggingMachine,
            StoredBagReceiver bagReceiver,
            List<PrlConveyor> prlConveyors,
            List<PdcDiversionDevice> pdcDiversionDevices,
            SorterTipperDownstreamFlow sorterDownstreamFlow,
            ToteTrackTipperFlowController tipperFlowController,
            ToteToBagFlowController toteToBagFlowController,
            TipperInputQueueController tipperInputQueueController,
            OutboundToteAllocationController outboundToteAllocationController,
            ToteToBagP2pLineActivityProbe activityProbe) {
    }
}
