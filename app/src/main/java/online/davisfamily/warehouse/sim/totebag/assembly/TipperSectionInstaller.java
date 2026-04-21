package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;

public class TipperSectionInstaller {
    public TipperInstallation install(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            TipperTrackSection trackSection,
            TipperTotePayload totePayload) {
        if (tr == null
                || sim == null
                || objects == null
                || inspectionRegistry == null
                || trackSection == null
                || totePayload == null) {
            throw new IllegalArgumentException("Tipper install inputs must not be null");
        }

        TippingMachine tippingMachine = new TippingMachine("tipper", 0.45d, 0.18d, 0.35d);
        sim.addSimObject(tippingMachine);

        ConveyorRuntimeState tipperTrackRuntimeState = new ConveyorRuntimeState();
        tipperTrackRuntimeState.setRunning(true);

        Vec3 tipperAssemblyWorld = trackSection.localToWorld(trackSection.getTipperAssemblyLocalOrigin());
        TipperAssemblyFactory assemblyFactory = new TipperAssemblyFactory();
        TipperAssemblyFactory.BuildResult assemblyBuild = assemblyFactory.build(
                tr,
                trackSection.getTipperTrackSpec(),
                trackSection.getTrackAppearance(),
                tipperTrackRuntimeState,
                tipperAssemblyWorld,
                trackSection.rigYaw(),
                trackSection.getTipperLength(),
                trackSection.getTipperTrackOverallWidth());

        RenderableObject tipperAssemblyRenderable = assemblyBuild.assemblyRenderable();
        RenderableObject toteRenderable = totePayload.getToteRenderable();
        objects.add(toteRenderable);
        objects.add(tipperAssemblyRenderable);

        Vec3 dischargeToteInteriorLocal = new Vec3(0f, totePayload.getToteInteriorFloorLocalY() + 0.06f, 0.10f);
        Vec3 dischargeLidLocal = new Vec3(0f, 0.24f, -0.08f);
        Vec3 dischargeSlideEntryLocal = assemblyBuild.dischargeSlideEntryLocal();

        TipperModule tipperModule = new TipperModule(
                totePayload.getTote(),
                totePayload.getToteLoadPlan(),
                tippingMachine,
                toteRenderable,
                tipperAssemblyRenderable,
                trackSection.getLayoutSpec().origin(),
                trackSection.getTipperAssemblyLocalOrigin(),
                dischargeToteInteriorLocal,
                dischargeLidLocal,
                dischargeSlideEntryLocal,
                trackSection.getTippedAngleRadians(),
                trackSection.rigYaw());

        registerInspectableObjects(inspectionRegistry, tipperModule);

        return new TipperInstallation(
                trackSection,
                totePayload,
                tippingMachine,
                tipperModule,
                tipperAssemblyRenderable,
                tipperTrackRuntimeState);
    }

    private void registerInspectableObjects(
            SelectionInspectionRegistry inspectionRegistry,
            TipperModule tipperModule) {
        inspectionRegistry.register(tipperModule.getToteRenderable(), () -> List.of(
                "Type: Tote",
                "Id: " + tipperModule.getTote().getId(),
                "Motion: " + tipperModule.getTote().getInteractionMode(),
                "Distance: " + (tipperModule.getTote().getLastSnapshot() == null
                        ? "None"
                        : String.format("%.3f", tipperModule.getTote().getLastSnapshot().distanceAlongSegment()))));

        inspectionRegistry.register(tipperModule.getAssemblyRenderable(), () -> List.of(
                "Type: Tipper",
                "State: " + tipperModule.getTippingMachine().getState(),
                "Remaining packs: " + tipperModule.getTippingMachine().getRemainingPackCount()));
    }
}
