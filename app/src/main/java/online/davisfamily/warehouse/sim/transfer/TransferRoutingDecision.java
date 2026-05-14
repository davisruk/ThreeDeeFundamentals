package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;

public final class TransferRoutingDecision {
    private final boolean continueOnCurrentRoute;
    private final TransferTarget transferTarget;

    private TransferRoutingDecision(boolean continueOnCurrentRoute, TransferTarget transferTarget) {
        this.continueOnCurrentRoute = continueOnCurrentRoute;
        this.transferTarget = transferTarget;
    }

    public static TransferRoutingDecision continueOnCurrentRoute() {
        return new TransferRoutingDecision(true, null);
    }

    public static TransferRoutingDecision transferTo(TransferTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        return new TransferRoutingDecision(false, target);
    }

    public static TransferRoutingDecision fromDecision(
            TransferDecision decision,
            TransferTarget branchTarget) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        return decision == TransferDecision.CONTINUE
                ? continueOnCurrentRoute()
                : transferTo(branchTarget);
    }

    public boolean isContinueOnCurrentRoute() {
        return continueOnCurrentRoute;
    }

    public boolean isTransfer() {
        return transferTarget != null;
    }

    public TransferTarget transferTarget() {
        return transferTarget;
    }
}
