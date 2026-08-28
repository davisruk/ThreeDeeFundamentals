package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.time.Duration;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingAdmissionDecision;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingClaim;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingTarget;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

/**
 * Station-processing target for the arrival boundary of one exact P2P tipper.
 *
 * <p>The target keeps P2P's existing admission policy and tipper-input boundary together while
 * handing station ownership to the shared processing coordinator. It deliberately does not call
 * the downstream tipper flow directly.</p>
 */
public final class P2pStationProcessingTarget implements StationProcessingTarget {
    private final P2pArrivalAdmissionPolicy admissionPolicy;
    private final P2pArrivalRouteBinding routeBinding;
    private final P2pTipperPayloadFactory payloadFactory;
    private final P2pTipperArrivalTarget target;
    private final StationProcessingCoordinator coordinator;
    private String lastPolicyReason = "";

    public P2pStationProcessingTarget(
            P2pArrivalAdmissionPolicy admissionPolicy,
            P2pArrivalRouteBinding routeBinding,
            P2pTipperPayloadFactory payloadFactory,
            P2pTipperArrivalTarget target,
            StationProcessingCoordinator coordinator) {
        if (admissionPolicy == null) {
            throw new IllegalArgumentException("admissionPolicy must not be null");
        }
        if (routeBinding == null) {
            throw new IllegalArgumentException("routeBinding must not be null");
        }
        if (payloadFactory == null) {
            throw new IllegalArgumentException("payloadFactory must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (target.destination().stationType() != StationType.P2P) {
            throw new IllegalArgumentException("target must identify a P2P destination");
        }
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.admissionPolicy = admissionPolicy;
        this.routeBinding = routeBinding;
        this.payloadFactory = payloadFactory;
        this.target = target;
        this.coordinator = coordinator;
    }

    @Override
    public OperationalRouteDestination destination() {
        return target.destination();
    }

    @Override
    public StationProcessingCoordinator coordinator() {
        return coordinator;
    }

    @Override
    public StationProcessingAdmissionDecision evaluate(RoutedPhysicalTote routedTote) {
        requireTerminalRoutePosition(routedTote);
        if (!destination().equals(routedTote.destination())) {
            throw new IllegalStateException(
                    "Routed tote destination does not match the configured P2P target");
        }
        P2pArrivalAdmissionRequest request = P2pArrivalAdmissionRequest.from(routedTote);
        P2pArrivalAdmissionDecision decision = admissionPolicy.evaluate(request);
        if (decision == null) {
            throw new IllegalStateException("P2P arrival admission policy returned null");
        }
        if (!decision.permitted()) {
            lastPolicyReason = decision.reason();
            return StationProcessingAdmissionDecision.defer(decision.reason());
        }

        lastPolicyReason = "";
        if (!target.canAccept()) {
            return StationProcessingAdmissionDecision.defer(
                    P2pArrivalConsumerController.TIPPER_INPUT_FULL);
        }
        coordinator.validateCanEvaluateClaim(routedTote);
        return StationProcessingAdmissionDecision.permit();
    }

    @Override
    public StationProcessingClaim accept(
            RoutedPhysicalTote routedTote,
            Duration claimedAt) {
        StationProcessingAdmissionDecision decision = evaluate(routedTote);
        if (!decision.permitted()) {
            throw new IllegalStateException(
                    "P2P station processing admission was deferred: " + decision.reason());
        }
        coordinator.validateCanClaim(routedTote, claimedAt);

        TipperTotePayload payload = payloadFactory.create(routedTote);
        if (payload == null) {
            throw new IllegalStateException("P2P tipper payload factory returned null");
        }
        target.accept(routedTote, payload);
        return coordinator.claim(routedTote, claimedAt);
    }

    /**
     * Returns the policy reason from the most recent deferred policy evaluation. This is package
     * private so the compatibility controller can retain its historical diagnostic snapshot
     * without making policy diagnostics part of the generic target contract.
     */
    String lastPolicyReason() {
        return lastPolicyReason;
    }

    private void requireTerminalRoutePosition(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        RouteFollower follower = routedTote.tote().getRouteFollower();
        if (!routeBinding.matchesTerminalSegment(follower.getCurrentSegment())) {
            throw new IllegalStateException(
                    "P2P arrival tote is not on the configured terminal segment: "
                            + routedTote.physicalToteId().value());
        }
    }
}
