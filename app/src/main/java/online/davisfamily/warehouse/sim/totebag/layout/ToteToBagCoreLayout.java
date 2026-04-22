package online.davisfamily.warehouse.sim.totebag.layout;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

public class ToteToBagCoreLayout {
    private static final float PRL_LANE_END_INSET = 0.30f;

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
        return spec.pcrCenterX() - (spec.pcrLength() * 0.5f);
    }

    public float pcrEndX() {
        return spec.pcrCenterX() + (spec.pcrLength() * 0.5f);
    }

    public float prlCenterX(int index) {
        return spec.prlFirstCenterX() + (index * spec.prlSpacing());
    }

    public float prlRenderCenterZ() {
        return prlInfeedEndZ() - (spec.prlLength() * 0.5f);
    }

    public float prlStartZ() {
        return prlInfeedEndZ() - PRL_LANE_END_INSET;
    }

    public float pcrZ() {
        return prlOutfeedEndZ() - spec.prlGap() - (spec.singlePackConveyorWidth() * 0.5f);
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
        float dz = prlStartZ() - spec.pdcZ();
        float travelLength = (float) Math.sqrt((dx * dx) + (dz * dz));
        return travelLength / spec.pdcTransferSpeed();
    }

    public double prlToPcrTransferDurationFor(int prlIndex) {
        Pack probe = probePack();
        float prlX = prlCenterX(prlIndex);
        float joinX = pcrStartX() + prlToPcrEntryFrontDistanceFor(prlIndex, probe) - (probe.getDimensions().length() * 0.5f);
        float startZ = prlToPcrTransferStartZ(probe);
        float dx = joinX - prlX;
        float dz = pcrZ() - startZ;
        float travelLength = (float) Math.sqrt((dx * dx) + (dz * dz));
        return travelLength / spec.prlBeltSpeed();
    }

    public ToteToBagAttachmentPose resolveAttachmentPose(MachineAttachmentSpec spec) {
        ToteToBagAttachmentPose anchor = switch (spec.attachmentPoint()) {
            case UPSTREAM_MODULE_ROOT -> new ToteToBagAttachmentPose(pdcStartX(), 0f, this.spec.pdcZ(), 0f);
            case PDC_INFEED -> new ToteToBagAttachmentPose(pdcStartX(), this.spec.conveyorY(), this.spec.pdcZ(), 0f);
            case PDC_OUTFEED -> new ToteToBagAttachmentPose(pdcEndX(), this.spec.conveyorY(), this.spec.pdcZ(), 0f);
            case PDC_CENTER -> new ToteToBagAttachmentPose(this.spec.pdcCenterX(), this.spec.conveyorY(), this.spec.pdcZ(), 0f);
            case PCR_INFEED -> new ToteToBagAttachmentPose(pcrStartX(), this.spec.conveyorY(), pcrZ(), 0f);
            case PCR_OUTFEED -> new ToteToBagAttachmentPose(pcrEndX(), this.spec.conveyorY(), pcrZ(), 0f);
            case PCR_CENTER -> new ToteToBagAttachmentPose(this.spec.pcrCenterX(), this.spec.conveyorY(), pcrZ(), 0f);
        };
        return new ToteToBagAttachmentPose(
                anchor.x() + spec.offsetX(),
                anchor.y() + spec.offsetY(),
                anchor.z() + spec.offsetZ(),
                spec.yawRadians());
    }

    public TipperEntryLayoutSpec resolveTipperEntryLayoutSpec() {
        ToteToBagAttachmentPose pose = resolveAttachmentPose(spec.tipperEntryMount());
        return new TipperEntryLayoutSpec(
                new Vec3(pose.x(), pose.y(), pose.z()),
                pose.yawRadians());
    }

    public float prlToPcrTransferStartZ(Pack pack) {
        return prlStartZ() - spec.prlToPcrTransferStartZOffset() + (pack.getDimensions().length() * 0.5f);
    }

    public float baggerPackDisplayX(int packIndex) {
        return resolveAttachmentPose(spec.baggerMount()).x() + spec.baggerPackDisplayOffsetX()
                + (packIndex * spec.baggerPackDisplaySpacingX());
    }

    public float baggerPackDisplayY() {
        return spec.baggerPackDisplayY();
    }

    public float completedBagDisplayX(int bagIndex) {
        return resolveAttachmentPose(spec.baggerMount()).x() + spec.completedBagDisplayOffsetX()
                + (bagIndex * spec.completedBagDisplaySpacingX());
    }

    public float completedBagDisplayY() {
        return spec.completedBagDisplayY();
    }

    private Pack probePack() {
        return new Pack("probe", "probe", new PackDimensions(0.18f, 0.1f, 0.1f));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float prlInfeedEndZ() {
        return spec.pdcZ() - (spec.singlePackConveyorWidth() * 0.5f) - spec.prlGap();
    }

    private float prlOutfeedEndZ() {
        return prlInfeedEndZ() - spec.prlLength();
    }
}
