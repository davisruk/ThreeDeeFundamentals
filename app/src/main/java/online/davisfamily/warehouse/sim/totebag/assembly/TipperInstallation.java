package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class TipperInstallation {
    private final TipperTrackSection trackSection;
    private final TipperTotePayload totePayload;
    private final TippingMachine tippingMachine;
    private final TipperModule tipperModule;
    private final RenderableObject tipperAssemblyRenderable;
    private final ConveyorRuntimeState tipperTrackRuntimeState;

    public TipperInstallation(
            TipperTrackSection trackSection,
            TipperTotePayload totePayload,
            TippingMachine tippingMachine,
            TipperModule tipperModule,
            RenderableObject tipperAssemblyRenderable,
            ConveyorRuntimeState tipperTrackRuntimeState) {
        if (trackSection == null
                || totePayload == null
                || tippingMachine == null
                || tipperModule == null
                || tipperAssemblyRenderable == null
                || tipperTrackRuntimeState == null) {
            throw new IllegalArgumentException("Tipper installation inputs must not be null");
        }
        this.trackSection = trackSection;
        this.totePayload = totePayload;
        this.tippingMachine = tippingMachine;
        this.tipperModule = tipperModule;
        this.tipperAssemblyRenderable = tipperAssemblyRenderable;
        this.tipperTrackRuntimeState = tipperTrackRuntimeState;
    }

    public TipperTrackSection getTrackSection() {
        return trackSection;
    }

    public TipperTotePayload getTotePayload() {
        return totePayload;
    }

    public Tote getTote() {
        return totePayload.getTote();
    }

    public ToteLoadPlan getToteLoadPlan() {
        return totePayload.getToteLoadPlan();
    }

    public TippingMachine getTippingMachine() {
        return tippingMachine;
    }

    public TipperModule getTipperModule() {
        return tipperModule;
    }

    public RenderableObject getTipperAssemblyRenderable() {
        return tipperAssemblyRenderable;
    }

    public ConveyorRuntimeState getTipperTrackRuntimeState() {
        return tipperTrackRuntimeState;
    }
}
