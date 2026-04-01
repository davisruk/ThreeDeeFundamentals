package online.davisfamily.threedee.behaviour.routing.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.transfer.TransferZone;

public interface TransferDecisionStrategy {
    boolean shouldTransfer(
            RouteSegment currentSegment,
            TransferZone transferZone,
            RenderableObject renderableObject
    );
}