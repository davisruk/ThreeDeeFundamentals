package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class WarehouseRouteDefinitionTest {

    @Test
    void shouldPreserveExactTopologyAndNormalizeTerminalSensorId() {
        OperationalRouteDestination destination = destination(
                StationType.P2P, "p2p-1");
        RouteSegment entry = segment("common-entry", 10f);
        RouteSegment terminal = segment("p2p-terminal", 4f);

        WarehouseRouteDefinition definition = new WarehouseRouteDefinition(
                destination,
                entry,
                2.5f,
                TravelDirection.REVERSE,
                "  p2p-arrival-sensor  ",
                terminal);

        assertSame(destination, definition.destination());
        assertSame(entry, definition.entrySegment());
        assertEquals(2.5f, definition.entryDistance());
        assertEquals(TravelDirection.REVERSE, definition.entryDirection());
        assertEquals("p2p-arrival-sensor", definition.terminalArrivalSensorId());
        assertSame(terminal, definition.terminalSegment());
    }

    @Test
    void shouldAllowEntryAtEitherSegmentBoundary() {
        RouteSegment entry = segment("entry", 10f);
        RouteSegment terminal = segment("terminal", 2f);

        assertEquals(0f, definition(entry, 0f, terminal).entryDistance());
        assertEquals(10f, definition(entry, 10f, terminal).entryDistance());
    }

    @Test
    void shouldRejectInvalidDefinitionState() {
        RouteSegment entry = segment("entry", 10f);
        RouteSegment terminal = segment("terminal", 2f);

        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteDefinition(
                        null, entry, 0f, TravelDirection.FORWARD, "sensor", terminal));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteDefinition(
                        destination(StationType.P2P, "p2p-1"),
                        null, 0f, TravelDirection.FORWARD, "sensor", terminal));
        for (float invalidDistance : new float[] {
                -0.1f,
                10.1f,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        }) {
            assertThrows(IllegalArgumentException.class, () ->
                    definition(entry, invalidDistance, terminal));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteDefinition(
                        destination(StationType.P2P, "p2p-1"),
                        entry, 0f, null, "sensor", terminal));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteDefinition(
                        destination(StationType.P2P, "p2p-1"),
                        entry, 0f, TravelDirection.FORWARD, null, terminal));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteDefinition(
                        destination(StationType.P2P, "p2p-1"),
                        entry, 0f, TravelDirection.FORWARD, " ", terminal));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteDefinition(
                        destination(StationType.P2P, "p2p-1"),
                        entry, 0f, TravelDirection.FORWARD, "sensor", null));
    }

    private static WarehouseRouteDefinition definition(
            RouteSegment entry,
            float entryDistance,
            RouteSegment terminal) {
        return new WarehouseRouteDefinition(
                destination(StationType.P2P, "p2p-1"),
                entry,
                entryDistance,
                TravelDirection.FORWARD,
                "sensor",
                terminal);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return RoutedToteRoutingTestFixtures.destination(stationType, targetId);
    }

    private static RouteSegment segment(String label, float length) {
        return RoutedToteRoutingTestFixtures.routeSegment(label, length);
    }
}
