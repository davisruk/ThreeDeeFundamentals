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
}
