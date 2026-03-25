package online.davisfamily.threedee.behaviour.routing.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.TransferZone;
import online.davisfamily.threedee.rendering.RenderableObject;

public class ToggleTransferStrategy implements TransferDecisionStrategy {

	private boolean transfer = true;
	@Override
	public boolean shouldTransfer(RouteSegment currentSegment, TransferZone transferZone,
			RenderableObject renderableObject) {
		boolean ret = transfer;
		transfer = !transfer;
		return ret;
	}

}
