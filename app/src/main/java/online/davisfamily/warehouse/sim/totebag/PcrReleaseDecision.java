package online.davisfamily.warehouse.sim.totebag;

public record PcrReleaseDecision(
        boolean allowed,
        float requiredLength,
        float availableLength,
        String reason) {
}
