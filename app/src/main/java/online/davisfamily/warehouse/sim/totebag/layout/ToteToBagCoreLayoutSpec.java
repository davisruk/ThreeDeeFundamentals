package online.davisfamily.warehouse.sim.totebag.layout;

public record ToteToBagCoreLayoutSpec(
        float pdcCenterX,
        float pcrCenterX,
        float conveyorY,
        float pdcZ,
        float packY,
        float singlePackConveyorWidth,
        float pdcLength,
        float pcrLength,
        float prlLength,
        int prlCount,
        float prlFirstCenterX,
        float prlSpacing,
        float pdcBeltSpeed,
        float pdcTransferSpeed,
        float prlBeltSpeed,
        float prlIndexDistance,
        float prlGap,
        float pcrMinimumGap,
        float pcrSafetyMargin,
        float conveyorMinimumGap,
        float upstreamPdcExtensionLength,
        float prlToPcrTransferStartZOffset,
        MachineAttachmentSpec tipperEntryMount,
        MachineAttachmentSpec baggerMount,
        float baggerPackDisplayOffsetX,
        float baggerPackDisplayY,
        float baggerPackDisplaySpacingX,
        float completedBagDisplayOffsetX,
        float completedBagDisplayY,
        float completedBagDisplaySpacingX,
        double sorterReleaseIntervalSeconds,
        double diversionArmDelaySeconds,
        double diversionActuationDurationSeconds,
        double diversionResetDurationSeconds) {

    public ToteToBagCoreLayoutSpec {
        if (pdcLength <= 0f || pcrLength <= 0f || prlLength <= 0f) {
            throw new IllegalArgumentException("Conveyor lengths must be > 0");
        }
        if (prlCount <= 0) {
            throw new IllegalArgumentException("prlCount must be > 0");
        }
        if (singlePackConveyorWidth <= 0f) {
            throw new IllegalArgumentException("singlePackConveyorWidth must be > 0");
        }
        if (upstreamPdcExtensionLength < 0f) {
            throw new IllegalArgumentException("upstreamPdcExtensionLength must be >= 0");
        }
        if (prlToPcrTransferStartZOffset <= 0f) {
            throw new IllegalArgumentException("prlToPcrTransferStartZOffset must be > 0");
        }
        if (tipperEntryMount == null) {
            throw new IllegalArgumentException("tipperEntryMount must not be null");
        }
        if (baggerMount == null) {
            throw new IllegalArgumentException("baggerMount must not be null");
        }
    }

    public static ToteToBagCoreLayoutSpec debugDefaults() {
        return new ToteToBagCoreLayoutSpec(
                -0.9f,
                -0.9f,
                0.02f,
                0.2f,
                0.16f,
                0.18f,
                4.6f,
                4.6f,
                2.4f,
                3,
                -2.2f,
                1.45f,
                1.55f,
                1.55f,
                1.80f,
                0.34f,
                0.08f,
                0.06f,
                0.15f,
                0.06f,
                2.6f,
                1.8f,
                new MachineAttachmentSpec(ToteToBagAttachmentPoint.UPSTREAM_MODULE_ROOT, -1.62f, 1.45f, 0.99f, 0f),
                new MachineAttachmentSpec(ToteToBagAttachmentPoint.PCR_OUTFEED, 2.6f, 0.26f, 0f, 0f),
                -1.2f,
                0.48f,
                0.10f,
                0.4f,
                0.16f,
                0.42f,
                0.95d,
                0d,
                0.08d,
                0.08d);
    }

    public static ToteToBagCoreLayoutSpec integratedDebugDefaults() {
        ToteToBagCoreLayoutSpec defaults = debugDefaults();
        return new ToteToBagCoreLayoutSpec(
                defaults.pdcCenterX() - (defaults.upstreamPdcExtensionLength() * 0.5f),
                defaults.pcrCenterX(),
                defaults.conveyorY(),
                defaults.pdcZ(),
                defaults.packY(),
                defaults.singlePackConveyorWidth(),
                defaults.pdcLength() + defaults.upstreamPdcExtensionLength(),
                defaults.pcrLength(),
                defaults.prlLength(),
                defaults.prlCount(),
                defaults.prlFirstCenterX(),
                defaults.prlSpacing(),
                defaults.pdcBeltSpeed(),
                defaults.pdcTransferSpeed(),
                defaults.prlBeltSpeed(),
                defaults.prlIndexDistance(),
                defaults.prlGap(),
                defaults.pcrMinimumGap(),
                defaults.pcrSafetyMargin(),
                defaults.conveyorMinimumGap(),
                defaults.upstreamPdcExtensionLength(),
                defaults.prlToPcrTransferStartZOffset(),
                defaults.tipperEntryMount(),
                defaults.baggerMount(),
                defaults.baggerPackDisplayOffsetX(),
                defaults.baggerPackDisplayY(),
                defaults.baggerPackDisplaySpacingX(),
                defaults.completedBagDisplayOffsetX(),
                defaults.completedBagDisplayY(),
                defaults.completedBagDisplaySpacingX(),
                defaults.sorterReleaseIntervalSeconds(),
                defaults.diversionArmDelaySeconds(),
                defaults.diversionActuationDurationSeconds(),
                defaults.diversionResetDurationSeconds());
    }
}
