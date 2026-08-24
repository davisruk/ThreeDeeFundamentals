package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteConnection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;

public final class P2pArrivalRouteBinding {
    private final RouteSegment terminalSegment;
    private final RouteSegment tipperEntrySegment;
    private final int hopCount;

    public P2pArrivalRouteBinding(
            RouteSegment terminalSegment,
            RouteSegment tipperEntrySegment) {
        if (terminalSegment == null) {
            throw new IllegalArgumentException("terminalSegment must not be null");
        }
        if (tipperEntrySegment == null) {
            throw new IllegalArgumentException("tipperEntrySegment must not be null");
        }
        this.terminalSegment = terminalSegment;
        this.tipperEntrySegment = tipperEntrySegment;
        this.hopCount = validateDeterministicForwardPath(
                terminalSegment,
                tipperEntrySegment);
    }

    public String terminalSegmentLabel() {
        return terminalSegment.getLabel();
    }

    public String tipperEntrySegmentLabel() {
        return tipperEntrySegment.getLabel();
    }

    public int hopCount() {
        return hopCount;
    }

    public boolean matchesTerminalSegment(RouteSegment candidate) {
        return candidate == terminalSegment;
    }

    public boolean matchesTipperEntrySegment(RouteSegment candidate) {
        return candidate == tipperEntrySegment;
    }

    private static int validateDeterministicForwardPath(
            RouteSegment terminalSegment,
            RouteSegment tipperEntrySegment) {
        if (terminalSegment == tipperEntrySegment) {
            return 0;
        }

        Set<RouteSegment> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        RouteSegment current = terminalSegment;
        int hops = 0;
        while (current != tipperEntrySegment) {
            if (!visited.add(current)) {
                throw new IllegalArgumentException(
                        "P2P arrival route contains a cycle before tipper entry: "
                                + current.getLabel());
            }

            List<RouteConnection> nextConnections = current.getNextConnections();
            if (nextConnections.isEmpty()) {
                throw new IllegalArgumentException(
                        "P2P arrival route does not reach tipper entry from terminal: "
                                + terminalSegment.getLabel());
            }
            if (nextConnections.size() != 1) {
                throw new IllegalArgumentException(
                        "P2P arrival route is ambiguous before tipper entry at segment: "
                                + current.getLabel());
            }

            current = nextConnections.get(0).getSegment();
            hops++;
        }
        return hops;
    }
}
