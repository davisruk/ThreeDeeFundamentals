package online.davisfamily.warehouse.sim.dsp.osr.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapState;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyCoordinator;
import online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyPlan;
import online.davisfamily.warehouse.sim.dsp.supply.FixedIntervalInboundToteArrivalPolicy;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplyState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplyBatch;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplyConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

class DspOsrProcessingReleaseScenarioTest {

    private static final DspOperationalClock CLOCK = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 8, 20)));

    @Test
    void shouldReleaseStoredPhysicalToteIntoLifecycleAfterDownstreamAcceptance() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        ReleaseScenario scenario = releaseScenario(2, List.of(manifest), List.of(manifest));

        OsrProcessingReleaseSnapshot before = scenario.releaseSnapshot();
        SchedulerCommandApplicationResult result = scenario.release(manifest, 5);

        assertEquals(List.of(manifest.physicalToteId()), before.availableCandidates().stream()
                .map(OsrProcessingReleaseCandidate::physicalToteId)
                .toList());
        assertTrue(result.applied());
        assertEquals(0, scenario.inventory().snapshot().occupancy());
        assertEquals(List.of(manifest), scenario.inventory().snapshot().departedTotes());
        assertEquals(1, scenario.lifecycle().snapshot().assignments().size());
        assertEquals(PhysicalToteAssignmentStage.INBOUND_PACK,
                scenario.lifecycle().snapshot().assignments().getFirst().stage());
        assertEquals(Duration.ofSeconds(5),
                scenario.lifecycle().snapshot().assignments().getFirst().activatedAt());
        assertEquals(List.of(manifest), scenario.target().acceptedManifests());
        assertEquals(List.of(Duration.ofSeconds(5)), scenario.target().releaseTimes());
    }

    @Test
    void shouldSequenceSeveralPhysicalTotesForOneLogicalSheet() {
        OrderSheetKey sharedSheet = sheet("adapted-order");
        InboundToteManifest first = manifest(
                "tote-1", sharedSheet, OrderType.ADAPTED, "104", 1);
        InboundToteManifest second = manifest(
                "tote-2", sharedSheet, OrderType.ADAPTED, "104", 2);
        ReleaseScenario scenario = releaseScenario(
                2, List.of(first, second), List.of(first, second));

        assertTrue(scenario.release(first, 1).applied());

        OsrProcessingReleaseSnapshot blockedSnapshot = scenario.releaseSnapshot();
        OsrProcessingReleaseCandidate blocked = blockedSnapshot.candidates().getFirst();
        assertEquals(second.physicalToteId(), blocked.physicalToteId());
        assertEquals(
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                blocked.availability());
        assertEquals(first.physicalToteId(), blocked.blockingPhysicalToteId().orElseThrow());
        assertEquals(List.of(second), scenario.inventory().snapshot().storedTotes());
        assertEquals(List.of(first), scenario.inventory().snapshot().departedTotes());

        scenario.lifecycle().consumeAtAdapting(
                first.physicalToteId(), Duration.ofSeconds(2));

        OsrProcessingReleaseSnapshot availableSnapshot = scenario.releaseSnapshot();
        assertEquals(second.physicalToteId(), availableSnapshot.availableCandidates()
                .getFirst().physicalToteId());
        assertTrue(scenario.release(second, 3).applied());
        assertEquals(List.of(first, second), scenario.inventory().snapshot().departedTotes());
        assertEquals(2, scenario.lifecycle().snapshot()
                .assignmentHistoryFor(sharedSheet).size());
        assertEquals(List.of(first, second), scenario.target().acceptedManifests());
    }

    @Test
    void shouldFreeCapacityForRateLimitedServiceCentreSupply() {
        SupplyScenario scenario = supplyScenario();

        scenario.coordinator().advance(CLOCK.snapshotAt(Duration.ZERO));
        assertTrue(scenario.release().release(scenario.preloaded(), 1).applied());
        scenario.coordinator().advance(CLOCK.snapshotAt(Duration.ofSeconds(1)));
        scenario.coordinator().advance(CLOCK.snapshotAt(Duration.ofSeconds(4)));
        scenario.coordinator().advance(CLOCK.snapshotAt(Duration.ofSeconds(7)));

        assertEquals(PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY,
                supplyState(scenario, scenario.secondUpstream()));
        assertEquals(List.of(scenario.firstUpstream()),
                scenario.release().inventory().snapshot().storedTotes());

        assertTrue(scenario.release().release(scenario.firstUpstream(), 8).applied());
        scenario.coordinator().advance(CLOCK.snapshotAt(Duration.ofSeconds(8)));

        assertEquals(1, scenario.release().inventory().snapshot().occupancy());
        assertEquals(List.of(scenario.secondUpstream()),
                scenario.release().inventory().snapshot().storedTotes());
        assertEquals(
                List.of(scenario.preloaded(), scenario.firstUpstream()),
                scenario.release().inventory().snapshot().departedTotes());
        assertEquals(PhysicalToteSupplyState.STORED_IN_OSR,
                supplyState(scenario, scenario.secondUpstream()));
    }

    @Test
    void shouldReconstructExactStartupReleaseState() {
        SupplyScenario running = supplyScenario();
        running.release().release(running.preloaded(), 1);
        running.coordinator().advance(CLOCK.snapshotAt(Duration.ofSeconds(1)));
        running.coordinator().advance(CLOCK.snapshotAt(Duration.ofSeconds(4)));

        SupplyScenario reconstructed = supplyScenario();

        assertEquals(List.of(reconstructed.preloaded()),
                reconstructed.release().inventory().snapshot().storedTotes());
        assertTrue(reconstructed.release().inventory().snapshot().departedTotes().isEmpty());
        assertTrue(reconstructed.release().lifecycle().snapshot().assignments().isEmpty());
        assertEquals(List.of(reconstructed.preloaded().physicalToteId()),
                reconstructed.release().releaseSnapshot().availableCandidates().stream()
                        .map(OsrProcessingReleaseCandidate::physicalToteId)
                        .toList());
        assertTrue(reconstructed.release().target().acceptedManifests().isEmpty());
        assertEquals(1, reconstructed.coordinator().snapshot().osrOccupancy());
    }

    private static SupplyScenario supplyScenario() {
        InboundToteManifest preloaded = manifest(
                "preloaded", sheet("preloaded-order"), OrderType.FULL_PACK, "104", 0);
        InboundToteManifest firstUpstream = manifest(
                "upstream-1", sheet("upstream-order-1"), OrderType.ADAPTED, "108", 1);
        InboundToteManifest secondUpstream = manifest(
                "upstream-2", sheet("upstream-order-2"), OrderType.FULL_PACK, "108", 2);
        ReleaseScenario release = releaseScenario(
                1,
                List.of(preloaded, firstUpstream, secondUpstream),
                List.of(preloaded));
        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlan(
                List.of(
                        new ServiceCentreSupplyBatch(
                                "104", 999, 0, true, List.of(preloaded), Set.of()),
                        new ServiceCentreSupplyBatch(
                                "108", 998, 1, false,
                                List.of(firstUpstream, secondUpstream), Set.of())),
                List.of());
        OsrBootstrapState bootstrap = new OsrBootstrapState(
                release.inventory(), Set.of());
        DspServiceCentreSupplyCoordinator coordinator =
                new DspServiceCentreSupplyCoordinator(
                        plan,
                        new ServiceCentreSupplyConfig(0),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        bootstrap);
        return new SupplyScenario(
                release, coordinator, preloaded, firstUpstream, secondUpstream);
    }

    private static PhysicalToteSupplyState supplyState(
            SupplyScenario scenario,
            InboundToteManifest manifest) {
        return scenario.coordinator().snapshot().serviceCentres().stream()
                .flatMap(serviceCentre -> serviceCentre.physicalTotes().stream())
                .filter(tote -> tote.physicalToteId().equals(manifest.physicalToteId()))
                .map(PhysicalToteSupplySnapshot::state)
                .findFirst()
                .orElseThrow();
    }

    private static ReleaseScenario releaseScenario(
            int capacity,
            List<InboundToteManifest> catalogManifests,
            List<InboundToteManifest> storedManifests) {
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(
                new OsrInventoryConfig(capacity, List.of()));
        if (!storedManifests.isEmpty()) {
            inventory.storeAll(storedManifests);
        }
        InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                new InboundToteManifestCatalog(catalogManifests));
        AtomicReference<DspOperationalClockSnapshot> clockSnapshot =
                new AtomicReference<>(CLOCK.initialSnapshot());
        AcceptingTarget target = new AcceptingTarget("target-1");
        OsrProcessingReleaseCommandHandler handler =
                new OsrProcessingReleaseCommandHandler(
                        inventory,
                        lifecycle,
                        clockSnapshot::get,
                        new OsrProcessingReleaseTargetRegistry(List.of(target)));
        return new ReleaseScenario(
                inventory,
                lifecycle,
                clockSnapshot,
                target,
                handler,
                new OsrProcessingReleaseSnapshotFactory());
    }

    private static InboundToteManifest manifest(
            String toteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            String serviceCentreId,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(toteId),
                orderSheetKey,
                orderType,
                serviceCentreId,
                List.of(new DspOrderItem("line-" + toteId, "product-" + toteId, 1)),
                sourceSequenceNumber);
    }

    private static OrderSheetKey sheet(String orderId) {
        return new OrderSheetKey(orderId, 1);
    }

    private record SupplyScenario(
            ReleaseScenario release,
            DspServiceCentreSupplyCoordinator coordinator,
            InboundToteManifest preloaded,
            InboundToteManifest firstUpstream,
            InboundToteManifest secondUpstream) {
    }

    private record ReleaseScenario(
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycle,
            AtomicReference<DspOperationalClockSnapshot> clockSnapshot,
            AcceptingTarget target,
            OsrProcessingReleaseCommandHandler handler,
            OsrProcessingReleaseSnapshotFactory snapshotFactory) {

        private SchedulerCommandApplicationResult release(
                InboundToteManifest manifest,
                long elapsedSeconds) {
            clockSnapshot.set(CLOCK.snapshotAt(Duration.ofSeconds(elapsedSeconds)));
            return handler.apply(new ReleasePhysicalToteFromOsrCommand(
                    manifest.physicalToteId(),
                    manifest.orderSheetKey(),
                    manifest.serviceCentreId(),
                    target.targetId()));
        }

        private OsrProcessingReleaseSnapshot releaseSnapshot() {
            return snapshotFactory.create(inventory.snapshot(), lifecycle.snapshot());
        }
    }

    private static final class AcceptingTarget implements OsrProcessingReleaseTarget {
        private final String targetId;
        private final List<OsrProcessingReleaseRequest> requests = new ArrayList<>();

        private AcceptingTarget(String targetId) {
            this.targetId = targetId;
        }

        @Override
        public String targetId() {
            return targetId;
        }

        @Override
        public SchedulerCommandApplicationResult accept(OsrProcessingReleaseRequest request) {
            requests.add(request);
            return SchedulerCommandApplicationResult.appliedResult();
        }

        private List<InboundToteManifest> acceptedManifests() {
            return requests.stream()
                    .map(OsrProcessingReleaseRequest::manifest)
                    .toList();
        }

        private List<Duration> releaseTimes() {
            return requests.stream()
                    .map(OsrProcessingReleaseRequest::releaseTime)
                    .toList();
        }
    }
}
