package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;

public class SortingSectionInstaller {
    public SortingInstallation install(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            TipperEntryLayoutSpec layoutSpec,
            Vec3 intakeLocalPoint) {
        if (tr == null
                || sim == null
                || objects == null
                || inspectionRegistry == null
                || layoutSpec == null
                || intakeLocalPoint == null) {
            throw new IllegalArgumentException("Sorting install inputs must not be null");
        }

        SortingMachine sortingMachine = new SortingMachine("sorter", 0.22d);
        sim.addSimObject(sortingMachine);

        SortingModule sortingModule = new SortingModule(
                tr,
                sortingMachine,
                layoutSpec.origin(),
                layoutSpec.yawRadians(),
                intakeLocalPoint);
        objects.add(sortingModule.getRenderable());
        registerInspectableObjects(inspectionRegistry, sortingModule);
        return new SortingInstallation(sortingMachine, sortingModule);
    }

    private void registerInspectableObjects(
            SelectionInspectionRegistry inspectionRegistry,
            SortingModule sortingModule) {
        inspectionRegistry.register(sortingModule.getRenderable(), () -> List.of(
                "Type: Sorter",
                "State: " + sortingModule.getSortingMachine().getState(),
                "Queued packs: " + sortingModule.getSortingMachine().getQueuedPacks().size()));
    }
}
