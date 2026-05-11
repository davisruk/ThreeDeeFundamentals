package online.davisfamily.warehouse.testing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

public class DebugToteLidController implements SimulationController {
    private final List<Tote> totes;
    private final Map<String, MotionSample> lastSamplesByToteId = new LinkedHashMap<>();

    public DebugToteLidController(List<Tote> totes) {
        if (totes == null) {
            throw new IllegalArgumentException("totes must not be null");
        }
        if (totes.stream().anyMatch(tote -> tote == null)) {
            throw new IllegalArgumentException("totes must not contain null");
        }
        this.totes = List.copyOf(totes);
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        for (Tote tote : totes) {
            RouteFollowerSnapshot snapshot = tote.getLastSnapshot();
            MotionSample previous = lastSamplesByToteId.get(tote.getId());
            if (shouldOpenLids(tote, snapshot, previous)) {
                tote.openLids();
            }
            if (snapshot != null) {
                lastSamplesByToteId.put(tote.getId(), new MotionSample(
                        snapshot.currentSegment(),
                        snapshot.distanceAlongSegment()));
            }
        }
    }

    private boolean shouldOpenLids(
            Tote tote,
            RouteFollowerSnapshot snapshot,
            MotionSample previous) {
        if (tote.areLidsOpen()
                || tote.getInteractionMode() != ToteMotionState.MOVING
                || snapshot == null) {
            return false;
        }
        if (previous == null) {
            return true;
        }
        return previous.segment() != snapshot.currentSegment()
                || Math.abs(previous.distanceAlongSegment() - snapshot.distanceAlongSegment()) > 0.0001f;
    }

    private record MotionSample(
            Object segment,
            float distanceAlongSegment) {
    }
}
