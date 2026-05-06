package online.davisfamily.warehouse.sim.dsp.p2p;

public record P2pAdmissionResult(boolean accepted, String rejectionReason) {
    public P2pAdmissionResult {
        rejectionReason = rejectionReason == null ? "" : rejectionReason;
        if (!accepted && rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason must not be blank when admission is rejected");
        }
    }

    public static P2pAdmissionResult acceptedResult() {
        return new P2pAdmissionResult(true, "");
    }

    public static P2pAdmissionResult rejectedResult(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new P2pAdmissionResult(false, reason);
    }
}
