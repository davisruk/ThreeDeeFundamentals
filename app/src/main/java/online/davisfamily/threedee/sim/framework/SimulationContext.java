package online.davisfamily.threedee.sim.framework;

import java.util.ArrayDeque;
import java.util.Queue;

public class SimulationContext {
	private final Queue<SimulationEvent> eventQueue = new ArrayDeque<>();
	private double simulationTimeSeconds;
	
	public double getSimulationTimeSeconds() {
		return simulationTimeSeconds;
	}
	public void setSimulationTimeSeconds(double simulationTimeSeconds) {
		this.simulationTimeSeconds = simulationTimeSeconds;
	}
	public Queue<SimulationEvent> getEventQueue() {
		return eventQueue;
	}
}
