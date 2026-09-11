package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class DspServiceCentreSupplyFlowTest {

    private static final DspOperationalClock CLOCK = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 1, 1)));

    @Test
    void shouldReplenishPriorityOrderedCentresWithoutReorderingOrBursting() {
        Fixture fixture = fixture();

        fixture.coordinator().advance(clockAtSeconds(0));
        assertHeld(fixture, "sc-later-a");
        assertHeld(fixture, "sc-later-b");

        fixture.bootstrapState().inventory().recordDeparture(new PhysicalToteId("pre-a-1"));
        fixture.coordinator().advance(clockAtSeconds(1));

        DspSupplySnapshot authorized = fixture.coordinator().snapshot();
        assertEquals(2, authorized.osrOccupancy());
        assertEquals("sc-later-a", authorized.activeInboundServiceCentreId().orElseThrow());
        assertEquals(
                ServiceCentreAuthorizationState.AUTHORIZED,
                serviceCentre(authorized, "sc-later-a").authorizationState());
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(authorized, "sc-later-b").authorizationState());
        assertTrue(authorized.authorizedEmptyOrderSheetKeys()
                .contains(fixture.laterAEmptyKey()));

        fixture.coordinator().advance(clockAtSeconds(4));
        assertStored(fixture, "adapted-a-1");
        assertEquals(3, fixture.coordinator().snapshot().osrOccupancy());

        fixture.coordinator().advance(clockAtSeconds(7));
        assertStored(fixture, "full-a-1");
        assertEquals(4, fixture.coordinator().snapshot().osrOccupancy());

        fixture.coordinator().advance(clockAtSeconds(10));
        DspSupplySnapshot blocked = fixture.coordinator().snapshot();
        assertEquals(
                PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY,
                physicalTote(blocked, "associated-a-1").state());
        assertEquals(
                PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(blocked, "associated-a-2").state());
        assertEquals(Optional.of(Duration.ofSeconds(10)),
                blocked.nextPhysicalAdmissionElapsedTime());

        DspSupplySnapshot blockedAgain = fixture.coordinator().snapshot();
        fixture.coordinator().advance(clockAtSeconds(10));
        assertEquals(blockedAgain, fixture.coordinator().snapshot());

        fixture.bootstrapState().inventory().recordDeparture(new PhysicalToteId("pre-a-2"));
        fixture.coordinator().advance(clockAtSeconds(10));
        DspSupplySnapshot resumed = fixture.coordinator().snapshot();
        assertStored(fixture, "associated-a-1");
        assertEquals(
                PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(resumed, "associated-a-2").state());
        assertEquals(Optional.of(Duration.ofSeconds(13)),
                resumed.nextPhysicalAdmissionElapsedTime());
        assertEquals(3, resumed.admittedAfterStartupCount());

        fixture.coordinator().advance(clockAtSeconds(12));
        assertEquals(Optional.of(Duration.ofSeconds(13)),
                fixture.coordinator().snapshot().nextPhysicalAdmissionElapsedTime());

        fixture.bootstrapState().inventory().recordDeparture(new PhysicalToteId("pre-b-1"));
        fixture.coordinator().advance(clockAtSeconds(13));
        DspSupplySnapshot completedA = fixture.coordinator().snapshot();
        assertStored(fixture, "associated-a-2");
        assertEquals(
                ServiceCentreAuthorizationState.SUPPLY_COMPLETE,
                serviceCentre(completedA, "sc-later-a").authorizationState());
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(completedA, "sc-later-b").authorizationState());

        fixture.bootstrapState().inventory().recordDeparture(new PhysicalToteId("adapted-a-1"));
        fixture.bootstrapState().inventory().recordDeparture(new PhysicalToteId("full-a-1"));
        assertEquals(2, fixture.bootstrapState().inventorySnapshot().occupancy());

        fixture.coordinator().advance(clockAtSeconds(14));
        DspSupplySnapshot authorizedB = fixture.coordinator().snapshot();
        assertEquals("sc-later-b", authorizedB.activeInboundServiceCentreId().orElseThrow());
        assertEquals(
                ServiceCentreAuthorizationState.AUTHORIZED,
                serviceCentre(authorizedB, "sc-later-b").authorizationState());
        assertEquals(4, authorizedB.authorizedEmptyOrderSheetKeys().size());
        assertTrue(authorizedB.authorizedEmptyOrderSheetKeys()
                .contains(fixture.laterAEmptyKey()));
        assertTrue(authorizedB.authorizedEmptyOrderSheetKeys()
                .contains(fixture.laterBEmptyKey()));
        assertEquals(2, authorizedB.osrOccupancy());
        assertStored(fixture, "associated-a-1");
        assertStored(fixture, "associated-a-2");

        DspSupplySnapshot authorizedBAgain = fixture.coordinator().snapshot();
        fixture.coordinator().advance(clockAtSeconds(14));
        assertEquals(authorizedBAgain, fixture.coordinator().snapshot());

        fixture.coordinator().advance(clockAtSeconds(17));
        assertStored(fixture, "adapted-b-1");
        assertTrue(fixture.bootstrapState().inventorySnapshot().occupancy()
                <= fixture.bootstrapState().inventorySnapshot().capacity());
    }

    @Test
    void shouldDrainStartupOverflowBeforeAuthorizingLaterCentres() {
        InboundToteManifest startupAInitial = manifest(
                "startup-a-initial", "sc-startup-a", OrderType.FULL_PACK, 0);
        InboundToteManifest startupAOverflow = manifest(
                "startup-a-overflow", "sc-startup-a", OrderType.ADAPTED, 1);
        InboundToteManifest startupBInitial = manifest(
                "startup-b-initial", "sc-startup-b", OrderType.FULL_PACK, 2);
        InboundToteManifest startupBOverflow = manifest(
                "startup-b-overflow", "sc-startup-b", OrderType.ADAPTED, 3);
        InboundToteManifest later = manifest(
                "later", "sc-later", OrderType.FULL_PACK, 4);
        OrderSheetKey startupAEmpty = new OrderSheetKey("startup-a-empty", 1);
        OrderSheetKey startupBEmpty = new OrderSheetKey("startup-b-empty", 1);
        OrderSheetKey laterEmpty = new OrderSheetKey("later-empty", 1);
        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlan(
                List.of(
                        batch("sc-startup-a", 999, true,
                                List.of(startupAOverflow, startupAInitial),
                                Set.of(startupAEmpty)),
                        batch("sc-startup-b", 998, true,
                                List.of(startupBOverflow, startupBInitial),
                                Set.of(startupBEmpty)),
                        batch("sc-later", 997, false, List.of(later), Set.of(laterEmpty))),
                List.of());
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(
                2, List.of("sc-startup-a", "sc-startup-b"));
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(inventoryConfig);
        inventory.storeAll(List.of(startupAInitial, startupBInitial));
        OsrBootstrapState bootstrapState = new OsrBootstrapState(
                inventory,
                Set.of(startupAEmpty, startupBEmpty),
                List.of(startupAOverflow.physicalToteId(), startupBOverflow.physicalToteId()));
        DspServiceCentreSupplyCoordinator coordinator = new DspServiceCentreSupplyCoordinator(
                plan,
                new ServiceCentreSupplyConfig(0),
                FixedIntervalInboundToteArrivalPolicy.peak(),
                bootstrapState);

        coordinator.advance(clockAtSeconds(0));
        DspSupplySnapshot initial = coordinator.snapshot();
        assertEquals("sc-startup-a", initial.activeInboundServiceCentreId().orElseThrow());
        assertEquals(ServiceCentreAuthorizationState.AUTHORIZED,
                serviceCentre(initial, "sc-startup-b").authorizationState());
        assertEquals(
                PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(initial, "startup-b-overflow").state());

        coordinator.advance(clockAtSeconds(3));
        DspSupplySnapshot blockedA = coordinator.snapshot();
        assertEquals(PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY,
                physicalTote(blockedA, "startup-a-overflow").state());
        inventory.recordDeparture(startupAInitial.physicalToteId());
        coordinator.advance(clockAtSeconds(3));
        DspSupplySnapshot activeB = coordinator.snapshot();
        assertStored(bootstrapState, "startup-a-overflow");
        assertEquals("sc-startup-b", activeB.activeInboundServiceCentreId().orElseThrow());
        assertEquals(Optional.of(Duration.ofSeconds(6)),
                activeB.nextPhysicalAdmissionElapsedTime());
        assertEquals(1, activeB.admittedAfterStartupCount());
        assertEquals(PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(activeB, "startup-b-overflow").state());

        coordinator.advance(clockAtSeconds(5));
        assertEquals(PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(coordinator.snapshot(), "startup-b-overflow").state());
        inventory.recordDeparture(startupAOverflow.physicalToteId());
        coordinator.advance(clockAtSeconds(6));
        DspSupplySnapshot laterHeld = coordinator.snapshot();
        assertStored(bootstrapState, "startup-b-overflow");
        assertTrue(laterHeld.activeInboundServiceCentreId().isEmpty());
        assertEquals(ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(laterHeld, "sc-later").authorizationState());
        assertEquals(2, laterHeld.admittedAfterStartupCount());

        inventory.recordDeparture(startupBInitial.physicalToteId());
        inventory.recordDeparture(startupBOverflow.physicalToteId());
        coordinator.advance(clockAtSeconds(7));
        DspSupplySnapshot laterAuthorized = coordinator.snapshot();
        assertEquals("sc-later", laterAuthorized.activeInboundServiceCentreId().orElseThrow());
        assertEquals(ServiceCentreAuthorizationState.AUTHORIZED,
                serviceCentre(laterAuthorized, "sc-later").authorizationState());
        assertEquals(Optional.of(Duration.ofSeconds(10)),
                laterAuthorized.nextPhysicalAdmissionElapsedTime());
    }

    private static Fixture fixture() {
        InboundToteManifest preA1 = manifest(
                "pre-a-1", "sc-preloaded-a", OrderType.FULL_PACK, 0);
        InboundToteManifest preA2 = manifest(
                "pre-a-2", "sc-preloaded-a", OrderType.ADAPTED, 1);
        InboundToteManifest preB1 = manifest(
                "pre-b-1", "sc-preloaded-b", OrderType.FULL_PACK, 2);
        List<InboundToteManifest> laterAManifests = List.of(
                manifest("adapted-a-1", "sc-later-a", OrderType.ADAPTED, 3),
                manifest("full-a-1", "sc-later-a", OrderType.FULL_PACK, 4),
                manifest("associated-a-1", "sc-later-a", OrderType.ASSOCIATED, 5),
                manifest("associated-a-2", "sc-later-a", OrderType.ASSOCIATED, 6));
        List<InboundToteManifest> laterBManifests = List.of(
                manifest("adapted-b-1", "sc-later-b", OrderType.ADAPTED, 7),
                manifest("full-b-1", "sc-later-b", OrderType.FULL_PACK, 8),
                manifest("associated-b-1", "sc-later-b", OrderType.ASSOCIATED, 9));
        OrderSheetKey preAEmpty = new OrderSheetKey("pre-a-empty", 1);
        OrderSheetKey preBEmpty = new OrderSheetKey("pre-b-empty", 1);
        OrderSheetKey laterAEmpty = new OrderSheetKey("later-a-empty", 1);
        OrderSheetKey laterBEmpty = new OrderSheetKey("later-b-empty", 1);

        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlan(
                List.of(
                        batch("sc-preloaded-a", 999, true, List.of(preA1, preA2), Set.of(preAEmpty)),
                        batch("sc-preloaded-b", 998, true, List.of(preB1), Set.of(preBEmpty)),
                        batch("sc-later-a", 997, false, laterAManifests, Set.of(laterAEmpty)),
                        batch("sc-later-b", 996, false, laterBManifests, Set.of(laterBEmpty))),
                List.of());
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(
                4,
                List.of("sc-preloaded-a", "sc-preloaded-b"));
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(inventoryConfig);
        inventory.storeAll(List.of(preA1, preA2, preB1));
        OsrBootstrapState bootstrapState = new OsrBootstrapState(
                inventory,
                Set.of(preAEmpty, preBEmpty));
        return new Fixture(
                new DspServiceCentreSupplyCoordinator(
                        plan,
                        new ServiceCentreSupplyConfig(2),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        bootstrapState),
                bootstrapState,
                laterAManifests,
                laterAEmpty,
                laterBEmpty);
    }

    private static ServiceCentreSupplyBatch batch(
            String serviceCentreId,
            int priority,
            boolean preloadedAtStart,
            List<InboundToteManifest> physicalManifests,
            Set<OrderSheetKey> emptyOrderSheetKeys) {
        return new ServiceCentreSupplyBatch(
                serviceCentreId,
                priority,
                physicalManifests.stream()
                        .mapToLong(InboundToteManifest::sourceSequenceNumber)
                        .min()
                        .orElseThrow(),
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

    private static void assertHeld(Fixture fixture, String serviceCentreId) {
        assertEquals(
                ServiceCentreAuthorizationState.HELD_UPSTREAM,
                serviceCentre(fixture.coordinator().snapshot(), serviceCentreId)
                        .authorizationState());
    }

    private static void assertStored(Fixture fixture, String physicalToteId) {
        assertStored(fixture.bootstrapState(), physicalToteId);
    }

    private static void assertStored(
            OsrBootstrapState bootstrapState,
            String physicalToteId) {
        assertTrue(bootstrapState.inventorySnapshot()
                .contains(new PhysicalToteId(physicalToteId)));
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

    private record Fixture(
            DspServiceCentreSupplyCoordinator coordinator,
            OsrBootstrapState bootstrapState,
            List<InboundToteManifest> laterAManifests,
            OrderSheetKey laterAEmptyKey,
            OrderSheetKey laterBEmptyKey) {
    }
}
