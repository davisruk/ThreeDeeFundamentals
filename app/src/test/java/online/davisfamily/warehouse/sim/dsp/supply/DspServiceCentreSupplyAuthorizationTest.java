package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapState;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

class DspServiceCentreSupplyAuthorizationTest {

    private static final DspOperationalClock CLOCK = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 1, 1)));

    @Test
    void shouldNotAuthorizeWhileOccupancyIsAboveLowWaterMark() {
        Fixture fixture = fixture();

        fixture.coordinator().advance(clockAtSeconds(10));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        assertEquals(3, snapshot.osrOccupancy());
        assertTrue(snapshot.activeInboundServiceCentreId().isEmpty());
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(snapshot, "sc-high").authorizationState());
    }

    @Test
    void shouldAuthorizeHighestPriorityHeldCentreAtInclusiveLowWaterMark() {
        Fixture fixture = fixture();
        fixture.bootstrapState().inventory().recordDeparture(fixture.preloadedToteId());

        fixture.coordinator().advance(clockAtSeconds(5));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        ServiceCentreSupplySnapshot high = serviceCentre(snapshot, "sc-high");
        assertEquals(2, snapshot.osrOccupancy());
        assertEquals(ServiceCentreAuthorizationState.AUTHORIZED, high.authorizationState());
        assertEquals(Optional.of(Duration.ofSeconds(5)), high.authorizationElapsedTime());
        assertEquals("sc-high", snapshot.activeInboundServiceCentreId().orElseThrow());
    }

    @Test
    void shouldAuthorizeAtMostOneCentrePerAdvance() {
        EmptyOnlyFixture fixture = emptyOnlyFixture();

        fixture.coordinator().advance(clockAtSeconds(1));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        assertEquals(
                ServiceCentreAuthorizationState.SUPPLY_COMPLETE,
                serviceCentre(snapshot, "sc-empty-only").authorizationState());
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(snapshot, "sc-next").authorizationState());

        fixture.coordinator().advance(clockAtSeconds(2));
        assertEquals(
                ServiceCentreAuthorizationState.AUTHORIZED,
                serviceCentre(fixture.coordinator().snapshot(), "sc-next")
                        .authorizationState());
    }

    @Test
    void shouldAuthorizeEmptySheetsWithoutConsumingOsrCapacity() {
        Fixture fixture = fixture();
        fixture.bootstrapState().inventory().recordDeparture(fixture.preloadedToteId());

        fixture.coordinator().advance(clockAtSeconds(5));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        assertEquals(2, snapshot.osrOccupancy());
        assertTrue(snapshot.authorizedEmptyOrderSheetKeys().contains(fixture.highEmptyKey()));
        assertEquals(
                Set.of(fixture.highEmptyKey()),
                serviceCentre(snapshot, "sc-high").authorizedEmptyOrderSheetKeys());
    }

    @Test
    void shouldScheduleFirstPhysicalArrivalOneIntervalAfterAuthorization() {
        Fixture fixture = fixture();
        fixture.bootstrapState().inventory().recordDeparture(fixture.preloadedToteId());

        fixture.coordinator().advance(clockAtSeconds(5));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        assertEquals(Optional.of(Duration.ofSeconds(8)), snapshot.nextPhysicalAdmissionElapsedTime());
        assertEquals(
                Set.of(PhysicalToteSupplyState.AUTHORIZED_WAITING),
                serviceCentre(snapshot, "sc-high").physicalTotes().stream()
                        .map(PhysicalToteSupplySnapshot::state)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldNotAuthorizeAnotherCentreWhileAuthorizedPhysicalSupplyRemains() {
        Fixture fixture = fixture();
        fixture.bootstrapState().inventory().recordDeparture(fixture.preloadedToteId());

        fixture.coordinator().advance(clockAtSeconds(5));
        fixture.coordinator().advance(clockAtSeconds(7));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        assertEquals("sc-high", snapshot.activeInboundServiceCentreId().orElseThrow());
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(snapshot, "sc-next").authorizationState());
    }

    @Test
    void shouldRejectClockRegression() {
        Fixture fixture = fixture();
        fixture.bootstrapState().inventory().recordDeparture(fixture.preloadedToteId());
        fixture.coordinator().advance(clockAtSeconds(5));

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.coordinator().advance(clockAtSeconds(4)));
    }

    private static Fixture fixture() {
        InboundToteManifest preloadedOne = manifest("preloaded-1", "sc-preloaded", OrderType.FULL_PACK, 0);
        InboundToteManifest preloadedTwo = manifest("preloaded-2", "sc-preloaded", OrderType.ADAPTED, 1);
        InboundToteManifest preloadedThree = manifest("preloaded-3", "sc-preloaded", OrderType.FULL_PACK, 2);
        InboundToteManifest highManifest = manifest("high-1", "sc-high", OrderType.ADAPTED, 3);
        InboundToteManifest nextManifest = manifest("next-1", "sc-next", OrderType.FULL_PACK, 4);
        OrderSheetKey preloadedEmpty = new OrderSheetKey("preloaded-empty", 1);
        OrderSheetKey highEmpty = new OrderSheetKey("high-empty", 1);
        OrderSheetKey nextEmpty = new OrderSheetKey("next-empty", 1);

        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlan(
                List.of(
                        batch("sc-preloaded", 999, true,
                                List.of(preloadedOne, preloadedTwo, preloadedThree),
                                Set.of(preloadedEmpty)),
                        batch("sc-high", 998, false, List.of(highManifest), Set.of(highEmpty)),
                        batch("sc-next", 997, false, List.of(nextManifest), Set.of(nextEmpty))),
                List.of());
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(4, List.of("sc-preloaded"));
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(inventoryConfig);
        inventory.storeAll(List.of(preloadedOne, preloadedTwo, preloadedThree));
        OsrBootstrapState bootstrapState = new OsrBootstrapState(
                inventory,
                Set.of(preloadedEmpty));
        return new Fixture(
                new DspServiceCentreSupplyCoordinator(
                        plan,
                        new ServiceCentreSupplyConfig(2),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        bootstrapState),
                bootstrapState,
                preloadedOne.physicalToteId(),
                highEmpty);
    }

    private static EmptyOnlyFixture emptyOnlyFixture() {
        InboundToteManifest preloaded = manifest("preloaded", "sc-preloaded", OrderType.FULL_PACK, 0);
        InboundToteManifest nextManifest = manifest("next-1", "sc-next", OrderType.FULL_PACK, 2);
        OrderSheetKey preloadedEmpty = new OrderSheetKey("preloaded-empty", 1);
        OrderSheetKey emptyOnlyKey = new OrderSheetKey("empty-only", 1);

        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlan(
                List.of(
                        batch("sc-preloaded", 999, true, List.of(preloaded), Set.of(preloadedEmpty)),
                        batch("sc-empty-only", 998, false, List.of(), Set.of(emptyOnlyKey)),
                        batch("sc-next", 997, false, List.of(nextManifest), Set.of())),
                List.of());
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(2, List.of("sc-preloaded"));
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(inventoryConfig);
        inventory.store(preloaded);
        OsrBootstrapState bootstrapState = new OsrBootstrapState(
                inventory,
                Set.of(preloadedEmpty));
        return new EmptyOnlyFixture(
                new DspServiceCentreSupplyCoordinator(
                        plan,
                        new ServiceCentreSupplyConfig(1),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        bootstrapState),
                bootstrapState);
    }

    private static ServiceCentreSupplyBatch batch(
            String serviceCentreId,
            int priority,
            boolean preloadedAtStart,
            List<InboundToteManifest> physicalManifests,
            Set<OrderSheetKey> emptyOrderSheetKeys) {
        long firstSequence = physicalManifests.isEmpty()
                ? emptyOrderSheetKeys.stream().findFirst().orElseThrow().sheetNumber()
                : physicalManifests.stream()
                        .mapToLong(InboundToteManifest::sourceSequenceNumber)
                        .min()
                        .orElseThrow();
        return new ServiceCentreSupplyBatch(
                serviceCentreId,
                priority,
                firstSequence,
                preloadedAtStart,
                physicalManifests,
                emptyOrderSheetKeys);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String serviceCentreId,
            OrderType orderType,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                orderType,
                serviceCentreId,
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-" + physicalToteId,
                        1,
                        "pharmacy-1",
                        "patient-1",
                        "prescription-" + physicalToteId,
                        DspOrderLineType.FULL_PACK,
                        "order-" + physicalToteId,
                        1,
                        1)),
                sourceSequenceNumber);
    }

    private static DspOperationalClockSnapshot clockAtSeconds(long seconds) {
        return CLOCK.snapshotAt(Duration.ofSeconds(seconds));
    }

    private static ServiceCentreSupplySnapshot serviceCentre(
            DspSupplySnapshot snapshot,
            String serviceCentreId) {
        return snapshot.serviceCentres().stream()
                .filter(candidate -> candidate.serviceCentreId().equals(serviceCentreId))
                .findFirst()
                .orElseThrow();
    }

    private record Fixture(
            DspServiceCentreSupplyCoordinator coordinator,
            OsrBootstrapState bootstrapState,
            PhysicalToteId preloadedToteId,
            OrderSheetKey highEmptyKey) {
    }

    private record EmptyOnlyFixture(
            DspServiceCentreSupplyCoordinator coordinator,
            OsrBootstrapState bootstrapState) {
    }

}
