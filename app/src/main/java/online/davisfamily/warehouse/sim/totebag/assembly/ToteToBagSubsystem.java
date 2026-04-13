package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.sim.totebag.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.layout.MachineAttachmentSpec;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagAttachmentPose;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayout;

public class ToteToBagSubsystem {
    private final ToteToBagCoreLayout layout;
    private final PdcConveyor pdcConveyor;
    private final PcrConveyor pcrConveyor;
    private final List<PrlConveyor> prls;
    private final List<PdcDiversionDevice> pdcDiversionDevices;
    private final RenderableObject pdcRenderable;
    private final RenderableObject pcrRenderable;
    private final Map<String, RenderableObject> prlRenderablesById;
    private final Map<String, RenderableObject> pdcBumpersByPrlId;
    private final ConveyorRuntimeState pdcRuntimeState;
    private final ConveyorRuntimeState pcrRuntimeState;
    private final Map<String, ConveyorRuntimeState> prlRuntimeStatesById;

    public ToteToBagSubsystem(
            ToteToBagCoreLayout layout,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            List<PrlConveyor> prls,
            List<PdcDiversionDevice> pdcDiversionDevices,
            RenderableObject pdcRenderable,
            RenderableObject pcrRenderable,
            Map<String, RenderableObject> prlRenderablesById,
            Map<String, RenderableObject> pdcBumpersByPrlId,
            ConveyorRuntimeState pdcRuntimeState,
            ConveyorRuntimeState pcrRuntimeState,
            Map<String, ConveyorRuntimeState> prlRuntimeStatesById) {
        this.layout = layout;
        this.pdcConveyor = pdcConveyor;
        this.pcrConveyor = pcrConveyor;
        this.prls = List.copyOf(prls);
        this.pdcDiversionDevices = List.copyOf(pdcDiversionDevices);
        this.pdcRenderable = pdcRenderable;
        this.pcrRenderable = pcrRenderable;
        this.prlRenderablesById = Map.copyOf(prlRenderablesById);
        this.pdcBumpersByPrlId = Map.copyOf(pdcBumpersByPrlId);
        this.pdcRuntimeState = pdcRuntimeState;
        this.pcrRuntimeState = pcrRuntimeState;
        this.prlRuntimeStatesById = Map.copyOf(prlRuntimeStatesById);
    }

    public ToteToBagCoreLayout getLayout() {
        return layout;
    }

    public PdcConveyor getPdcConveyor() {
        return pdcConveyor;
    }

    public PcrConveyor getPcrConveyor() {
        return pcrConveyor;
    }

    public List<PrlConveyor> getPrls() {
        return prls;
    }

    public List<PdcDiversionDevice> getPdcDiversionDevices() {
        return pdcDiversionDevices;
    }

    public RenderableObject getPdcRenderable() {
        return pdcRenderable;
    }

    public RenderableObject getPcrRenderable() {
        return pcrRenderable;
    }

    public Map<String, RenderableObject> getPrlRenderablesById() {
        return prlRenderablesById;
    }

    public Map<String, RenderableObject> getPdcBumpersByPrlId() {
        return pdcBumpersByPrlId;
    }

    public ConveyorRuntimeState getPdcRuntimeState() {
        return pdcRuntimeState;
    }

    public ConveyorRuntimeState getPcrRuntimeState() {
        return pcrRuntimeState;
    }

    public Map<String, ConveyorRuntimeState> getPrlRuntimeStatesById() {
        return prlRuntimeStatesById;
    }

    public List<RenderableObject> getCoreRenderables() {
        List<RenderableObject> renderables = new java.util.ArrayList<>();
        renderables.add(pdcRenderable);
        renderables.add(pcrRenderable);
        renderables.addAll(prlRenderablesById.values());
        renderables.addAll(pdcBumpersByPrlId.values());
        return List.copyOf(renderables);
    }

    public ToteToBagAttachmentPose resolveAttachmentPose(MachineAttachmentSpec spec) {
        return layout.resolveAttachmentPose(spec);
    }

    public void attachRenderable(RenderableObject renderable, MachineAttachmentSpec spec) {
        ToteToBagAttachmentPose pose = resolveAttachmentPose(spec);
        renderable.transformation.xTranslation = pose.x();
        renderable.transformation.yTranslation = pose.y();
        renderable.transformation.zTranslation = pose.z();
        renderable.transformation.angleY = pose.yawRadians();
    }
}
