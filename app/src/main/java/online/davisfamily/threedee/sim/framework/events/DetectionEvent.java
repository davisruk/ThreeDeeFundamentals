package online.davisfamily.threedee.sim.framework.events;

public class DetectionEvent implements SimulationEvent {

	// Detection events currently identify the tracked object by id only.
	// This keeps the event payload small, but it means listeners that need the
	// actual object must perform a secondary lookup against SimulationContext.
	//
	// That lookup is still a known source of fragility because it relies on id
	// consistency across the publishing object, the tracked object, and any
	// attached renderable object. A broader event-model review is still pending:
	// the likely future choice is either object references for in-process events
	// or immutable boundary-safe payloads if simulation/rendering are split.
	private String sourceId;
    private double simulationTimeSeconds;
    private String sensorId;
    private String objectId;
    private DetectionType type;


    public DetectionEvent() {
		super();
	}

	public DetectionEvent(String sourceId, double simulationTimeSeconds, String sensorId, String objectId,
			DetectionType type) {
		super();
		this.sourceId = sourceId;
		this.simulationTimeSeconds = simulationTimeSeconds;
		this.sensorId = sensorId;
		this.objectId = objectId;
		this.type = type;
	}

	public enum DetectionType {
		ENTER, EXIT, PRESENT, NOT_PRESENT 
	}
	
	public void set(String sourceId, double simulationTimeSeconds, String sensorId, String objectId, DetectionType type) {
		this.sourceId = sourceId;
		this.simulationTimeSeconds = simulationTimeSeconds;
		this.sensorId = sensorId;
		this.objectId = objectId;
		this.type = type;		
	}
	
	public void set(double simTime, String followerId, DetectionType detected) {
		this.objectId = followerId;
		this.simulationTimeSeconds = simTime;
		this.type = detected;
	}

	public String getSourceId() {
		return sourceId;
	}

	public double getSimulationTimeSeconds() {
		return simulationTimeSeconds;
	}

	public String getSensorId() {
		return sensorId;
	}

	public String getObjectId() {
		return objectId;
	}

	public DetectionType getDetectionType() {
		return type;
	}

    @Override
	public String getType() {
		return "DetectionEvent";
	}

	@Override
	public String toString() {
		return "DetectionEvent [sourceId=" + sourceId + ", simulationTimeSeconds=" + simulationTimeSeconds
				+ ", sensorId=" + sensorId + ", objectId=" + objectId + ", type=" + type + "]";
	}
}
