package online.davisfamily.warehouse.sim.dsp.osr.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;

class OsrProcessingReleaseSnapshotFactoryTest {

    private final OsrProcessingReleaseSnapshotFactory factory =
            new OsrProcessingReleaseSnapshotFactory();

    @Test
    void shouldExposeOnlyCurrentlyStoredPhysicalManifests() {
        InboundToteManifest stored = manifest(
                "stored", sheet("order-stored"), OrderType.FULL_PACK, "104", 1);
        InboundToteManifest departed = manifest(
                "departed", sheet("order-departed"), OrderType.ADAPTED, "104", 2);
        InboundToteManifest upstream = manifest(
                "upstream", sheet("order-upstream"), OrderType.ASSOCIATED, "108", 3);
        OsrInventorySnapshot inventory = new OsrInventorySnapshot(
                3,
                List.of(stored),
                List.of(departed));

        OsrProcessingReleaseSnapshot snapshot = factory.create(
                inventory,
                lifecycle(stored, departed, upstream));

        assertEquals(List.of(stored.physicalToteId()), snapshot.candidates().stream()
                .map(OsrProcessingReleaseCandidate::physicalToteId)
                .toList());
    }

    @Test
    void shouldPreserveInventoryOrderAcrossServiceCentresAndOrderTypes() {
        InboundToteManifest first = manifest(
                "tote-1", sheet("order-1"), OrderType.ASSOCIATED, "108", 7);
        InboundToteManifest second = manifest(
                "tote-2", sheet("order-2"), OrderType.ADAPTED, "104", 2);
        InboundToteManifest third = manifest(
                "tote-3", sheet("order-3"), OrderType.FULL_PACK, "116", 5);

        OsrProcessingReleaseSnapshot snapshot = factory.create(
                new OsrInventorySnapshot(3, List.of(first, second, third), List.of()),
                lifecycle(first, second, third));

        assertEquals(
                List.of("tote-1", "tote-2", "tote-3"),
                snapshot.candidates().stream()
                        .map(candidate -> candidate.physicalToteId().value())
                        .toList());
        assertEquals(
                List.of(OrderType.ASSOCIATED, OrderType.ADAPTED, OrderType.FULL_PACK),
                snapshot.candidates().stream()
                        .map(OsrProcessingReleaseCandidate::orderType)
                        .toList());
        assertEquals(List.of(7L, 2L, 5L), snapshot.candidates().stream()
                .map(OsrProcessingReleaseCandidate::sourceSequenceNumber)
                .toList());
    }

    @Test
    void shouldBlockLaterManifestWhileSameSheetAssignmentIsActive() {
        OrderSheetKey sharedSheet = sheet("order-1");
        InboundToteManifest active = manifest(
                "active", sharedSheet, OrderType.ADAPTED, "104", 1);
        InboundToteManifest stored = manifest(
                "stored", sharedSheet, OrderType.ADAPTED, "104", 2);
        PhysicalToteAssignment activeAssignment = activeAssignment(active, 0);

        OsrProcessingReleaseSnapshot snapshot = factory.create(
                new OsrInventorySnapshot(2, List.of(stored), List.of(active)),
                lifecycle(List.of(active, stored), List.of(activeAssignment)));

        OsrProcessingReleaseCandidate candidate = snapshot.candidates().getFirst();
        assertEquals(
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                candidate.availability());
        assertEquals(
                Optional.of(active.physicalToteId()),
                candidate.blockingPhysicalToteId());
        assertTrue(snapshot.availableCandidates().isEmpty());
    }

    @Test
    void shouldMakeLaterManifestAvailableAfterEarlierAssignmentTerminates() {
        OrderSheetKey sharedSheet = sheet("order-1");
        InboundToteManifest earlier = manifest(
                "earlier", sharedSheet, OrderType.ADAPTED, "104", 1);
        InboundToteManifest later = manifest(
                "later", sharedSheet, OrderType.ADAPTED, "104", 2);
        PhysicalToteAssignment terminated = activeAssignment(earlier, 0).terminate(
                Duration.ofSeconds(2),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_ADAPTING);

        OsrProcessingReleaseSnapshot snapshot = factory.create(
                new OsrInventorySnapshot(2, List.of(later), List.of(earlier)),
                lifecycle(List.of(earlier, later), List.of(terminated)));

        OsrProcessingReleaseCandidate candidate = snapshot.availableCandidates().getFirst();
        assertEquals(later.physicalToteId(), candidate.physicalToteId());
        assertEquals(Optional.empty(), candidate.blockingPhysicalToteId());
    }

    @Test
    void shouldRejectStoredManifestMissingFromLifecycleLedger() {
        InboundToteManifest stored = manifest(
                "stored", sheet("order-1"), OrderType.FULL_PACK, "104", 1);

        assertThrows(IllegalArgumentException.class, () -> factory.create(
                new OsrInventorySnapshot(1, List.of(stored), List.of()),
                new PhysicalToteLifecycleSnapshot(Map.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                null,
                new PhysicalToteLifecycleSnapshot(Map.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                new OsrInventorySnapshot(1, List.of(), List.of()),
                null));
    }

    @Test
    void shouldRejectStoredManifestWithActiveOrTerminalPhysicalState() {
        InboundToteManifest stored = manifest(
                "stored", sheet("order-1"), OrderType.ADAPTED, "104", 1);
        OsrInventorySnapshot inventory = new OsrInventorySnapshot(
                1,
                List.of(stored),
                List.of());
        PhysicalToteRecord activeState = PhysicalToteRecord.inboundPack(stored.physicalToteId())
                .transitionTo(PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        PhysicalToteRecord terminalState = PhysicalToteRecord.inboundPack(stored.physicalToteId())
                .transitionTo(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING);

        assertThrows(IllegalArgumentException.class, () -> factory.create(
                inventory,
                new PhysicalToteLifecycleSnapshot(
                        Map.of(stored.physicalToteId(), activeState),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                inventory,
                new PhysicalToteLifecycleSnapshot(
                        Map.of(stored.physicalToteId(), terminalState),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                inventory,
                lifecycle(List.of(stored), List.of(activeAssignment(stored, 0)))));
    }

    @Test
    void shouldNeverCreateCandidateForEmptyAuthorization() {
        OsrProcessingReleaseSnapshot snapshot = factory.create(
                new OsrInventorySnapshot(1, List.of(), List.of()),
                new PhysicalToteLifecycleSnapshot(Map.of(), List.of()));

        assertTrue(snapshot.candidates().isEmpty());
    }

    private static PhysicalToteLifecycleSnapshot lifecycle(
            InboundToteManifest... manifests) {
        return lifecycle(List.of(manifests), List.of());
    }

    private static PhysicalToteLifecycleSnapshot lifecycle(
            List<InboundToteManifest> manifests,
            List<PhysicalToteAssignment> assignments) {
        Map<PhysicalToteId, PhysicalToteRecord> records = new LinkedHashMap<>();
        for (InboundToteManifest manifest : manifests) {
            records.put(
                    manifest.physicalToteId(),
                    PhysicalToteRecord.inboundPack(manifest.physicalToteId()));
        }
        return new PhysicalToteLifecycleSnapshot(records, assignments);
    }

    private static PhysicalToteAssignment activeAssignment(
            InboundToteManifest manifest,
            long sequenceNumber) {
        return PhysicalToteAssignment.active(
                sequenceNumber,
                manifest.orderSheetKey(),
                manifest.physicalToteId(),
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            String serviceCentreId,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
                orderType,
                serviceCentreId,
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-" + physicalToteId,
                        1)),
                sourceSequenceNumber);
    }

    private static OrderSheetKey sheet(String orderId) {
        return new OrderSheetKey(orderId, 1);
    }
}
