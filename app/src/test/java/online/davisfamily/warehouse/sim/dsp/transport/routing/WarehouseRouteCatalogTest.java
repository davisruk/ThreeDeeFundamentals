package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class WarehouseRouteCatalogTest {

    @Test
    void shouldPreserveOrderedDefinitionsAndResolveEveryIdentity() {
        RouteSegment commonEntry = segment("common-entry", 10f);
        WarehouseRouteDefinition thirdParty = definition(
                StationType.THIRD_PARTY,
                "third-party-1",
                commonEntry,
                1f,
                TravelDirection.FORWARD,
                "third-party-sensor",
                segment("third-party-terminal", 2f));
        WarehouseRouteDefinition adapting = definition(
                StationType.ADAPTING,
                "bench-1",
                commonEntry,
                1f,
                TravelDirection.FORWARD,
                "adapting-sensor",
                segment("adapting-terminal", 2f));
        WarehouseRouteDefinition p2p = definition(
                StationType.P2P,
                "p2p-1",
                commonEntry,
                1f,
                TravelDirection.FORWARD,
                "p2p-sensor",
                segment("p2p-terminal", 2f));

        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(
                List.of(thirdParty, adapting, p2p));

        assertEquals(List.of(thirdParty, adapting, p2p), catalog.definitions());
        assertSame(commonEntry, catalog.commonEntrySegment());
        assertEquals(1f, catalog.entryDistance());
        assertEquals(TravelDirection.FORWARD, catalog.entryDirection());
        assertSame(thirdParty, catalog.find(thirdParty.destination()).orElseThrow());
        assertSame(adapting,
                catalog.find(StationType.ADAPTING, "  bench-1  ").orElseThrow());
        assertSame(p2p, catalog.findByTargetId("  p2p-1  ").orElseThrow());
        assertSame(thirdParty, catalog.findByTerminalSensorId(
                "  third-party-sensor  ").orElseThrow());
        assertTrue(catalog.findByTargetId("missing").isEmpty());
        assertTrue(catalog.findByTerminalSensorId("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class, () ->
                catalog.definitions().clear());
    }

    @Test
    void shouldAcceptDisconnectedTerminalSegmentsUntilTransferRoutingIsConfigured() {
        RouteSegment commonEntry = segment("common-entry", 10f);
        RouteSegment disconnectedTerminal = segment("disconnected-terminal", 2f);
        WarehouseRouteDefinition definition = definition(
                StationType.P2P,
                "p2p-1",
                commonEntry,
                0f,
                TravelDirection.FORWARD,
                "p2p-sensor",
                disconnectedTerminal);

        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(definition));

        assertSame(disconnectedTerminal,
                catalog.findByTargetId("p2p-1").orElseThrow().terminalSegment());
    }

    @Test
    void shouldRejectWrongStationForExistingTarget() {
        RouteSegment commonEntry = segment("common-entry", 10f);
        WarehouseRouteDefinition p2p = definition(
                StationType.P2P,
                "shared-target",
                commonEntry,
                0f,
                TravelDirection.FORWARD,
                "p2p-sensor",
                segment("p2p-terminal", 2f));
        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(p2p));

        assertThrows(IllegalArgumentException.class, () -> catalog.find(
                destination(StationType.ADAPTING, "shared-target")));
        assertThrows(IllegalArgumentException.class, () -> catalog.find(
                StationType.THIRD_PARTY, "shared-target"));
        assertTrue(catalog.find(
                StationType.ADAPTING, "unknown-target").isEmpty());
    }

    @Test
    void shouldRejectDuplicateAndInconsistentTopologyConfiguration() {
        RouteSegment commonEntry = segment("common-entry", 10f);
        RouteSegment otherEntry = segment("common-entry", 10f);
        WarehouseRouteDefinition p2p = definition(
                StationType.P2P,
                "shared-target",
                commonEntry,
                1f,
                TravelDirection.FORWARD,
                "shared-sensor",
                segment("p2p-terminal", 2f));

        assertThrows(IllegalArgumentException.class, () -> new WarehouseRouteCatalog(List.of(
                p2p,
                definition(
                        StationType.ADAPTING,
                        "shared-target",
                        commonEntry,
                        1f,
                        TravelDirection.FORWARD,
                        "other-sensor",
                        segment("adapting-terminal", 2f)))));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRouteCatalog(List.of(
                p2p,
                definition(
                        StationType.ADAPTING,
                        "bench-1",
                        commonEntry,
                        1f,
                        TravelDirection.FORWARD,
                        "shared-sensor",
                        segment("adapting-terminal", 2f)))));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRouteCatalog(List.of(
                p2p,
                definition(
                        StationType.ADAPTING,
                        "bench-1",
                        otherEntry,
                        1f,
                        TravelDirection.FORWARD,
                        "adapting-sensor",
                        segment("adapting-terminal", 2f)))));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRouteCatalog(List.of(
                p2p,
                definition(
                        StationType.ADAPTING,
                        "bench-1",
                        commonEntry,
                        2f,
                        TravelDirection.FORWARD,
                        "adapting-sensor",
                        segment("adapting-terminal", 2f)))));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseRouteCatalog(List.of(
                p2p,
                definition(
                        StationType.ADAPTING,
                        "bench-1",
                        commonEntry,
                        1f,
                        TravelDirection.REVERSE,
                        "adapting-sensor",
                        segment("adapting-terminal", 2f)))));
    }

    @Test
    void shouldRejectInvalidCatalogAndLookupInput() {
        RouteSegment commonEntry = segment("common-entry", 10f);
        WarehouseRouteDefinition p2p = definition(
                StationType.P2P,
                "p2p-1",
                commonEntry,
                0f,
                TravelDirection.FORWARD,
                "p2p-sensor",
                segment("p2p-terminal", 2f));
        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(p2p));

        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalog(null));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalog(List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalog(Arrays.asList(p2p, null)));
        assertThrows(IllegalArgumentException.class, () -> catalog.find(null));
        assertThrows(IllegalArgumentException.class, () -> catalog.find(null, "p2p-1"));
        assertThrows(IllegalArgumentException.class, () ->
                catalog.find(StationType.P2P, null));
        assertThrows(IllegalArgumentException.class, () ->
                catalog.findByTargetId(" "));
        assertThrows(IllegalArgumentException.class, () ->
                catalog.findByTerminalSensorId(null));
    }

    private static WarehouseRouteDefinition definition(
            StationType stationType,
            String targetId,
            RouteSegment commonEntry,
            float entryDistance,
            TravelDirection entryDirection,
            String terminalSensorId,
            RouteSegment terminalSegment) {
        return new WarehouseRouteDefinition(
                destination(stationType, targetId),
                commonEntry,
                entryDistance,
                entryDirection,
                terminalSensorId,
                terminalSegment);
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
