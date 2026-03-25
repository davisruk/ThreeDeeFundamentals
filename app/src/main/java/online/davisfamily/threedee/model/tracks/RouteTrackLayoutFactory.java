package online.davisfamily.threedee.model.tracks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.TransferZone;

public final class RouteTrackLayoutFactory {

    private RouteTrackLayoutFactory() {
    }

    public static RouteTrackLayout create(RouteSegment segment) {
        if (segment == null) {
            throw new IllegalArgumentException("segment must not be null");
        }

        float totalLength = segment.getGeometry().getTotalLength();
        List<TransferZone> zones = new ArrayList<>(segment.getTransferZones());
        zones.sort(Comparator.comparing(TransferZone::getStartDistance));

        List<TrackInterval> intervals = new ArrayList<>();
        float cursor = 0f;

        for (TransferZone zone : zones) {
            if (zone.getStartDistance() > cursor) {
                intervals.add(new TrackInterval(
                        cursor,
                        zone.getStartDistance(),
                        TrackIntervalType.NORMAL,
                        null));
            }

            intervals.add(new TrackInterval(
                    zone.getStartDistance(),
                    zone.getEndDistance(),
                    TrackIntervalType.TRANSFER,
                    zone));

            cursor = zone.getEndDistance();
        }

        if (cursor < totalLength) {
            intervals.add(new TrackInterval(
                    cursor,
                    totalLength,
                    TrackIntervalType.NORMAL,
                    null));
        }

        return new RouteTrackLayout(segment, intervals);
    }

    public static List<TrackSpan> createGuideSpans(RouteTrackLayout layout, TrackSpec spec) {
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }

        float total = layout.getGeometry().getTotalLength();

        float guideStart = layout.getRouteSegment().getPreviousConnections().isEmpty()
                ? 0f
                : spec.connectionGuideCutback;

        float guideEnd = layout.getRouteSegment().getNextConnections().isEmpty()
                ? total
                : total - spec.connectionGuideCutback;

        guideStart = Math.max(0f, guideStart);
        guideEnd = Math.min(total, guideEnd);

        if (guideEnd <= guideStart) {
            return List.of();
        }

        List<TrackSpan> spans = new ArrayList<>();

        for (TrackInterval interval : layout.getIntervals()) {
            if (interval.getType() != TrackIntervalType.NORMAL) {
                continue;
            }

            float start = Math.max(interval.getStartDistance(), guideStart);
            float end = Math.min(interval.getEndDistance(), guideEnd);

            if (end > start) {
                spans.add(new TrackSpan(start, end));
            }
        }

        return spans;
    }
}