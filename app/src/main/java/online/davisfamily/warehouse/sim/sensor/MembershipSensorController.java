package online.davisfamily.warehouse.sim.sensor;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.SimulationEventListener;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;

public class MembershipSensorController implements SimulationController, SimulationEventListener<DetectionEvent>{

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleEvent(DetectionEvent event, SimulationContext context) {

        String secs = String.format("%.3f", event.getSimulationTimeSeconds());
    	if (event.getType() == DetectionType.ENTER) {
        	System.out.println("MembershipSensorController (" + event.getSourceId() + "): Object: " + event.getObjectId() + " entered at " + secs);
        }

        if (event.getType() == DetectionType.EXIT) {
        	System.out.println("MembershipSensorController (" + event.getSourceId() + "): Object: " + event.getObjectId() + " left at " + secs);
        }
        if (event.getType() == DetectionType.PRESENT) {
        	//System.out.println("MembershipSensorController (" + evt.sourceId() + "): Object: " + evt.objectId() + " still present at " + secs);
	        
	    }		
	}

}
