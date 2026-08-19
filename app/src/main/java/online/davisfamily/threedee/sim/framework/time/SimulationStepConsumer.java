package online.davisfamily.threedee.sim.framework.time;

@FunctionalInterface
public interface SimulationStepConsumer {
    void advance(double fixedStepSeconds);
}
