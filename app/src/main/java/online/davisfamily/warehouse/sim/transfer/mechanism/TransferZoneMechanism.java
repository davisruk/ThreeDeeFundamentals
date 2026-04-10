package online.davisfamily.warehouse.sim.transfer.mechanism;

import java.util.List;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.transfer.TransferOutcome;

public interface TransferZoneMechanism {
    String getId();
    void command(TransferOutcome outcome);
    void update(double dtSeconds);
    boolean isReadyFor(TransferOutcome outcome);
    MechanismMotionState getMotionState();
    List<RenderableObject> getRenderables();
}
