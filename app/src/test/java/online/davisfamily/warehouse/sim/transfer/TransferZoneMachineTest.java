package online.davisfamily.warehouse.sim.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteBuilder;
import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

class TransferZoneMachineTest {

    @Test
    void shouldUseUpstreamSegmentEndForStandaloneApproachSensor() {
        RouteBuilder builder = new RouteBuilder();
        RouteSegment approach = builder.segment("approach", new LinearSegment3(
                new Vec3(0f, 0f, 0f),
                new Vec3(1f, 0f, 0f),
                false));
        RouteSegment transfer = builder.segment("transfer", new LinearSegment3(
                new Vec3(1f, 0f, 0f),
                new Vec3(2f, 0f, 0f),
                false));
        RouteSegment target = builder.segment("target", new LinearSegment3(
                new Vec3(1f, 0f, 0f),
                new Vec3(1f, 0f, 1f),
                false));
        builder.connect(approach, transfer);

        TransferTargetDecisionStrategy strategy =
                (tote, machine) -> Optional.of(TransferRoutingDecision.transferTo(
                        new TransferTarget(target, 0f, RouteFollower.TravelDirection.FORWARD)));
        TransferZone zone = new TransferZone(
                "standalone",
                0f,
                transfer.length(),
                transfer,
                target,
                0f,
                GuideSide.RIGHT,
                GuideSide.RIGHT,
                strategy,
                new TransferMotionConfig(0.35, 0f, 0f));
        SimulationWorld world = new SimulationWorld();
        TransferZoneMachine machine = TransferZoneMachine.createTransferZoneMachine(
                world,
                approach,
                0.5f,
                transfer,
                zone,
                strategy);
        Tote tote = tote("tote", approach, 0.4f);

        world.addTrackableObject(tote);
        world.update(0.2d);

        assertEquals(TransferZoneState.RESERVED, machine.getState());
        assertEquals("tote", machine.getReservedToteId());
    }

    private static Tote tote(String id, RouteSegment sourceSegment, float startDistance) {
        RenderableObject toteRenderable = RenderableToteFactory.createRenderableTote(
                id,
                null,
                new ToteGeometry(),
                true);
        return new Tote(
                id,
                new RouteFollower(id, sourceSegment, startDistance, 1.0d),
                toteRenderable,
                new Vec3(),
                0f);
    }
}
