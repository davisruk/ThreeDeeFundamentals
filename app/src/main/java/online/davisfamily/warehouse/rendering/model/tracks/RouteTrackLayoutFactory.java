package online.davisfamily.warehouse.rendering.model.tracks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.transfer.TransferZone;

public final class RouteTrackLayoutFactory {

    private RouteTrackLayoutFactory() {
    }

    public static RouteTrackLayout create(RouteSegment segment, WarehouseSegmentMetadata metadata) {
        if (segment == null) {
            throw new IllegalArgumentException("segment must not be null");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }

        float totalLength = segment.getGeometry().getTotalLength();

        float renderStart = metadata.getRenderTrimStartDistance();
        float renderEnd = totalLength - metadata.getRenderTrimEndDistance();

        renderStart = Math.max(0f, renderStart);
        renderEnd = Math.min(totalLength, renderEnd);

        if (renderEnd < renderStart) {
            renderEnd = renderStart;
        }

        List<TransferZone> zones = new ArrayList<>(metadata.getTransferZones());
        zones.sort(Comparator.comparing(TransferZone::getStartDistance));

        List<TrackInterval> intervals = new ArrayList<>();
        float cursor = renderStart;

        for (TransferZone zone : zones) {
            float zoneStart = Math.max(zone.getStartDistance(), renderStart);
            float zoneEnd = Math.min(zone.getEndDistance(), renderEnd);

            if (zoneEnd <= renderStart || zoneStart >= renderEnd) {
                continue;
            }

            if (zoneStart > cursor) {
                intervals.add(new TrackInterval(
                        cursor,
                        zoneStart,
                        TrackIntervalType.NORMAL,
                        null));
            }

            if (zoneEnd > zoneStart) {
                intervals.add(new TrackInterval(
                        zoneStart,
                        zoneEnd,
                        TrackIntervalType.TRANSFER,
                        zone));
            }

            cursor = Math.max(cursor, zoneEnd);
        }

        if (cursor < renderEnd) {
            intervals.add(new TrackInterval(
                    cursor,
                    renderEnd,
                    TrackIntervalType.NORMAL,
                    null));
        }

        return new RouteTrackLayout(segment, metadata, intervals, renderStart, renderEnd);
    }

    public static List<TrackSpan> createGuideSpans(RouteTrackLayout layout, TrackSpec spec, GuideSide side) {
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }

        float guideStart = layout.getRenderStartDistance();
        float guideEnd = layout.getRenderEndDistance();

        if (guideEnd <= guideStart) {
            return List.of();
        }

        List<TrackSpan> blockedSpans = new ArrayList<>();

        for (GuideOpening opening : layout.getMetadata().getGuideOpenings()) {
            if (opening.getSide() == side) {
                blockedSpans.add(new TrackSpan(
                        opening.getStartDistance(),
                        opening.getEndDistance()));
            }
        }

        for (ConnectionClearance clearance : layout.getMetadata().getConnectionClearances()) {
            if (clearance.getSide() == side) {
                blockedSpans.add(new TrackSpan(
                        clearance.getStartDistance(),
                        clearance.getEndDistance()));
            }
        }

        List<TrackSpan> clipped = clipAndMerge(blockedSpans, guideStart, guideEnd);
        return subtractBlockedSpans(guideStart, guideEnd, clipped);
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
                            new TrackSpan(
                                    last.getStartDistance(),
                                    Math.max(last.getEndDistance(), span.getEndDistance())));
                } else {
                    merged.add(span);
                }
            }
        }

        return merged;
    }

    private static List<TrackSpan> subtractBlockedSpans(float guideStart, float guideEnd, List<TrackSpan> blockedSpans) {
        List<TrackSpan> result = new ArrayList<>();
        float cursor = guideStart;

        for (TrackSpan blockedSpan : blockedSpans) {
            if (blockedSpan.getStartDistance() > cursor) {
                result.add(new TrackSpan(cursor, blockedSpan.getStartDistance()));
            }
            cursor = Math.max(cursor, blockedSpan.getEndDistance());
        }

        if (cursor < guideEnd) {
            result.add(new TrackSpan(cursor, guideEnd));
        }

        return result;
    }
}
