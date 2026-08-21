package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

public record OperationalReleaseSelection(
        DspOperationalReleaseCandidate candidate,
        OperationalRouteEntry routeEntry) {

    public OperationalReleaseSelection {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (routeEntry == null) {
            throw new IllegalArgumentException("routeEntry must not be null");
        }
    }
}
