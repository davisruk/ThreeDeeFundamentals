package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.P2pPlaceholderDurations;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagWorkPlanProvider;

/**
 * Caller-owned dependencies for one headless production P2P line.
 *
 * <p>The config deliberately contains the station boundary and mutable domain
 * owners supplied by the surrounding runtime. The line factory creates the
 * physical tote-to-bag machinery around those owners and never creates
 * rendering or inspection state.</p>
 */
public final class DspHeadlessP2pLineConfig {
    private final P2pLineDefinition lineDefinition;
    private final StationRoutedToteArrivalQueue stationArrivalQueue;
    private final int tipperInputQueueCapacity;
    private final P2pArrivalAdmissionPolicy admissionPolicy;
    private final P2pArrivalRouteBinding routeBinding;
    private final RouteSegment tipperSegment;
    private final P2pTipperPayloadFactory payloadFactory;
    private final StationProcessingCoordinator stationProcessingCoordinator;
    private final ToteToBagWorkPlanProvider workPlanProvider;
    private final BagPlanningResult bagPlanningResult;
    private final OutboundToteAllocator outboundToteAllocator;
    private final TipperToteCompletedListener toteCompletedListener;
    private final P2pPlaceholderDurations durations;

    public DspHeadlessP2pLineConfig(
            P2pLineDefinition lineDefinition,
            StationRoutedToteArrivalQueue stationArrivalQueue,
            int tipperInputQueueCapacity,
            P2pArrivalAdmissionPolicy admissionPolicy,
            P2pArrivalRouteBinding routeBinding,
            RouteSegment tipperSegment,
            P2pTipperPayloadFactory payloadFactory,
            StationProcessingCoordinator stationProcessingCoordinator,
            ToteToBagWorkPlanProvider workPlanProvider,
            BagPlanningResult bagPlanningResult,
            OutboundToteAllocator outboundToteAllocator,
            TipperToteCompletedListener toteCompletedListener,
            P2pPlaceholderDurations durations) {
        requireNonNull(lineDefinition, "lineDefinition");
        requireNonNull(stationArrivalQueue, "stationArrivalQueue");
        if (tipperInputQueueCapacity <= 0) {
            throw new IllegalArgumentException("tipperInputQueueCapacity must be > 0");
        }
        requireNonNull(admissionPolicy, "admissionPolicy");
        requireNonNull(routeBinding, "routeBinding");
        requireNonNull(tipperSegment, "tipperSegment");
        requireNonNull(payloadFactory, "payloadFactory");
        requireNonNull(stationProcessingCoordinator, "stationProcessingCoordinator");
        requireNonNull(workPlanProvider, "workPlanProvider");
        requireNonNull(bagPlanningResult, "bagPlanningResult");
        requireNonNull(outboundToteAllocator, "outboundToteAllocator");
        requireNonNull(toteCompletedListener, "toteCompletedListener");
        requireNonNull(durations, "durations");

        if (!lineDefinition.destination().equals(stationArrivalQueue.destination())) {
            throw new IllegalArgumentException(
                    "stationArrivalQueue destination must match the P2P line definition");
        }
        if (!routeBinding.matchesTipperEntrySegment(tipperSegment)) {
            throw new IllegalArgumentException(
                    "tipperSegment must be the route binding's tipper entry segment");
        }
        if (durations.tipperDischargeDurationSeconds() <= 0d) {
            throw new IllegalArgumentException(
                    "tipperDischargeDurationSeconds must be > 0 for a live tipper flow");
        }

        this.lineDefinition = lineDefinition;
        this.stationArrivalQueue = stationArrivalQueue;
        this.tipperInputQueueCapacity = tipperInputQueueCapacity;
        this.admissionPolicy = admissionPolicy;
        this.routeBinding = routeBinding;
        this.tipperSegment = tipperSegment;
        this.payloadFactory = payloadFactory;
        this.stationProcessingCoordinator = stationProcessingCoordinator;
        this.workPlanProvider = workPlanProvider;
        this.bagPlanningResult = bagPlanningResult;
        this.outboundToteAllocator = outboundToteAllocator;
        this.toteCompletedListener = toteCompletedListener;
        this.durations = durations;
    }

    public P2pLineDefinition lineDefinition() {
        return lineDefinition;
    }

    public StationRoutedToteArrivalQueue stationArrivalQueue() {
        return stationArrivalQueue;
    }

    public int tipperInputQueueCapacity() {
        return tipperInputQueueCapacity;
    }

    public P2pArrivalAdmissionPolicy admissionPolicy() {
        return admissionPolicy;
    }

    public P2pArrivalRouteBinding routeBinding() {
        return routeBinding;
    }

    public RouteSegment tipperSegment() {
        return tipperSegment;
    }

    public P2pTipperPayloadFactory payloadFactory() {
        return payloadFactory;
    }

    public StationProcessingCoordinator stationProcessingCoordinator() {
        return stationProcessingCoordinator;
    }

    public ToteToBagWorkPlanProvider workPlanProvider() {
        return workPlanProvider;
    }

    public BagPlanningResult bagPlanningResult() {
        return bagPlanningResult;
    }

    public OutboundToteAllocator outboundToteAllocator() {
        return outboundToteAllocator;
    }

    public TipperToteCompletedListener toteCompletedListener() {
        return toteCompletedListener;
    }

    public P2pPlaceholderDurations durations() {
        return durations;
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
