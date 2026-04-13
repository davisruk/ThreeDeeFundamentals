package online.davisfamily.warehouse.sim.totebag.layout;

public record MachineAttachmentSpec(
        ToteToBagAttachmentPoint attachmentPoint,
        float offsetX,
        float offsetY,
        float offsetZ,
        float yawRadians) {

    public MachineAttachmentSpec {
        if (attachmentPoint == null) {
            throw new IllegalArgumentException("attachmentPoint must not be null");
        }
    }
}
