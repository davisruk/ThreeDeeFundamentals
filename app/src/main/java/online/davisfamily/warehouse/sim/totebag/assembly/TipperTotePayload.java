package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.Map;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public class TipperTotePayload {
    private final Tote tote;
    private final ToteLoadPlan toteLoadPlan;
    private final RenderableObject toteRenderable;
    private final float toteInteriorFloorLocalY;
    private final Map<String, Vec3> containedPackLayoutById;

    public TipperTotePayload(
            Tote tote,
            ToteLoadPlan toteLoadPlan,
            RenderableObject toteRenderable,
            float toteInteriorFloorLocalY,
            Map<String, Vec3> containedPackLayoutById) {
        if (tote == null
                || toteLoadPlan == null
                || toteRenderable == null
                || containedPackLayoutById == null) {
            throw new IllegalArgumentException("Tipper tote payload inputs must not be null");
        }
        this.tote = tote;
        this.toteLoadPlan = toteLoadPlan;
        this.toteRenderable = toteRenderable;
        this.toteInteriorFloorLocalY = toteInteriorFloorLocalY;
        this.containedPackLayoutById = Map.copyOf(containedPackLayoutById);
    }

    public Tote getTote() {
        return tote;
    }

    public ToteLoadPlan getToteLoadPlan() {
        return toteLoadPlan;
    }

    public RenderableObject getToteRenderable() {
        return toteRenderable;
    }

    public float getToteInteriorFloorLocalY() {
        return toteInteriorFloorLocalY;
    }

    public Map<String, Vec3> getContainedPackLayoutById() {
        return containedPackLayoutById;
    }
}
