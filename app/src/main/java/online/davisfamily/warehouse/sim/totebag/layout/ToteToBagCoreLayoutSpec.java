package online.davisfamily.warehouse.sim.totebag.layout;

public record ToteToBagCoreLayoutSpec(
        float pdcCenterX,
        float conveyorY,
        float pdcZ,
        float pcrZ,
        float prlStartZ,
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
        float pcrMinimumGap,
        float pcrSafetyMargin,
        float conveyorMinimumGap,
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
    }

    public static ToteToBagCoreLayoutSpec debugDefaults() {
        return new ToteToBagCoreLayoutSpec(
                -0.9f,
                0.02f,
                0.2f,
                -2.9f,
                -0.42f,
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
                0.06f,
                0.15f,
                0.06f,
                0.95d,
                0d,
                0.08d,
                0.08d);
    }
}
