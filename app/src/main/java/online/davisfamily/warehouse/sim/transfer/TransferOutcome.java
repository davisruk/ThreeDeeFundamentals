package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;

public enum TransferOutcome {
    CONTINUE,
    BRANCH;

    public static TransferOutcome fromDecision(TransferDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        return decision == TransferDecision.BRANCH ? BRANCH : CONTINUE;
    }
}
