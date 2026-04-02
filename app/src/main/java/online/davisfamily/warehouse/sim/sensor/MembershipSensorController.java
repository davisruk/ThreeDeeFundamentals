package online.davisfamily.warehouse.sim.sensor;

import online.davisfamily.threedee.sim.framework.DetectionEvent;
import online.davisfamily.threedee.sim.framework.DetectionEventPayload;
import online.davisfamily.threedee.sim.framework.DetectionEventPayload.DetectionType;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationEventListener;

public class MembershipSensorController implements SimulationController, SimulationEventListener<DetectionEvent>{

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleEvent(DetectionEvent event, SimulationContext context) {
	    DetectionEventPayload payload = event.getPayload();
        String secs = String.format("%.3f", payload.simulationTimeSeconds());
    	if (payload.type() == DetectionType.ENTER) {
        	System.out.println("MembershipSensorController (" + payload.sourceId() + "): Object: " + payload.objectId() + " entered at " + secs);
        }

        if (payload.type() == DetectionType.EXIT) {
        	System.out.println("MembershipSensorController (" + payload.sourceId() + "): Object: " + payload.objectId() + " left at " + secs);
        }
        if (payload.type() == DetectionType.PRESENT) {
        	//System.out.println("MembershipSensorController (" + evt.sourceId() + "): Object: " + evt.objectId() + " still present at " + secs);
	        
	    }		
	}

}
