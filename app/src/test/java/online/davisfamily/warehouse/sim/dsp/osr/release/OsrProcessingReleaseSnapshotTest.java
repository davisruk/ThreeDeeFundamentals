package online.davisfamily.warehouse.sim.dsp.osr.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OsrProcessingReleaseSnapshotTest {

    @Test
    void shouldPreserveDistinctPhysicalCandidatesForOneLogicalSheet() {
        OrderSheetKey sharedSheet = new OrderSheetKey("order-1", 1);
        OsrProcessingReleaseCandidate first = available("tote-1", sharedSheet, 1);
        OsrProcessingReleaseCandidate second = available("tote-2", sharedSheet, 2);

        OsrProcessingReleaseSnapshot snapshot = new OsrProcessingReleaseSnapshot(
                List.of(first, second));

        assertEquals(List.of(first, second), snapshot.candidates());
        assertEquals("sc-1", first.serviceCentreId());
        assertEquals(first, snapshot.findByPhysicalToteId(new PhysicalToteId("tote-1")).orElseThrow());
        assertEquals(second, snapshot.findByPhysicalToteId(new PhysicalToteId("tote-2")).orElseThrow());
    }

    @Test
    void shouldExposeAvailableCandidatesInInventoryOrder() {
        OsrProcessingReleaseCandidate first = available("tote-1", sheet("order-1"), 1);
        OsrProcessingReleaseCandidate blocked = blocked(
                "tote-2",
                sheet("order-2"),
                2,
                "active-tote");
        OsrProcessingReleaseCandidate third = available("tote-3", sheet("order-3"), 3);

        OsrProcessingReleaseSnapshot snapshot = new OsrProcessingReleaseSnapshot(
                List.of(first, blocked, third));

        assertEquals(List.of(first, third), snapshot.availableCandidates());
        assertFalse(snapshot.findByPhysicalToteId(new PhysicalToteId("missing")).isPresent());
        assertThrows(IllegalArgumentException.class, () -> snapshot.findByPhysicalToteId(null));
    }

    @Test
    void shouldRequireConsistentBlockingIdentity() {
        PhysicalToteId candidateId = new PhysicalToteId("tote-1");
        OrderSheetKey sheet = sheet("order-1");

        assertThrows(IllegalArgumentException.class, () -> candidate(
                candidateId,
                sheet,
                OrderType.FULL_PACK,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.of(new PhysicalToteId("blocker"))));
        assertThrows(IllegalArgumentException.class, () -> candidate(
                candidateId,
                sheet,
                OrderType.FULL_PACK,
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> candidate(
                candidateId,
                sheet,
                OrderType.FULL_PACK,
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                Optional.of(candidateId)));
        assertThrows(IllegalArgumentException.class, () -> candidate(
                candidateId,
                sheet,
                OrderType.EMPTY,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseCandidate(
                candidateId,
                sheet,
                OrderType.FULL_PACK,
                " ",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseCandidate(
                candidateId,
                sheet,
                OrderType.FULL_PACK,
                "sc-1",
                -1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseCandidate(
                null,
                sheet,
                OrderType.FULL_PACK,
                "sc-1",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseCandidate(
                candidateId,
                null,
                OrderType.FULL_PACK,
                "sc-1",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseCandidate(
                candidateId,
                sheet,
                null,
                "sc-1",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseCandidate(
                candidateId,
                sheet,
                OrderType.FULL_PACK,
                "sc-1",
                1,
                null,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseCandidate(
                candidateId,
                sheet,
                OrderType.FULL_PACK,
                "sc-1",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                null));
    }

    @Test
    void shouldRejectDuplicatePhysicalCandidateIdentity() {
        OsrProcessingReleaseCandidate candidate = available("tote-1", sheet("order-1"), 1);
        OsrProcessingReleaseCandidate duplicate = available("tote-1", sheet("order-2"), 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> new OsrProcessingReleaseSnapshot(List.of(candidate, duplicate)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OsrProcessingReleaseSnapshot(candidateListWithNull(candidate)));
        assertThrows(IllegalArgumentException.class, () -> new OsrProcessingReleaseSnapshot(null));
    }

    @Test
    void shouldReturnDefensiveImmutableCandidateCollections() {
        OsrProcessingReleaseCandidate candidate = available("tote-1", sheet("order-1"), 1);
        List<OsrProcessingReleaseCandidate> source = new ArrayList<>();
        source.add(candidate);

        OsrProcessingReleaseSnapshot snapshot = new OsrProcessingReleaseSnapshot(source);
        source.clear();

        assertEquals(List.of(candidate), snapshot.candidates());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.candidates().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.availableCandidates().clear());
    }

    private static OsrProcessingReleaseCandidate available(
            String physicalToteId,
            OrderSheetKey orderSheetKey,
            long sourceSequenceNumber) {
        return candidate(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
                OrderType.FULL_PACK,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty(),
                sourceSequenceNumber);
    }

    private static OsrProcessingReleaseCandidate blocked(
            String physicalToteId,
            OrderSheetKey orderSheetKey,
            long sourceSequenceNumber,
            String blockingPhysicalToteId) {
        return candidate(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
                OrderType.ASSOCIATED,
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                Optional.of(new PhysicalToteId(blockingPhysicalToteId)),
                sourceSequenceNumber);
    }

    private static OsrProcessingReleaseCandidate candidate(
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            OsrProcessingReleaseAvailability availability,
            Optional<PhysicalToteId> blockingPhysicalToteId) {
        return candidate(
                physicalToteId,
                orderSheetKey,
                orderType,
                availability,
                blockingPhysicalToteId,
                1);
    }

    private static OsrProcessingReleaseCandidate candidate(
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            OsrProcessingReleaseAvailability availability,
            Optional<PhysicalToteId> blockingPhysicalToteId,
            long sourceSequenceNumber) {
        return new OsrProcessingReleaseCandidate(
                physicalToteId,
                orderSheetKey,
                orderType,
                " sc-1 ",
                sourceSequenceNumber,
                availability,
                blockingPhysicalToteId);
    }

    private static OrderSheetKey sheet(String orderId) {
        return new OrderSheetKey(orderId, 1);
    }

    private static List<OsrProcessingReleaseCandidate> candidateListWithNull(
            OsrProcessingReleaseCandidate candidate) {
        List<OsrProcessingReleaseCandidate> candidates = new ArrayList<>();
        candidates.add(candidate);
        candidates.add(null);
        return candidates;
    }
}
