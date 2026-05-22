package online.davisfamily.warehouse.testing;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.tote.Tote;

final class AdaptingBenchJourney {
    private final String orderId;
    private final Tote tote;
    private final AdaptingVisit visit;
    private final AdaptingBenchId benchId;
    private final RouteSegment benchSegment;
    private AdaptingJourneyPhase phase;

    AdaptingBenchJourney(
            String orderId,
            Tote tote,
            AdaptingVisit visit,
            AdaptingBenchId benchId,
            RouteSegment benchSegment,
            AdaptingJourneyPhase phase) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (benchId == null) {
            throw new IllegalArgumentException("benchId must not be null");
        }
        if (benchSegment == null) {
            throw new IllegalArgumentException("benchSegment must not be null");
        }
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        this.orderId = orderId;
        this.tote = tote;
        this.visit = visit;
        this.benchId = benchId;
        this.benchSegment = benchSegment;
        this.phase = phase;
    }

    String orderId() {
        return orderId;
    }

    Tote tote() {
        return tote;
    }

    AdaptingVisit visit() {
        return visit;
    }

    AdaptingBenchId benchId() {
        return benchId;
    }

    RouteSegment benchSegment() {
        return benchSegment;
    }

    AdaptingJourneyPhase phase() {
        return phase;
    }

    void setPhase(AdaptingJourneyPhase phase) {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        this.phase = phase;
    }
}
