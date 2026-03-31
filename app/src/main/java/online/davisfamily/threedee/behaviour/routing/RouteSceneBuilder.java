package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.behaviour.routing.RouteTrackFactory.SpecAndSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.ToggleTransferStrategy;
import online.davisfamily.threedee.behaviour.routing.transfer.TransferDecisionStrategy;
import online.davisfamily.warehouse.rendering.model.tracks.ConnectionClearance;
import online.davisfamily.warehouse.rendering.model.tracks.GuideOpening;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;

public class RouteSceneBuilder {

    private final List<RouteSegment> segments = new ArrayList<>();
    private final List<SpecAndSegment> specsAndSegments = new ArrayList<>();
    private final Map<RouteSegment, TrackSpec> specBySegment = new IdentityHashMap<>();

    public RouteSegment segment(String label, online.davisfamily.threedee.path.PathSegment3 geometry) {
        RouteSegment segment = new RouteSegment(label, geometry);
        segments.add(segment);
        return segment;
    }

    public RouteSceneBuilder renderWith(RouteSegment segment, TrackSpec spec) {
        specsAndSegments.add(new SpecAndSegment(spec, segment));
        specBySegment.put(segment, spec);
        return this;
    }

    public RouteSceneBuilder connectLoop(RouteSegment from, RouteSegment to) {
        from.connectTo(to);
        return this;
    }

    public RouteSceneBuilder connectLoop(RouteSegment from, RouteSegment to, float targetEntryDistance) {
        from.connectTo(to, targetEntryDistance);
        return this;
    }

    public RouteSceneBuilder connectLinkInto(
            RouteSegment linkSegment,
            RouteSegment destinationSegment,
            float targetEntryDistance,
            GuideSide targetOpenSide,
            float openingLength) {

        return connectLinkInto(
                linkSegment,
                destinationSegment,
                targetEntryDistance,
                targetOpenSide,
                openingLength,
                linkSegment.getRenderTrimEndDistance());
    }

    public RouteSceneBuilder connectLinkInto(
            RouteSegment linkSegment,
            RouteSegment destinationSegment,
            float targetEntryDistance,
            GuideSide targetOpenSide) {

        TrackSpec linkSpec = requireSpec(linkSegment);
        return connectLinkInto(
                linkSegment,
                destinationSegment,
                targetEntryDistance,
                targetOpenSide,
                linkSpec.getGuideJoinOpeningLength(),
                linkSegment.getRenderTrimEndDistance());
    }

    public RouteSceneBuilder connectLinkInto(
            RouteSegment linkSegment,
            RouteSegment destinationSegment,
            float targetEntryDistance,
            GuideSide targetOpenSide,
            float openingLength,
            float targetConnectionClearanceLength) {

        float sourceExitDistance = linkSegment.getGeometry().getTotalLength();

        linkSegment.connectTo(
                destinationSegment,
                sourceExitDistance,
                targetEntryDistance,
                null,
                targetOpenSide,
                openingLength
        );

        addCentredConnectionClearance(
                destinationSegment,
                targetEntryDistance,
                targetConnectionClearanceLength,
                targetOpenSide,
                ConnectionClearance.ConnectionClearanceType.CONNECTION_TARGET);

        return this;
    }

    public RouteSceneBuilder addTransferToLink(
            RouteSegment sourceSegment,
            RouteSegment linkSegment,
            float transferCentreDistance,
            float transferLength,
            GuideSide sourceOpenSide,
            GuideSide linkOpenSide,
            boolean initialToggleState) {

        TrackSpec linkSpec = requireSpec(linkSegment);
        return addTransferToLink(
                sourceSegment,
                linkSegment,
                transferCentreDistance,
                transferLength,
                linkSpec.getGuideJoinOpeningLength(),
                sourceOpenSide,
                linkOpenSide,
                initialToggleState,
                linkSegment.getRenderTrimStartDistance());
    }

    public RouteSceneBuilder addTransferToLink(
            RouteSegment sourceSegment,
            RouteSegment linkSegment,
            float transferCentreDistance,
            float transferLength,
            float sourceOpeningLength,
            GuideSide sourceOpenSide,
            GuideSide linkOpenSide,
            boolean initialToggleState) {

        return addTransferToLink(
                sourceSegment,
                linkSegment,
                transferCentreDistance,
                transferLength,
                sourceOpeningLength,
                sourceOpenSide,
                linkOpenSide,
                initialToggleState,
                linkSegment.getRenderTrimStartDistance());
    }

    public RouteSceneBuilder addTransferToLink(
            RouteSegment sourceSegment,
            RouteSegment linkSegment,
            float transferCentreDistance,
            float transferLength,
            float sourceOpeningLength,
            GuideSide sourceOpenSide,
            GuideSide linkOpenSide,
            boolean initialToggleState,
            float sourceConnectionClearanceLength) {

        float transferStart = transferCentreDistance - (transferLength * 0.5f);

        TransferZone zone = new TransferZone(
                transferStart,
                transferLength,
                linkSegment,
                0f,
                sourceOpenSide,
                linkOpenSide,
                new ToggleTransferStrategy(initialToggleState)
        );

        sourceSegment.addTransferZone(zone);

        addCentredGuideOpening(
                sourceSegment,
                transferCentreDistance,
                sourceOpeningLength,
                sourceOpenSide,
                GuideOpening.GuideOpeningType.TRANSFER_SOURCE);

        addCentredConnectionClearance(
                sourceSegment,
                transferCentreDistance,
                sourceConnectionClearanceLength,
                sourceOpenSide,
                ConnectionClearance.ConnectionClearanceType.TRANSFER_SOURCE);

        return this;
    }

    public RouteSceneBuilder addDirectTransfer(
            RouteSegment sourceSegment,
            RouteSegment targetSegment,
            float sourceTransferCentreDistance,
            float openingLength,
            float targetEntryDistance,
            GuideSide sourceOpenSide,
            GuideSide targetOpenSide,
            TransferDecisionStrategy transferStrategy) {

        return addDirectTransfer(
                sourceSegment,
                targetSegment,
                sourceTransferCentreDistance,
                openingLength,
                targetEntryDistance,
                sourceOpenSide,
                targetOpenSide,
                transferStrategy,
                0f,
                0f);
    }

    public RouteSceneBuilder addDirectTransfer(
            RouteSegment sourceSegment,
            RouteSegment targetSegment,
            float sourceTransferCentreDistance,
            float openingLength,
            float targetEntryDistance,
            GuideSide sourceOpenSide,
            GuideSide targetOpenSide,
            TransferDecisionStrategy transferStrategy,
            float sourceConnectionClearanceLength,
            float targetConnectionClearanceLength) {

        if (sourceSegment == null) {
            throw new IllegalArgumentException("sourceSegment must not be null");
        }
        if (targetSegment == null) {
            throw new IllegalArgumentException("targetSegment must not be null");
        }
        if (sourceOpenSide == null) {
            throw new IllegalArgumentException("sourceOpenSide must not be null");
        }
        if (targetOpenSide == null) {
            throw new IllegalArgumentException("targetOpenSide must not be null");
        }
        if (openingLength <= 0f) {
            throw new IllegalArgumentException("openingLength must be > 0");
        }

        float sourceStart = sourceTransferCentreDistance - (openingLength * 0.5f);

        TransferZone zone = new TransferZone(
                sourceStart,
                openingLength,
                targetSegment,
                targetEntryDistance,
                sourceOpenSide,
                targetOpenSide,
                transferStrategy
        );

        sourceSegment.addTransferZone(zone);

        addCentredGuideOpening(
                sourceSegment,
                sourceTransferCentreDistance,
                openingLength,
                sourceOpenSide,
                GuideOpening.GuideOpeningType.TRANSFER_SOURCE);

        addCentredGuideOpening(
                targetSegment,
                targetEntryDistance,
                openingLength,
                targetOpenSide,
                GuideOpening.GuideOpeningType.CONNECTION_TARGET);

        addCentredConnectionClearance(
                sourceSegment,
                sourceTransferCentreDistance,
                sourceConnectionClearanceLength,
                sourceOpenSide,
                ConnectionClearance.ConnectionClearanceType.TRANSFER_SOURCE);

        addCentredConnectionClearance(
                targetSegment,
                targetEntryDistance,
                targetConnectionClearanceLength,
                targetOpenSide,
                ConnectionClearance.ConnectionClearanceType.CONNECTION_TARGET);

        return this;
    }

    public List<RouteSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public List<SpecAndSegment> getSpecsAndSegments() {
        return Collections.unmodifiableList(specsAndSegments);
    }

    private TrackSpec requireSpec(RouteSegment segment) {
        TrackSpec spec = specBySegment.get(segment);
        if (spec == null) {
            throw new IllegalStateException(
                    "No TrackSpec registered for segment " + segment.getLabel() + ". Call renderWith(segment, spec) before creating derived guide joins.");
        }
        return spec;
    }

    private static void addCentredGuideOpening(
            RouteSegment segment,
            float centreDistance,
            float openingLength,
            GuideSide side,
            GuideOpening.GuideOpeningType type) {

        if (openingLength <= 0f || side == null) {
            return;
        }

        float total = segment.getGeometry().getTotalLength();
        float halfOpening = openingLength * 0.5f;
        float openingStart = clamp(centreDistance - halfOpening, 0f, total);
        float openingEnd = clamp(centreDistance + halfOpening, 0f, total);

        segment.addGuideOpening(new GuideOpening(
                openingStart,
                openingEnd,
                side,
                type));
    }

    private static void addCentredConnectionClearance(
            RouteSegment segment,
            float centreDistance,
            float clearanceLength,
            GuideSide side,
            ConnectionClearance.ConnectionClearanceType type) {

        if (clearanceLength <= 0f || side == null) {
            return;
        }

        float total = segment.getGeometry().getTotalLength();
        float halfClearance = clearanceLength * 0.5f;
        float clearanceStart = clamp(centreDistance - halfClearance, 0f, total);
        float clearanceEnd = clamp(centreDistance + halfClearance, 0f, total);

        segment.addConnectionClearance(new ConnectionClearance(
                clearanceStart,
                clearanceEnd,
                side,
                type));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}