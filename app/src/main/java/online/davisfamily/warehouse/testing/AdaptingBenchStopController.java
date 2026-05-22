package online.davisfamily.warehouse.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchState;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitType;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

public class AdaptingBenchStopController implements SimulationController {
    private final AdaptingArea adaptingArea;
    private final AdaptingAreaController areaController;
    private final Map<String, AdaptingBenchJourney> journeysByToteId;
    private final Map<AdaptingBenchId, AdaptingBenchStop> benchStopsById;
    private final RouteSegment completionSegment;
    private final Consumer<Tote> storeToteHider;
    private final Map<String, MotionSample> lastSamplesByToteId = new LinkedHashMap<>();

    public AdaptingBenchStopController(
            AdaptingArea adaptingArea,
            AdaptingAreaController areaController,
            Map<String, AdaptingBenchJourney> journeysByToteId,
            Map<AdaptingBenchId, AdaptingBenchStop> benchStopsById,
            RouteSegment completionSegment,
            Consumer<Tote> storeToteHider) {
        if (adaptingArea == null) {
            throw new IllegalArgumentException("adaptingArea must not be null");
        }
        if (areaController == null) {
            throw new IllegalArgumentException("areaController must not be null");
        }
        if (journeysByToteId == null) {
            throw new IllegalArgumentException("journeysByToteId must not be null");
        }
        if (benchStopsById == null || benchStopsById.isEmpty()) {
            throw new IllegalArgumentException("benchStopsById must not be empty");
        }
        if (completionSegment == null) {
            throw new IllegalArgumentException("completionSegment must not be null");
        }
        if (storeToteHider == null) {
            throw new IllegalArgumentException("storeToteHider must not be null");
        }
        this.adaptingArea = adaptingArea;
        this.areaController = areaController;
        this.journeysByToteId = journeysByToteId;
        this.benchStopsById = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(benchStopsById));
        this.completionSegment = completionSegment;
        this.storeToteHider = storeToteHider;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        for (AdaptingBenchId benchId : benchStopsById.keySet()) {
            adaptingArea.bench(benchId).tick(dtSeconds);
        }

        for (AdaptingBenchJourney journey : new ArrayList<>(journeysByToteId.values())) {
            updateJourney(journey);
        }
    }

    private void updateJourney(AdaptingBenchJourney journey) {
        Tote tote = journey.tote();
        RouteFollowerSnapshot current = tote.getLastSnapshot();
        MotionSample previous = lastSamplesByToteId.get(tote.getId());

        if (journey.phase() == AdaptingJourneyPhase.TO_BENCH && hasReachedAssignedStop(journey, previous, current)) {
            holdAtAssignedStop(journey);
            lastSamplesByToteId.put(tote.getId(), sampleFor(tote.getLastSnapshot()));
            return;
        }

        if ((journey.phase() == AdaptingJourneyPhase.PROCESSING_STORE
                || journey.phase() == AdaptingJourneyPhase.PROCESSING_COLLECT)
                && adaptingArea.bench(journey.benchId()).state() == AdaptingBenchState.COMPLETED) {
            areaController.applyBenchCompletion(journey.benchId()).orElseThrow();
            if (journey.phase() == AdaptingJourneyPhase.PROCESSING_STORE) {
                storeToteHider.accept(tote);
                journey.setPhase(AdaptingJourneyPhase.COMPLETE);
                journeysByToteId.remove(tote.getId());
                lastSamplesByToteId.remove(tote.getId());
            } else {
                tote.closeLids();
                tote.setInteractionMode(ToteMotionState.MOVING);
                journey.setPhase(AdaptingJourneyPhase.RETURNING);
            }
            return;
        }

        if (journey.phase() == AdaptingJourneyPhase.RETURNING
                && current != null
                && current.currentSegment() == completionSegment
                && current.distanceAlongSegment() > 0.5f) {
            journey.setPhase(AdaptingJourneyPhase.COMPLETE);
            journeysByToteId.remove(tote.getId());
            lastSamplesByToteId.remove(tote.getId());
            return;
        }

        if (current != null) {
            lastSamplesByToteId.put(tote.getId(), sampleFor(current));
        }
    }

    private boolean hasReachedAssignedStop(
            AdaptingBenchJourney journey,
            MotionSample previous,
            RouteFollowerSnapshot current) {
        if (current == null) {
            return false;
        }
        AdaptingBenchStop stop = benchStopsById.get(journey.benchId());
        if (stop == null) {
            throw new IllegalStateException("No bench stop registered for " + journey.benchId().value());
        }
        if (current.currentSegment() == stop.segment() && current.distanceAlongSegment() >= stop.sensorDistance()) {
            return true;
        }
        return previous != null
                && previous.segment() == stop.segment()
                && previous.distanceAlongSegment() < stop.sensorDistance()
                && (current.currentSegment() != stop.segment()
                        || current.distanceAlongSegment() >= stop.sensorDistance());
    }

    private void holdAtAssignedStop(AdaptingBenchJourney journey) {
        AdaptingBenchStop stop = benchStopsById.get(journey.benchId());
        Tote tote = journey.tote();
        tote.getRouteFollower().setCurrentSegment(stop.segment());
        tote.snapToRouteDistance(stop.holdDistance());
        tote.setInteractionMode(ToteMotionState.HELD);
        tote.openLids();

        var bench = adaptingArea.bench(journey.benchId());
        if (bench.state() == AdaptingBenchState.QUEUED
                && bench.snapshot().activeToteId().equals(tote.getId())) {
            bench.startProcessing();
            journey.setPhase(journey.visit().visitType() == AdaptingVisitType.STORE
                    ? AdaptingJourneyPhase.PROCESSING_STORE
                    : AdaptingJourneyPhase.PROCESSING_COLLECT);
        }
    }

    private static MotionSample sampleFor(RouteFollowerSnapshot snapshot) {
        return snapshot == null ? null : new MotionSample(snapshot.currentSegment(), snapshot.distanceAlongSegment());
    }

    private record MotionSample(
            Object segment,
            float distanceAlongSegment) {
    }
}
