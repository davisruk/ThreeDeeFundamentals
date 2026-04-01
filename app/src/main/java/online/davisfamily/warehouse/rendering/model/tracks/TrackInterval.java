package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.warehouse.sim.transfer.TransferZone;

public final class TrackInterval {
    private final float startDistance;
    private final float endDistance;
    private final TrackIntervalType type;
    private final TransferZone transferZone;

    public TrackInterval(float startDistance,
                         float endDistance,
                         TrackIntervalType type,
                         TransferZone transferZone) {
        if (endDistance < startDistance) {
            throw new IllegalArgumentException("endDistance must be >= startDistance");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }

        this.startDistance = startDistance;
        this.endDistance = endDistance;
        this.type = type;
        this.transferZone = transferZone;
    }

    public float getStartDistance() {
        return startDistance;
    }

    public float getEndDistance() {
        return endDistance;
    }

    public float getLength() {
        return endDistance - startDistance;
    }

    public TrackIntervalType getType() {
        return type;
    }

    public TransferZone getTransferZone() {
        return transferZone;
    }
    
    public boolean isTransfer() {
    	return type == TrackIntervalType.TRANSFER;
    }
}