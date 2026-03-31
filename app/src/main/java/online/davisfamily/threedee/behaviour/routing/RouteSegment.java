package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.warehouse.rendering.model.tracks.ConnectionClearance;
import online.davisfamily.warehouse.rendering.model.tracks.GuideOpening;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.rendering.model.tracks.TargetGuideOpening;

public final class RouteSegment {
    private final String label;
    private final PathSegment3 geometry;

    private final List<RouteConnection> nextConnections = new ArrayList<>();
    private final List<RouteConnection> previousConnections = new ArrayList<>();
    private final List<TransferZone> transferZones = new ArrayList<>();
    private final List<TargetGuideOpening> targetGuideOpenings = new ArrayList<>();
    private final List<GuideOpening> guideOpenings = new ArrayList<>();
    private final List<ConnectionClearance> connectionClearances = new ArrayList<>();

    private float renderTrimStartDistance;
    private float renderTrimEndDistance;

    public RouteSegment(String label, PathSegment3 geometry) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (geometry == null) {
            throw new IllegalArgumentException("geometry must not be null");
        }

        this.label = label;
        this.geometry = geometry;
    }

    public String getLabel() {
        return label;
    }

    public PathSegment3 getGeometry() {
        return geometry;
    }

    public float length() {
    	return geometry.getTotalLength();
    }
    
    public List<RouteConnection> getNextConnections() {
        return nextConnections;
    }

    public List<RouteConnection> getPreviousConnections() {
        return previousConnections;
    }

    public List<TransferZone> getTransferZones() {
        return transferZones;
    }

    public List<TargetGuideOpening> getTargetGuideOpenings() {
        return Collections.unmodifiableList(targetGuideOpenings);
    }

    public List<GuideOpening> getGuideOpenings() {
        return Collections.unmodifiableList(guideOpenings);
    }

    public List<ConnectionClearance> getConnectionClearances() {
        return Collections.unmodifiableList(connectionClearances);
    }

    public void addTargetGuideOpening(TargetGuideOpening opening) {
        if (opening == null) {
            throw new IllegalArgumentException("opening must not be null");
        }

        float total = geometry.getTotalLength();
        float centre = opening.getCentreDistance();
        if (centre < 0f || centre > total) {
            throw new IllegalArgumentException("target guide opening out of range for segment " + label);
        }

        targetGuideOpenings.add(opening);
    }

    public void addTransferZone(TransferZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }

        float total = geometry.getTotalLength();

        if (zone.getStartDistance() < 0f || zone.getEndDistance() > total) {
            throw new IllegalArgumentException("transfer zone out of range for segment " + label);
        }

        float targetDistance = zone.getTargetStartDistance();
        float targetTotal = zone.getTargetSegment().getGeometry().getTotalLength();
        if (targetDistance < 0f || targetDistance > targetTotal) {
            throw new IllegalArgumentException("targetStartDistance out of range");
        }

        for (TransferZone existing : transferZones) {
            boolean overlaps =
                    zone.getStartDistance() < existing.getEndDistance()
                    && zone.getEndDistance() > existing.getStartDistance();
            if (overlaps) {
                throw new IllegalArgumentException("transfer zones must not overlap");
            }
        }

        transferZones.add(zone);
        transferZones.sort(Comparator.comparing(TransferZone::getStartDistance));
    }

    public void connectTo(RouteSegment next) {
        RouteConnection nextConnection = new RouteConnection(next, 0f);
        this.nextConnections.add(nextConnection);

        RouteConnection previousConnection = new RouteConnection(this, geometry.getTotalLength());
        next.previousConnections.add(previousConnection);
    }

    public void connectTo(RouteSegment next, float nextEntryDistance) {
        RouteConnection nextConnection = new RouteConnection(next, nextEntryDistance);
        this.nextConnections.add(nextConnection);

        RouteConnection previousConnection = new RouteConnection(this, geometry.getTotalLength());
        next.previousConnections.add(previousConnection);
    }

    public RouteConnection connectTo(
            RouteSegment targetSegment,
            float sourceExitDistance,
            float targetEntryDistance,
            GuideSide sourceOpenSide,
            GuideSide targetOpenSide,
            float openingLength) {

        if (targetSegment == null) {
            throw new IllegalArgumentException("targetSegment must not be null");
        }

        if (openingLength <= 0f && sourceOpenSide == null && targetOpenSide == null) {
            throw new IllegalArgumentException("openingLength must be > 0");
        }

        RouteConnection nextConnection = new RouteConnection(targetSegment, targetEntryDistance);
        this.nextConnections.add(nextConnection);

        RouteConnection previousConnection = new RouteConnection(this, sourceExitDistance);
        targetSegment.previousConnections.add(previousConnection);

        float half = openingLength * 0.5f;
        float targetTotal = targetSegment.geometry.getTotalLength();

        if (sourceOpenSide != null) {
            float sourceTotal = this.geometry.getTotalLength();
            float sStart = clamp(sourceExitDistance - half, 0f, sourceTotal);
            float sEnd = clamp(sourceExitDistance + half, 0f, sourceTotal);
            this.addGuideOpening(new GuideOpening(
                    sStart,
                    sEnd,
                    sourceOpenSide,
                    GuideOpening.GuideOpeningType.CONNECTION_SOURCE));
        }

        if (targetOpenSide != null) {
            float tStart = clamp(targetEntryDistance - half, 0f, targetTotal);
            float tEnd = clamp(targetEntryDistance + half, 0f, targetTotal);
            targetSegment.addGuideOpening(new GuideOpening(
                    tStart,
                    tEnd,
                    targetOpenSide,
                    GuideOpening.GuideOpeningType.CONNECTION_TARGET));
        }

        return nextConnection;
    }

    public void setRenderTrimStartDistance(float renderTrimStartDistance) {
        if (renderTrimStartDistance < 0f) {
            throw new IllegalArgumentException("renderTrimStartDistance must be >= 0");
        }
        this.renderTrimStartDistance = renderTrimStartDistance;
    }

    public void setRenderTrimEndDistance(float renderTrimEndDistance) {
        if (renderTrimEndDistance < 0f) {
            throw new IllegalArgumentException("renderTrimEndDistance must be >= 0");
        }
        this.renderTrimEndDistance = renderTrimEndDistance;
    }

    public float getRenderTrimStartDistance() {
        return renderTrimStartDistance;
    }

    public float getRenderTrimEndDistance() {
        return renderTrimEndDistance;
    }

    public void addGuideOpening(GuideOpening opening) {
        if (opening == null) {
            throw new IllegalArgumentException("opening must not be null");
        }

        validateRange(opening.getStartDistance(), opening.getEndDistance(), "guide opening");
        guideOpenings.add(opening);
    }

    public void addConnectionClearance(ConnectionClearance clearance) {
        if (clearance == null) {
            throw new IllegalArgumentException("clearance must not be null");
        }

        validateRange(clearance.getStartDistance(), clearance.getEndDistance(), "connection clearance");
        connectionClearances.add(clearance);
    }

    private void validateRange(float startDistance, float endDistance, String description) {
        float total = geometry.getTotalLength();
        if (startDistance < 0f || endDistance > total) {
            throw new IllegalArgumentException(description + " out of range for segment " + label);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}