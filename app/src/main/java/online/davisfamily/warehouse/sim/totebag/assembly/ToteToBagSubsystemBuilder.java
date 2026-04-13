package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.ConveyorVisualSpeed;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.StraightConveyorSpec;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayout;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayoutSpec;

public class ToteToBagSubsystemBuilder {
    public ToteToBagSubsystem buildCore(
            TriangleRenderer tr,
            TrackAppearance conveyorAppearance,
            ToteToBagCoreLayoutSpec spec) {
        if (tr == null || conveyorAppearance == null || spec == null) {
            throw new IllegalArgumentException("Builder inputs must not be null");
        }

        ToteToBagCoreLayout layout = new ToteToBagCoreLayout(spec);
        PdcConveyor pdcConveyor = new PdcConveyor(
                "pdc",
                new ConveyorOccupancyModel(spec.pdcLength(), spec.conveyorMinimumGap(), 0f),
                spec.pdcBeltSpeed());

        List<PrlConveyor> prls = new java.util.ArrayList<>();
        List<PdcDiversionDevice> pdcDiversionDevices = new java.util.ArrayList<>();
        Map<String, RenderableObject> prlRenderablesById = new LinkedHashMap<>();
        Map<String, RenderableObject> pdcBumpersByPrlId = new LinkedHashMap<>();
        Map<String, ConveyorRuntimeState> prlRuntimeStatesById = new LinkedHashMap<>();

        for (int i = 0; i < spec.prlCount(); i++) {
            String prlId = "prl-" + (i + 1);
            PrlConveyor prl = new PrlConveyor(
                    prlId,
                    spec.prlIndexDistance(),
                    new ConveyorOccupancyModel(spec.prlLength() - 0.6f, spec.conveyorMinimumGap(), 0f),
                    spec.prlBeltSpeed());
            prls.add(prl);
            pdcDiversionDevices.add(new PdcDiversionDevice(
                    "pdc_diverter_" + prlId,
                    prlId,
                    spec.diversionArmDelaySeconds(),
                    spec.diversionActuationDurationSeconds(),
                    spec.diversionResetDurationSeconds()));

            ConveyorRuntimeState prlRuntimeState = new ConveyorRuntimeState();
            prlRuntimeState.setRunning(false);
            prlRuntimeStatesById.put(prlId, prlRuntimeState);

            RenderableObject prlRenderable = createConveyor(
                    tr,
                    conveyorAppearance,
                    prlId,
                    layout.prlCenterX(i),
                    spec.conveyorY(),
                    -1.25f,
                    spec.prlLength(),
                    spec.singlePackConveyorWidth(),
                    (float) (Math.PI / 2.0),
                    prlRuntimeState);
            prlRenderablesById.put(prlId, prlRenderable);

            RenderableObject bumper = createBumper(tr, layout.prlCenterX(i), 0.27f);
            pdcBumpersByPrlId.put(prlId, bumper);
        }

        double pcrTravelDurationSeconds = spec.pcrLength() / spec.prlBeltSpeed();
        PcrConveyor pcrConveyor = new PcrConveyor(
                "pcr",
                new ConveyorOccupancyModel(spec.pcrLength() - 0.1f, spec.pcrMinimumGap(), spec.pcrSafetyMargin()),
                pcrTravelDurationSeconds);

        ConveyorRuntimeState pdcRuntimeState = new ConveyorRuntimeState();
        pdcRuntimeState.setRunning(true);
        ConveyorRuntimeState pcrRuntimeState = new ConveyorRuntimeState();
        pcrRuntimeState.setRunning(false);

        RenderableObject pdcRenderable = createConveyor(
                tr,
                conveyorAppearance,
                "pdc",
                spec.pdcCenterX(),
                spec.conveyorY(),
                spec.pdcZ(),
                spec.pdcLength(),
                spec.singlePackConveyorWidth(),
                0f,
                pdcRuntimeState);
        RenderableObject pcrRenderable = createConveyor(
                tr,
                conveyorAppearance,
                "pcr",
                spec.pdcCenterX(),
                spec.conveyorY(),
                spec.pcrZ(),
                spec.pcrLength(),
                spec.singlePackConveyorWidth(),
                0f,
                pcrRuntimeState);

        return new ToteToBagSubsystem(
                layout,
                pdcConveyor,
                pcrConveyor,
                prls,
                pdcDiversionDevices,
                pdcRenderable,
                pcrRenderable,
                prlRenderablesById,
                pdcBumpersByPrlId,
                pdcRuntimeState,
                pcrRuntimeState,
                prlRuntimeStatesById);
    }

    private RenderableObject createConveyor(
            TriangleRenderer tr,
            TrackAppearance conveyorAppearance,
            String id,
            float x,
            float y,
            float z,
            float length,
            float width,
            float yawRadians,
            ConveyorRuntimeState runtimeState) {
        RenderableObject conveyor = StraightConveyorFactory.create(
                id,
                tr,
                new StraightConveyorSpec(
                        length,
                        width,
                        0.05f,
                        0.01f,
                        0.10f,
                        0.08f,
                        0.004f,
                        ConveyorVisualSpeed.fixed(0.8d)),
                runtimeState,
                conveyorAppearance);
        conveyor.transformation.xTranslation = x;
        conveyor.transformation.yTranslation = y;
        conveyor.transformation.zTranslation = z;
        conveyor.transformation.angleY = yawRadians;
        configureConveyorSelection(conveyor);
        return conveyor;
    }

    private RenderableObject createBumper(TriangleRenderer tr, float x, float z) {
        RenderableObject bumper = RenderableObject.create(
                "pdc_bumper_" + x,
                tr,
                online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory.createBoxRollerMesh(0.10f, 0.14f, 0.08f),
                new online.davisfamily.threedee.matrices.Mat4.ObjectTransformation(
                        0f,
                        0f,
                        (float) (Math.PI / 2.0),
                        x,
                        0.12f,
                        z,
                        new online.davisfamily.threedee.matrices.Mat4()),
                new online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl(0xFFCC8844),
                true);
        return bumper;
    }

    private void configureConveyorSelection(RenderableObject conveyorRoot) {
        for (RenderableObject child : conveyorRoot.children) {
            if (child.id.endsWith("_top_belt")) {
                child.setSelectable(true);
                child.setSelectionTarget(conveyorRoot);
                break;
            }
        }
    }
}
