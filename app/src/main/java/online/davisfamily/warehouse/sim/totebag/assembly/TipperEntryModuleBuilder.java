package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;

public class TipperEntryModuleBuilder {
    public TipperEntryModule build(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            TipperEntryLayoutSpec layoutSpec,
            PackReceiveTarget sorterOutfeedTarget) {
        return new TipperEntryModule(tr, sim, objects, inspectionRegistry, layoutSpec, sorterOutfeedTarget);
    }
}
