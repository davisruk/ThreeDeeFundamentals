package online.davisfamily.warehouse.sim.totebag.handoff;

public interface PackHandoffPointProvider {
    PackHandoffPoint resolveHandoffPoint(MachineHandoffPointId pointId);
}
