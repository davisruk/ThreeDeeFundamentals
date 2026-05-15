package online.davisfamily.threedee.sim.framework.objects.sensors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.sim.tote.Tote;

class WindowSensorTest {

    @Test
    void shouldEmitEnterForSecondTrackableAfterFirstLeavesWindowSegment() {
        RouteSegment windowSegment = segment("window", new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f));
        RouteSegment otherSegment = segment("other", new Vec3(0f, 0f, 1f), new Vec3(1f, 0f, 1f));
        WindowSensor sensor = new WindowSensor(
                "sensor",
                new WindowSensorAreaImpl("area", windowSegment, 0f, 1f));
        SimulationContext context = new SimulationContext();
        Tote first = tote("first", windowSegment, 0.25f);
        Tote second = tote("second", windowSegment, 0.25f);

        context.addTrackedObject(first);
        first.update(context, 0d);
        sensor.update(context, 0d);
        assertDetection(context, "first", DetectionType.ENTER);

        first.getRouteFollower().setCurrentSegment(otherSegment);
        first.getRouteFollower().setDistanceAlongSegment(0.25f);
        first.update(context, 0d);
        sensor.update(context, 0d);
        assertDetection(context, "first", DetectionType.EXIT);

        context.addTrackedObject(second);
        second.update(context, 0d);
        sensor.update(context, 0d);
        assertDetection(context, "second", DetectionType.ENTER);
    }

    private static void assertDetection(
            SimulationContext context,
            String objectId,
            DetectionType detectionType) {
        DetectionEvent event = (DetectionEvent) context.getEventQueue().remove();

        assertEquals(objectId, event.getObjectId());
        assertEquals(detectionType, event.getDetectionType());
    }

    private static RouteSegment segment(String label, Vec3 start, Vec3 end) {
        return new RouteSegment(label, new LinearSegment3(start, end, false));
    }

    private static Tote tote(String id, RouteSegment segment, float startDistance) {
        RenderableObject renderable = RenderableToteFactory.createRenderableTote(
                id,
                null,
                new ToteGeometry(),
                true);
        return new Tote(
                id,
                new RouteFollower(id, segment, startDistance, 1.0d),
                renderable,
                new Vec3(),
                0f);
    }
}
