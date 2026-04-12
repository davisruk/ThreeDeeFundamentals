package online.davisfamily.warehouse.sim.totebag;

@FunctionalInterface
public interface PdcTransferDurationProvider {
    double durationSecondsFor(String targetPrlId);
}
