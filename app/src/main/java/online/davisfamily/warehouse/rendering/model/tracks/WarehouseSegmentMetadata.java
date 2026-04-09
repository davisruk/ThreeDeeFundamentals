package online.davisfamily.warehouse.rendering.model.tracks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.TransferZone;

public class WarehouseSegmentMetadata {
    private final List<TransferZone> transferZones = new ArrayList<>();
    private final List<GuideOpening> guideOpenings = new ArrayList<>();
    private final List<ConnectionClearance> connectionClearances = new ArrayList<>();

    private float renderTrimStartDistance;
    private float renderTrimEndDistance;

    public List<TransferZone> getTransferZones() {
        return Collections.unmodifiableList(transferZones);
    }

    public List<GuideOpening> getGuideOpenings() {
        return Collections.unmodifiableList(guideOpenings);
    }

    public List<ConnectionClearance> getConnectionClearances() {
        return Collections.unmodifiableList(connectionClearances);
    }

    public void addTransferZone(RouteSegment owner, TransferZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }

        float total = owner.getGeometry().getTotalLength();
        if (zone.getStartDistance() < 0f || zone.getEndDistance() > total) {
            throw new IllegalArgumentException("transfer zone out of range for segment " + owner.getLabel());
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

    public void addGuideOpening(RouteSegment owner, GuideOpening opening) {
        if (opening == null) {
            throw new IllegalArgumentException("opening must not be null");
        }

        validateRange(owner, opening.getStartDistance(), opening.getEndDistance(), "guide opening");
        guideOpenings.add(opening);
    }

    public void addConnectionClearance(RouteSegment owner, ConnectionClearance clearance) {
        if (clearance == null) {
            throw new IllegalArgumentException("clearance must not be null");
        }

        validateRange(owner, clearance.getStartDistance(), clearance.getEndDistance(), "connection clearance");
        connectionClearances.add(clearance);
    }

    private void validateRange(RouteSegment owner, float startDistance, float endDistance, String description) {
        float total = owner.getGeometry().getTotalLength();
        if (startDistance < 0f || endDistance > total) {
            throw new IllegalArgumentException(description + " out of range for segment " + owner.getLabel());
        }
    }
}
