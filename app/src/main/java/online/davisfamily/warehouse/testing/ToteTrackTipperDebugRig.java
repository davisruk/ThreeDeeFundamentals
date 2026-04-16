package online.davisfamily.warehouse.testing;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperEntryModule;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperEntryModuleBuilder;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;

public class ToteTrackTipperDebugRig {
    private final TipperEntryModule module;
    private final SorterOutfeedDebugConveyor sorterOutfeedDebugConveyor;

    public ToteTrackTipperDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        ForwardingPackReceiveTarget sorterOutfeedTarget = new ForwardingPackReceiveTarget();
        module = new TipperEntryModuleBuilder().build(
                tr,
                sim,
                objects,
                inspectionRegistry,
                TipperEntryLayoutSpec.debugDefaults(),
                sorterOutfeedTarget);
        sorterOutfeedDebugConveyor = new SorterOutfeedDebugConveyor(
                tr,
                sim,
                objects,
                inspectionRegistry,
                module.getSortingModule().outfeedPoint(),
                null);
        sorterOutfeedTarget.setDelegate(sorterOutfeedDebugConveyor);
    }

    public void syncVisuals() {
        module.syncVisuals();
        sorterOutfeedDebugConveyor.syncVisuals(module::getPackRenderable);
    }
}
