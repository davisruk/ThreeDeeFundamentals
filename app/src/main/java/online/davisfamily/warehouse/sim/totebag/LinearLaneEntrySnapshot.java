package online.davisfamily.warehouse.sim.totebag;

public record LinearLaneEntrySnapshot(Pack pack, float frontDistance) {

    public float rearDistance() {
        return frontDistance - pack.getDimensions().length();
    }
}
