package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;

public class TrackSpec {
    public final ToteEnvelope toteEnvelope;
    public final TrackDriveType driveType;

    // running surface
    public final float sideClearance;
    public final float deckThickness;
    public final float deckTopY;

    // side guides
    public final boolean includeGuides;
    public final float guideHeight;
    public final float guideThickness;
    public final float guideGap;
    public final float connectionGuideCutback;
    public final float targetGuideOpeningLength;

    // rollers
    public final boolean includeRollers;
    public final float rollerPitch;
    public final float rollerWidthInset;
    public final float rollerHeight;
    public final float rollerDepthAlongPath;

    // conveyor
    public final float conveyorWidthInset;
    public final float conveyorBeltThickness;
    public final float conveyorReturnDepth;
    public final float conveyorMarkerPitch;
    public final float conveyorMarkerLength;
    public final float conveyorMarkerThickness;

    // transfer zone rendering
    public final boolean suppressRollersInTransferZones;
    public final boolean suppressGuidesInTransferZones;

    // tessellation
    public float sampleStep;

    public TrackSpec(ToteEnvelope toteEnvelope, float sideClearance, float deckThickness, float deckTopY,
            boolean includeGuides, float guideHeight, float guideThickness, float guideGap,
            float connectionGuideCutback, float targetGuideOpeningLength, boolean includeRollers,
            float rollerPitch, float rollerWidthInset, float rollerHeight, float rollerDepthAlongPath,
            float sampleStep) {
        this(toteEnvelope,
                sideClearance,
                deckThickness,
                deckTopY,
                includeGuides,
                guideHeight,
                guideThickness,
                guideGap,
                connectionGuideCutback,
                targetGuideOpeningLength,
                TrackDriveType.ROLLER,
                includeRollers,
                rollerPitch,
                rollerWidthInset,
                rollerHeight,
                rollerDepthAlongPath,
                0.010f,
                0.012f,
                0.080f,
                0.220f,
                0.050f,
                0.004f,
                true,
                false,
                sampleStep);
    }

    public TrackSpec(
            ToteEnvelope toteEnvelope,
            float sideClearance,
            float deckThickness,
            float deckTopY,
            boolean includeGuides,
            float guideHeight,
            float guideThickness,
            float guideGap,
            float connectionGuideCutback,
            float targetGuideOpeningLength,
            TrackDriveType driveType,
            boolean includeRollers,
            float rollerPitch,
            float rollerWidthInset,
            float rollerHeight,
            float rollerDepthAlongPath,
            float conveyorWidthInset,
            float conveyorBeltThickness,
            float conveyorReturnDepth,
            float conveyorMarkerPitch,
            float conveyorMarkerLength,
            float conveyorMarkerThickness,
            boolean suppressRollersInTransferZones,
            boolean suppressGuidesInTransferZones,
            float sampleStep) {

        this.toteEnvelope = toteEnvelope;
        this.driveType = driveType != null ? driveType : TrackDriveType.NONE;
        this.sideClearance = sideClearance;
        this.deckThickness = deckThickness;
        this.deckTopY = deckTopY;
        this.includeGuides = includeGuides;
        this.guideHeight = guideHeight;
        this.guideThickness = guideThickness;
        this.guideGap = guideGap;
        this.connectionGuideCutback = connectionGuideCutback;
        this.includeRollers = includeRollers;
        this.rollerPitch = rollerPitch;
        this.rollerWidthInset = rollerWidthInset;
        this.rollerHeight = rollerHeight;
        this.rollerDepthAlongPath = rollerDepthAlongPath;
        this.conveyorWidthInset = conveyorWidthInset;
        this.conveyorBeltThickness = conveyorBeltThickness;
        this.conveyorReturnDepth = conveyorReturnDepth;
        this.conveyorMarkerPitch = conveyorMarkerPitch;
        this.conveyorMarkerLength = conveyorMarkerLength;
        this.conveyorMarkerThickness = conveyorMarkerThickness;
        this.suppressRollersInTransferZones = suppressRollersInTransferZones;
        this.suppressGuidesInTransferZones = suppressGuidesInTransferZones;
        this.sampleStep = sampleStep;
        this.targetGuideOpeningLength = targetGuideOpeningLength;
    }

    public static TrackSpec conveyor(
            ToteEnvelope toteEnvelope,
            float sideClearance,
            float deckThickness,
            float deckTopY,
            boolean includeGuides,
            float guideHeight,
            float guideThickness,
            float guideGap,
            float connectionGuideCutback,
            float targetGuideOpeningLength,
            float conveyorWidthInset,
            float conveyorBeltThickness,
            float conveyorReturnDepth,
            float conveyorMarkerPitch,
            float conveyorMarkerLength,
            float conveyorMarkerThickness,
            boolean suppressGuidesInTransferZones,
            float sampleStep) {

        return new TrackSpec(
                toteEnvelope,
                sideClearance,
                deckThickness,
                deckTopY,
                includeGuides,
                guideHeight,
                guideThickness,
                guideGap,
                connectionGuideCutback,
                targetGuideOpeningLength,
                TrackDriveType.CONVEYOR,
                false,
                0.080f,
                0.010f,
                0.025f,
                0.018f,
                conveyorWidthInset,
                conveyorBeltThickness,
                conveyorReturnDepth,
                conveyorMarkerPitch,
                conveyorMarkerLength,
                conveyorMarkerThickness,
                true,
                suppressGuidesInTransferZones,
                sampleStep);
    }

    public float getRunningWidth() {
        return toteEnvelope.bottomWidth + (2f * sideClearance);
    }

    public float getGuideInnerWidth() {
        return getRunningWidth() + (2f * guideGap);
    }

    public float getOverallWidth() {
        if (!includeGuides) return getRunningWidth();
        return getGuideInnerWidth() + (2f * guideThickness);
    }

    /**
     * Width that a neighbouring guide opening should reserve when joining into
     * this segment's guide channel. This reaches the inner face of the receiving
     * guide bodies, not just the deck/running width.
     */
    public float getGuideJoinOpeningLength() {
        return includeGuides ? getGuideInnerWidth() : getRunningWidth();
    }

    public boolean hasRollerDrive() {
        return driveType == TrackDriveType.ROLLER && includeRollers;
    }

    public boolean hasConveyorDrive() {
        return driveType == TrackDriveType.CONVEYOR;
    }

    public float getLoadSurfaceHeight() {
        if (hasConveyorDrive()) {
            return conveyorBeltThickness;
        }
        if (hasRollerDrive()) {
            return rollerHeight;
        }
        return 0f;
    }

    public float getLoadSurfaceY() {
        return deckTopY + getLoadSurfaceHeight();
    }
}
