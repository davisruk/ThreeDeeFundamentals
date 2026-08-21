package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

class DspOperationalReleaseSnapshotTest {

    @Test
    void shouldRetainDistinctPhysicalCandidatesForOneLogicalSheet() {
        DspSchedulerOrderState logicalState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", "pharmacy-1");
        DspOperationalReleaseCandidate first = candidate(
                "tote-1", 1, logicalState, List.of("pharmacy-1"));
        DspOperationalReleaseCandidate second = candidate(
                "tote-2", 2, logicalState, List.of("pharmacy-1"));

        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(first, second),
                List.of(group("sc-1", "pharmacy-1", 0, 1)));

        assertEquals(List.of(first, second), snapshot.candidates());
        assertEquals(first, snapshot.findByPhysicalToteId(new PhysicalToteId("tote-1")).orElseThrow());
        assertEquals(second, snapshot.findByPhysicalToteId(new PhysicalToteId("tote-2")).orElseThrow());
        assertFalse(snapshot.findByPhysicalToteId(new PhysicalToteId("missing")).isPresent());
        assertThrows(IllegalArgumentException.class, () -> snapshot.findByPhysicalToteId(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(List.of(first, first), List.of(group("sc-1", "pharmacy-1", 0, 1))));
    }

    @Test
    void shouldPreserveMultiPharmacyAdaptedCandidateWithoutDuplication() {
        DspSchedulerOrderState logicalState = logicalState(
                "adapted-1", OrderType.ADAPTED, "sc-1", "pharmacy-2");
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", 5, logicalState, List.of(" pharmacy-2 ", "pharmacy-1"));
        List<ServiceCentrePharmacyGroup> groups = List.of(
                group("sc-1", "pharmacy-1", 0, 2),
                group("sc-1", "pharmacy-2", 1, 5));

        DspOperationalReleaseSnapshot snapshot = snapshot(List.of(candidate), groups);

        assertEquals(List.of(candidate), snapshot.candidates());
        assertEquals(List.of("pharmacy-2", "pharmacy-1"), candidate.pharmacyIds());
        assertEquals(0, snapshot.groupIndexFor(candidate));
        assertEquals(groups, snapshot.groupsForServiceCentre(" sc-1 "));
        assertEquals(List.of(), snapshot.groupsForServiceCentre("sc-2"));
    }

    @Test
    void shouldRequirePharmacyPureFulfilmentCandidates() {
        DspSchedulerOrderState fullPack = logicalState(
                "full-pack-1", OrderType.FULL_PACK, "sc-1", "pharmacy-1");
        DspSchedulerOrderState associated = logicalState(
                "associated-1", OrderType.ASSOCIATED, "sc-1", "pharmacy-1");

        assertThrows(
                IllegalArgumentException.class,
                () -> candidate(
                        "tote-1", 1, fullPack, List.of("pharmacy-1", "pharmacy-2")));
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate(
                        "tote-2", 2, associated, List.of("pharmacy-1", "pharmacy-2")));
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate("tote-3", 3, fullPack, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate(
                        "tote-4", 4, fullPack, List.of("pharmacy-1", " pharmacy-1 ")));
    }

    @Test
    void shouldRequireContiguousServiceCentrePharmacyGroups() {
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        List.of(),
                        List.of(group("sc-1", "pharmacy-1", 1, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        List.of(),
                        List.of(
                                group("sc-1", "pharmacy-1", 0, 1),
                                group("sc-1", "pharmacy-2", 2, 2))));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        List.of(),
                        List.of(
                                group("sc-1", "pharmacy-1", 0, 1),
                                group("sc-1", "pharmacy-2", 0, 2))));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        List.of(),
                        List.of(
                                group("sc-1", "pharmacy-1", 0, 1),
                                group(" sc-1 ", " pharmacy-1 ", 1, 2))));

        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(),
                List.of(
                        group("sc-1", "pharmacy-1", 0, 1),
                        group("sc-1", "pharmacy-2", 1, 2),
                        group("sc-2", "pharmacy-3", 0, 3)));
        assertEquals(3, snapshot.pharmacyGroups().size());
    }

    @Test
    void shouldRejectCandidateWithoutConfiguredPharmacyGroup() {
        DspSchedulerOrderState logicalState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", "pharmacy-1");
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", 1, logicalState, List.of("pharmacy-1"));

        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        List.of(candidate),
                        List.of(group("sc-1", "different-pharmacy", 0, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        List.of(candidate),
                        List.of(group("different-sc", "pharmacy-1", 0, 1))));
    }

    @Test
    void shouldReturnDefensiveImmutableCollections() {
        DspSchedulerOrderState logicalState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", "pharmacy-1");
        List<String> sourcePharmacies = new ArrayList<>(List.of("pharmacy-1"));
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", 1, logicalState, sourcePharmacies);
        List<DspOperationalReleaseCandidate> sourceCandidates = new ArrayList<>(List.of(candidate));
        List<ServiceCentrePharmacyGroup> sourceGroups = new ArrayList<>(
                List.of(group("sc-1", "pharmacy-1", 0, 1)));
        Map<StationType, StationAdmissionSnapshot> sourceAdmissions = new LinkedHashMap<>();
        sourceAdmissions.put(StationType.P2P, openAdmission(StationType.P2P));
        Set<PreparedLineKey> sourcePreparedLines = new LinkedHashSet<>(
                Set.of(new PreparedLineKey("order-1", "line-1")));
        OperationalCandidateRouteAdmission routeAdmission =
                new OperationalCandidateRouteAdmission(
                        new PhysicalToteId("tote-1"),
                        openAdmission(StationType.P2P, "p2p-1"));
        List<OperationalCandidateRouteAdmission> sourceRouteAdmissions =
                new ArrayList<>(List.of(routeAdmission));

        DspOperationalReleaseSnapshot snapshot = new DspOperationalReleaseSnapshot(
                sourceCandidates,
                sourceGroups,
                sourceAdmissions,
                sourcePreparedLines,
                sourceRouteAdmissions);
        sourcePharmacies.clear();
        sourceCandidates.clear();
        sourceGroups.clear();
        sourceAdmissions.clear();
        sourcePreparedLines.clear();
        sourceRouteAdmissions.clear();

        assertEquals(List.of("pharmacy-1"), candidate.pharmacyIds());
        assertEquals(List.of(candidate), snapshot.candidates());
        assertEquals(1, snapshot.pharmacyGroups().size());
        assertEquals(Set.of(new PreparedLineKey("order-1", "line-1")), snapshot.preparedLineKeys());
        assertEquals(Set.of(StationType.P2P), snapshot.stationAdmissions().keySet());
        assertEquals(List.of(routeAdmission), snapshot.routeAdmissions());
        assertEquals(
                routeAdmission,
                snapshot.findRouteAdmission(new PhysicalToteId("tote-1"), StationType.P2P)
                        .orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> candidate.pharmacyIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.candidates().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.pharmacyGroups().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.stationAdmissions().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.preparedLineKeys().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.routeAdmissions().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot.groupIndexFor(candidate(
                        "other-tote", 2, logicalState, List.of("pharmacy-1"))));
    }

    @Test
    void shouldRejectRouteAdmissionOutsideCandidateRoute() {
        DspSchedulerOrderState logicalState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", "pharmacy-1");
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", 1, logicalState, List.of("pharmacy-1"));
        List<ServiceCentrePharmacyGroup> groups = List.of(
                group("sc-1", "pharmacy-1", 0, 1));
        OperationalCandidateRouteAdmission wrongStation =
                new OperationalCandidateRouteAdmission(
                        new PhysicalToteId("tote-1"),
                        openAdmission(StationType.THIRD_PARTY, "third-party-1"));
        OperationalCandidateRouteAdmission unknownCandidate =
                new OperationalCandidateRouteAdmission(
                        new PhysicalToteId("missing"),
                        openAdmission(StationType.P2P, "p2p-1"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalReleaseSnapshot(
                        List.of(candidate), groups, Map.of(), Set.of(),
                        List.of(wrongStation)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalReleaseSnapshot(
                        List.of(candidate), groups, Map.of(), Set.of(),
                        List.of(unknownCandidate)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalReleaseSnapshot(
                        List.of(candidate), groups, Map.of(), Set.of(),
                        List.of(
                                new OperationalCandidateRouteAdmission(
                                        new PhysicalToteId("tote-1"),
                                        openAdmission(StationType.P2P, "p2p-1")),
                                new OperationalCandidateRouteAdmission(
                                        new PhysicalToteId("tote-1"),
                                        openAdmission(StationType.P2P, "p2p-2")))));
    }

    private static DspOperationalReleaseSnapshot snapshot(
            List<DspOperationalReleaseCandidate> candidates,
            List<ServiceCentrePharmacyGroup> groups) {
        return new DspOperationalReleaseSnapshot(candidates, groups, Map.of(), Set.of());
    }

    private static DspOperationalReleaseCandidate candidate(
            String physicalToteId,
            long sourceSequenceNumber,
            DspSchedulerOrderState logicalState,
            List<String> pharmacyIds) {
        NotionalToteOrder order = logicalState.order();
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                sourceSequenceNumber,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        return new DspOperationalReleaseCandidate(
                physicalCandidate,
                logicalState,
                pharmacyIds);
    }

    private static DspSchedulerOrderState logicalState(
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            String pharmacyId) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                "product-1",
                1,
                pharmacyId,
                "patient-1",
                "prescription-1",
                DspOrderLineType.FULL_PACK,
                orderId,
                1,
                1);
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(item),
                999,
                1);
        RouteRequirements route = new RouteRequirements(
                false,
                orderType == OrderType.ADAPTED,
                false,
                orderType != OrderType.ADAPTED,
                false,
                StartLocation.OSR);
        return new DspSchedulerOrderState(order, route, DspOrderStatus.WAITING);
    }

    private static ServiceCentrePharmacyGroup group(
            String serviceCentreId,
            String pharmacyId,
            int groupIndex,
            long sourceSequenceNumber) {
        return new ServiceCentrePharmacyGroup(
                serviceCentreId,
                pharmacyId,
                groupIndex,
                sourceSequenceNumber);
    }

    private static StationAdmissionSnapshot openAdmission(StationType stationType) {
        return openAdmission(stationType, null);
    }

    private static StationAdmissionSnapshot openAdmission(
            StationType stationType,
            String targetId) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                true,
                "",
                Optional.ofNullable(targetId));
    }
}
