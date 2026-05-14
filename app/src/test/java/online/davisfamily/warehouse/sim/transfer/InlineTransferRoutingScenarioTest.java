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
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine.TransferZoneState;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

class InlineTransferRoutingScenarioTest {

    @Test
    void shouldRouteInlineTransferToLeftTarget() {
        RouteSegment leftTarget = scenarioLeftTarget();
        RouteScenario scenario = scenario(leftTarget, scenarioRightTarget(), new TransferTarget(leftTarget, 0f, TravelDirection.FORWARD));

        scenario.runTransfer();

        assertEquals(TransferZoneState.TRANSFERRING, scenario.machine.getState());
        assertEquals(scenario.leftTarget, scenario.tote.getRouteFollower().getCurrentSegment());
        assertEquals(TravelDirection.FORWARD, scenario.tote.getRouteFollower().getTravelDirection());
    }

    @Test
    void shouldRouteInlineTransferToRightTargetWithReverseDirection() {
        RouteSegment rightTarget = scenarioRightTarget();
        RouteScenario scenario = scenario(scenarioLeftTarget(), rightTarget, new TransferTarget(rightTarget, 0f, TravelDirection.REVERSE));

        scenario.runTransfer();

        assertEquals(TransferZoneState.TRANSFERRING, scenario.machine.getState());
        assertEquals(scenario.rightTarget, scenario.tote.getRouteFollower().getCurrentSegment());
        assertEquals(TravelDirection.REVERSE, scenario.tote.getRouteFollower().getTravelDirection());
    }

    private static RouteScenario scenario(
            RouteSegment leftTarget,
            RouteSegment rightTarget,
            TransferTarget selectedTarget) {
        RouteSegment source = segment("source", new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f));
        TransferTargetDecisionStrategy strategy =
                (tote, machine) -> Optional.of(TransferRoutingDecision.transferTo(selectedTarget));
        TransferZone zone = new TransferZone(
                "inline",
                0f,
                1f,
                source,
                leftTarget,
                0f,
                GuideSide.RIGHT,
                GuideSide.LEFT,
                strategy,
                new TransferMotionConfig(0.35, 0f, 0f));
        TransferZoneMachine machine = new TransferZoneMachine("machine", "approach", "window", zone);
        TransferZoneController controller = new TransferZoneController(machine, strategy);
        Tote tote = tote("tote", source);
        SimulationContext context = new SimulationContext();
        context.addTrackedObject(tote);

        return new RouteScenario(controller, machine, tote, context, leftTarget, rightTarget);
    }

    private static RouteSegment scenarioLeftTarget() {
        return segment("left", new Vec3(2f, 0f, 1f), new Vec3(3f, 0f, 1f));
    }

    private static RouteSegment scenarioRightTarget() {
        return segment("right", new Vec3(2f, 0f, -1f), new Vec3(1f, 0f, -1f));
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

    private record RouteScenario(
            TransferZoneController controller,
            TransferZoneMachine machine,
            Tote tote,
            SimulationContext context,
            RouteSegment leftTarget,
            RouteSegment rightTarget) {

        private void runTransfer() {
            tote.update(context, 0d);
            controller.handleDetection(new DetectionEvent(
                    "sensor-source",
                    0d,
                    "approach",
                    tote.getId(),
                    DetectionType.ENTER), context);
            controller.handleDetection(new DetectionEvent(
                    "sensor-source",
                    0d,
                    "window",
                    tote.getId(),
                    DetectionType.ENTER), context);
            for (int i = 0; i < 10 && tote.getInteractionMode() == Tote.ToteMotionState.TRANSFERRING; i++) {
                tote.update(context, 0.5d);
            }
        }
    }
}
