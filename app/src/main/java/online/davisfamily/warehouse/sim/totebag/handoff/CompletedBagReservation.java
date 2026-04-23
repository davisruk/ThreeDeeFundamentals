package online.davisfamily.warehouse.sim.totebag.handoff;

public record CompletedBagReservation(
        String receiverId,
        String correlationId) {
    public CompletedBagReservation {
        if (receiverId == null || receiverId.isBlank()) {
            throw new IllegalArgumentException("receiverId must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
    }
}
