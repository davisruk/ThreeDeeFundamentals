package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

class StationRouteContinuationSelectorTest {
    private static final StationType[] ROUTE_ORDER = {
            StationType.THIRD_PARTY,
            StationType.ADAPTING,
            StationType.MANUAL,
            StationType.P2P,
            StationType.MANUAL_MERGE};

    private final StationRouteContinuationSelector selector =
            new StationRouteContinuationSelector();

    @Test
    void shouldReturnFirstRequiredStationStrictlyAfterEveryRequiredStation() {
        for (int mask = 1; mask < (1 << ROUTE_ORDER.length); mask++) {
            int routeMask = mask;
            RouteRequirements requirements = requirements(routeMask);
            for (int completedIndex = 0; completedIndex < ROUTE_ORDER.length; completedIndex++) {
                if (!required(routeMask, completedIndex)) {
                    continue;
                }
                Optional<StationType> expected = IntStream.range(completedIndex + 1,
                                ROUTE_ORDER.length)
                        .filter(nextIndex -> required(routeMask, nextIndex))
                        .mapToObj(index -> ROUTE_ORDER[index])
                        .findFirst();
                assertEquals(expected,
                        selector.nextStation(requirements, ROUTE_ORDER[completedIndex]),
                        "mask=" + routeMask + " completed=" + ROUTE_ORDER[completedIndex]);
            }
        }
    }

    @Test
    void shouldReturnEmptyWhenCompletedStationIsTheLastRequiredStation() {
        assertEquals(Optional.empty(), selector.nextStation(
                new RouteRequirements(false, false, false, false, true, StartLocation.OSR),
                StationType.MANUAL_MERGE));
        assertEquals(Optional.empty(), selector.nextStation(
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                StationType.P2P));
    }

    @Test
    void shouldRejectNullUnsupportedAndNonRequiredCompletedStations() {
        RouteRequirements thirdPartyOnly =
                new RouteRequirements(true, false, false, false, false, StartLocation.OSR);

        assertThrows(IllegalArgumentException.class,
                () -> selector.nextStation(null, StationType.THIRD_PARTY));
        assertThrows(IllegalArgumentException.class,
                () -> selector.nextStation(thirdPartyOnly, null));
        assertThrows(IllegalArgumentException.class,
                () -> selector.nextStation(thirdPartyOnly, StationType.ADAPTING));
        assertThrows(IllegalArgumentException.class,
                () -> selector.nextStation(thirdPartyOnly, StationType.OSR));
        assertThrows(IllegalArgumentException.class,
                () -> selector.nextStation(thirdPartyOnly, StationType.AV02));
        assertThrows(IllegalArgumentException.class,
                () -> selector.nextStation(thirdPartyOnly, StationType.DISPATCH));
    }

    private static RouteRequirements requirements(int mask) {
        return new RouteRequirements(
                required(mask, 0),
                required(mask, 1),
                required(mask, 2),
                required(mask, 3),
                required(mask, 4),
                StartLocation.OSR);
    }

    private static boolean required(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }
}
