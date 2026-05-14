package online.davisfamily.warehouse.sim.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;

class TransferZoneControllerTest {

    @Test
    void shouldBeginTransferUsingSelectedRoutingTargetRatherThanZoneDefault() {
        RouteSegment sourceSegment = segment("source", new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f));
        RouteSegment zoneDefaultTarget = segment("zone-default", new Vec3(2f, 0f, 0f), new Vec3(3f, 0f, 0f));
        RouteSegment selectedTarget = segment("selected", new Vec3(4f, 0f, 0f), new Vec3(5f, 0f, 0f));
        TransferZone zone = new TransferZone(
                "zone",
                0f,
                1f,
                sourceSegment,
                zoneDefaultTarget,
                0f,
                GuideSide.RIGHT,
                GuideSide.LEFT,
                legacyBranchStrategy(),
                new TransferMotionConfig(0.35, 0f, 0f));
        TransferZoneMachine machine = new TransferZoneMachine("machine", "approach", "window", zone);
        TransferZoneController controller = new TransferZoneController(machine, legacyBranchStrategy());
        Tote tote = tote("tote", sourceSegment);
        SimulationContext context = new SimulationContext();

        context.addTrackedObject(tote);
        tote.update(context, 0d);

        controller.handleDetection(new DetectionEvent(
                "sensor-source",
                0d,
                "approach",
                "tote",
                DetectionType.ENTER), context);

        machine.setActiveRoutingDecision(TransferRoutingDecision.transferTo(
                new TransferTarget(selectedTarget, 0f, TravelDirection.REVERSE)));
        controller.handleDetection(new DetectionEvent(
                "sensor-source",
                0d,
                "window",
                "tote",
                DetectionType.ENTER), context);

        for (int i = 0; i < 10 && tote.getInteractionMode() == Tote.ToteMotionState.TRANSFERRING; i++) {
            tote.update(context, 0.5d);
        }

        assertEquals(TransferZoneState.TRANSFERRING, machine.getState());
        assertEquals(selectedTarget, tote.getRouteFollower().getCurrentSegment());
        assertEquals(TravelDirection.REVERSE, tote.getRouteFollower().getTravelDirection());
    }

    private static TransferDecisionStrategy legacyBranchStrategy() {
        return (tote, machine) -> Optional.of(TransferDecision.BRANCH);
    }

    private static RouteSegment segment(String label, Vec3 start, Vec3 end) {
        return new RouteSegment(label, new LinearSegment3(start, end, false));
    }

    private static Tote tote(String id, RouteSegment sourceSegment) {
        RenderableObject toteRenderable = RenderableToteFactory.createRenderableTote(
                id,
                null,
                new ToteGeometry(),
                true);
        return new Tote(
                id,
                new RouteFollower(id, sourceSegment, 0f, 1.0d),
                toteRenderable,
                new Vec3(),
                0f);
    }
}
