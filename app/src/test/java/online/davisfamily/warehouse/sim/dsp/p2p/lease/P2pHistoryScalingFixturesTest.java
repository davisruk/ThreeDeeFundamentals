package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixtures.Candidate;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixtures.Fixture;

class P2pHistoryScalingFixturesTest {
    private static final List<Integer> HISTORY_SIZES = List.of(0, 500, 2000, 5000);
    private static final List<Integer> PENDING_SIZES = List.of(100, 400, 800);

    @Test
    void shouldBuildExactOrderedStateAcrossTheEntireMatrix() {
        for (int history : HISTORY_SIZES) {
            for (int pending : PENDING_SIZES) {
                Fixture fixture = P2pHistoryScalingFixtures.create(history, pending);
                assertEquals(history, fixture.historicalCount());
                assertEquals(5, fixture.lines().size());
                assertEquals(history + 40, fixture.indexedAssignments().size());
                Set<PhysicalToteId> allIds = new HashSet<>();
                List<PhysicalToteId> lineOrderedIds = new ArrayList<>();
                for (int lineIndex = 0; lineIndex < 5; lineIndex++) {
                    P2pLineLeaseSnapshot line = fixture.lines().get(lineIndex);
                    assertSame(line, fixture.catalog().lines().get(lineIndex));
                    assertEquals("diagnostic-line-" + (lineIndex + 1), line.definition().lineId().value());
                    assertEquals(StationType.P2P, line.definition().destination().stationType());
                    assertEquals("diagnostic-target-" + (lineIndex + 1),
                            line.definition().destination().targetId());
                    assertEquals(Optional.of("125"), line.serviceCentreId());
                    assertEquals(new P2pInputActivitySnapshot(6, 1, true, 0), line.activity().input());
                    assertTrue(line.activity().packPath().empty());
                    assertTrue(line.activity().bagging().empty());
                    assertTrue(line.activity().openOutboundTote().isEmpty());
                    assertFalse(line.activity().quiescent());

                    List<PhysicalToteId> expectedIds = new ArrayList<>();
                    for (int index = lineIndex; index < history; index += 5) {
                        expectedIds.add(new PhysicalToteId("history-" + index));
                    }
                    int historicalOnLine = expectedIds.size();
                    for (int index = lineIndex; index < 40; index += 5) {
                        expectedIds.add(new PhysicalToteId("current-" + index));
                    }
                    assertEquals(historicalOnLine + 8, line.physicalAssignments().size());
                    assertEquals(expectedIds, line.physicalAssignments().stream()
                            .map(P2pPhysicalToteAssignment::physicalToteId).toList());
                    for (int index = 0; index < line.physicalAssignments().size(); index++) {
                        P2pPhysicalToteAssignment assignment = line.physicalAssignments().get(index);
                        assertEquals(index < historicalOnLine ? "104" : "125", assignment.serviceCentreId());
                        assertEquals(line.definition().lineId(), assignment.lineId());
                        assertEquals(line.definition().destination(), assignment.destination());
                        assertTrue(allIds.add(assignment.physicalToteId()));
                        lineOrderedIds.add(assignment.physicalToteId());
                    }
                }
                assertEquals(lineOrderedIds, new ArrayList<>(fixture.indexedAssignments().keySet()));
                assertEquals(pending, fixture.candidates().size());
                for (int index = 0; index < pending; index++) {
                    Candidate candidate = fixture.candidates().get(index);
                    assertEquals(new PhysicalToteId("pending-" + index), candidate.toteId());
                    assertEquals(new OrderSheetKey("pending-order-" + index, 1), candidate.sheetKey());
                    assertEquals(index % 2 == 0 ? OperationalPhysicalToteSource.OSR
                            : OperationalPhysicalToteSource.AV02, candidate.source());
                    assertTrue(allIds.add(candidate.toteId()));
                }
                assertEquals(history + 40 + pending, allIds.size());
            }
        }
    }

    @Test
    void shouldCreateRealRequestsWithSharedImmutableBatchValuesAcrossTheEntireMatrix() {
        for (int history : HISTORY_SIZES) {
            for (int pending : PENDING_SIZES) {
                Fixture fixture = P2pHistoryScalingFixtures.create(history, pending);
                P2pLineAllocationRequest first = request(fixture, fixture.candidates().getFirst());
                for (Candidate candidate : fixture.candidates()) {
                    P2pLineAllocationRequest request = request(fixture, candidate);
                    assertSame(candidate.toteId(), request.physicalToteId());
                    assertEquals("125", request.serviceCentreId());
                    assertEquals(List.of("diagnostic-pharmacy"), request.pharmacyIds());
                    assertTrue(request.p2pFirstRouteStation());
                    assertSame(fixture.catalog(), request.lineCatalog());
                    assertSame(first.routeAdmissionByDestination(), request.routeAdmissionByDestination());
                    assertEquals(5, request.routeAdmissionByDestination().size());
                    assertTrue(request.elasticAllocation().isEmpty());
                    assertTrue(request.requiredBagCorrelations().isEmpty());
                    assertSame(P2pBagCorrelationAssignmentSnapshot.empty(), request.correlationAssignmentSnapshot());
                    assertSame(first.correlationAssignmentSnapshot(), request.correlationAssignmentSnapshot());
                    for (P2pLineLeaseSnapshot line : fixture.lines()) {
                        assertTrue(request.routeAdmissible(line.definition().destination()));
                        assertTrue(request.bagCorrelationsCompatibleWith(line.definition().lineId()));
                    }
                    OperationalPhysicalToteSource otherSource = candidate.source() == OperationalPhysicalToteSource.OSR
                            ? OperationalPhysicalToteSource.AV02 : OperationalPhysicalToteSource.OSR;
                    assertEquals(request, request(fixture,
                            new Candidate(otherSource, candidate.toteId(), candidate.sheetKey())));
                }
            }
        }
    }

    @Test
    void shouldMatchIndexedControlToProductionLookupForEveryAssignedAndPendingId() {
        for (int history : HISTORY_SIZES) {
            for (int pending : PENDING_SIZES) {
                Fixture fixture = P2pHistoryScalingFixtures.create(history, pending);
                for (P2pLineLeaseSnapshot line : fixture.lines()) {
                    for (P2pPhysicalToteAssignment assignment : line.physicalAssignments()) {
                        PhysicalToteId equalId = new PhysicalToteId(assignment.physicalToteId().value());
                        assertSame(assignment, fixture.catalog().findAssignment(equalId).orElseThrow());
                        assertSame(assignment, fixture.indexedAssignments().get(equalId));
                    }
                }
                for (Candidate candidate : fixture.candidates()) {
                    assertEquals(Optional.empty(), fixture.catalog().findAssignment(candidate.toteId()));
                    assertEquals(Optional.empty(), Optional.ofNullable(
                            fixture.indexedAssignments().get(candidate.toteId())));
                }
            }
        }
    }

    @Test
    void shouldRejectAlreadyAssignedHistoricalAndCurrentIdsAtTheRealRequestBoundary() {
        Fixture fixture = P2pHistoryScalingFixtures.create(10, 2);
        for (P2pLineLeaseSnapshot line : fixture.lines()) {
            for (P2pPhysicalToteAssignment assignment : List.of(
                    line.physicalAssignments().getFirst(), line.physicalAssignments().getLast())) {
                for (OperationalPhysicalToteSource source : OperationalPhysicalToteSource.values()) {
                    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                            () -> request(fixture, new Candidate(source, assignment.physicalToteId(),
                                    fixture.candidates().getFirst().sheetKey())));
                    assertEquals("physical tote already has a P2P line assignment", failure.getMessage());
                }
            }
        }
    }

    @Test
    void shouldPublishImmutableCollectionsAndIndependentFixtures() {
        Fixture first = P2pHistoryScalingFixtures.create(10, 2);
        Fixture second = P2pHistoryScalingFixtures.create(10, 2);
        assertNotSame(first, second);
        assertNotSame(first.lines(), second.lines());
        assertNotSame(first.catalog(), second.catalog());
        assertNotSame(first.requestFactory(), second.requestFactory());
        assertNotSame(first.indexedAssignments(), second.indexedAssignments());
        assertNotSame(first.candidates(), second.candidates());
        assertThrows(UnsupportedOperationException.class, () -> first.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.catalog().lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.candidates().clear());
        P2pPhysicalToteAssignment original = first.lines().getFirst().physicalAssignments().getFirst();
        assertThrows(UnsupportedOperationException.class,
                () -> first.indexedAssignments().put(new PhysicalToteId("extra"), original));
        assertThrows(UnsupportedOperationException.class,
                () -> first.indexedAssignments().entrySet().iterator().next().setValue(original));
        for (int index = 0; index < 5; index++) {
            P2pLineLeaseSnapshot line = first.lines().get(index);
            assertNotSame(line, second.lines().get(index));
            assertNotSame(line.physicalAssignments(), second.lines().get(index).physicalAssignments());
            assertThrows(UnsupportedOperationException.class, () -> line.physicalAssignments().clear());
        }
        P2pLineAllocationRequest firstRequest = request(first, first.candidates().getFirst());
        P2pLineAllocationRequest secondRequest = request(second, second.candidates().getFirst());
        assertNotSame(firstRequest.routeAdmissionByDestination(), secondRequest.routeAdmissionByDestination());
        assertThrows(UnsupportedOperationException.class,
                () -> firstRequest.routeAdmissionByDestination().clear());
        assertThrows(UnsupportedOperationException.class, () -> firstRequest.pharmacyIds().clear());
        assertEquals(50, second.indexedAssignments().size());
        assertEquals(2, second.candidates().size());
        assertEquals(firstRequest, request(first, first.candidates().getFirst()));
        assertEquals(firstRequest, secondRequest);
    }

    @Test
    void shouldDetachFixtureRecordCollectionsFromMutableInputs() {
        Fixture original = P2pHistoryScalingFixtures.create(10, 2);
        List<P2pLineLeaseSnapshot> lines = new ArrayList<>(original.lines());
        LinkedHashMap<PhysicalToteId, P2pPhysicalToteAssignment> index =
                new LinkedHashMap<>(original.indexedAssignments());
        List<Candidate> candidates = new ArrayList<>(original.candidates());
        Fixture detached = new Fixture(10, lines, original.catalog(), original.requestFactory(), index, candidates);

        lines.clear();
        index.clear();
        candidates.clear();

        assertEquals(original.lines(), detached.lines());
        assertEquals(original.indexedAssignments(), detached.indexedAssignments());
        assertEquals(new ArrayList<>(original.indexedAssignments().keySet()),
                new ArrayList<>(detached.indexedAssignments().keySet()));
        assertEquals(original.candidates(), detached.candidates());
    }

    @Test
    void shouldRejectInvalidFixtureSizes() {
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingFixtures.create(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingFixtures.create(0, 0));
        assertThrows(IllegalArgumentException.class, () -> P2pHistoryScalingFixtures.create(0, -1));
    }

    @Test
    void shouldRejectCrossLineDuplicateIdsInTheRealCatalogConstructor() {
        Fixture fixture = P2pHistoryScalingFixtures.create(0, 1);
        P2pLineDefinition first = fixture.lines().get(0).definition();
        P2pLineDefinition second = fixture.lines().get(1).definition();
        PhysicalToteId duplicateId = new PhysicalToteId("duplicate");
        P2pLineLeaseSnapshot firstLine = new P2pLineLeaseSnapshot(first, Optional.of("125"),
                P2pLineActivitySnapshot.idle(), List.of(new P2pPhysicalToteAssignment(
                        duplicateId, "125", first.lineId(), first.destination())));
        P2pLineLeaseSnapshot secondLine = new P2pLineLeaseSnapshot(second, Optional.of("125"),
                P2pLineActivitySnapshot.idle(), List.of(new P2pPhysicalToteAssignment(
                        new PhysicalToteId("duplicate"), "125", second.lineId(), second.destination())));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseCatalogSnapshot(List.of(firstLine, secondLine)));

        assertEquals("physical assignment IDs must be distinct across lines", failure.getMessage());
    }

    private static P2pLineAllocationRequest request(Fixture fixture, Candidate candidate) {
        return fixture.requestFactory().create(candidate.source(), candidate.toteId(), candidate.sheetKey(),
                "125", List.of("diagnostic-pharmacy"), true);
    }
}
