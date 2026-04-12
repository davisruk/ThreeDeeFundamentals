package online.davisfamily.warehouse.sim.totebag;

@FunctionalInterface
public interface PrlToPcrTransferDurationProvider {
    double durationSecondsFor(String prlId);
}
