package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class ThirdPartyArea {
    private final ThirdPartyAreaConfig config;
    private final Deque<ThirdPartyVisit> waitingVisits = new ArrayDeque<>();
    private final List<ActiveVisit> activeVisits = new ArrayList<>();
    private final Deque<ThirdPartyCompletion> completions = new ArrayDeque<>();

    public ThirdPartyArea(ThirdPartyAreaConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    public boolean canAccept() {
        return activeVisits.size() + waitingVisits.size()
                < config.maxConcurrentVisits() + config.waitingCapacity();
    }

    public boolean submitVisit(ThirdPartyVisit visit) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (!canAccept()) {
            return false;
        }

        if (activeVisits.size() < config.maxConcurrentVisits() && waitingVisits.isEmpty()) {
            startVisit(visit);
        } else {
            waitingVisits.addLast(visit);
        }
        return true;
    }

    public void update(double dtSeconds) {
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        startWaitingVisits();
        for (ActiveVisit activeVisit : activeVisits) {
            activeVisit.remainingProcessingSeconds = Math.max(
                    0d,
                    activeVisit.remainingProcessingSeconds - dtSeconds);
        }

        Iterator<ActiveVisit> iterator = activeVisits.iterator();
        while (iterator.hasNext()) {
            ActiveVisit activeVisit = iterator.next();
            if (activeVisit.remainingProcessingSeconds == 0d) {
                completions.addLast(new ThirdPartyCompletion(activeVisit.visit));
                iterator.remove();
            }
        }
        startWaitingVisits();
    }

    public List<ThirdPartyCompletion> drainCompletions() {
        List<ThirdPartyCompletion> drained = List.copyOf(completions);
        completions.clear();
        return drained;
    }

    public ThirdPartyAreaSnapshot snapshot() {
        return new ThirdPartyAreaSnapshot(
                config,
                waitingVisits.stream().map(ThirdPartyVisit::orderId).toList(),
                waitingVisits.stream().map(ThirdPartyVisit::notionalToteId).toList(),
                activeVisits.stream().map(ActiveVisit::snapshot).toList());
    }

    private void startWaitingVisits() {
        while (activeVisits.size() < config.maxConcurrentVisits() && !waitingVisits.isEmpty()) {
            startVisit(waitingVisits.removeFirst());
        }
    }

    private void startVisit(ThirdPartyVisit visit) {
        activeVisits.add(new ActiveVisit(visit, config.processingDurationSeconds()));
    }

    private static final class ActiveVisit {
        private final ThirdPartyVisit visit;
        private double remainingProcessingSeconds;

        private ActiveVisit(ThirdPartyVisit visit, double remainingProcessingSeconds) {
            this.visit = visit;
            this.remainingProcessingSeconds = remainingProcessingSeconds;
        }

        private ThirdPartyVisitState snapshot() {
            return new ThirdPartyVisitState(
                    visit.orderId(),
                    visit.notionalToteId(),
                    visit.lineWork().size(),
                    visit.outstandingPackCount(),
                    remainingProcessingSeconds);
        }
    }
}
