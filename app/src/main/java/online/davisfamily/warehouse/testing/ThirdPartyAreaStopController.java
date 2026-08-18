package online.davisfamily.warehouse.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaController;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaSnapshot;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisit;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

public class ThirdPartyAreaStopController implements SimulationController {
    private final ThirdPartyArea area;
    private final ThirdPartyAreaController areaController;
    private final ThirdPartyVisitFactory visitFactory;
    private final List<ThirdPartyAreaStop> stops;
    private final Map<String, Journey> journeysByToteId = new LinkedHashMap<>();
    private final Set<String> reservedStopIds = new LinkedHashSet<>();
    private final Set<String> completedToteIds = new LinkedHashSet<>();

    public ThirdPartyAreaStopController(
            ThirdPartyArea area,
            ThirdPartyAreaController areaController,
            ThirdPartyVisitFactory visitFactory,
            List<ThirdPartyAreaStop> stops) {
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (areaController == null) {
            throw new IllegalArgumentException("areaController must not be null");
        }
        if (visitFactory == null) {
            throw new IllegalArgumentException("visitFactory must not be null");
        }
        if (stops == null || stops.isEmpty()) {
            throw new IllegalArgumentException("stops must not be empty");
        }
        this.area = area;
        this.areaController = areaController;
        this.visitFactory = visitFactory;
        this.stops = validatedStops(stops);
    }

    public boolean registerTote(Tote tote, NotionalToteOrder order) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (completedToteIds.contains(tote.getId()) || journeysByToteId.containsKey(tote.getId())) {
            return true;
        }

        ThirdPartyVisit visit = visitFactory.create(new PhysicalToteId(tote.getId()), order).orElse(null);
        if (visit == null) {
            return true;
        }

        ThirdPartyAreaStop stop = firstAvailableStop();
        if (stop == null || !hasUnreservedAreaCapacity()) {
            return false;
        }
        reservedStopIds.add(stop.id());
        journeysByToteId.put(tote.getId(), new Journey(tote, visit, stop));
        return true;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        areaController.update(dtSeconds);

        for (Journey journey : new ArrayList<>(journeysByToteId.values())) {
            if (journey.submitted
                    && areaController.completionForTote(new PhysicalToteId(journey.tote.getId())).isPresent()) {
                release(journey);
                continue;
            }

            RouteFollowerSnapshot current = journey.tote.getLastSnapshot();
            if (!journey.submitted && hasReachedStop(journey, current)) {
                holdAndSubmit(journey);
                continue;
            }
            journey.lastSample = sampleFor(current);
        }
    }

    public Set<String> activeToteIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(journeysByToteId.keySet()));
    }

    public Set<String> completedToteIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(completedToteIds));
    }

    private void holdAndSubmit(Journey journey) {
        Tote tote = journey.tote;
        tote.getRouteFollower().setCurrentSegment(journey.stop.segment());
        tote.snapToRouteDistance(journey.stop.holdDistance());
        tote.setInteractionMode(ToteMotionState.HELD);
        tote.openLids();

        if (!area.submitVisit(journey.visit)) {
            throw new IllegalStateException("Reserved Third Party capacity was unavailable for " + tote.getId());
        }
        journey.submitted = true;
        journey.lastSample = sampleFor(tote.getLastSnapshot());
    }

    private void release(Journey journey) {
        Tote tote = journey.tote;
        tote.closeLids();
        tote.setInteractionMode(ToteMotionState.MOVING);
        journeysByToteId.remove(tote.getId());
        reservedStopIds.remove(journey.stop.id());
        completedToteIds.add(tote.getId());
    }

    private boolean hasReachedStop(Journey journey, RouteFollowerSnapshot current) {
        if (current == null) {
            return false;
        }
        ThirdPartyAreaStop stop = journey.stop;
        if (current.currentSegment() == stop.segment() && current.distanceAlongSegment() >= stop.sensorDistance()) {
            return true;
        }
        MotionSample previous = journey.lastSample;
        return previous != null
                && previous.segment() == stop.segment()
                && previous.distanceAlongSegment() < stop.sensorDistance()
                && (current.currentSegment() != stop.segment()
                        || current.distanceAlongSegment() >= stop.sensorDistance());
    }

    private boolean hasUnreservedAreaCapacity() {
        ThirdPartyAreaSnapshot snapshot = area.snapshot();
        int pendingReservations = (int) journeysByToteId.values().stream()
                .filter(journey -> !journey.submitted)
                .count();
        int totalCapacity = snapshot.config().maxConcurrentVisits() + snapshot.config().waitingCapacity();
        return snapshot.admittedCount() + pendingReservations < totalCapacity;
    }

    private ThirdPartyAreaStop firstAvailableStop() {
        return stops.stream()
                .filter(stop -> !reservedStopIds.contains(stop.id()))
                .findFirst()
                .orElse(null);
    }

    private static List<ThirdPartyAreaStop> validatedStops(List<ThirdPartyAreaStop> stops) {
        List<ThirdPartyAreaStop> copy = List.copyOf(stops);
        Set<String> ids = new LinkedHashSet<>();
        for (ThirdPartyAreaStop stop : copy) {
            if (!ids.add(stop.id())) {
                throw new IllegalArgumentException("Duplicate stop id: " + stop.id());
            }
        }
        return copy;
    }

    private static MotionSample sampleFor(RouteFollowerSnapshot snapshot) {
        return snapshot == null ? null : new MotionSample(snapshot.currentSegment(), snapshot.distanceAlongSegment());
    }

    private static final class Journey {
        private final Tote tote;
        private final ThirdPartyVisit visit;
        private final ThirdPartyAreaStop stop;
        private MotionSample lastSample;
        private boolean submitted;

        private Journey(Tote tote, ThirdPartyVisit visit, ThirdPartyAreaStop stop) {
            this.tote = tote;
            this.visit = visit;
            this.stop = stop;
            this.lastSample = sampleFor(tote.getLastSnapshot());
        }
    }

    private record MotionSample(RouteSegment segment, float distanceAlongSegment) {
    }
}
