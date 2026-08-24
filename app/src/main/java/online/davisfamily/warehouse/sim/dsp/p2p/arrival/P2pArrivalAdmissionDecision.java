package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

public record P2pArrivalAdmissionDecision(boolean permitted, String reason) {

    public P2pArrivalAdmissionDecision {
        reason = reason == null ? "" : reason.trim();
        if (permitted && !reason.isEmpty()) {
            throw new IllegalArgumentException("reason must be empty when admission is permitted");
        }
        if (!permitted && reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank when admission is deferred");
        }
    }

    public static P2pArrivalAdmissionDecision permit() {
        return new P2pArrivalAdmissionDecision(true, "");
    }

    public static P2pArrivalAdmissionDecision defer(String reason) {
        return new P2pArrivalAdmissionDecision(false, reason);
    }
}
