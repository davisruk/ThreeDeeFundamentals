package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;

class PharmacyGroupedSourceSequenceRankingPolicyTest {

    private final PharmacyGroupedSourceSequenceRankingPolicy policy =
            new PharmacyGroupedSourceSequenceRankingPolicy();

    @Test
    void shouldChooseHighestPriorityEligibleServiceCentreCohort() {
        OperationalReleaseSelection lowerPriority = selection(candidate(
                "low-tote", "low-order", 1, OrderType.FULL_PACK,
                "sc-low", 990, 1, List.of("pharmacy-low")));
        OperationalReleaseSelection firstHighPriority = selection(candidate(
                "high-tote-1", "high-order-1", 1, OrderType.FULL_PACK,
                "sc-high", 999, 20, List.of("pharmacy-high")));
        OperationalReleaseSelection secondHighPriority = selection(candidate(
                "high-tote-2", "high-order-2", 1, OrderType.FULL_PACK,
                "sc-high", 999, 21, List.of("pharmacy-high")));
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(lowerPriority, firstHighPriority, secondHighPriority),
                List.of(
                        group("sc-low", "pharmacy-low", 0, 1),
                        group("sc-high", "pharmacy-high", 0, 20)));

        assertEquals(
                List.of(firstHighPriority, secondHighPriority),
                policy.rank(
                        List.of(lowerPriority, secondHighPriority, firstHighPriority),
                        snapshot));
    }

    @Test
    void shouldResolveServiceCentrePriorityTieDeterministically() {
        OperationalReleaseSelection serviceCentreB = selection(candidate(
                "tote-b", "order-b", 1, OrderType.FULL_PACK,
                "sc-b", 999, 1, List.of("pharmacy-b")));
        OperationalReleaseSelection serviceCentreA = selection(candidate(
                "tote-a", "order-a", 1, OrderType.FULL_PACK,
                "sc-a", 999, 2, List.of("pharmacy-a")));
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(serviceCentreB, serviceCentreA),
                List.of(
                        group("sc-b", "pharmacy-b", 0, 1),
                        group("sc-a", "pharmacy-a", 0, 2)));

        assertEquals(
                List.of(serviceCentreA),
                policy.rank(List.of(serviceCentreB, serviceCentreA), snapshot));
    }

    @Test
    void shouldKeepPharmacyGroupTogetherBeforeLaterGroup() {
        OperationalReleaseSelection laterGroupEarlySource = selection(candidate(
                "later-tote", "later-order", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-2")));
        OperationalReleaseSelection firstGroupLateSource = selection(candidate(
                "first-tote", "first-order", 1, OrderType.FULL_PACK,
                "sc-1", 999, 99, List.of("pharmacy-1")));
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(laterGroupEarlySource, firstGroupLateSource),
                List.of(
                        group("sc-1", "pharmacy-1", 0, 10),
                        group("sc-1", "pharmacy-2", 1, 1)));

        assertEquals(
                List.of(firstGroupLateSource, laterGroupEarlySource),
                policy.rank(List.of(laterGroupEarlySource, firstGroupLateSource), snapshot));
    }

    @Test
    void shouldRankActiveLinePharmacyAffinityBeforeStaticPharmacyGroup() {
        OperationalReleaseSelection firstGroup = selection(candidate(
                "first-tote", "first-order", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-1")));
        DspOperationalReleaseCandidate affinityCandidate = candidate(
                "affinity-tote", "affinity-order", 1, OrderType.FULL_PACK,
                "sc-1", 999, 2, List.of("pharmacy-2"));
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P, "target-affinity");
        OperationalReleaseSelection affinity = new OperationalReleaseSelection(
                affinityCandidate,
                new OperationalRouteEntry(StationType.P2P, destination.targetId()),
                Optional.of(new P2pPhysicalToteAssignment(
                        affinityCandidate.physicalCandidate().physicalToteId(),
                        "sc-1",
                        new P2pLineId("line-1"),
                        destination)),
                true);
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(firstGroup, affinity),
                List.of(
                        group("sc-1", "pharmacy-1", 0, 1),
                        group("sc-1", "pharmacy-2", 1, 2)));

        assertEquals(List.of(affinity, firstGroup),
                policy.rank(List.of(firstGroup, affinity), snapshot));
    }

    @Test
    void shouldUseStablePhysicalSourceOrderWithinPharmacy() {
        OperationalReleaseSelection laterSource = selection(candidate(
                "tote-z", "order-z", 1, OrderType.FULL_PACK,
                "sc-1", 999, 2, List.of("pharmacy-1")));
        OperationalReleaseSelection laterSheet = selection(candidate(
                "tote-y", "order-y", 2, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-1")));
        OperationalReleaseSelection laterOrderId = selection(candidate(
                "tote-x", "order-b", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-1")));
        OperationalReleaseSelection earlierOrderId = selection(candidate(
                "tote-b", "order-a", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-1")));
        OperationalReleaseSelection physicalIdTieBreak = selection(candidate(
                "tote-a", "order-a", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-1")));
        List<OperationalReleaseSelection> selections = List.of(
                laterSource,
                laterSheet,
                laterOrderId,
                earlierOrderId,
                physicalIdTieBreak);
        DspOperationalReleaseSnapshot snapshot = snapshot(
                selections,
                List.of(group("sc-1", "pharmacy-1", 0, 1)));

        assertEquals(
                List.of(
                        physicalIdTieBreak,
                        earlierOrderId,
                        laterOrderId,
                        laterSheet,
                        laterSource),
                policy.rank(selections, snapshot));
    }

    @Test
    void shouldNotPrioritizeAssociatedOverFullPack() {
        OperationalReleaseSelection associated = selection(candidate(
                "associated-tote", "associated-order", 1, OrderType.ASSOCIATED,
                "sc-1", 999, 2, List.of("pharmacy-1")));
        OperationalReleaseSelection fullPack = selection(candidate(
                "full-pack-tote", "full-pack-order", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-1")));
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(associated, fullPack),
                List.of(group("sc-1", "pharmacy-1", 0, 1)));

        assertEquals(
                List.of(fullPack, associated),
                policy.rank(List.of(associated, fullPack), snapshot));
    }

    @Test
    void shouldRankMultiPharmacyAdaptedCandidateOnceAtEarliestGroup() {
        OperationalReleaseSelection multiPharmacyAdapted = selection(candidate(
                "adapted-tote", "adapted-order", 1, OrderType.ADAPTED,
                "sc-1", 999, 10, List.of("pharmacy-2", "pharmacy-1")));
        OperationalReleaseSelection laterGroup = selection(candidate(
                "full-pack-tote", "full-pack-order", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-2")));
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(multiPharmacyAdapted, laterGroup),
                List.of(
                        group("sc-1", "pharmacy-1", 0, 2),
                        group("sc-1", "pharmacy-2", 1, 1)));

        assertEquals(
                List.of(multiPharmacyAdapted, laterGroup),
                policy.rank(List.of(laterGroup, multiPharmacyAdapted), snapshot));
        assertEquals(1, policy.rank(
                List.of(laterGroup, multiPharmacyAdapted), snapshot).stream()
                .filter(multiPharmacyAdapted::equals)
                .count());
    }

    @Test
    void shouldReturnDefensiveImmutableRanking() {
        OperationalReleaseSelection selection = selection(candidate(
                "tote-1", "order-1", 1, OrderType.FULL_PACK,
                "sc-1", 999, 1, List.of("pharmacy-1")));
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(selection),
                List.of(group("sc-1", "pharmacy-1", 0, 1)));
        List<OperationalReleaseSelection> source = new ArrayList<>(List.of(selection));

        List<OperationalReleaseSelection> ranked = policy.rank(source, snapshot);
        source.clear();

        assertEquals(List.of(selection), ranked);
        assertThrows(UnsupportedOperationException.class, () -> ranked.clear());
        assertEquals(List.of(), policy.rank(List.of(), snapshot));
        assertThrows(IllegalArgumentException.class, () -> policy.rank(null, snapshot));
        assertThrows(IllegalArgumentException.class, () -> policy.rank(List.of(selection), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.rank(List.of(selection, selection), snapshot));

        OperationalReleaseSelection absent = selection(candidate(
                "absent-tote", "absent-order", 1, OrderType.FULL_PACK,
                "sc-1", 999, 2, List.of("pharmacy-1")));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.rank(List.of(absent), snapshot));

        List<OperationalReleaseSelection> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> policy.rank(withNull, snapshot));
    }

    private static DspOperationalReleaseSnapshot snapshot(
            List<OperationalReleaseSelection> selections,
            List<ServiceCentrePharmacyGroup> pharmacyGroups) {
        return new DspOperationalReleaseSnapshot(
                selections.stream().map(OperationalReleaseSelection::candidate).toList(),
                pharmacyGroups,
                Map.of(),
                Set.of());
    }

    private static OperationalReleaseSelection selection(
            DspOperationalReleaseCandidate candidate) {
        return new OperationalReleaseSelection(
                candidate,
                new OperationalRouteEntry(
                        StationType.P2P,
                        "target-" + candidate.physicalCandidate().physicalToteId().value()));
    }

    private static DspOperationalReleaseCandidate candidate(
            String physicalToteId,
            String orderId,
            int sheetNumber,
            OrderType orderType,
            String serviceCentreId,
            int priority,
            long sourceSequenceNumber,
            List<String> pharmacyIds) {
        List<DspOrderItem> items = pharmacyIds.stream()
                .map(pharmacyId -> item(
                        "line-" + physicalToteId + "-" + pharmacyId,
                        pharmacyId,
                        orderType == OrderType.ADAPTED
                                ? DspOrderLineType.ADAPTED
                                : DspOrderLineType.FULL_PACK))
                .toList();
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                sheetNumber,
                orderType,
                items,
                priority,
                sourceSequenceNumber);
        DspSchedulerOrderState logicalState = new DspSchedulerOrderState(
                order,
                new RouteRequirements(
                        false,
                        orderType == OrderType.ADAPTED,
                        false,
                        orderType != OrderType.ADAPTED,
                        false,
                        StartLocation.OSR),
                DspOrderStatus.WAITING);
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                orderType,
                serviceCentreId,
                sourceSequenceNumber,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        return new DspOperationalReleaseCandidate(
                physicalCandidate, logicalState, pharmacyIds);
    }

    private static DspOrderItem item(
            String lineReference,
            String pharmacyId,
            DspOrderLineType lineType) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                lineType,
                "reference-" + lineReference,
                1,
                1);
    }

    private static ServiceCentrePharmacyGroup group(
            String serviceCentreId,
            String pharmacyId,
            int groupIndex,
            long firstSourceSequenceNumber) {
        return new ServiceCentrePharmacyGroup(
                serviceCentreId,
                pharmacyId,
                groupIndex,
                firstSourceSequenceNumber);
    }
}
