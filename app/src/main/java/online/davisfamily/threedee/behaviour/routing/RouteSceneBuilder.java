package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteTrackFactory.SpecAndSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.ToggleTransferStrategy;
import online.davisfamily.threedee.behaviour.routing.transfer.TransferDecisionStrategy;
import online.davisfamily.threedee.model.tracks.GuideOpening;
import online.davisfamily.threedee.model.tracks.GuideOpening.GuideOpeningType;
import online.davisfamily.threedee.model.tracks.GuideSide;
import online.davisfamily.threedee.model.tracks.TrackSpec;

public class RouteSceneBuilder {

	private final List<RouteSegment> segments = new ArrayList<>();
    private final List<SpecAndSegment> specsAndSegments = new ArrayList<>();

    public RouteSegment segment(String label, online.davisfamily.threedee.path.PathSegment3 geometry) {
        RouteSegment segment = new RouteSegment(label, geometry);
        segments.add(segment);
        return segment;
    }

    public RouteSceneBuilder renderWith(RouteSegment segment, TrackSpec spec) {
        specsAndSegments.add(new SpecAndSegment(spec, segment));
        return this;
    }

    /**
     * Ordinary topology connection with no guide openings.
     */
    public RouteSceneBuilder connectLoop(RouteSegment from, RouteSegment to) {
        from.connectTo(to);
        return this;
    }

    /**
     * Ordinary topology connection with no guide openings, entering the target at an arbitrary distance.
     */
    public RouteSceneBuilder connectLoop(RouteSegment from, RouteSegment to, float targetEntryDistance) {
        from.connectTo(to, targetEntryDistance);
        return this;
    }

    /**
     * A linking segment entering a destination segment and creating a guide opening on the destination only.
     */
    public RouteSceneBuilder connectLinkInto(
            RouteSegment linkSegment,
            RouteSegment destinationSegment,
            float targetEntryDistance,
            GuideSide targetOpenSide,
            float openingLength) {

        float sourceExitDistance = linkSegment.getGeometry().getTotalLength();

        linkSegment.connectTo(
                destinationSegment,
                sourceExitDistance,
                targetEntryDistance,
                null,
                targetOpenSide,
                openingLength
        );

        return this;
    }

    /**
     * A transfer from a source segment into a link.
     *
     * Movement length is based on transferLength.
     * Source guide opening width is based on sourceOpeningLength.
     * The opening is centred on transferCentreDistance.
     */
    public RouteSceneBuilder addTransferToLink(
            RouteSegment sourceSegment,
            RouteSegment linkSegment,
            float transferCentreDistance,
            float transferLength,
            float sourceOpeningLength,
            GuideSide sourceOpenSide,
            GuideSide linkOpenSide,
            boolean initialToggleState) {

        float transferStart = transferCentreDistance - (transferLength * 0.5f);

        TransferZone zone = new TransferZone(
                transferStart,
                transferLength,
                linkSegment,
                0.0f,
                sourceOpenSide,
                linkOpenSide,
                new ToggleTransferStrategy(initialToggleState)
        );

        sourceSegment.addTransferZone(zone);

        float halfOpening = sourceOpeningLength * 0.5f;
        float sourceTotal = sourceSegment.getGeometry().getTotalLength();

        float openingStart = clamp(transferCentreDistance - halfOpening, 0f, sourceTotal);
        float openingEnd = clamp(transferCentreDistance + halfOpening, 0f, sourceTotal);

        sourceSegment.addGuideOpening(new GuideOpening(
                openingStart,
                openingEnd,
                sourceOpenSide,
                GuideOpeningType.TRANSFER_SOURCE
        ));

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
            throw new IllegalArgumentException("sourceTransferLength must be > 0");
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

        float sourceTotal = sourceSegment.getGeometry().getTotalLength();
        float targetTotal = targetSegment.getGeometry().getTotalLength();

        float sourceHalf = openingLength * 0.5f;
        float sourceOpeningStart = clamp(sourceTransferCentreDistance - sourceHalf, 0f, sourceTotal);
        float sourceOpeningEnd = clamp(sourceTransferCentreDistance + sourceHalf, 0f, sourceTotal);

        sourceSegment.addGuideOpening(new GuideOpening(
                sourceOpeningStart,
                sourceOpeningEnd,
                sourceOpenSide,
                GuideOpeningType.TRANSFER_SOURCE
        ));

        float targetHalf = openingLength * 0.5f;
        float targetOpeningStart = clamp(targetEntryDistance - targetHalf, 0f, targetTotal);
        float targetOpeningEnd = clamp(targetEntryDistance + targetHalf, 0f, targetTotal);

        targetSegment.addGuideOpening(new GuideOpening(
                targetOpeningStart,
                targetOpeningEnd,
                targetOpenSide,
                GuideOpeningType.CONNECTION_TARGET
        ));

        return this;
    }    

    public List<RouteSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public List<SpecAndSegment> getSpecsAndSegments() {
        return Collections.unmodifiableList(specsAndSegments);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}