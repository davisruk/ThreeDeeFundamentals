package online.davisfamily.threedee.behaviour.routing.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.TransferZone;
import online.davisfamily.threedee.rendering.RenderableObject;

public class NeverTransferStrategy implements TransferDecisionStrategy {
	@Override
    public boolean shouldTransfer(RouteSegment currentSegment,
                                  TransferZone transferZone,
                                  RenderableObject renderableObject) {
        return false;
    }
}
