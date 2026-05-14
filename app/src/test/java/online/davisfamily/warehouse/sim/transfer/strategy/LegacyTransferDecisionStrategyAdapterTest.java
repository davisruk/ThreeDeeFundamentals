package online.davisfamily.warehouse.sim.transfer.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;

class LegacyTransferDecisionStrategyAdapterTest {

    @Test
    void shouldRejectNullLegacyStrategy() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyTransferDecisionStrategyAdapter(null));

        assertEquals("legacyStrategy must not be null", ex.getMessage());
    }

    @Test
    void shouldAdaptContinueDecision() {
        RouteSegment source = segment("source", 0f, 1f);
        RouteSegment target = segment("target", 2f, 3f);
        Tote tote = tote(source);
        TransferZoneMachine machine = machine(source, target);
        LegacyTransferDecisionStrategyAdapter adapter = new LegacyTransferDecisionStrategyAdapter(
                (ignoredTote, ignoredMachine) -> Optional.of(TransferDecision.CONTINUE));

        Optional<TransferRoutingDecision> decision = adapter.decide(tote, machine);

        assertTrue(decision.isPresent());
        assertTrue(decision.get().isContinueOnCurrentRoute());
        assertFalse(decision.get().isTransfer());
    }

    @Test
    void shouldAdaptBranchDecisionUsingZoneTargetAndCurrentTravelDirection() {
        RouteSegment source = segment("source", 0f, 1f);
        RouteSegment target = segment("target", 2f, 3f);
        Tote tote = tote(source);
        tote.getRouteFollower().setTravelDirection(TravelDirection.REVERSE);
        TransferZoneMachine machine = machine(source, target);
        LegacyTransferDecisionStrategyAdapter adapter = new LegacyTransferDecisionStrategyAdapter(
                (ignoredTote, ignoredMachine) -> Optional.of(TransferDecision.BRANCH));

        Optional<TransferRoutingDecision> decision = adapter.decide(tote, machine);

        assertTrue(decision.isPresent());
        assertTrue(decision.get().isTransfer());
        assertSame(target, decision.get().transferTarget().segment());
        assertEquals(0f, decision.get().transferTarget().entryDistance());
        assertEquals(TravelDirection.REVERSE, decision.get().transferTarget().travelDirection());
    }

    private static TransferZoneMachine machine(RouteSegment source, RouteSegment target) {
        TransferZone zone = new TransferZone(
                "zone",
                0f,
                1f,
                source,
                target,
                0f,
                GuideSide.RIGHT,
                GuideSide.LEFT,
                (TransferDecisionStrategy) (ignoredTote, ignoredMachine) -> Optional.of(TransferDecision.BRANCH),
                new TransferMotionConfig(0.35, 0f, 0f));
        return new TransferZoneMachine("machine", "approach", "window", zone);
    }

    private static Tote tote(RouteSegment sourceSegment) {
        RenderableObject toteRenderable = RenderableToteFactory.createRenderableTote(
                "tote",
                null,
                new ToteGeometry(),
                true);
        return new Tote(
                "tote",
                new RouteFollower("tote", sourceSegment, 0f, 1.0d),
                toteRenderable,
                new Vec3(),
                0f);
    }

    private static RouteSegment segment(String label, float startX, float endX) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(startX, 0f, 0f),
                        new Vec3(endX, 0f, 0f),
                        false));
    }
}
