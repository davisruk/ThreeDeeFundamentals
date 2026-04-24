package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.layout.MachineAttachmentSpec;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayout;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagAttachmentPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;

public class BaggingSectionInstaller {
    public BaggingInstallation install(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            ToteToBagCoreLayout layout,
            TrackAppearance conveyorAppearance) {
        if (tr == null
                || sim == null
                || objects == null
                || inspectionRegistry == null
                || layout == null
                || conveyorAppearance == null) {
            throw new IllegalArgumentException("Bagging install inputs must not be null");
        }

        StoredBagReceiver bagReceiver = new StoredBagReceiver("bagger_bag_receiver", 2);
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger",
                new BagSpec(0.34f, 0.28f, 0.22f),
                0.35d,
                0.25d,
                0.30d,
                0.25d,
                bagReceiver);
        sim.addSimObject(baggingMachine);

        BaggingModule baggingModule = new BaggingModule(
                tr,
                baggingMachine,
                layout.resolveAttachmentPose(layout.getSpec().baggerMount()),
                layout.resolveAttachmentPose(new MachineAttachmentSpec(
                        ToteToBagAttachmentPoint.PCR_OUTFEED,
                        0f,
                        0f,
                        0f,
                        0f)),
                conveyorAppearance);
        objects.add(baggingModule.getRenderable());
        registerInspectableObjects(inspectionRegistry, baggingModule);
        return new BaggingInstallation(baggingMachine, baggingModule, bagReceiver);
    }

    private void registerInspectableObjects(
            SelectionInspectionRegistry inspectionRegistry,
            BaggingModule baggingModule) {
        inspectionRegistry.register(baggingModule.getRenderable(), () -> List.of(
                "Type: Bagging machine",
                "State: " + baggingModule.getBaggingMachine().getState(),
                "Current group: " + (baggingModule.getBaggingMachine().getCurrentGroup() == null
                        ? "None"
                        : baggingModule.getBaggingMachine().getCurrentGroup().correlationId()),
                "Completed bags: " + baggingModule.getBaggingMachine().getCompletedCorrelationIds()));
    }
}
