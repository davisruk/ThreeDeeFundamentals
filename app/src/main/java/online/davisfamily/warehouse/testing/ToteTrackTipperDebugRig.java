package online.davisfamily.warehouse.testing;

import java.util.List;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.assembly.SortingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.SortingSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSection;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSection;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSectionInstaller;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;

public class ToteTrackTipperDebugRig implements DebugSceneRuntime {
    private final TipperToSorterSection section;
    private final SorterOutfeedDebugConveyor sorterOutfeedDebugConveyor;

    public ToteTrackTipperDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        ForwardingPackReceiveTarget sorterOutfeedTarget = new ForwardingPackReceiveTarget();
        TipperTrackSection trackSection = new TipperTrackSectionInstaller().install(
                tr,
                objects,
                TipperEntryLayoutSpec.debugDefaults());
        TipperDemoFixtures.DemoTipperFeed demoTipperFeed = TipperDemoFixtures.createDemoTipperFeed(tr, sim, trackSection);
        TipperInstallation tipperInstallation = new TipperSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                trackSection,
                demoTipperFeed.totePayload(),
                demoTipperFeed.toteLoadPlanProvider());
        SortingInstallation sortingInstallation = new SortingSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                trackSection.getLayoutSpec(),
                tipperInstallation.getTipperModule().sorterIntakeMountLocalPoint());
        sorterOutfeedDebugConveyor = new SorterOutfeedDebugConveyor(
                tr,
                sim,
                objects,
                inspectionRegistry,
                sortingInstallation.getSortingModule().outfeedPoint(),
                null);
        sorterOutfeedTarget.setDelegate(sorterOutfeedDebugConveyor);
        section = new TipperToSorterSectionInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                tipperInstallation,
                sortingInstallation,
                sorterOutfeedTarget);
    }

    @Override
    public void syncVisuals() {
        section.syncVisuals();
        sorterOutfeedDebugConveyor.syncVisuals(section::getPackRenderable);
    }
}
