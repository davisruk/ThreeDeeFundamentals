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
        return createDemoTipperFeed(
                tr,
                sim,
                trackSection,
                "tote-1",
                true,
                List.of(
                        new PackPlan("pack-a1", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b1", "bag-b", MEDIUM_PACK),
                        new PackPlan("pack-c1", "bag-c", LONG_PACK),
                        new PackPlan("pack-d1", "bag-d", SMALL_PACK),
                        new PackPlan("pack-e1", "bag-e", MEDIUM_PACK),
                        new PackPlan("pack-f1", "bag-f", LONG_PACK),
                        new PackPlan("pack-g1", "bag-g", SMALL_PACK),
                        new PackPlan("pack-h1", "bag-h", MEDIUM_PACK),
                        new PackPlan("pack-i1", "bag-i", SMALL_PACK),
                        new PackPlan("pack-j1", "bag-j", LONG_PACK),
                        new PackPlan("pack-a2", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b2", "bag-b", MEDIUM_PACK),
                        new PackPlan("pack-c2", "bag-c", LONG_PACK),
                        new PackPlan("pack-d2", "bag-d", MEDIUM_PACK),
                        new PackPlan("pack-e2", "bag-e", SMALL_PACK),
                        new PackPlan("pack-f2", "bag-f", SMALL_PACK),
                        new PackPlan("pack-g2", "bag-g", MEDIUM_PACK),
                        new PackPlan("pack-h2", "bag-h", LONG_PACK),
                        new PackPlan("pack-i2", "bag-i", MEDIUM_PACK),
                        new PackPlan("pack-j2", "bag-j", SMALL_PACK)));
    }

    public static DemoTipperFeedSet createDemoTipperFeedSet(
            TriangleRenderer tr,
            SimulationWorld sim,
            TipperTrackSection trackSection) {
        if (tr == null || sim == null || trackSection == null) {
            throw new IllegalArgumentException("Demo tote payload inputs must not be null");
        }

        DemoTipperFeed firstFeed = createDemoTipperFeed(
                tr,
                sim,
                trackSection,
                "tote-1",
                true,
                List.of(
                        new PackPlan("pack-a1", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b1", "bag-b", MEDIUM_PACK),
                        new PackPlan("pack-c1", "bag-c", LONG_PACK),
                        new PackPlan("pack-d1", "bag-d", SMALL_PACK),
                        new PackPlan("pack-e1", "bag-e", MEDIUM_PACK),
                        new PackPlan("pack-f1", "bag-f", LONG_PACK),
                        new PackPlan("pack-g1", "bag-g", SMALL_PACK),
                        new PackPlan("pack-h1", "bag-h", MEDIUM_PACK),
                        new PackPlan("pack-i1", "bag-i", SMALL_PACK),
                        new PackPlan("pack-j1", "bag-j", LONG_PACK)));
        DemoTipperFeed secondFeed = createDemoTipperFeed(
                tr,
                sim,
                trackSection,
                "tote-2",
                false,
                List.of(
                        new PackPlan("pack-a2", "bag-a", SMALL_PACK),
                        new PackPlan("pack-b2", "bag-b", MEDIUM_PACK),
                        new PackPlan("pack-c2", "bag-c", LONG_PACK),
                        new PackPlan("pack-d2", "bag-d", MEDIUM_PACK),
                        new PackPlan("pack-e2", "bag-e", SMALL_PACK),
                        new PackPlan("pack-f2", "bag-f", SMALL_PACK),
                        new PackPlan("pack-g2", "bag-g", MEDIUM_PACK),
                        new PackPlan("pack-h2", "bag-h", LONG_PACK),
                        new PackPlan("pack-i2", "bag-i", MEDIUM_PACK),
                        new PackPlan("pack-j2", "bag-j", SMALL_PACK)));

        ToteLoadPlanProvider toteLoadPlanProvider = toteId -> {
            if (firstFeed.toteLoadPlan().getToteId().equals(toteId)) {
                return firstFeed.toteLoadPlan();
            }
            if (secondFeed.toteLoadPlan().getToteId().equals(toteId)) {
                return secondFeed.toteLoadPlan();
            }
            return null;
        };
        return new DemoTipperFeedSet(List.of(firstFeed, secondFeed), toteLoadPlanProvider);
    }

    private static DemoTipperFeed createDemoTipperFeed(
            TriangleRenderer tr,
            SimulationWorld sim,
            TipperTrackSection trackSection,
            String toteId,
            boolean registerInSimulation,
            List<PackPlan> packPlans) {
        if (tr == null || sim == null || trackSection == null) {
            throw new IllegalArgumentException("Demo tote payload inputs must not be null");
        }

        ToteGeometry toteGeometry = new ToteGeometry();
        var toteRenderable = RenderableToteFactory.createRenderableTote(toteId, tr, toteGeometry, true);
        float toteInteriorFloorLocalY = 0.04f + (toteGeometry.getOuterHeight() - toteGeometry.getInnerHeight()) + 0.01f;
        Tote tote = new Tote(
                toteId,
                new RouteFollower(toteId, trackSection.getInfeedSegment(), 0f, 1.4d),
                toteRenderable,
                new Vec3(0f, trackSection.getToteYOffset(), 0f),
                toteRenderable.yawOffsetRadians);
        tote.openLids();

        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(tote.getId(), packPlans);
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
        if (registerInSimulation) {
            sim.addTrackableObject(tote);
        }
        ToteLoadPlanProvider toteLoadPlanProvider = candidateToteId -> toteLoadPlan.getToteId().equals(candidateToteId) ? toteLoadPlan : null;
        return new DemoTipperFeed(toteLoadPlan, totePayload, toteLoadPlanProvider);
    }

    public record DemoTipperFeed(
            ToteLoadPlan toteLoadPlan,
            TipperTotePayload totePayload,
            ToteLoadPlanProvider toteLoadPlanProvider) {

        public DemoTipperFeed {
            if (toteLoadPlan == null || totePayload == null || toteLoadPlanProvider == null) {
                throw new IllegalArgumentException("Demo tipper feed inputs must not be null");
            }
        }
    }

    public record DemoTipperFeedSet(
            List<DemoTipperFeed> demoTipperFeeds,
            ToteLoadPlanProvider toteLoadPlanProvider) {

        public DemoTipperFeedSet {
            if (demoTipperFeeds == null || demoTipperFeeds.isEmpty() || toteLoadPlanProvider == null) {
                throw new IllegalArgumentException("Demo tipper feed set inputs must not be null or empty");
            }
            demoTipperFeeds = List.copyOf(demoTipperFeeds);
        }

        public DemoTipperFeed primaryFeed() {
            return demoTipperFeeds.getFirst();
        }

        public List<DemoTipperFeed> additionalFeeds() {
            if (demoTipperFeeds.size() <= 1) {
                return List.of();
            }
            return List.copyOf(demoTipperFeeds.subList(1, demoTipperFeeds.size()));
        }
    }
}
