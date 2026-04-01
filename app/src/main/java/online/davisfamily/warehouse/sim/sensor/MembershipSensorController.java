package online.davisfamily.warehouse.sim.sensor;

import online.davisfamily.threedee.sim.framework.DetectionEvent;
import online.davisfamily.threedee.sim.framework.DetectionType;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationEvent;

public class MembershipSensorController implements SimulationController {

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleEvent(SimulationEvent event, SimulationContext context) {
	    if (event instanceof DetectionEvent detection) {
	    	DetectionEvent evt = (DetectionEvent) detection;
	        String secs = String.format("%.3f", evt.simulationTimeSeconds());
	    	if (detection.type() == DetectionType.ENTER) {
	        	System.out.println("MembershipSensorController (" + evt.sourceId() + "): Object: " + evt.objectId() + " entered at " + secs);
	        }

	        if (detection.type() == DetectionType.EXIT) {
	        	System.out.println("MembershipSensorController (" + evt.sourceId() + "): Object: " + evt.objectId() + " left at " + secs);
	        }
	        if (detection.type() == DetectionType.PRESENT) {
	        	//System.out.println("MembershipSensorController (" + evt.sourceId() + "): Object: " + evt.objectId() + " still present at " + secs);
	        }
	        
	    }		
	}

}
