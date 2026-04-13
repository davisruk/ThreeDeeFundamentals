package online.davisfamily.warehouse.sim.totebag;

import java.util.Optional;

public class PdcDiversionDevice {
    private final String id;
    private final String targetPrlId;
    private final double armDelaySeconds;
    private final double actuationDurationSeconds;
    private final double resetDurationSeconds;

    private PdcDiversionDeviceState state = PdcDiversionDeviceState.IDLE;
    private double remainingStateSeconds;
    private Pack pendingPack;
    private boolean actuationStartPending;

    public PdcDiversionDevice(
            String id,
            String targetPrlId,
            double armDelaySeconds,
            double actuationDurationSeconds,
            double resetDurationSeconds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (targetPrlId == null || targetPrlId.isBlank()) {
            throw new IllegalArgumentException("targetPrlId must not be blank");
        }
        if (armDelaySeconds < 0d || actuationDurationSeconds < 0d || resetDurationSeconds < 0d) {
            throw new IllegalArgumentException("device timings must be >= 0");
        }
        this.id = id;
        this.targetPrlId = targetPrlId;
        this.armDelaySeconds = armDelaySeconds;
        this.actuationDurationSeconds = actuationDurationSeconds;
        this.resetDurationSeconds = resetDurationSeconds;
    }

    public String getId() {
        return id;
    }

    public String getTargetPrlId() {
        return targetPrlId;
    }

    public PdcDiversionDeviceState getState() {
        return state;
    }

    public Optional<Pack> getPendingPack() {
        return Optional.ofNullable(pendingPack);
    }

    public boolean isIdle() {
        return state == PdcDiversionDeviceState.IDLE;
    }

    public boolean requestDiversion(Pack pack) {
        if (pack == null || !isIdle()) {
            return false;
        }
        pendingPack = pack;
        state = PdcDiversionDeviceState.ARMED;
        remainingStateSeconds = armDelaySeconds;
        if (armDelaySeconds == 0d) {
            transitionFromArmed();
        }
        return true;
    }

    public void update(double dtSeconds) {
        if (state == PdcDiversionDeviceState.IDLE) {
            return;
        }
        if (remainingStateSeconds > 0d) {
            remainingStateSeconds = Math.max(0d, remainingStateSeconds - Math.max(0d, dtSeconds));
        }
        if (remainingStateSeconds > 0d) {
            return;
        }
        switch (state) {
            case ARMED -> transitionFromArmed();
            case ACTUATING -> transitionToResetting();
            case RESETTING -> transitionToIdle();
            case IDLE -> {
            }
        }
    }

    public Optional<Pack> consumeActuationStartPack() {
        if (!actuationStartPending || pendingPack == null) {
            return Optional.empty();
        }
        actuationStartPending = false;
        return Optional.of(pendingPack);
    }

    private void transitionFromArmed() {
        state = PdcDiversionDeviceState.ACTUATING;
        remainingStateSeconds = actuationDurationSeconds;
        actuationStartPending = true;
        if (actuationDurationSeconds == 0d) {
            transitionToResetting();
        }
    }

    private void transitionToResetting() {
        state = PdcDiversionDeviceState.RESETTING;
        remainingStateSeconds = resetDurationSeconds;
        if (resetDurationSeconds == 0d) {
            transitionToIdle();
        }
    }

    private void transitionToIdle() {
        state = PdcDiversionDeviceState.IDLE;
        remainingStateSeconds = 0d;
        pendingPack = null;
        actuationStartPending = false;
    }
}
