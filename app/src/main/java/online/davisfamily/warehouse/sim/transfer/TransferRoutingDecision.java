package online.davisfamily.warehouse.sim.transfer;

import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;

public final class TransferRoutingDecision {
    private final boolean continueOnCurrentRoute;
    private final TransferTarget transferTarget;
    private final TransferOrientationPolicy orientationPolicy;

    private TransferRoutingDecision(
            boolean continueOnCurrentRoute,
            TransferTarget transferTarget,
            TransferOrientationPolicy orientationPolicy) {
        this.continueOnCurrentRoute = continueOnCurrentRoute;
        this.transferTarget = transferTarget;
        this.orientationPolicy = orientationPolicy;
    }

    public static TransferRoutingDecision continueOnCurrentRoute() {
        return new TransferRoutingDecision(true, null, TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION);
    }

    public static TransferRoutingDecision transferTo(TransferTarget target) {
        return transferTo(target, TransferOrientationPolicy.PRESERVE_TOTE_ORIENTATION);
    }

    public static TransferRoutingDecision transferTo(
            TransferTarget target,
            TransferOrientationPolicy orientationPolicy) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (orientationPolicy == null) {
            throw new IllegalArgumentException("orientationPolicy must not be null");
        }
        return new TransferRoutingDecision(false, target, orientationPolicy);
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

    public TransferOrientationPolicy orientationPolicy() {
        return orientationPolicy;
    }
}
