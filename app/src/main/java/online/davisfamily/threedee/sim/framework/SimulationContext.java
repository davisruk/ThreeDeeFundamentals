package online.davisfamily.threedee.sim.framework;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class SimulationContext {
	private final Queue<SimulationEvent> eventQueue = new ArrayDeque<>();
	private double simulationTimeSeconds;
	private final List<TrackableObject> trackedObjects = new ArrayList<>();
	
	public void publish(SimulationEvent event) {
		eventQueue.add(event);
	}
	public double getSimulationTimeSeconds() {
		return simulationTimeSeconds;
	}
	
	public void addToSimulationTimeSeconds(double seconds) {
		this.simulationTimeSeconds+=seconds;
	}
	public void setSimulationTimeSeconds(double simulationTimeSeconds) {
		this.simulationTimeSeconds = simulationTimeSeconds;
	}
	public Queue<SimulationEvent> getEventQueue() {
		return eventQueue;
	}
	public void addTrackedObject(TrackableObject obj) {
		trackedObjects.add(obj);
	}
	public List<TrackableObject> getTrackedObjects() {
		return trackedObjects;
	}
	
}
