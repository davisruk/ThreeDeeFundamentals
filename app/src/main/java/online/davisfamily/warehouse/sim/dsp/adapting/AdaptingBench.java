package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdaptingBench {
    private final String id;
    private final AdaptedLineStore store;
    private final List<AdaptingProcessingPosition> positions;
    private int occupiedProcessingPositions;

    public AdaptingBench(String id, AdaptedLineStore store, double processingDurationSeconds) {
        this(id, store, processingDurationSeconds, processingDurationSeconds, 1);
    }

    public AdaptingBench(String id, AdaptedLineStore store, double storeDurationSeconds,
            double collectDurationSeconds, int processingPositions) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (storeDurationSeconds < 0d || collectDurationSeconds < 0d) {
            throw new IllegalArgumentException("STORE and COLLECT durations must be >= 0");
        }
        if (processingPositions < 1) {
            throw new IllegalArgumentException("processingPositions must be positive");
        }
        this.id = id;
        this.store = store;
        List<AdaptingProcessingPosition> owners = new ArrayList<>(processingPositions);
        for (int index = 0; index < processingPositions; index++) {
            owners.add(new AdaptingProcessingPosition(this, index + 1, store,
                    storeDurationSeconds, collectDurationSeconds));
        }
        positions = List.copyOf(owners);
    }

    public String id() {
        return id;
    }

    void bindStorageMap(AdaptingStorageMap storageMap) {
        store.bindStorageMap(storageMap);
    }

    public AdaptingBenchState state() {
        return representativePosition().state();
    }

    public boolean canAcceptVisit() {
        return occupiedProcessingPositions < processingCapacity();
    }

    public int processingCapacity() {
        return positions.size();
    }

    public int occupiedProcessingPositions() {
        return occupiedProcessingPositions;
    }

    AdaptingProcessingPosition position(int ordinal) {
        if (ordinal < 1 || ordinal > positions.size()) {
            throw new IllegalArgumentException("position ordinal is outside bench capacity: " + ordinal);
        }
        return positions.get(ordinal - 1);
    }

    List<AdaptingProcessingPosition> positions() {
        return positions;
    }

    Optional<AdaptingProcessingPosition> firstIdlePosition() {
        if (!canAcceptVisit()) {
            return Optional.empty();
        }
        for (int index = 0; index < positions.size(); index++) {
            AdaptingProcessingPosition position = positions.get(index);
            if (position.state() == AdaptingBenchState.IDLE) {
                return Optional.of(position);
            }
        }
        return Optional.empty();
    }

    void positionAccepted() {
        if (occupiedProcessingPositions >= processingCapacity()) {
            throw new IllegalStateException("Bench processing capacity is full: " + id);
        }
        occupiedProcessingPositions++;
    }

    void positionReleased() {
        if (occupiedProcessingPositions < 1) {
            throw new IllegalStateException("Bench has no occupied processing positions: " + id);
        }
        occupiedProcessingPositions--;
    }

    public void acceptVisit(AdaptingVisit visit) {
        singlePosition().acceptVisit(visit);
    }

    public void startProcessing() {
        singlePosition().startProcessing();
    }

    public void tick(double dtSeconds) {
        if (dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be >= 0");
        }
        for (int index = 0; index < positions.size(); index++) {
            positions.get(index).tick(dtSeconds);
        }
    }

    public Optional<AdaptingBenchCompletion> consumeCompletion() {
        return singlePosition().consumeCompletion();
    }

    /**
     * Returns the staged completion without consuming it or changing bench state.
     */
    public Optional<AdaptingBenchCompletion> peekCompletion() {
        return singlePosition().peekCompletion();
    }

    void commitOrderGroup(AdaptingPreparedOrderGroup decision) {
        singlePosition().commitOrderGroup(decision);
    }

    public void clearBlocked() {
        singlePosition().clearBlocked();
    }

    public AdaptingBenchSnapshot snapshot() {
        return representativePosition().snapshot();
    }

    private AdaptingProcessingPosition singlePosition() {
        if (positions.size() != 1) {
            throw new IllegalStateException("Singular bench operation requires one processing position: " + id);
        }
        return positions.getFirst();
    }

    private AdaptingProcessingPosition representativePosition() {
        if (occupiedProcessingPositions > 0) {
            for (int index = 0; index < positions.size(); index++) {
                AdaptingProcessingPosition position = positions.get(index);
                if (position.state() != AdaptingBenchState.IDLE) {
                    return position;
                }
            }
        }
        return positions.getFirst();
    }
}
