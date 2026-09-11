package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapState;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;

class DspServiceCentreSupplyCoordinatorBootstrapTest {

    @Test
    void shouldRepresentPreloadedAndHeldUpstreamBatches() {
        Fixture fixture = fixture();
        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();

        ServiceCentreSupplySnapshot preloaded = snapshot.serviceCentres().get(0);
        ServiceCentreSupplySnapshot held = snapshot.serviceCentres().get(1);

        assertEquals("sc-preloaded", preloaded.serviceCentreId());
        assertEquals(ServiceCentreAuthorizationState.PRELOADED, preloaded.authorizationState());
        assertEquals(2, preloaded.preloadedCount());
        assertEquals(0, preloaded.upstreamWaitingCount());
        assertEquals(ServiceCentreAuthorizationState.HELD_UPSTREAM, held.authorizationState());
        assertEquals(0, held.preloadedCount());
        assertEquals(2, held.upstreamWaitingCount());
        assertEquals(2, snapshot.osrOccupancy());
    }

    @Test
    void shouldSeedOnlyPreloadedEmptyAuthorization() {
        Fixture fixture = fixture();
        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();

        assertEquals(Set.of(fixture.preloadedEmpty().orderSheetKey()),
                snapshot.authorizedEmptyOrderSheetKeys());
        assertEquals(Set.of(fixture.preloadedEmpty().orderSheetKey()),
                snapshot.serviceCentres().getFirst().authorizedEmptyOrderSheetKeys());
        assertTrue(snapshot.serviceCentres().get(1).authorizedEmptyOrderSheetKeys().isEmpty());
    }

    @Test
    void shouldRepresentEveryPhysicalManifestIndependently() {
        Fixture fixture = fixture();
        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();

        assertEquals(
                Set.of("preloaded-full", "preloaded-adapted", "later-adapted", "later-full"),
                snapshot.serviceCentres().stream()
                        .flatMap(serviceCentre -> serviceCentre.physicalTotes().stream())
                        .map(tote -> tote.physicalToteId().value())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                Set.of(PhysicalToteSupplyState.PRELOADED_IN_OSR, PhysicalToteSupplyState.HELD_UPSTREAM),
                snapshot.serviceCentres().stream()
                        .flatMap(serviceCentre -> serviceCentre.physicalTotes().stream())
                        .map(PhysicalToteSupplySnapshot::state)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldRejectLowWaterMarkAtOrAboveCapacity() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class,
                () -> new DspServiceCentreSupplyCoordinator(
                        fixture.plan(),
                        new ServiceCentreSupplyConfig(10),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        fixture.bootstrapState()));
    }

    @Test
    void shouldRejectBootstrapInventoryThatDoesNotMatchSupplyPlan() {
        Fixture fixture = fixture();
        OsrPhysicalInventory incompleteInventory = new OsrPhysicalInventory(
                new OsrInventoryConfig(10, List.of("sc-preloaded")));
        incompleteInventory.store(fixture.preloadedFull());
        OsrBootstrapState incompleteBootstrap = new OsrBootstrapState(
                incompleteInventory,
                Set.of(fixture.preloadedEmpty().orderSheetKey()));

        assertThrows(IllegalArgumentException.class,
                () -> new DspServiceCentreSupplyCoordinator(
                        fixture.plan(),
                        new ServiceCentreSupplyConfig(2),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        incompleteBootstrap));
    }

    @Test
    void shouldReturnDeeplyImmutableSnapshots() {
        Fixture fixture = fixture();
        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.serviceCentres().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.authorizedEmptyOrderSheetKeys().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.serviceCentres().getFirst().physicalTotes().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.serviceCentres().getFirst().authorizedEmptyOrderSheetKeys().clear());
    }

    @Test
    void shouldRepresentPartialStartupResidenceAndAuthorizeAllStartupOverflow() {
        PartialFixture fixture = partialFixture();
        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        ServiceCentreSupplySnapshot startupA = serviceCentre(snapshot, "sc-startup-a");
        ServiceCentreSupplySnapshot startupB = serviceCentre(snapshot, "sc-startup-b");

        assertEquals("sc-startup-a", snapshot.activeInboundServiceCentreId().orElseThrow());
        assertEquals(Optional.of(Duration.ZERO), startupA.authorizationElapsedTime());
        assertEquals(Optional.of(Duration.ZERO), startupB.authorizationElapsedTime());
        assertEquals(ServiceCentreAuthorizationState.AUTHORIZED, startupA.authorizationState());
        assertEquals(ServiceCentreAuthorizationState.AUTHORIZED, startupB.authorizationState());
        assertEquals(1, startupA.preloadedCount());
        assertEquals(0, startupA.admittedAfterStartupCount());
        assertEquals(1, startupA.upstreamWaitingCount());
        assertEquals(0, startupB.preloadedCount());
        assertEquals(0, startupB.admittedAfterStartupCount());
        assertEquals(2, startupB.upstreamWaitingCount());
        assertEquals(1, snapshot.osrOccupancy());
        assertEquals(
                PhysicalToteSupplyState.PRELOADED_IN_OSR,
                physicalTote(snapshot, "startup-a-1").state());
        assertEquals(
                PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(snapshot, "startup-a-2").state());
        assertEquals(
                PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(snapshot, "startup-b-1").state());
        assertEquals(
                Set.of(fixture.startupAEmpty(), fixture.startupBEmpty()),
                snapshot.authorizedEmptyOrderSheetKeys());
        assertEquals(Optional.of(Duration.ofSeconds(3)),
                snapshot.nextPhysicalAdmissionElapsedTime());
    }

    @Test
    void shouldRejectInvalidStartupOverflowPartitions() {
        PartialFixture fixture = partialFixture();

        OsrBootstrapState unknownOverflow = bootstrapState(
                fixture.inventoryConfig(),
                List.of(fixture.startupAOne()),
                Set.of(fixture.startupAEmpty(), fixture.startupBEmpty()),
                List.of(fixture.startupATwo(), fixture.startupBOne(), fixture.startupBTwo(),
                        manifest("unknown", "sc-startup-a", OrderType.FULL_PACK, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> new DspServiceCentreSupplyCoordinator(
                        fixture.plan(),
                        new ServiceCentreSupplyConfig(0),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        unknownOverflow));

        InboundToteManifest later = manifest("later-overflow", "sc-later", OrderType.FULL_PACK, 4);
        DspServiceCentreSupplyPlan planWithLater = new DspServiceCentreSupplyPlan(
                List.of(fixture.plan().batches().get(0), fixture.plan().batches().get(1),
                        new ServiceCentreSupplyBatch(
                                "sc-later", 1, 4, false, List.of(later), Set.of())),
                List.of());
        OsrBootstrapState nonStartupOverflow = bootstrapState(
                fixture.inventoryConfig(),
                List.of(fixture.startupAOne()),
                Set.of(fixture.startupAEmpty(), fixture.startupBEmpty()),
                List.of(fixture.startupATwo(), fixture.startupBOne(), fixture.startupBTwo(), later));
        assertThrows(IllegalArgumentException.class,
                () -> new DspServiceCentreSupplyCoordinator(
                        planWithLater,
                        new ServiceCentreSupplyConfig(0),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        nonStartupOverflow));

        OsrBootstrapState omittedStartupManifest = bootstrapState(
                fixture.inventoryConfig(),
                List.of(fixture.startupAOne()),
                Set.of(fixture.startupAEmpty(), fixture.startupBEmpty()),
                List.of(fixture.startupATwo(), fixture.startupBOne()));
        assertThrows(IllegalArgumentException.class,
                () -> new DspServiceCentreSupplyCoordinator(
                        fixture.plan(),
                        new ServiceCentreSupplyConfig(0),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        omittedStartupManifest));
    }

    private static PartialFixture partialFixture() {
        InboundToteManifest startupAOne = manifest(
                "startup-a-1", "sc-startup-a", OrderType.FULL_PACK, 0);
        InboundToteManifest startupATwo = manifest(
                "startup-a-2", "sc-startup-a", OrderType.ADAPTED, 1);
        InboundToteManifest startupBOne = manifest(
                "startup-b-1", "sc-startup-b", OrderType.FULL_PACK, 2);
        InboundToteManifest startupBTwo = manifest(
                "startup-b-2", "sc-startup-b", OrderType.ASSOCIATED, 3);
        OrderSheetKey startupAEmpty = new OrderSheetKey("startup-a-empty", 1);
        OrderSheetKey startupBEmpty = new OrderSheetKey("startup-b-empty", 1);
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(
                1, List.of("sc-startup-a", "sc-startup-b"));
        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlan(
                List.of(
                        new ServiceCentreSupplyBatch(
                                "sc-startup-a", 2, 0, true,
                                List.of(startupAOne, startupATwo), Set.of(startupAEmpty)),
                        new ServiceCentreSupplyBatch(
                                "sc-startup-b", 1, 2, true,
                                List.of(startupBOne, startupBTwo), Set.of(startupBEmpty))),
                List.of());
        OsrBootstrapState bootstrapState = bootstrapState(
                inventoryConfig,
                List.of(startupAOne),
                Set.of(startupAEmpty, startupBEmpty),
                List.of(startupATwo, startupBOne, startupBTwo));
        return new PartialFixture(
                plan,
                inventoryConfig,
                new DspServiceCentreSupplyCoordinator(
                        plan,
                        new ServiceCentreSupplyConfig(0),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        bootstrapState),
                startupAOne,
                startupATwo,
                startupBOne,
                startupBTwo,
                startupAEmpty,
                startupBEmpty);
    }

    private static OsrBootstrapState bootstrapState(
            OsrInventoryConfig inventoryConfig,
            List<InboundToteManifest> stored,
            Set<OrderSheetKey> emptyKeys,
            List<InboundToteManifest> overflow) {
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(inventoryConfig);
        inventory.storeAll(stored);
        return new OsrBootstrapState(
                inventory,
                emptyKeys,
                overflow.stream().map(InboundToteManifest::physicalToteId).toList());
    }

    private static ServiceCentreSupplySnapshot serviceCentre(
            DspSupplySnapshot snapshot,
            String serviceCentreId) {
        return snapshot.serviceCentres().stream()
                .filter(candidate -> candidate.serviceCentreId().equals(serviceCentreId))
                .findFirst()
                .orElseThrow();
    }

    private static PhysicalToteSupplySnapshot physicalTote(
            DspSupplySnapshot snapshot,
            String physicalToteId) {
        return snapshot.serviceCentres().stream()
                .flatMap(serviceCentre -> serviceCentre.physicalTotes().stream())
                .filter(tote -> tote.physicalToteId().value().equals(physicalToteId))
                .findFirst()
                .orElseThrow();
    }

    private static Fixture fixture() {
        NotionalToteOrder preloadedFull = order(
                "preloaded-full", "sc-preloaded", OrderType.FULL_PACK, 999, 0);
        NotionalToteOrder preloadedAdapted = order(
                "preloaded-adapted", "sc-preloaded", OrderType.ADAPTED, 999, 1);
        NotionalToteOrder preloadedEmpty = order(
                "preloaded-empty", "sc-preloaded", OrderType.EMPTY, 999, 2);
        NotionalToteOrder laterAdapted = order(
                "later-adapted", "sc-later", OrderType.ADAPTED, 998, 3);
        NotionalToteOrder laterFull = order(
                "later-full", "sc-later", OrderType.FULL_PACK, 998, 4);
        NotionalToteOrder laterEmpty = order(
                "later-empty", "sc-later", OrderType.EMPTY, 998, 5);

        InboundToteManifest preloadedFullManifest = manifest(
                "preloaded-full", preloadedFull, OrderType.FULL_PACK, 0);
        InboundToteManifest preloadedAdaptedManifest = manifest(
                "preloaded-adapted", preloadedAdapted, OrderType.ADAPTED, 1);
        InboundToteManifest laterAdaptedManifest = manifest(
                "later-adapted", laterAdapted, OrderType.ADAPTED, 2);
        InboundToteManifest laterFullManifest = manifest(
                "later-full", laterFull, OrderType.FULL_PACK, 3);

        List<NotionalToteOrder> orders = List.of(
                preloadedFull,
                preloadedAdapted,
                preloadedEmpty,
                laterAdapted,
                laterFull,
                laterEmpty);
        List<InboundToteManifest> manifests = List.of(
                preloadedFullManifest,
                preloadedAdaptedManifest,
                laterAdaptedManifest,
                laterFullManifest);
        LoadedDspData data = new LoadedDspData(
                List.of(),
                orders,
                List.of(),
                Set.of(),
                Set.of(),
                manifests,
                DspDatasetLoadReport.empty());
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(10, List.of("sc-preloaded"));
        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlanFactory().create(
                data,
                inventoryConfig);
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(inventoryConfig);
        inventory.storeAll(List.of(preloadedFullManifest, preloadedAdaptedManifest));
        OsrBootstrapState bootstrapState = new OsrBootstrapState(
                inventory,
                Set.of(preloadedEmpty.orderSheetKey()));
        DspServiceCentreSupplyCoordinator coordinator = new DspServiceCentreSupplyCoordinator(
                plan,
                new ServiceCentreSupplyConfig(2),
                FixedIntervalInboundToteArrivalPolicy.peak(),
                bootstrapState);
        return new Fixture(
                plan,
                bootstrapState,
                coordinator,
                preloadedFullManifest,
                preloadedEmpty);
    }

    private static NotionalToteOrder order(
            String orderId,
            String serviceCentreId,
            OrderType orderType,
            int priority,
            long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(item("line-" + orderId, orderId)),
                priority,
                sequenceNumber);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String serviceCentreId,
            OrderType orderType,
            long sourceSequenceNumber) {
        return manifest(
                physicalToteId,
                order(
                        "order-" + physicalToteId,
                        serviceCentreId,
                        orderType,
                        1,
                        sourceSequenceNumber),
                orderType,
                sourceSequenceNumber);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            NotionalToteOrder order,
            OrderType orderType,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                orderType,
                order.serviceCentreId(),
                List.of(item("line-" + physicalToteId, order.orderId())),
                sourceSequenceNumber);
    }

    private static DspOrderItem item(String lineReference, String referenceOrderId) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-" + lineReference,
                DspOrderLineType.FULL_PACK,
                referenceOrderId,
                1,
                1);
    }

    private record Fixture(
            DspServiceCentreSupplyPlan plan,
            OsrBootstrapState bootstrapState,
            DspServiceCentreSupplyCoordinator coordinator,
            InboundToteManifest preloadedFull,
            NotionalToteOrder preloadedEmpty) {
    }

    private record PartialFixture(
            DspServiceCentreSupplyPlan plan,
            OsrInventoryConfig inventoryConfig,
            DspServiceCentreSupplyCoordinator coordinator,
            InboundToteManifest startupAOne,
            InboundToteManifest startupATwo,
            InboundToteManifest startupBOne,
            InboundToteManifest startupBTwo,
            OrderSheetKey startupAEmpty,
            OrderSheetKey startupBEmpty) {
    }
}
