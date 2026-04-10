package online.davisfamily.warehouse.sim.events;

import online.davisfamily.threedee.sim.framework.events.SimulationEvent;

public class TransferCompletedEvent implements SimulationEvent {
    public final String type = "TransferCompletedEvent";
	public String sourceId;
    public double simulationTimeSeconds;
    public String toteId;
	
    
    public TransferCompletedEvent(String sourceId, double simulationTimeSeconds, String toteId) {
		super();
		this.sourceId = sourceId;
		this.simulationTimeSeconds = simulationTimeSeconds;
		this.toteId = toteId;
	}


	@Override
	public String getType() {
		return type;
	}

    
}