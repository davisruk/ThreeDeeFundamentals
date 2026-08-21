package online.davisfamily.warehouse.sim.dsp.transport;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class RoutedPhysicalToteTest {

    @Test
    void shouldRetainExactValidatedPayloadAndDeriveRouteIdentity() {
        OsrOutboundRouteLaunchRequest launchRequest =
                RoutedPhysicalToteTestFixtures.launchRequest(
                        "tote-1", StationType.P2P, "p2p-1");
        ToteLoadPlan loadPlan = new ToteLoadPlan(
                new PhysicalToteId("tote-1"), List.of());
        Tote tote = RoutedPhysicalToteTestFixtures.tote(
                "tote-1", "tote-1", "tote-1");
        RenderableObject renderable = tote.getRenderable();

        RoutedPhysicalTote routedTote = new RoutedPhysicalTote(
                launchRequest, loadPlan, tote, renderable);

        assertSame(launchRequest, routedTote.launchRequest());
        assertSame(loadPlan, routedTote.loadPlan());
        assertSame(tote, routedTote.tote());
        assertSame(renderable, routedTote.renderable());
        assertSame(launchRequest.physicalToteId(), routedTote.physicalToteId());
        assertSame(launchRequest.destination(), routedTote.destination());
    }

    @Test
    void shouldRejectNullPayloadComponents() {
        OsrOutboundRouteLaunchRequest launchRequest =
                RoutedPhysicalToteTestFixtures.launchRequest(
                        "tote-1", StationType.P2P, "p2p-1");
        ToteLoadPlan loadPlan = new ToteLoadPlan("tote-1", List.of());
        Tote tote = RoutedPhysicalToteTestFixtures.tote(
                "tote-1", "tote-1", "tote-1");

        assertThrows(IllegalArgumentException.class, () ->
                new RoutedPhysicalTote(null, loadPlan, tote, tote.getRenderable()));
        assertThrows(IllegalArgumentException.class, () ->
                new RoutedPhysicalTote(launchRequest, null, tote, tote.getRenderable()));
        assertThrows(IllegalArgumentException.class, () ->
                new RoutedPhysicalTote(launchRequest, loadPlan, null, tote.getRenderable()));
        assertThrows(IllegalArgumentException.class, () ->
                new RoutedPhysicalTote(launchRequest, loadPlan, tote, null));
    }

    @Test
    void shouldRejectEveryPhysicalIdentityMismatch() {
        OsrOutboundRouteLaunchRequest launchRequest =
                RoutedPhysicalToteTestFixtures.launchRequest(
                        "tote-1", StationType.ADAPTING, "adapting-1");
        ToteLoadPlan matchingPlan = new ToteLoadPlan("tote-1", List.of());

        Tote matchingTote = RoutedPhysicalToteTestFixtures.tote(
                "tote-1", "tote-1", "tote-1");
        assertThrows(IllegalArgumentException.class, () -> new RoutedPhysicalTote(
                launchRequest,
                new ToteLoadPlan("other", List.of()),
                matchingTote,
                matchingTote.getRenderable()));

        Tote wrongTote = RoutedPhysicalToteTestFixtures.tote(
                "other", "tote-1", "tote-1");
        assertThrows(IllegalArgumentException.class, () -> new RoutedPhysicalTote(
                launchRequest, matchingPlan, wrongTote, wrongTote.getRenderable()));

        Tote wrongRenderable = RoutedPhysicalToteTestFixtures.tote(
                "tote-1", "other", "tote-1");
        assertThrows(IllegalArgumentException.class, () -> new RoutedPhysicalTote(
                launchRequest,
                matchingPlan,
                wrongRenderable,
                wrongRenderable.getRenderable()));

        Tote wrongFollower = RoutedPhysicalToteTestFixtures.tote(
                "tote-1", "tote-1", "other");
        assertThrows(IllegalArgumentException.class, () -> new RoutedPhysicalTote(
                launchRequest,
                matchingPlan,
                wrongFollower,
                wrongFollower.getRenderable()));
    }

    @Test
    void shouldRequireTheTotesExactRenderableAndRouteFollower() {
        OsrOutboundRouteLaunchRequest launchRequest =
                RoutedPhysicalToteTestFixtures.launchRequest(
                        "tote-1", StationType.THIRD_PARTY, "third-party-1");
        ToteLoadPlan loadPlan = new ToteLoadPlan("tote-1", List.of());
        Tote tote = RoutedPhysicalToteTestFixtures.tote(
                "tote-1", "tote-1", "tote-1");

        assertThrows(IllegalArgumentException.class, () -> new RoutedPhysicalTote(
                launchRequest,
                loadPlan,
                tote,
                RoutedPhysicalToteTestFixtures.renderable("tote-1")));

        Tote toteWithoutFollower = new Tote(
                "tote-1",
                null,
                RoutedPhysicalToteTestFixtures.renderable("tote-1"),
                new Vec3(),
                0f);
        assertThrows(IllegalArgumentException.class, () -> new RoutedPhysicalTote(
                launchRequest,
                loadPlan,
                toteWithoutFollower,
                toteWithoutFollower.getRenderable()));
    }
}
