package online.davisfamily.threedee.behaviour.routing.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.transfer.TransferZone;

public class ToggleTransferStrategy implements TransferDecisionStrategy {

	private boolean transfer = true;
	
	
	public ToggleTransferStrategy(boolean intialState) {
		this.transfer = intialState;
	}


	@Override
	public boolean shouldTransfer(RouteSegment currentSegment, TransferZone transferZone,
			RenderableObject renderableObject) {
		boolean ret = transfer;
		transfer = !transfer;
		return ret;
	}

}
