package online.davisfamily.warehouse.sim.dsp.supply;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapState;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

public final class DspServiceCentreSupplyCoordinator {
    private final DspServiceCentreSupplyPlan plan;
    private final ServiceCentreSupplyConfig config;
    private final InboundToteArrivalPolicy arrivalPolicy;
    private final OsrBootstrapState bootstrapState;
    private final Map<String, ServiceCentreAuthorizationState> authorizationStates =
            new LinkedHashMap<>();
    private final Map<String, Optional<Duration>> authorizationElapsedTimes =
            new LinkedHashMap<>();
    private final Map<PhysicalToteId, PhysicalToteSupplyState> physicalToteStates =
            new LinkedHashMap<>();
    private final Set<OrderSheetKey> authorizedEmptyOrderSheetKeys = new LinkedHashSet<>();

    private long admittedAfterStartupCount;
    private String activeInboundServiceCentreId;
    private Duration nextPhysicalAdmissionElapsedTime;
    private Duration latestClockElapsedTime;

    public DspServiceCentreSupplyCoordinator(
            DspServiceCentreSupplyPlan plan,
            ServiceCentreSupplyConfig config,
            InboundToteArrivalPolicy arrivalPolicy,
            OsrBootstrapState bootstrapState) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (arrivalPolicy == null) {
            throw new IllegalArgumentException("arrivalPolicy must not be null");
        }
        if (bootstrapState == null) {
            throw new IllegalArgumentException("bootstrapState must not be null");
        }
        if (config.lowWaterMark() >= bootstrapState.inventorySnapshot().capacity()) {
            throw new IllegalArgumentException(
                    "lowWaterMark must be less than OSR capacity");
        }
        this.plan = plan;
        this.config = config;
        this.arrivalPolicy = arrivalPolicy;
        this.bootstrapState = bootstrapState;
        initializeFromBootstrap();
    }

    public void advance(DspOperationalClockSnapshot clockSnapshot) {
        if (clockSnapshot == null) {
            throw new IllegalArgumentException("clockSnapshot must not be null");
        }

        Duration elapsedSimulationTime = clockSnapshot.elapsedSimulationTime();
        if (latestClockElapsedTime != null
                && elapsedSimulationTime.compareTo(latestClockElapsedTime) < 0) {
            throw new IllegalArgumentException(
                    "clockSnapshot elapsedSimulationTime must not move backwards");
        }
        latestClockElapsedTime = elapsedSimulationTime;

        boolean activeAtStart = activeInboundServiceCentreId != null;
        if (!activeAtStart) {
            if (authorizeNextServiceCentre(
                    bootstrapState.inventorySnapshot(),
                    elapsedSimulationTime)) {
                return;
            }
            return;
        }

        admitDuePhysicalManifests(elapsedSimulationTime);
    }

    private boolean authorizeNextServiceCentre(
            OsrInventorySnapshot inventorySnapshot,
            Duration elapsedSimulationTime) {
        if (activeInboundServiceCentreId != null
                || inventorySnapshot.occupancy() > config.lowWaterMark()) {
            return false;
        }

        ServiceCentreSupplyBatch batch = plan.batches().stream()
                .filter(candidate -> authorizationStates.get(candidate.serviceCentreId())
                        == ServiceCentreAuthorizationState.HELD_UPSTREAM)
                .findFirst()
                .orElse(null);
        if (batch == null) {
            return false;
        }

        Duration firstInterval = null;
        if (!batch.physicalManifests().isEmpty()) {
            firstInterval = positiveArrivalInterval(
                    batch.physicalManifests().getFirst());
        }
        authorizationStates.put(
                batch.serviceCentreId(),
                batch.physicalManifests().isEmpty()
                        ? ServiceCentreAuthorizationState.SUPPLY_COMPLETE
                        : ServiceCentreAuthorizationState.AUTHORIZED);
        authorizationElapsedTimes.put(
                batch.serviceCentreId(),
                Optional.of(elapsedSimulationTime));
        authorizedEmptyOrderSheetKeys.addAll(batch.emptyOrderSheetKeys());

        if (batch.physicalManifests().isEmpty()) {
            return true;
        }

        activeInboundServiceCentreId = batch.serviceCentreId();
        for (InboundToteManifest manifest : batch.physicalManifests()) {
            physicalToteStates.put(
                    manifest.physicalToteId(),
                    PhysicalToteSupplyState.AUTHORIZED_WAITING);
        }
        nextPhysicalAdmissionElapsedTime = elapsedSimulationTime.plus(firstInterval);
        return true;
    }

    private void admitDuePhysicalManifests(Duration elapsedSimulationTime) {
        ServiceCentreSupplyBatch activeBatch = activeBatch();
        OsrInventorySnapshot inventorySnapshot = bootstrapState.inventorySnapshot();
        InboundToteManifest nextManifest = nextPendingManifest(activeBatch, inventorySnapshot);
        if (nextManifest == null) {
            completeActiveBatch(activeBatch);
            return;
        }
        if (nextPhysicalAdmissionElapsedTime == null) {
            throw new IllegalStateException(
                    "Active inbound service centre has no next physical admission time");
        }
        if (elapsedSimulationTime.compareTo(nextPhysicalAdmissionElapsedTime) < 0) {
            return;
        }

        boolean wasCapacityBlocked = physicalToteStates.get(nextManifest.physicalToteId())
                == PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY;
        if (wasCapacityBlocked) {
            if (inventorySnapshot.full()) {
                return;
            }
            admitManifest(nextManifest, inventorySnapshot);
            scheduleAfterCapacityBlock(activeBatch, elapsedSimulationTime);
            return;
        }

        while (true) {
            inventorySnapshot = bootstrapState.inventorySnapshot();
            nextManifest = nextPendingManifest(activeBatch, inventorySnapshot);
            if (nextManifest == null) {
                completeActiveBatch(activeBatch);
                return;
            }
            if (inventorySnapshot.full()) {
                physicalToteStates.put(
                        nextManifest.physicalToteId(),
                        PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY);
                return;
            }
            Duration scheduledDueTime = nextPhysicalAdmissionElapsedTime;
            if (elapsedSimulationTime.compareTo(scheduledDueTime) < 0) {
                return;
            }

            admitManifest(nextManifest, inventorySnapshot);
            inventorySnapshot = bootstrapState.inventorySnapshot();
            InboundToteManifest followingManifest = nextPendingManifest(
                    activeBatch,
                    inventorySnapshot);
            if (followingManifest == null) {
                completeActiveBatch(activeBatch);
                return;
            }
            nextPhysicalAdmissionElapsedTime = scheduledDueTime.plus(
                    positiveArrivalInterval(followingManifest));
        }
    }

    private void scheduleAfterCapacityBlock(
            ServiceCentreSupplyBatch activeBatch,
            Duration elapsedSimulationTime) {
        InboundToteManifest followingManifest = nextPendingManifest(
                activeBatch,
                bootstrapState.inventorySnapshot());
        if (followingManifest == null) {
            completeActiveBatch(activeBatch);
            return;
        }
        nextPhysicalAdmissionElapsedTime = elapsedSimulationTime.plus(
                positiveArrivalInterval(followingManifest));
    }

    private void admitManifest(
            InboundToteManifest manifest,
            OsrInventorySnapshot inventorySnapshot) {
        if (inventorySnapshot.full()) {
            throw new IllegalStateException("OSR capacity is full");
        }
        bootstrapState.inventory().store(manifest);
        physicalToteStates.put(
                manifest.physicalToteId(),
                PhysicalToteSupplyState.STORED_IN_OSR);
        admittedAfterStartupCount++;
    }

    private ServiceCentreSupplyBatch activeBatch() {
        return plan.findBatch(activeInboundServiceCentreId)
                .orElseThrow(() -> new IllegalStateException(
                        "No supply batch for active service centre: "
                                + activeInboundServiceCentreId));
    }

    private InboundToteManifest nextPendingManifest(
            ServiceCentreSupplyBatch batch,
            OsrInventorySnapshot inventorySnapshot) {
        return batch.physicalManifests().stream()
                .filter(manifest -> !inventorySnapshot.contains(manifest.physicalToteId())
                        && !inventorySnapshot.hasDeparted(manifest.physicalToteId()))
                .findFirst()
                .orElse(null);
    }

    private void completeActiveBatch(ServiceCentreSupplyBatch batch) {
        authorizationStates.put(
                batch.serviceCentreId(),
                ServiceCentreAuthorizationState.SUPPLY_COMPLETE);
        activeInboundServiceCentreId = null;
        nextPhysicalAdmissionElapsedTime = null;
    }

    private Duration positiveArrivalInterval(InboundToteManifest nextManifest) {
        Duration interval = arrivalPolicy.intervalBeforeNextTote(
                nextManifest,
                admittedAfterStartupCount);
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalStateException(
                    "Inbound tote arrival policy must return a positive interval");
        }
        return interval;
    }

    public DspSupplySnapshot snapshot() {
        OsrInventorySnapshot inventorySnapshot = bootstrapState.inventorySnapshot();
        List<ServiceCentreSupplySnapshot> serviceCentreSnapshots = new ArrayList<>();
        Set<OrderSheetKey> snapshotAuthorizedEmptyKeys = new LinkedHashSet<>();
        long snapshotAdmittedAfterStartupCount = 0;

        for (ServiceCentreSupplyBatch batch : plan.batches()) {
            List<PhysicalToteSupplySnapshot> physicalToteSnapshots = batch.physicalManifests().stream()
                    .map(manifest -> physicalToteSnapshot(batch, manifest, inventorySnapshot))
                    .toList();
            int preloadedCount = batch.preloadedAtStart() ? physicalToteSnapshots.size() : 0;
            int admittedCount = batch.preloadedAtStart()
                    ? 0
                    : (int) physicalToteSnapshots.stream()
                            .filter(this::isAdmittedAfterStartup)
                            .count();
            int upstreamWaitingCount = (int) physicalToteSnapshots.stream()
                    .filter(this::isUpstreamWaiting)
                    .count();
            Set<OrderSheetKey> batchAuthorizedEmptyKeys = authorizedEmptyKeysFor(batch);
            ServiceCentreSupplySnapshot serviceCentreSnapshot = new ServiceCentreSupplySnapshot(
                    batch.serviceCentreId(),
                    batch.priority(),
                    authorizationStates.get(batch.serviceCentreId()),
                    authorizationElapsedTimes.get(batch.serviceCentreId()),
                    physicalToteSnapshots.size(),
                    preloadedCount,
                    admittedCount,
                    upstreamWaitingCount,
                    batchAuthorizedEmptyKeys,
                    physicalToteSnapshots);
            serviceCentreSnapshots.add(serviceCentreSnapshot);
            snapshotAuthorizedEmptyKeys.addAll(batchAuthorizedEmptyKeys);
            snapshotAdmittedAfterStartupCount += admittedCount;
        }

        admittedAfterStartupCount = snapshotAdmittedAfterStartupCount;
        return new DspSupplySnapshot(
                arrivalPolicy.policyId(),
                config.lowWaterMark(),
                inventorySnapshot.capacity(),
                inventorySnapshot.occupancy(),
                Optional.ofNullable(activeInboundServiceCentreId),
                Optional.ofNullable(nextPhysicalAdmissionElapsedTime),
                snapshotAuthorizedEmptyKeys,
                serviceCentreSnapshots,
                admittedAfterStartupCount);
    }

    private void initializeFromBootstrap() {
        OsrInventorySnapshot inventorySnapshot = bootstrapState.inventorySnapshot();
        Set<PhysicalToteId> expectedPreloadedPhysicalToteIds = new LinkedHashSet<>();
        Set<OrderSheetKey> expectedAuthorizedEmptyKeys = new LinkedHashSet<>();

        for (ServiceCentreSupplyBatch batch : plan.batches()) {
            authorizationStates.put(
                    batch.serviceCentreId(),
                    batch.preloadedAtStart()
                            ? ServiceCentreAuthorizationState.PRELOADED
                            : ServiceCentreAuthorizationState.HELD_UPSTREAM);
            authorizationElapsedTimes.put(batch.serviceCentreId(), Optional.empty());
            if (batch.preloadedAtStart()) {
                expectedAuthorizedEmptyKeys.addAll(batch.emptyOrderSheetKeys());
                for (InboundToteManifest manifest : batch.physicalManifests()) {
                    if (!expectedPreloadedPhysicalToteIds.add(manifest.physicalToteId())) {
                        throw new IllegalArgumentException(
                                "Duplicate preloaded physical tote ID: "
                                        + manifest.physicalToteId().value());
                    }
                    if (inventorySnapshot.hasDeparted(manifest.physicalToteId())
                            || !inventorySnapshot.contains(manifest.physicalToteId())) {
                        throw new IllegalArgumentException(
                                "Preloaded physical tote is not currently stored in OSR: "
                                        + manifest.physicalToteId().value());
                    }
                    physicalToteStates.put(
                            manifest.physicalToteId(),
                            PhysicalToteSupplyState.PRELOADED_IN_OSR);
                }
            } else {
                for (InboundToteManifest manifest : batch.physicalManifests()) {
                    if (inventorySnapshot.contains(manifest.physicalToteId())
                            || inventorySnapshot.hasDeparted(manifest.physicalToteId())) {
                        throw new IllegalArgumentException(
                                "Post-start physical tote is already in OSR history: "
                                        + manifest.physicalToteId().value());
                    }
                    if (physicalToteStates.put(
                            manifest.physicalToteId(),
                            PhysicalToteSupplyState.HELD_UPSTREAM) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate physical tote ID in supply plan: "
                                        + manifest.physicalToteId().value());
                    }
                }
            }
        }

        for (InboundToteManifest storedManifest : inventorySnapshot.storedTotes()) {
            if (!expectedPreloadedPhysicalToteIds.contains(storedManifest.physicalToteId())) {
                throw new IllegalArgumentException(
                        "Bootstrap OSR inventory contains a manifest outside the preload plan: "
                                + storedManifest.physicalToteId().value());
            }
        }
        if (!inventorySnapshot.departedTotes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Bootstrap OSR inventory must not contain departed physical totes");
        }
        if (!bootstrapState.authorizedEmptyOrderSheetKeys().equals(expectedAuthorizedEmptyKeys)) {
            throw new IllegalArgumentException(
                    "Bootstrap EMPTY authorization does not match preloaded supply batches");
        }
        authorizedEmptyOrderSheetKeys.addAll(bootstrapState.authorizedEmptyOrderSheetKeys());
    }

    private PhysicalToteSupplySnapshot physicalToteSnapshot(
            ServiceCentreSupplyBatch batch,
            InboundToteManifest manifest,
            OsrInventorySnapshot inventorySnapshot) {
        PhysicalToteSupplyState state = physicalToteStates.get(manifest.physicalToteId());
        if (inventorySnapshot.hasDeparted(manifest.physicalToteId())) {
            state = PhysicalToteSupplyState.DEPARTED_FROM_OSR;
        } else if (inventorySnapshot.contains(manifest.physicalToteId())) {
            state = batch.preloadedAtStart()
                    ? PhysicalToteSupplyState.PRELOADED_IN_OSR
                    : PhysicalToteSupplyState.STORED_IN_OSR;
        }
        if (state == null) {
            throw new IllegalStateException(
                    "No supply state for physical tote: " + manifest.physicalToteId().value());
        }
        return new PhysicalToteSupplySnapshot(
                manifest.physicalToteId(),
                manifest.orderSheetKey(),
                manifest.orderType(),
                manifest.serviceCentreId(),
                manifest.sourceSequenceNumber(),
                state);
    }

    private Set<OrderSheetKey> authorizedEmptyKeysFor(ServiceCentreSupplyBatch batch) {
        if (batch.preloadedAtStart()) {
            return batch.emptyOrderSheetKeys();
        }
        Set<OrderSheetKey> authorizedKeys = new LinkedHashSet<>();
        for (OrderSheetKey key : batch.emptyOrderSheetKeys()) {
            if (authorizedEmptyOrderSheetKeys.contains(key)) {
                authorizedKeys.add(key);
            }
        }
        return authorizedKeys;
    }

    private boolean isAdmittedAfterStartup(PhysicalToteSupplySnapshot physicalTote) {
        return physicalTote.state() == PhysicalToteSupplyState.STORED_IN_OSR
                || physicalTote.state() == PhysicalToteSupplyState.DEPARTED_FROM_OSR;
    }

    private boolean isUpstreamWaiting(PhysicalToteSupplySnapshot physicalTote) {
        return physicalTote.state() == PhysicalToteSupplyState.HELD_UPSTREAM
                || physicalTote.state() == PhysicalToteSupplyState.AUTHORIZED_WAITING
                || physicalTote.state() == PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY;
    }
}
