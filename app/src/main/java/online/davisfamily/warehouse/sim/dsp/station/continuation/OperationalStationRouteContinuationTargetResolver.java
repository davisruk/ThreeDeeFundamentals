package online.davisfamily.warehouse.sim.dsp.station.continuation;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSelection;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitFactory;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitProfile;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;

/**
 * Production continuation target resolution backed by the real Adapting area policy and the
 * already-committed P2P assignment.
 */
public final class OperationalStationRouteContinuationTargetResolver
        implements StationRouteContinuationTargetResolver {
    private final AdaptingArea adaptingArea;
    private final AdaptingVisitFactory adaptingVisitFactory;

    public OperationalStationRouteContinuationTargetResolver(
            AdaptingArea adaptingArea,
            AdaptingVisitFactory adaptingVisitFactory) {
        if (adaptingArea == null) {
            throw new IllegalArgumentException("adaptingArea must not be null");
        }
        if (adaptingVisitFactory == null) {
            throw new IllegalArgumentException("adaptingVisitFactory must not be null");
        }
        this.adaptingArea = adaptingArea;
        this.adaptingVisitFactory = adaptingVisitFactory;
    }

    @Override
    public StationRouteContinuationDecision resolve(
            StationProcessingDisposition disposition,
            NotionalToteOrder order,
            StationType nextStation) {
        if (disposition == null) {
            throw new IllegalArgumentException("disposition must not be null");
        }
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (nextStation == null) {
            throw new IllegalArgumentException("nextStation must not be null");
        }

        OperationalPhysicalToteIdentity identity =
                disposition.claim().routedTote().launchRequest().identity();
        validateOrderContinuity(identity, order);

        return switch (nextStation) {
            case ADAPTING -> resolveAdapting(disposition, order);
            case P2P -> resolveP2p(disposition, identity, order);
            case THIRD_PARTY, MANUAL, MANUAL_MERGE, OSR, AV02, DISPATCH ->
                    throw new IllegalStateException(
                            "Unsupported continuation target station: " + nextStation);
        };
    }

    private StationRouteContinuationDecision resolveAdapting(
            StationProcessingDisposition disposition,
            NotionalToteOrder order) {
        AdaptingVisitProfile profile = adaptingVisitFactory.profileFor(order);
        if (!order.orderSheetKey().equals(profile.orderSheetKey())
                || !order.serviceCentreId().equals(profile.serviceCentreId())) {
            throw new IllegalStateException(
                    "Adapting visit profile does not match retained order identity");
        }

        AdaptingBenchSelection selection = adaptingArea.selectBenchFor(profile);
        if (selection == null) {
            throw new IllegalStateException("Adapting area returned a null bench selection");
        }
        if (!selection.accepted()) {
            return StationRouteContinuationDecision.defer(selection.blockedReason());
        }
        return StationRouteContinuationDecision.continueTo(
                new OperationalRouteDestination(
                        StationType.ADAPTING,
                        selection.benchId().value()));
    }

    private StationRouteContinuationDecision resolveP2p(
            StationProcessingDisposition disposition,
            OperationalPhysicalToteIdentity identity,
            NotionalToteOrder order) {
        Optional<P2pPhysicalToteAssignment> optionalAssignment =
                disposition.claim().routedTote().launchRequest().p2pAssignment();
        P2pPhysicalToteAssignment assignment = optionalAssignment.orElseThrow(() ->
                new IllegalStateException(
                        "P2P continuation requires an exact committed P2P assignment"));
        if (!assignment.physicalToteId().equals(disposition.physicalToteId())
                || !assignment.physicalToteId().equals(identity.physicalToteId())
                || !assignment.serviceCentreId().equals(identity.serviceCentreId())
                || !assignment.serviceCentreId().equals(order.serviceCentreId())) {
            throw new IllegalStateException(
                    "P2P assignment does not match the continued tote identity");
        }
        return StationRouteContinuationDecision.continueTo(assignment.destination());
    }

    private static void validateOrderContinuity(
            OperationalPhysicalToteIdentity identity,
            NotionalToteOrder order) {
        if (!identity.orderSheetKey().equals(order.orderSheetKey())
                || identity.orderType() != order.orderType()
                || !identity.serviceCentreId().equals(order.serviceCentreId())) {
            throw new IllegalStateException(
                    "Continued order does not match the source-neutral release identity");
        }
    }
}
