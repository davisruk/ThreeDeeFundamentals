package online.davisfamily.warehouse.sim.totebag;

@FunctionalInterface
public interface PrlToPcrEntryDistanceProvider {
    float frontDistanceFor(String prlId, Pack pack);
}
