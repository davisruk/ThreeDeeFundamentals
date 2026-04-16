package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.function.Supplier;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class TipperModule {
    private final Tote tote;
    private final ToteLoadPlan toteLoadPlan;
    private final TippingMachine tippingMachine;
    private final RenderableObject toteRenderable;
    private final RenderableObject assemblyRenderable;
    private final Supplier<PackHandoffPoint> dischargePointSupplier;

    public TipperModule(
            Tote tote,
            ToteLoadPlan toteLoadPlan,
            TippingMachine tippingMachine,
            RenderableObject toteRenderable,
            RenderableObject assemblyRenderable,
            Supplier<PackHandoffPoint> dischargePointSupplier) {
        if (tote == null
                || toteLoadPlan == null
                || tippingMachine == null
                || toteRenderable == null
                || assemblyRenderable == null
                || dischargePointSupplier == null) {
            throw new IllegalArgumentException("TipperModule inputs must not be null");
        }
        this.tote = tote;
        this.toteLoadPlan = toteLoadPlan;
        this.tippingMachine = tippingMachine;
        this.toteRenderable = toteRenderable;
        this.assemblyRenderable = assemblyRenderable;
        this.dischargePointSupplier = dischargePointSupplier;
    }

    public Tote getTote() {
        return tote;
    }

    public ToteLoadPlan getToteLoadPlan() {
        return toteLoadPlan;
    }

    public TippingMachine getTippingMachine() {
        return tippingMachine;
    }

    public RenderableObject getToteRenderable() {
        return toteRenderable;
    }

    public RenderableObject getAssemblyRenderable() {
        return assemblyRenderable;
    }

    public PackHandoffPoint dischargePoint() {
        return dischargePointSupplier.get();
    }
}
