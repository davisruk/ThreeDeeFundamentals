package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02OperationalPhysicalToteCandidate;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
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
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationCalibrationStatus;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetDefinition;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class DspOperationalReleaseSnapshotFactoryTest {

    private final DspOperationalReleaseSnapshotFactory factory =
            new DspOperationalReleaseSnapshotFactory();

    @Test
    void shouldBuildDetachedStickyLineAndTargetAdmissionSnapshot() {
        OperationalRouteDestination destination = new OperationalRouteDestination(
                StationType.P2P, "p2p-line-1");
        P2pLineLeaseCatalogSnapshot leases = new P2pLineLeaseCatalogSnapshot(List.of(
                new P2pLineLeaseSnapshot(
                        new P2pLineDefinition(new P2pLineId("line-1"), destination),
                        Optional.empty(),
                        P2pLineActivitySnapshot.idle(),
                        List.of())));
        List<OperationalRouteTargetAdmissionSnapshot> targetAdmissions = new ArrayList<>(List.of(
                new OperationalRouteTargetAdmissionSnapshot(
                        StationType.P2P, destination.targetId(), 1, 0)));
        OperationalRouteEntryQueue queue = new OperationalRouteEntryQueue(
                new OperationalRouteTargetDefinition(
                        StationType.P2P, destination.targetId(), 1));
        OperationalCandidateRouteAdmissionFactory admissionFactory =
                new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(),
                        (stationType, candidate, snapshot) -> null,
                        new OperationalRouteTargetRegistry(List.of(queue)));

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of()),
                new InboundToteManifestCatalog(List.of()),
                logicalSnapshot(List.of()),
                admissionFactory,
                leases,
                targetAdmissions);
        targetAdmissions.clear();

        assertSame(leases, snapshot.p2pLineLeases());
        assertEquals(Map.of(destination, true), snapshot.p2pRouteAdmissions());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.p2pRouteAdmissions().clear());

        P2pElasticAllocationSnapshot elasticAllocation = new P2pElasticAllocationSnapshot(
                P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                LocalDateTime.of(2026, 8, 24, 6, 0),
                List.of(new P2pLineId("line-1")),
                1,
                List.of(),
                List.of());
        DspOperationalReleaseSnapshot elasticSnapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of()),
                new InboundToteManifestCatalog(List.of()),
                logicalSnapshot(List.of()),
                admissionFactory,
                leases,
                List.of(new OperationalRouteTargetAdmissionSnapshot(
                        StationType.P2P, destination.targetId(), 1, 0)),
                elasticAllocation);
        assertSame(elasticAllocation, elasticSnapshot.elasticP2pAllocation().orElseThrow());
    }

    @Test
    void shouldJoinPhysicalManifestToExactLogicalSheet() {
        DspOrderItem item = item("line-1", "product-1", "pharmacy-1");
        InboundToteManifest manifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1", List.of(item), 4);
        OsrProcessingReleaseCandidate physicalCandidate = physicalCandidate(manifest);
        DspSchedulerOrderState logicalState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", List.of(item), 999,
                DspOrderStatus.WAITING);
        PreparedLineKey preparedLineKey = new PreparedLineKey("order-1", "line-1");
        WarehouseSchedulerSnapshot logicalSnapshot = new WarehouseSchedulerSnapshot(
                List.of(logicalState), Map.of(), Set.of(preparedLineKey), Optional.empty());

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(physicalCandidate)),
                new InboundToteManifestCatalog(List.of(manifest)),
                logicalSnapshot);

        DspOperationalReleaseCandidate joined = snapshot.candidates().get(0);
        assertSame(physicalCandidate, joined.physicalCandidate());
        assertSame(logicalState, joined.logicalOrderState());
        assertEquals(List.of("pharmacy-1"), joined.pharmacyIds());
        assertEquals(Set.of(preparedLineKey), snapshot.preparedLineKeys());
        assertEquals(0, snapshot.groupIndexFor(joined));
    }

    @Test
    void shouldJoinOsrAndAv02PhysicalCandidatesWithoutCreatingAnEmptyManifest() {
        DspOrderItem osrItem = item("line-osr", "product-osr", "pharmacy-osr");
        InboundToteManifest osrManifest = manifest(
                "tote-osr", "order-osr", OrderType.FULL_PACK, "sc-1", List.of(osrItem), 1);
        OsrProcessingReleaseCandidate osrCandidate = physicalCandidate(osrManifest);
        DspSchedulerOrderState osrState = logicalState(
                "order-osr", OrderType.FULL_PACK, "sc-1", List.of(osrItem), 999,
                DspOrderStatus.WAITING);

        DspOrderItem emptyItem = item("line-empty", "product-empty", "pharmacy-empty");
        NotionalToteOrder emptyOrder = new NotionalToteOrder(
                "order-empty",
                "notional-order-empty",
                "sc-1",
                1,
                OrderType.EMPTY,
                List.of(emptyItem),
                999,
                2);
        DspSchedulerOrderState emptyState = new DspSchedulerOrderState(
                emptyOrder,
                new RouteRequirements(false, false, false, true, false, StartLocation.AV02),
                DspOrderStatus.WAITING);
        PhysicalToteId av02PhysicalId = new PhysicalToteId("av02-000001");
        Av02AllocatedTote allocatedEmpty = new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        av02PhysicalId,
                        emptyOrder.orderSheetKey(),
                        OrderType.EMPTY,
                        "sc-1",
                        PhysicalToteRole.PRE_P2P,
                        2),
                PhysicalToteRecord.preP2p(av02PhysicalId));

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(osrCandidate)),
                new InboundToteManifestCatalog(List.of(osrManifest)),
                new Av02InventorySnapshot(2, List.of(allocatedEmpty), List.of()),
                logicalSnapshot(List.of(osrState, emptyState)));

        assertEquals(2, snapshot.candidates().size());
        assertSame(osrCandidate, snapshot.candidates().get(0).physicalCandidate());
        assertEquals(OperationalPhysicalToteSource.OSR,
                snapshot.candidates().get(0).physicalCandidate().source());
        assertTrue(snapshot.candidates().stream()
                .map(candidate -> candidate.physicalCandidate())
                .anyMatch(candidate -> candidate instanceof Av02OperationalPhysicalToteCandidate));
        DspOperationalReleaseCandidate av02Candidate = snapshot.candidates().stream()
                .filter(candidate -> candidate.physicalCandidate().source()
                        == OperationalPhysicalToteSource.AV02)
                .findFirst()
                .orElseThrow();
        assertEquals(OrderType.EMPTY, av02Candidate.physicalCandidate().orderType());
        assertEquals(List.of("pharmacy-empty"), av02Candidate.pharmacyIds());
        assertEquals(0, snapshot.groupIndexFor(snapshot.candidates().get(0)));
        assertEquals(1, snapshot.groupIndexFor(av02Candidate));
    }

    @Test
    void shouldKeepSeveralPhysicalManifestsForOneLogicalSheet() {
        DspOrderItem firstItem = item("line-1", "product-1", "pharmacy-1");
        DspOrderItem secondItem = item("line-2", "product-2", "pharmacy-1");
        InboundToteManifest firstManifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1", List.of(firstItem), 1);
        InboundToteManifest secondManifest = manifest(
                "tote-2", "order-1", OrderType.FULL_PACK, "sc-1", List.of(secondItem), 2);
        DspSchedulerOrderState logicalState = logicalState(
                "order-1",
                OrderType.FULL_PACK,
                "sc-1",
                List.of(firstItem, secondItem),
                999,
                DspOrderStatus.WAITING);

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(
                        physicalCandidate(firstManifest),
                        physicalCandidate(secondManifest))),
                new InboundToteManifestCatalog(List.of(firstManifest, secondManifest)),
                logicalSnapshot(List.of(logicalState)));

        assertEquals(
                List.of(new PhysicalToteId("tote-1"), new PhysicalToteId("tote-2")),
                snapshot.candidates().stream()
                        .map(candidate -> candidate.physicalCandidate().physicalToteId())
                        .toList());
        assertSame(logicalState, snapshot.candidates().get(0).logicalOrderState());
        assertSame(logicalState, snapshot.candidates().get(1).logicalOrderState());
    }

    @Test
    void shouldDeriveStablePharmacyGroupsFromCompleteCatalog() {
        InboundToteManifest laterStored = manifest(
                "tote-b", "order-b", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-b", "product-b", "pharmacy-b")), 20);
        InboundToteManifest sameSequenceLaterInCatalog = manifest(
                "tote-c", "order-c", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-c", "product-c", "pharmacy-c")), 20);
        InboundToteManifest earlierDeparted = manifest(
                "tote-a", "order-a", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-a", "product-a", "pharmacy-a")), 10);
        DspSchedulerOrderState storedLogicalState = logicalState(
                "order-b",
                OrderType.FULL_PACK,
                "sc-1",
                laterStored.items(),
                999,
                DspOrderStatus.WAITING);

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(physicalCandidate(laterStored))),
                new InboundToteManifestCatalog(List.of(
                        laterStored,
                        sameSequenceLaterInCatalog,
                        earlierDeparted)),
                logicalSnapshot(List.of(storedLogicalState)));

        assertEquals(List.of(
                new ServiceCentrePharmacyGroup("sc-1", "pharmacy-a", 0, 10),
                new ServiceCentrePharmacyGroup("sc-1", "pharmacy-b", 1, 20),
                new ServiceCentrePharmacyGroup("sc-1", "pharmacy-c", 2, 20)),
                snapshot.pharmacyGroups());
        assertEquals(1, snapshot.groupIndexFor(snapshot.candidates().get(0)));
    }

    @Test
    void shouldRetainAllPharmaciesForAdaptedManifest() {
        DspOrderItem first = adaptedItem("line-1", "product-1", "pharmacy-2", "associated-1");
        DspOrderItem second = adaptedItem("line-2", "product-2", "pharmacy-1", "associated-2");
        DspOrderItem repeated = adaptedItem("line-3", "product-3", "pharmacy-2", "associated-3");
        InboundToteManifest manifest = manifest(
                "tote-1",
                "adapted-1",
                OrderType.ADAPTED,
                "sc-1",
                List.of(first, second, repeated),
                1);
        DspSchedulerOrderState logicalState = logicalState(
                "adapted-1",
                OrderType.ADAPTED,
                "sc-1",
                manifest.items(),
                999,
                DspOrderStatus.BLOCKED);

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(physicalCandidate(manifest))),
                new InboundToteManifestCatalog(List.of(manifest)),
                logicalSnapshot(List.of(logicalState)));

        assertEquals(
                List.of("pharmacy-2", "pharmacy-1"),
                snapshot.candidates().get(0).pharmacyIds());
        assertEquals(1, snapshot.candidates().size());
    }

    @Test
    void shouldRejectMissingOrDuplicateLogicalSheetState() {
        InboundToteManifest manifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-1", "product-1", "pharmacy-1")), 1);
        DspSchedulerOrderState logicalState = logicalState(
                "order-1",
                OrderType.FULL_PACK,
                "sc-1",
                manifest.items(),
                999,
                DspOrderStatus.WAITING);
        OsrProcessingReleaseSnapshot physicalSnapshot = new OsrProcessingReleaseSnapshot(
                List.of(physicalCandidate(manifest)));
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of(manifest));

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(physicalSnapshot, catalog, logicalSnapshot(List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        physicalSnapshot,
                        catalog,
                        logicalSnapshot(List.of(logicalState, logicalState))));
    }

    @Test
    void shouldRejectManifestLogicalIdentityOrLineMismatch() {
        DspOrderItem logicalItem = item("line-1", "product-1", "pharmacy-1");
        DspSchedulerOrderState logicalState = logicalState(
                "order-1",
                OrderType.FULL_PACK,
                "sc-1",
                List.of(logicalItem),
                999,
                DspOrderStatus.WAITING);
        OsrProcessingReleaseCandidate physicalCandidate = physicalCandidate(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1", 1);
        InboundToteManifest wrongSheetManifest = manifest(
                "tote-1", "different-order", OrderType.FULL_PACK, "sc-1",
                List.of(logicalItem), 1);
        InboundToteManifest contradictoryLineManifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-1", "different-product", "pharmacy-1")), 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        new OsrProcessingReleaseSnapshot(List.of(physicalCandidate)),
                        new InboundToteManifestCatalog(List.of(wrongSheetManifest)),
                        logicalSnapshot(List.of(logicalState))));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        new OsrProcessingReleaseSnapshot(List.of(physicalCandidate)),
                        new InboundToteManifestCatalog(List.of(contradictoryLineManifest)),
                        logicalSnapshot(List.of(logicalState))));
    }

    @Test
    void shouldRejectReleasedOrCompletedLogicalStateForStoredCandidate() {
        InboundToteManifest manifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-1", "product-1", "pharmacy-1")), 1);
        OsrProcessingReleaseSnapshot physicalSnapshot = new OsrProcessingReleaseSnapshot(
                List.of(physicalCandidate(manifest)));
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of(manifest));

        for (DspOrderStatus status : List.of(DspOrderStatus.RELEASED, DspOrderStatus.COMPLETED)) {
            DspSchedulerOrderState logicalState = logicalState(
                    "order-1", OrderType.FULL_PACK, "sc-1", manifest.items(), 999, status);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> factory.create(
                            physicalSnapshot,
                            catalog,
                            logicalSnapshot(List.of(logicalState))));
        }
    }

    @Test
    void shouldRejectInconsistentServiceCentrePriority() {
        DspSchedulerOrderState first = logicalState(
                "order-1",
                OrderType.FULL_PACK,
                "sc-1",
                List.of(item("line-1", "product-1", "pharmacy-1")),
                999,
                DspOrderStatus.WAITING);
        DspSchedulerOrderState second = logicalState(
                "order-2",
                OrderType.FULL_PACK,
                "sc-1",
                List.of(item("line-2", "product-2", "pharmacy-2")),
                998,
                DspOrderStatus.WAITING);

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        new OsrProcessingReleaseSnapshot(List.of()),
                        new InboundToteManifestCatalog(List.of()),
                        logicalSnapshot(List.of(first, second))));
    }

    @Test
    void shouldIgnoreLegacyActiveServiceCentreWindow() {
        InboundToteManifest manifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-1", "product-1", "pharmacy-1")), 1);
        DspSchedulerOrderState logicalState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", manifest.items(), 999,
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot logicalSnapshot = new WarehouseSchedulerSnapshot(
                List.of(logicalState), Map.of(), Set.of(), Optional.of("different-sc"));

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(physicalCandidate(manifest))),
                new InboundToteManifestCatalog(List.of(manifest)),
                logicalSnapshot);

        assertEquals(new PhysicalToteId("tote-1"),
                snapshot.candidates().get(0).physicalCandidate().physicalToteId());
    }

    @Test
    void shouldDeriveCandidateAdmissionFromCompatibilityStationMap() {
        InboundToteManifest manifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-1", "product-1", "pharmacy-1")), 1);
        DspSchedulerOrderState logicalState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", manifest.items(), 999,
                DspOrderStatus.WAITING);
        StationAdmissionSnapshot admission = openAdmission(StationType.P2P, "p2p-legacy");
        WarehouseSchedulerSnapshot logicalSnapshot = new WarehouseSchedulerSnapshot(
                List.of(logicalState),
                Map.of(StationType.P2P, admission),
                Set.of(),
                Optional.empty());

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(physicalCandidate(manifest))),
                new InboundToteManifestCatalog(List.of(manifest)),
                logicalSnapshot);

        assertSame(admission, snapshot.routeAdmissions().get(0).stationAdmission());
        assertEquals(
                "p2p-legacy",
                snapshot.findRouteAdmission(new PhysicalToteId("tote-1"), StationType.P2P)
                        .orElseThrow()
                        .stationAdmission()
                        .selectedTargetId()
                        .orElseThrow());
    }

    @Test
    void shouldCaptureCandidateSpecificLiveAdmissionsUsingOneLogicalSnapshot() {
        InboundToteManifest firstManifest = manifest(
                "tote-1", "order-1", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-1", "product-1", "pharmacy-1")), 1);
        InboundToteManifest secondManifest = manifest(
                "tote-2", "order-2", OrderType.FULL_PACK, "sc-1",
                List.of(item("line-2", "product-2", "pharmacy-2")), 2);
        DspSchedulerOrderState firstState = logicalState(
                "order-1", OrderType.FULL_PACK, "sc-1", firstManifest.items(), 999,
                DspOrderStatus.WAITING);
        DspSchedulerOrderState secondState = logicalState(
                "order-2", OrderType.FULL_PACK, "sc-1", secondManifest.items(), 999,
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot logicalSnapshot = logicalSnapshot(
                List.of(firstState, secondState));
        OperationalRouteEntryQueue firstQueue = new OperationalRouteEntryQueue(
                new OperationalRouteTargetDefinition(StationType.P2P, "p2p-1", 1));
        OperationalRouteEntryQueue secondQueue = new OperationalRouteEntryQueue(
                new OperationalRouteTargetDefinition(StationType.P2P, "p2p-2", 1));
        AtomicInteger resolutionCount = new AtomicInteger();
        OperationalCandidateRouteAdmissionFactory admissionFactory =
                new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(),
                        (stationType, candidate, observedSnapshot) -> {
                            assertSame(logicalSnapshot, observedSnapshot);
                            resolutionCount.incrementAndGet();
                            String targetId = candidate.order().orderId().equals("order-1")
                                    ? "p2p-1"
                                    : "p2p-2";
                            return openAdmission(stationType, targetId);
                        },
                        new OperationalRouteTargetRegistry(List.of(firstQueue, secondQueue)));

        DspOperationalReleaseSnapshot snapshot = factory.create(
                new OsrProcessingReleaseSnapshot(List.of(
                        physicalCandidate(firstManifest),
                        physicalCandidate(secondManifest))),
                new InboundToteManifestCatalog(List.of(firstManifest, secondManifest)),
                logicalSnapshot,
                admissionFactory);

        assertEquals(2, resolutionCount.get());
        assertEquals(
                List.of("p2p-1", "p2p-2"),
                snapshot.routeAdmissions().stream()
                        .map(routeAdmission -> routeAdmission.stationAdmission()
                                .selectedTargetId().orElseThrow())
                        .toList());
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        new OsrProcessingReleaseSnapshot(List.of()),
                        new InboundToteManifestCatalog(List.of()),
                        logicalSnapshot(List.of()),
                        null));
    }

    private static WarehouseSchedulerSnapshot logicalSnapshot(
            List<DspSchedulerOrderState> logicalStates) {
        return new WarehouseSchedulerSnapshot(
                logicalStates, Map.of(), Set.of(), Optional.empty());
    }

    private static DspSchedulerOrderState logicalState(
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            List<DspOrderItem> items,
            int priority,
            DspOrderStatus status) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                items,
                priority,
                1);
        RouteRequirements route = new RouteRequirements(
                false,
                orderType == OrderType.ADAPTED,
                false,
                orderType != OrderType.ADAPTED,
                false,
                StartLocation.OSR);
        return new DspSchedulerOrderState(order, route, status);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            List<DspOrderItem> items,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey(orderId, 1),
                orderType,
                serviceCentreId,
                items,
                sourceSequenceNumber);
    }

    private static OsrProcessingReleaseCandidate physicalCandidate(
            InboundToteManifest manifest) {
        return physicalCandidate(
                manifest.physicalToteId().value(),
                manifest.orderSheetKey().orderId(),
                manifest.orderType(),
                manifest.serviceCentreId(),
                manifest.sourceSequenceNumber());
    }

    private static OsrProcessingReleaseCandidate physicalCandidate(
            String physicalToteId,
            String orderId,
            OrderType orderType,
            String serviceCentreId,
            long sourceSequenceNumber) {
        return new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey(orderId, 1),
                orderType,
                serviceCentreId,
                sourceSequenceNumber,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
    }

    private static DspOrderItem item(
            String lineReference,
            String productId,
            String pharmacyId) {
        return new DspOrderItem(
                lineReference,
                productId,
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                DspOrderLineType.FULL_PACK,
                lineReference,
                1,
                1);
    }

    private static DspOrderItem adaptedItem(
            String lineReference,
            String productId,
            String pharmacyId,
            String referenceOrderId) {
        return new DspOrderItem(
                lineReference,
                productId,
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                DspOrderLineType.ADAPTED,
                referenceOrderId,
                1,
                1);
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
                Optional.of(targetId));
    }
}
