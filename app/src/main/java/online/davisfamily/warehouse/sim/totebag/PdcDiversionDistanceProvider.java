package online.davisfamily.warehouse.sim.totebag;

@FunctionalInterface
public interface PdcDiversionDistanceProvider {
    float frontDistanceFor(String targetPrlId, Pack pack);
}
