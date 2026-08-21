package online.davisfamily.warehouse.sim.dsp.transport.routing;

import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

@FunctionalInterface
public interface WarehouseTransferStrategyInstaller {
    void install(
            TransferZoneMachine machine,
            DestinationAwareTransferTargetDecisionStrategy strategy);
}
