package online.davisfamily.warehouse.sim.totebag.assignment;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PrlAssignment {
    private final String prlId;
    private PrlState state = PrlState.IDLE;
    private String correlationId;
    private int expectedPackCount;
    private final List<String> receivedPackIds = new ArrayList<>();

    public PrlAssignment(String prlId) {
        if (prlId == null || prlId.isBlank()) {
            throw new IllegalArgumentException("prlId must not be blank");
        }
        this.prlId = prlId;
    }

    public String getPrlId() {
        return prlId;
    }

    public PrlState getState() {
        return state;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getExpectedPackCount() {
        return expectedPackCount;
    }

    public int getReceivedPackCount() {
        return receivedPackIds.size();
    }

    public List<String> getReceivedPackIds() {
        return Collections.unmodifiableList(receivedPackIds);
    }

    public void assign(String correlationId, int expectedPackCount) {
        if (state != PrlState.IDLE) {
            throw new IllegalStateException("PRL must be IDLE before assignment");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (expectedPackCount <= 0) {
            throw new IllegalArgumentException("expectedPackCount must be > 0");
        }
        this.correlationId = correlationId;
        this.expectedPackCount = expectedPackCount;
        this.receivedPackIds.clear();
        this.state = PrlState.ASSIGNED;
    }

    public void startAccumulating() {
        requireState(PrlState.ASSIGNED);
        state = PrlState.ACCUMULATING;
    }

    public void recordPackReceived(String packId) {
        requireState(PrlState.ACCUMULATING);
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("packId must not be blank");
        }
        if (receivedPackIds.size() >= expectedPackCount) {
            throw new IllegalStateException("Expected pack count already reached");
        }
        receivedPackIds.add(packId);
        if (receivedPackIds.size() == expectedPackCount) {
            state = PrlState.READY_TO_RELEASE;
        }
    }

    public void startRelease() {
        requireState(PrlState.READY_TO_RELEASE);
        state = PrlState.RELEASING;
    }

    public void clear() {
        correlationId = null;
        expectedPackCount = 0;
        receivedPackIds.clear();
        state = PrlState.IDLE;
    }

    private void requireState(PrlState requiredState) {
        if (state != requiredState) {
            throw new IllegalStateException("Expected state " + requiredState + " but was " + state);
        }
    }
}
