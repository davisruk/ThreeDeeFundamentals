package online.davisfamily.threedee.sim.framework;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.sim.objects.Sensor;

public class SimulationWorld {

	private final SimulationContext context = new SimulationContext();
	private final List<SimObject> simObjects = new ArrayList<>();
	private final List<Sensor> sensors = new ArrayList<>();
	private final List<SimulationController> controllers = new ArrayList<>();
	
	public void update(double dtSeconds) {
		context.addToSimulationTimeSeconds(dtSeconds);
		updateSimObjects(dtSeconds);
		updateSensors(dtSeconds);
		dispatchEvents();
		updateControllers(dtSeconds);
	}
	
	public void addSimObject(SimObject obj) {
		if (!simObjects.contains(obj))
			simObjects.add(obj);
	}
	
	public void addTrackableObject(TrackableObject obj) {
		simObjects.add(obj);
		context.addTrackedObject(obj);
	}
	
	public void addSensor(Sensor s) {
		sensors.add(s);
	}
	
	private void updateSimObjects(double dtSeconds) {
		for (SimObject simObject: simObjects) {
			simObject.update(context, dtSeconds);
		}
	}
	
	private void updateSensors(double dtSeconds) {
		for (Sensor sensor: sensors) {
			sensor.update(context, dtSeconds);
		}
	}
	
	private void dispatchEvents() {
		while (!context.getEventQueue().isEmpty()) {
			SimulationEvent evt = context.getEventQueue().poll();
			for (SimulationController ctl: controllers) {
				ctl.handleEvent(evt, context);
			}
		}
	}
	
	private void updateControllers(double dtSeconds) {
		for (SimulationController ctl: controllers) {
			ctl.update(context, dtSeconds);
		}
	}
}
