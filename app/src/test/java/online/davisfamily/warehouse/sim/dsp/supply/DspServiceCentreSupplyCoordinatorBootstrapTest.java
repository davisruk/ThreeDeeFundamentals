package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
}
