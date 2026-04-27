package online.davisfamily.warehouse.testing;

import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTrackSection;
import online.davisfamily.warehouse.sim.totebag.layout.ContainedPackLayout;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

public final class TipperDemoFixtures {
    private static final float CONTAINED_PACK_GAP_X = 0.012f;
    private static final float CONTAINED_PACK_GAP_Z = 0.012f;
    private static final float CONTAINED_PACK_GAP_Y = 0.010f;
    private static final PackDimensions SMALL_PACK = new PackDimensions(0.070f, 0.045f, 0.035f);
    private static final PackDimensions MEDIUM_PACK = new PackDimensions(0.080f, 0.050f, 0.040f);
    private static final PackDimensions LONG_PACK = new PackDimensions(0.090f, 0.055f, 0.040f);

    private TipperDemoFixtures() {
    }

    public static DemoTipperFeed createDemoTipperFeed(
            TriangleRenderer tr,
            SimulationWorld sim,
            TipperTrackSection trackSection) {
        if (tr == null || sim == null || trackSection == null) {
            throw new IllegalArgumentException("Demo tote payload inputs must not be null");
        }

        ToteGeometry toteGeometry = new ToteGeometry();
        var toteRenderable = RenderableToteFactory.createRenderableTote("tipper_demo_tote", tr, toteGeometry, true);
        float toteInteriorFloorLocalY = 0.04f + (toteGeometry.getOuterHeight() - toteGeometry.getInnerHeight()) + 0.01f;
        Tote tote = new Tote(
                toteRenderable.id,
                new RouteFollower(toteRenderable.id, trackSection.getInfeedSegment(), 0f, 1.4d),
                toteRenderable,
                new Vec3(0f, trackSection.getToteYOffset(), 0f),
                toteRenderable.yawOffsetRadians);
        tote.openLids();
        sim.addTrackableObject(tote);

        ToteLoadPlan toteLoadPlan = createDemoPlan(tote.getId());
        Map<String, Vec3> containedPackLayoutById = new ContainedPackLayout(
                toteGeometry.getInnerBottomWidth(),
                toteGeometry.getInnerBottomDepth(),
                toteInteriorFloorLocalY,
                CONTAINED_PACK_GAP_X,
                CONTAINED_PACK_GAP_Z,
                CONTAINED_PACK_GAP_Y)
                        .layoutPackPlans(toteLoadPlan.getPackPlans());

        TipperTotePayload totePayload = new TipperTotePayload(
                tote,
                toteRenderable,
                toteInteriorFloorLocalY,
                containedPackLayoutById);
        ToteLoadPlanProvider toteLoadPlanProvider = toteId -> toteLoadPlan.getToteId().equals(toteId) ? toteLoadPlan : null;
        return new DemoTipperFeed(totePayload, toteLoadPlanProvider);
    }

    private static ToteLoadPlan createDemoPlan(String toteId) {
        return new ToteLoadPlan(
                toteId,
                List.of(
                        new PackPlan("pack-a1", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b1", "bag-b", MEDIUM_PACK),
                        new PackPlan("pack-c1", "bag-c", LONG_PACK),
                        new PackPlan("pack-a2", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b2", "bag-b", MEDIUM_PACK),
                        new PackPlan("pack-c2", "bag-c", LONG_PACK),
                        new PackPlan("pack-a3", "bag-a", MEDIUM_PACK),
                        new PackPlan("pack-b3", "bag-b", SMALL_PACK),
                        new PackPlan("pack-c3", "bag-c", MEDIUM_PACK),
                        new PackPlan("pack-a4", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b4", "bag-b", LONG_PACK),
                        new PackPlan("pack-c4", "bag-c", SMALL_PACK),
                        new PackPlan("pack-a5", "bag-a", LONG_PACK),
                        new PackPlan("pack-b5", "bag-b", MEDIUM_PACK),
                        new PackPlan("pack-c5", "bag-c", SMALL_PACK),
                        new PackPlan("pack-a6", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b6", "bag-b", SMALL_PACK),
                        new PackPlan("pack-c6", "bag-c", MEDIUM_PACK),
                        new PackPlan("pack-a7", "bag-a", MEDIUM_PACK),
                        new PackPlan("pack-b7", "bag-b", SMALL_PACK)));
    }

    public record DemoTipperFeed(
            TipperTotePayload totePayload,
            ToteLoadPlanProvider toteLoadPlanProvider) {

        public DemoTipperFeed {
            if (totePayload == null || toteLoadPlanProvider == null) {
                throw new IllegalArgumentException("Demo tipper feed inputs must not be null");
            }
        }
    }
}
