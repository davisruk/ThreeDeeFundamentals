package online.davisfamily.warehouse.sim.totebag.layout;

import online.davisfamily.warehouse.sim.totebag.Pack;
import online.davisfamily.warehouse.sim.totebag.PackDimensions;

public class ToteToBagCoreLayout {
    private final ToteToBagCoreLayoutSpec spec;

    public ToteToBagCoreLayout(ToteToBagCoreLayoutSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        this.spec = spec;
    }

    public ToteToBagCoreLayoutSpec getSpec() {
        return spec;
    }

    public float pdcStartX() {
        return spec.pdcCenterX() - (spec.pdcLength() * 0.5f);
    }

    public float pdcEndX() {
        return spec.pdcCenterX() + (spec.pdcLength() * 0.5f);
    }

    public float pcrStartX() {
        return spec.pdcCenterX() - (spec.pcrLength() * 0.5f);
    }

    public float pcrEndX() {
        return spec.pdcCenterX() + (spec.pcrLength() * 0.5f);
    }

    public float prlCenterX(int index) {
        return spec.prlFirstCenterX() + (index * spec.prlSpacing());
    }

    public float pdcDiversionFrontDistanceFor(int prlIndex, Pack pack) {
        float bumperX = clamp(prlCenterX(prlIndex), pdcStartX() + 0.20f, pdcEndX() - 0.20f);
        return (bumperX - pdcStartX()) + (pack.getDimensions().length() * 0.5f);
    }

    public float prlToPcrEntryFrontDistanceFor(int prlIndex, Pack pack) {
        return (prlCenterX(prlIndex) - pcrStartX()) + (pack.getDimensions().length() * 0.5f);
    }

    public double pdcTransferDurationFor(int prlIndex) {
        Pack probe = probePack();
        float targetX = prlCenterX(prlIndex);
        float startX = pdcStartX() + pdcDiversionFrontDistanceFor(prlIndex, probe) - (probe.getDimensions().length() * 0.5f);
        float dx = targetX - startX;
        float dz = spec.prlStartZ() - spec.pdcZ();
        float travelLength = (float) Math.sqrt((dx * dx) + (dz * dz));
        return travelLength / spec.pdcTransferSpeed();
    }

    public double prlToPcrTransferDurationFor(int prlIndex) {
        Pack probe = probePack();
        float prlX = prlCenterX(prlIndex);
        float joinX = pcrStartX() + prlToPcrEntryFrontDistanceFor(prlIndex, probe) - (probe.getDimensions().length() * 0.5f);
        float startZ = spec.prlStartZ() - 1.8f + (probe.getDimensions().length() * 0.5f);
        float dx = joinX - prlX;
        float dz = spec.pcrZ() - startZ;
        float travelLength = (float) Math.sqrt((dx * dx) + (dz * dz));
        return travelLength / spec.prlBeltSpeed();
    }

    public ToteToBagAttachmentPose resolveAttachmentPose(MachineAttachmentSpec spec) {
        ToteToBagAttachmentPose anchor = switch (spec.attachmentPoint()) {
            case PDC_INFEED -> new ToteToBagAttachmentPose(pdcStartX(), this.spec.conveyorY(), this.spec.pdcZ(), 0f);
            case PDC_OUTFEED -> new ToteToBagAttachmentPose(pdcEndX(), this.spec.conveyorY(), this.spec.pdcZ(), 0f);
            case PDC_CENTER -> new ToteToBagAttachmentPose(this.spec.pdcCenterX(), this.spec.conveyorY(), this.spec.pdcZ(), 0f);
            case PCR_INFEED -> new ToteToBagAttachmentPose(pcrStartX(), this.spec.conveyorY(), this.spec.pcrZ(), 0f);
            case PCR_OUTFEED -> new ToteToBagAttachmentPose(pcrEndX(), this.spec.conveyorY(), this.spec.pcrZ(), 0f);
            case PCR_CENTER -> new ToteToBagAttachmentPose(this.spec.pdcCenterX(), this.spec.conveyorY(), this.spec.pcrZ(), 0f);
        };
        return new ToteToBagAttachmentPose(
                anchor.x() + spec.offsetX(),
                anchor.y() + spec.offsetY(),
                anchor.z() + spec.offsetZ(),
                spec.yawRadians());
    }

    private Pack probePack() {
        return new Pack("probe", "probe", new PackDimensions(0.18f, 0.1f, 0.1f));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
