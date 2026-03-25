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

    public static List<TrackSpan> createGuideSpans(RouteTrackLayout layout, TrackSpec spec, GuideSide side) {

        float total = layout.getGeometry().getTotalLength();

        List<TrackSpan> openings = new ArrayList<>();

        for (GuideOpening opening : layout.getRouteSegment().getGuideOpenings()) {
            if (opening.getSide() == side) {
                openings.add(new TrackSpan(
                        opening.getStartDistance(),
                        opening.getEndDistance()));
            }
        }

        List<TrackSpan> clipped = clipAndMerge(openings, 0f, total);
        return subtractOpenings(0f, total, clipped);
    }
    
  
    private static List<TrackSpan> clipAndMerge(List<TrackSpan> spans, float min, float max) {
        List<TrackSpan> clipped = new ArrayList<>();

        for (TrackSpan span : spans) {
            float s = Math.max(min, span.getStartDistance());
            float e = Math.min(max, span.getEndDistance());
            if (e > s) {
                clipped.add(new TrackSpan(s, e));
            }
        }

        clipped.sort(Comparator.comparing(TrackSpan::getStartDistance));

        List<TrackSpan> merged = new ArrayList<>();
        for (TrackSpan span : clipped) {
            if (merged.isEmpty()) {
                merged.add(span);
            } else {
                TrackSpan last = merged.get(merged.size() - 1);
                if (span.getStartDistance() <= last.getEndDistance()) {
                    merged.set(merged.size() - 1,
                            new TrackSpan(last.getStartDistance(),
                                    Math.max(last.getEndDistance(), span.getEndDistance())));
                } else {
                    merged.add(span);
                }
            }
        }

        return merged;
    }

    private static List<TrackSpan> subtractOpenings(float guideStart, float guideEnd, List<TrackSpan> openings) {
        List<TrackSpan> result = new ArrayList<>();
        float cursor = guideStart;

        for (TrackSpan opening : openings) {
            if (opening.getStartDistance() > cursor) {
                result.add(new TrackSpan(cursor, opening.getStartDistance()));
            }
            cursor = Math.max(cursor, opening.getEndDistance());
        }

        if (cursor < guideEnd) {
            result.add(new TrackSpan(cursor, guideEnd));
        }

        return result;
    }
}