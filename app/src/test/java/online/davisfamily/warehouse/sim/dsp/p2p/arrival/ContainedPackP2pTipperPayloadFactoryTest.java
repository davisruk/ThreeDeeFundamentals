package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class ContainedPackP2pTipperPayloadFactoryTest {

    @Test
    void shouldCreateEmptyPayloadWithExactRoutedIdentities() {
        RoutedPhysicalTote routedTote = routedTote("tote-1", List.of());
        ContainedPackP2pTipperPayloadFactory factory = factory();

        TipperTotePayload payload = factory.create(routedTote);

        assertSame(routedTote.tote(), payload.getTote());
        assertSame(routedTote.renderable(), payload.getToteRenderable());
        assertEquals(0.10f, payload.getToteInteriorFloorLocalY());
        assertEquals(0, payload.getContainedPackLayoutById().size());
    }

    @Test
    void shouldLayoutExactPlanPackIdsUsingSuppliedInteriorGeometry() {
        PackPlan first = new PackPlan(
                "pack-1", "bag-1", new PackDimensions(0.20f, 0.10f, 0.05f));
        PackPlan second = new PackPlan(
                "pack-2", "bag-1", new PackDimensions(0.30f, 0.10f, 0.08f));
        RoutedPhysicalTote routedTote = routedTote("tote-1", List.of(first, second));

        TipperTotePayload payload = factory().create(routedTote);

        assertEquals(2, payload.getContainedPackLayoutById().size());
        assertEquals(
                java.util.Set.of("pack-1", "pack-2"),
                payload.getContainedPackLayoutById().keySet());
        assertVectorEquals(
                new Vec3(-0.40f, 0.125f, -0.20f),
                payload.getContainedPackLayoutById().get("pack-1"));
        assertVectorEquals(
                new Vec3(-0.14f, 0.14f, -0.20f),
                payload.getContainedPackLayoutById().get("pack-2"));
        assertSame(first, routedTote.loadPlan().getPackPlans().get(0));
        assertSame(second, routedTote.loadPlan().getPackPlans().get(1));
    }

    @Test
    void shouldRejectOversizedPackWithoutChangingRoutedPlan() {
        PackPlan oversized = new PackPlan(
                "pack-wide", "bag-1", new PackDimensions(1.01f, 0.10f, 0.05f));
        RoutedPhysicalTote routedTote = routedTote("tote-1", List.of(oversized));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> factory().create(routedTote));

        assertEquals("Pack pack-wide length exceeds container width", exception.getMessage());
        assertEquals(List.of(oversized), routedTote.loadPlan().getPackPlans());
    }

    @Test
    void shouldRejectNullInputAndInvalidGeometryConfiguration() {
        ContainedPackP2pTipperPayloadFactory factory = factory();

        assertThrows(IllegalArgumentException.class, () -> factory.create(null));
        assertThrows(IllegalArgumentException.class, () -> new ContainedPackP2pTipperPayloadFactory(
                0f, 0.50f, 0.10f, 0.01f, 0.01f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new ContainedPackP2pTipperPayloadFactory(
                1f, Float.NaN, 0.10f, 0.01f, 0.01f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new ContainedPackP2pTipperPayloadFactory(
                1f, 0.50f, Float.POSITIVE_INFINITY, 0.01f, 0.01f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new ContainedPackP2pTipperPayloadFactory(
                1f, 0.50f, 0.10f, -0.01f, 0.01f, 0.01f));
        assertThrows(IllegalArgumentException.class,
                () -> new TipperToSorterP2pArrivalAcceptedListener(null));
    }

    private static ContainedPackP2pTipperPayloadFactory factory() {
        return new ContainedPackP2pTipperPayloadFactory(
                1f, 0.50f, 0.10f, 0.01f, 0.01f, 0.01f);
    }

    private static RoutedPhysicalTote routedTote(
            String physicalToteId,
            List<PackPlan> packPlans) {
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P, "p2p-1");
        RouteSegment terminal = new RouteSegment(
                "terminal",
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
        RoutedPhysicalTote empty = P2pArrivalRuntimeTestFixtures.routedTote(
                physicalToteId, destination, terminal);
        return new RoutedPhysicalTote(
                empty.launchRequest(),
                new ToteLoadPlan(empty.physicalToteId(), packPlans),
                empty.tote(),
                empty.renderable());
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.z, actual.z, 0.0001f);
    }
}
