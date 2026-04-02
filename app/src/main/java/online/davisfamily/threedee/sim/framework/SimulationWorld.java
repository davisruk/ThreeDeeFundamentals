package online.davisfamily.threedee.sim.framework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationWorld {

	private final SimulationContext context = new SimulationContext();
	private final List<SimObject> simObjects = new ArrayList<>();
	private final List<Sensor> sensors = new ArrayList<>();
	private final List<SimulationController> controllers = new ArrayList<>();
	// Map holds a list of event listeners keyed on the event class 
	private final Map<Class<? extends SimulationEvent>, List<SimulationEventListener<? extends SimulationEvent>>> listeners = new HashMap<>();

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
		if (!simObjects.contains(obj)) {
			simObjects.add(obj);
			context.addTrackedObject(obj);
		}
	}
	
	public void addSensor(Sensor s) {
		sensors.add(s);
	}
	
	public void addController(SimulationController controller) {
		if (!controllers.contains(controller))
			controllers.add(controller);
	}
	
	public <S extends SimulationEvent> void registerListener(Class<S> eventType, SimulationEventListener<S> listener) {
		listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
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
			List<SimulationEventListener<?>> registered = listeners.getOrDefault(evt.getClass(), List.of());
			for (SimulationEventListener<?> l: registered) {
				notifyListener(evt, l);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private <E extends SimulationEvent> void notifyListener(SimulationEvent event, SimulationEventListener<?> listener) {
		((SimulationEventListener<E>) listener).handleEvent((E)event, context);
	}
	
	private void updateControllers(double dtSeconds) {
		for (SimulationController ctl: controllers) {
			ctl.update(context, dtSeconds);
		}
	}
}
