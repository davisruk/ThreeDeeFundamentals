package online.davisfamily.warehouse.sim.dsp.osr.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseOrderCommand;
import online.davisfamily.warehouse.sim.dsp.time.DspOperatingPhase;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class OsrProcessingReleaseCommandHandlerTest {

    @Test
    void shouldCarryAndCommitPinnedAssignmentAfterTargetAcceptance() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                manifest.physicalToteId(),
                manifest.serviceCentreId(),
                new P2pLineId("line-1"),
                new OperationalRouteDestination(StationType.P2P, "target-1"));
        RecordingTarget target = new RecordingTarget("target-1");
        OsrPhysicalInventory inventory = inventory(List.of(manifest));
        InboundToteLifecycleController lifecycle = lifecycle(List.of(manifest));
        AtomicInteger phase = new AtomicInteger();
        P2pReleaseAssignmentCommitter committer = (command, liveManifest) -> {
            assertSame(manifest, liveManifest);
            assertEquals(Optional.of(assignment), command.proposedP2pAssignment());
            assertEquals(0, phase.get());
            return () -> {
                assertEquals(1, phase.get());
                assertEquals(1, inventory.snapshot().occupancy());
                assertTrue(lifecycle.snapshot().assignments().isEmpty());
                phase.set(2);
            };
        };
        target.observer = request -> {
            assertEquals(Optional.of(assignment), request.p2pAssignment());
            assertEquals(0, phase.getAndIncrement());
        };
        OsrProcessingReleaseCommandHandler handler = new OsrProcessingReleaseCommandHandler(
                inventory,
                lifecycle,
                clockAt(7),
                new OsrProcessingReleaseTargetRegistry(List.of(target)),
                committer);
        ReleasePhysicalToteFromOsrCommand command = new ReleasePhysicalToteFromOsrCommand(
                manifest.physicalToteId(),
                manifest.orderSheetKey(),
                manifest.serviceCentreId(),
                "target-1",
                Optional.of(assignment));

        SchedulerCommandApplicationResult result = handler.apply(command);

        assertTrue(result.applied());
        assertEquals(2, phase.get());
        assertEquals(0, inventory.snapshot().occupancy());
        assertEquals(1, lifecycle.snapshot().assignments().size());
    }

    @Test
    void shouldNotCommitPreparedAssignmentWhenTargetDefersOrRejects() {
        for (SchedulerCommandApplicationResult targetResult : List.of(
                SchedulerCommandApplicationResult.deferredResult("full"),
                SchedulerCommandApplicationResult.rejectedResult("invalid"))) {
            InboundToteManifest manifest = manifest(
                    "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
            RecordingTarget target = new RecordingTarget("target-1");
            target.result = targetResult;
            OsrPhysicalInventory inventory = inventory(List.of(manifest));
            InboundToteLifecycleController lifecycle = lifecycle(List.of(manifest));
            AtomicInteger commits = new AtomicInteger();
            OsrProcessingReleaseCommandHandler handler = new OsrProcessingReleaseCommandHandler(
                    inventory,
                    lifecycle,
                    clockAt(7),
                    new OsrProcessingReleaseTargetRegistry(List.of(target)),
                    (command, liveManifest) -> commits::incrementAndGet);

            SchedulerCommandApplicationResult result = handler.apply(command(manifest));

            assertSame(targetResult, result);
            assertEquals(0, commits.get());
            assertUncommitted(inventory, lifecycle, manifest);
        }
    }

    @Test
    void shouldRejectAssignmentPrevalidationFailureBeforeInvokingTarget() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        OsrPhysicalInventory inventory = inventory(List.of(manifest));
        InboundToteLifecycleController lifecycle = lifecycle(List.of(manifest));
        OsrProcessingReleaseCommandHandler handler = new OsrProcessingReleaseCommandHandler(
                inventory,
                lifecycle,
                clockAt(7),
                new OsrProcessingReleaseTargetRegistry(List.of(target)),
                (command, liveManifest) -> {
                    throw new IllegalStateException("assignment is stale");
                });

        SchedulerCommandApplicationResult result = handler.apply(command(manifest));

        assertFalse(result.applied());
        assertFalse(result.deferred());
        assertTrue(result.reason().contains("assignment is stale"));
        assertEquals(0, target.callCount);
        assertUncommitted(inventory, lifecycle, manifest);
    }

    @Test
    void shouldAcceptDownstreamBeforeCommittingDepartureAndActivation() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        Scenario scenario = scenario(List.of(manifest), List.of(manifest), target, clockAt(7));
        target.observer = request -> {
            assertEquals(1, scenario.inventory().snapshot().occupancy());
            assertTrue(scenario.lifecycle().snapshot().assignments().isEmpty());
        };

        SchedulerCommandApplicationResult result = scenario.handler().apply(command(manifest));

        assertTrue(result.applied());
        assertEquals(0, scenario.inventory().snapshot().occupancy());
        assertEquals(List.of(manifest), scenario.inventory().snapshot().departedTotes());
        assertEquals(1, scenario.lifecycle().snapshot().assignments().size());
        assertEquals(Duration.ofSeconds(7), scenario.lifecycle().snapshot()
                .assignments().getFirst().activatedAt());
        assertEquals(1, target.callCount);
        assertSame(manifest, target.request.manifest());
        assertEquals(Duration.ofSeconds(7), target.request.releaseTime());
    }

    @Test
    void shouldUseOneAuthoritativeClockSnapshotForReleaseTime() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.ADAPTED, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        AtomicInteger clockReads = new AtomicInteger();
        Supplier<DspOperationalClockSnapshot> clockSupplier = () ->
                clock(clockReads.getAndIncrement() == 0
                        ? Duration.ofSeconds(11)
                        : Duration.ofSeconds(99));
        Scenario scenario = scenario(
                List.of(manifest), List.of(manifest), target, clockSupplier);

        scenario.handler().apply(command(manifest));

        assertEquals(1, clockReads.get());
        assertEquals(Duration.ofSeconds(11), target.request.releaseTime());
        assertEquals(Duration.ofSeconds(11), scenario.lifecycle().snapshot()
                .assignments().getFirst().activatedAt());
    }

    @Test
    void shouldDeferWithoutMutationWhenTargetHasNoCapacity() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        target.result = SchedulerCommandApplicationResult.deferredResult("target full");
        Scenario scenario = scenario(List.of(manifest), List.of(manifest), target, clockAt(3));

        SchedulerCommandApplicationResult result = scenario.handler().apply(command(manifest));

        assertSame(target.result, result);
        assertUncommitted(scenario, manifest);
        assertEquals(1, target.callCount);
        assertSame(manifest, target.request.manifest());
        assertEquals(Duration.ofSeconds(3), target.request.releaseTime());
    }

    @Test
    void shouldRejectWithoutMutationWhenTargetRejects() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        target.result = SchedulerCommandApplicationResult.rejectedResult("invalid route");
        Scenario scenario = scenario(List.of(manifest), List.of(manifest), target, clockAt(3));

        SchedulerCommandApplicationResult result = scenario.handler().apply(command(manifest));

        assertSame(target.result, result);
        assertUncommitted(scenario, manifest);
        assertEquals(1, target.callCount);
        assertEquals(Duration.ofSeconds(3), target.request.releaseTime());
    }

    @Test
    void shouldLeaveLocalStateUnchangedWhenTargetThrows() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        RuntimeException failure = new RuntimeException("target failed");
        target.failure = failure;
        Scenario scenario = scenario(List.of(manifest), List.of(manifest), target, clockAt(4));

        assertSame(failure, assertThrows(
                RuntimeException.class,
                () -> scenario.handler().apply(command(manifest))));

        assertUncommitted(scenario, manifest);
        assertEquals(1, target.callCount);
        assertEquals(Duration.ofSeconds(4), target.request.releaseTime());
    }

    @Test
    void shouldRejectUnknownTargetBeforeInvokingDownstream() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        Scenario scenario = scenario(List.of(manifest), List.of(manifest), target, clockAt(1));
        ReleasePhysicalToteFromOsrCommand command = new ReleasePhysicalToteFromOsrCommand(
                manifest.physicalToteId(),
                manifest.orderSheetKey(),
                manifest.serviceCentreId(),
                "missing-target");

        SchedulerCommandApplicationResult result = scenario.handler().apply(command);

        assertFalse(result.applied());
        assertFalse(result.deferred());
        assertUncommitted(scenario, manifest);
        assertEquals(0, target.callCount);
    }

    @Test
    void shouldRejectCommandIdentityMismatchBeforeInvokingDownstream() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        Scenario scenario = scenario(List.of(manifest), List.of(manifest), target, clockAt(1));

        SchedulerCommandApplicationResult wrongSheet = scenario.handler().apply(
                new ReleasePhysicalToteFromOsrCommand(
                        manifest.physicalToteId(), sheet("other-order"), "104", "target-1"));
        SchedulerCommandApplicationResult wrongCentre = scenario.handler().apply(
                new ReleasePhysicalToteFromOsrCommand(
                        manifest.physicalToteId(), manifest.orderSheetKey(), "108", "target-1"));

        assertFalse(wrongSheet.applied());
        assertFalse(wrongCentre.applied());
        assertUncommitted(scenario, manifest);
        assertEquals(0, target.callCount);
    }

    @Test
    void shouldDeferWhileAnotherPhysicalToteOwnsTheLogicalSheet() {
        OrderSheetKey sharedSheet = sheet("order-1");
        InboundToteManifest active = manifest(
                "tote-active", sharedSheet, OrderType.ADAPTED, "104", 1);
        InboundToteManifest stored = manifest(
                "tote-stored", sharedSheet, OrderType.ADAPTED, "104", 2);
        RecordingTarget target = new RecordingTarget("target-1");
        Scenario scenario = scenario(
                List.of(active, stored), List.of(active, stored), target, clockAt(5));
        scenario.inventory().recordDeparture(active.physicalToteId());
        scenario.lifecycle().activate(active.physicalToteId(), Duration.ofSeconds(1));

        SchedulerCommandApplicationResult result = scenario.handler().apply(command(stored));

        assertTrue(result.deferred());
        assertTrue(result.reason().contains(active.physicalToteId().value()));
        assertEquals(List.of(stored), scenario.inventory().snapshot().storedTotes());
        assertEquals(List.of(active), scenario.inventory().snapshot().departedTotes());
        assertEquals(1, scenario.lifecycle().snapshot().assignments().size());
        assertEquals(0, target.callCount);
    }

    @Test
    void shouldRejectRepeatedStaleCommandWithoutInvokingTargetAgain() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        Scenario scenario = scenario(List.of(manifest), List.of(manifest), target, clockAt(2));

        assertTrue(scenario.handler().apply(command(manifest)).applied());
        SchedulerCommandApplicationResult repeated =
                scenario.handler().apply(command(manifest));

        assertFalse(repeated.applied());
        assertFalse(repeated.deferred());
        assertEquals(1, target.callCount);
        assertEquals(0, scenario.inventory().snapshot().occupancy());
        assertEquals(List.of(manifest), scenario.inventory().snapshot().departedTotes());
        assertEquals(1, scenario.lifecycle().snapshot().assignments().size());
    }

    @Test
    void shouldRejectUnsupportedSchedulerCommandWithoutMutation() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget target = new RecordingTarget("target-1");
        AtomicInteger clockReads = new AtomicInteger();
        Scenario scenario = scenario(
                List.of(manifest),
                List.of(manifest),
                target,
                () -> {
                    clockReads.incrementAndGet();
                    return clock(Duration.ZERO);
                });

        SchedulerCommandApplicationResult result = scenario.handler().apply(
                new ReleaseOrderCommand("order-1", "104", StartLocation.OSR));

        assertFalse(result.applied());
        assertUncommitted(scenario, manifest);
        assertEquals(0, target.callCount);
        assertEquals(0, clockReads.get());
    }

    @Test
    void shouldRejectInvalidLifecycleOrInventoryStateBeforeDownstreamMutation() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        RecordingTarget unknownInventoryTarget = new RecordingTarget("target-1");
        Scenario validScenario = scenario(
                List.of(manifest), List.of(manifest), unknownInventoryTarget, clockAt(1));

        SchedulerCommandApplicationResult unknownInventory = validScenario.handler().apply(
                new ReleasePhysicalToteFromOsrCommand(
                        new PhysicalToteId("unknown-tote"),
                        manifest.orderSheetKey(),
                        manifest.serviceCentreId(),
                        "target-1"));

        assertFalse(unknownInventory.applied());
        assertUncommitted(validScenario, manifest);
        assertEquals(0, unknownInventoryTarget.callCount);

        RecordingTarget missingLifecycleTarget = new RecordingTarget("target-1");
        OsrPhysicalInventory inventory = inventory(List.of(manifest));
        InboundToteLifecycleController emptyLifecycle = lifecycle(List.of());
        OsrProcessingReleaseCommandHandler missingLifecycleHandler = handler(
                inventory, emptyLifecycle, clockAt(1), missingLifecycleTarget);

        SchedulerCommandApplicationResult missingLifecycle =
                missingLifecycleHandler.apply(command(manifest));

        assertFalse(missingLifecycle.applied());
        assertEquals(List.of(manifest), inventory.snapshot().storedTotes());
        assertTrue(emptyLifecycle.snapshot().assignments().isEmpty());
        assertEquals(0, missingLifecycleTarget.callCount);

        RecordingTarget activeTarget = new RecordingTarget("target-1");
        Scenario activeScenario = scenario(
                List.of(manifest), List.of(manifest), activeTarget, clockAt(2));
        activeScenario.lifecycle().activate(manifest.physicalToteId(), Duration.ZERO);

        SchedulerCommandApplicationResult activeResult =
                activeScenario.handler().apply(command(manifest));

        assertFalse(activeResult.applied());
        assertEquals(List.of(manifest), activeScenario.inventory().snapshot().storedTotes());
        assertTrue(activeScenario.inventory().snapshot().departedTotes().isEmpty());
        assertEquals(1, activeScenario.lifecycle().snapshot().assignments().size());
        assertEquals(0, activeTarget.callCount);
    }

    @Test
    void shouldRejectNullDependenciesAndBrokenRuntimeCollaborators() {
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, "104", 1);
        OsrPhysicalInventory inventory = inventory(List.of(manifest));
        InboundToteLifecycleController lifecycle = lifecycle(List.of(manifest));
        RecordingTarget target = new RecordingTarget("target-1");
        OsrProcessingReleaseTargetRegistry registry =
                new OsrProcessingReleaseTargetRegistry(List.of(target));

        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseCommandHandler(null, lifecycle, clockAt(1), registry));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseCommandHandler(inventory, null, clockAt(1), registry));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseCommandHandler(inventory, lifecycle, null, registry));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseCommandHandler(inventory, lifecycle, clockAt(1), null));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseCommandHandler(
                        inventory,
                        lifecycle,
                        clockAt(1),
                        registry,
                        null));

        OsrProcessingReleaseCommandHandler nullClockHandler =
                new OsrProcessingReleaseCommandHandler(inventory, lifecycle, () -> null, registry);
        assertThrows(IllegalStateException.class, () ->
                nullClockHandler.apply(command(manifest)));
        assertThrows(IllegalArgumentException.class, () -> nullClockHandler.apply(null));
        assertUncommitted(inventory, lifecycle, manifest);

        target.result = null;
        OsrProcessingReleaseCommandHandler nullResultHandler =
                new OsrProcessingReleaseCommandHandler(
                        inventory, lifecycle, clockAt(1), registry);
        assertThrows(IllegalStateException.class, () ->
                nullResultHandler.apply(command(manifest)));
        assertUncommitted(inventory, lifecycle, manifest);
    }

    private static void assertUncommitted(Scenario scenario, InboundToteManifest manifest) {
        assertUncommitted(scenario.inventory(), scenario.lifecycle(), manifest);
    }

    private static void assertUncommitted(
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycle,
            InboundToteManifest manifest) {
        assertEquals(List.of(manifest), inventory.snapshot().storedTotes());
        assertTrue(inventory.snapshot().departedTotes().isEmpty());
        assertTrue(lifecycle.snapshot().assignments().isEmpty());
    }

    private static Scenario scenario(
            List<InboundToteManifest> catalogManifests,
            List<InboundToteManifest> storedManifests,
            RecordingTarget target,
            Supplier<DspOperationalClockSnapshot> clockSupplier) {
        OsrPhysicalInventory inventory = inventory(storedManifests);
        InboundToteLifecycleController lifecycle = lifecycle(catalogManifests);
        return new Scenario(
                inventory,
                lifecycle,
                handler(inventory, lifecycle, clockSupplier, target));
    }

    private static OsrProcessingReleaseCommandHandler handler(
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycle,
            Supplier<DspOperationalClockSnapshot> clockSupplier,
            RecordingTarget target) {
        return new OsrProcessingReleaseCommandHandler(
                inventory,
                lifecycle,
                clockSupplier,
                new OsrProcessingReleaseTargetRegistry(List.of(target)));
    }

    private static OsrPhysicalInventory inventory(List<InboundToteManifest> manifests) {
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(
                new OsrInventoryConfig(Math.max(1, manifests.size()), List.of()));
        if (!manifests.isEmpty()) {
            inventory.storeAll(manifests);
        }
        return inventory;
    }

    private static InboundToteLifecycleController lifecycle(
            List<InboundToteManifest> manifests) {
        return new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                new InboundToteManifestCatalog(manifests));
    }

    private static ReleasePhysicalToteFromOsrCommand command(InboundToteManifest manifest) {
        return new ReleasePhysicalToteFromOsrCommand(
                manifest.physicalToteId(),
                manifest.orderSheetKey(),
                manifest.serviceCentreId(),
                "target-1");
    }

    private static Supplier<DspOperationalClockSnapshot> clockAt(long seconds) {
        DspOperationalClockSnapshot snapshot = clock(Duration.ofSeconds(seconds));
        return () -> snapshot;
    }

    private static DspOperationalClockSnapshot clock(Duration elapsedTime) {
        LocalDate operatingDate = LocalDate.of(2026, 8, 20);
        return new DspOperationalClockSnapshot(
                elapsedTime,
                operatingDate,
                operatingDate.atTime(6, 0),
                OperationalDayTime.day0(LocalTime.of(6, 0)),
                DspOperatingPhase.NORMAL_OPERATIONS,
                operatingDate.atTime(22, 0),
                operatingDate.plusDays(1).atStartOfDay());
    }

    private static InboundToteManifest manifest(
            String toteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            String serviceCentreId,
            long sequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(toteId),
                orderSheetKey,
                orderType,
                serviceCentreId,
                List.of(new DspOrderItem("line-" + toteId, "product-1", 1)),
                sequenceNumber);
    }

    private static OrderSheetKey sheet(String orderId) {
        return new OrderSheetKey(orderId, 1);
    }

    private record Scenario(
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycle,
            OsrProcessingReleaseCommandHandler handler) {
    }

    private static final class RecordingTarget implements OsrProcessingReleaseTarget {
        private final String targetId;
        private SchedulerCommandApplicationResult result =
                SchedulerCommandApplicationResult.appliedResult();
        private RuntimeException failure;
        private Consumer<OsrProcessingReleaseRequest> observer = ignored -> { };
        private int callCount;
        private OsrProcessingReleaseRequest request;

        private RecordingTarget(String targetId) {
            this.targetId = targetId;
        }

        @Override
        public String targetId() {
            return targetId;
        }

        @Override
        public SchedulerCommandApplicationResult accept(OsrProcessingReleaseRequest request) {
            callCount++;
            this.request = request;
            observer.accept(request);
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
