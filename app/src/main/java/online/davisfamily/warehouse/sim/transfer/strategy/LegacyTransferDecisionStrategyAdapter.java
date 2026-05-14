package online.davisfamily.warehouse.sim.transfer.strategy;

import java.util.Optional;

import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;

public final class LegacyTransferDecisionStrategyAdapter implements TransferTargetDecisionStrategy {
    private final TransferDecisionStrategy legacyStrategy;

    public LegacyTransferDecisionStrategyAdapter(TransferDecisionStrategy legacyStrategy) {
        if (legacyStrategy == null) {
            throw new IllegalArgumentException("legacyStrategy must not be null");
        }
        this.legacyStrategy = legacyStrategy;
    }

    @Override
    public Optional<TransferRoutingDecision> decide(Tote tote, TransferZoneMachine machine) {
        Optional<TransferDecision> decision = legacyStrategy.decide(tote, machine);
        if (decision.isEmpty()) {
            return Optional.empty();
        }

        TransferZone zone = machine.getTransferZone();
        TransferTarget branchTarget = new TransferTarget(
                zone.getTargetSegment(),
                zone.getTargetStartDistance(),
                tote.getRouteFollower().getTravelDirection());
        return Optional.of(TransferRoutingDecision.fromDecision(decision.get(), branchTarget));
    }
}
