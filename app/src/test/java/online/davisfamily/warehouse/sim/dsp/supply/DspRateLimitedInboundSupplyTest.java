package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

class DspRateLimitedInboundSupplyTest {

    private static final DspOperationalClock CLOCK = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 1, 1)));

    @Test
    void shouldAdmitFirstManifestAtExactConfiguredDueTime() {
        Fixture fixture = fixture(10, laterManifests());

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(2));
        assertEquals(1, fixture.bootstrapState().inventorySnapshot().occupancy());

        fixture.coordinator().advance(clockAtSeconds(3));

        assertEquals(2, fixture.bootstrapState().inventorySnapshot().occupancy());
        assertTrue(fixture.bootstrapState().inventorySnapshot()
                .contains(fixture.laterManifests().getFirst().physicalToteId()));
    }

    @Test
    void shouldPreserveConfiguredIntervalsAndAdaptedFirstOrder() {
        Fixture fixture = fixture(10, laterManifests());

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(12));

        assertEquals(
                List.of("preloaded", "adapted-1", "adapted-2", "full-1", "associated-1"),
                fixture.bootstrapState().inventorySnapshot().storedTotes().stream()
                        .map(manifest -> manifest.physicalToteId().value())
                        .toList());
        assertEquals(Optional.empty(), fixture.coordinator().snapshot()
                .nextPhysicalAdmissionElapsedTime());
    }

    @Test
    void shouldCatchUpDeterministicallyWhenSeveralUnblockedArrivalsAreDue() {
        Fixture fixture = fixture(10, laterManifests());

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(10));

        assertEquals(3, fixture.coordinator().snapshot().admittedAfterStartupCount());
        assertEquals(
                Optional.of(Duration.ofSeconds(12)),
                fixture.coordinator().snapshot().nextPhysicalAdmissionElapsedTime());
        assertEquals(
                PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(fixture.coordinator().snapshot(), "associated-1").state());
    }

    @Test
    void shouldNotTreatFutureArrivalAsCapacityBlocked() {
        Fixture fixture = fixture(3, laterManifests());

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(6));

        DspSupplySnapshot fullBeforeNextDueTime = fixture.coordinator().snapshot();
        assertEquals(3, fullBeforeNextDueTime.osrOccupancy());
        assertEquals(
                PhysicalToteSupplyState.AUTHORIZED_WAITING,
                physicalTote(fullBeforeNextDueTime, "full-1").state());
        assertEquals(
                Optional.of(Duration.ofSeconds(9)),
                fullBeforeNextDueTime.nextPhysicalAdmissionElapsedTime());

        fixture.bootstrapState().inventory().recordDeparture(new PhysicalToteId("preloaded"));
        fixture.bootstrapState().inventory().recordDeparture(new PhysicalToteId("adapted-1"));
        fixture.coordinator().advance(clockAtSeconds(12));

        DspSupplySnapshot caughtUp = fixture.coordinator().snapshot();
        assertEquals(
                PhysicalToteSupplyState.STORED_IN_OSR,
                physicalTote(caughtUp, "full-1").state());
        assertEquals(
                PhysicalToteSupplyState.STORED_IN_OSR,
                physicalTote(caughtUp, "associated-1").state());
        assertEquals(
                ServiceCentreAuthorizationState.SUPPLY_COMPLETE,
                serviceCentre(caughtUp, "sc-later").authorizationState());
    }

    @Test
    void shouldStoreEveryRepeatedSheetManifestByPhysicalIdentity() {
        Fixture fixture = fixture(10, laterManifests());

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(12));

        OrderSheetKey repeatedSheet = fixture.laterManifests().getFirst().orderSheetKey();
        assertEquals(
                2,
                fixture.bootstrapState().inventorySnapshot().storedTotes().stream()
                        .filter(manifest -> manifest.orderSheetKey().equals(repeatedSheet))
                        .count());
        assertEquals(
                Set.of("adapted-1", "adapted-2"),
                fixture.bootstrapState().inventorySnapshot().storedTotes().stream()
                        .filter(manifest -> manifest.orderSheetKey().equals(repeatedSheet))
                        .map(manifest -> manifest.physicalToteId().value())
                        .collect(Collectors.toSet()));
    }

    @Test
    void shouldMarkBatchCompleteAfterItsFinalManifestIsStored() {
        Fixture fixture = fixture(
                2,
                List.of(manifest("single", "sc-later", OrderType.FULL_PACK, 1)));

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(3));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        assertEquals(
                ServiceCentreAuthorizationState.SUPPLY_COMPLETE,
                serviceCentre(snapshot, "sc-later").authorizationState());
        assertTrue(snapshot.activeInboundServiceCentreId().isEmpty());
        assertEquals(Optional.empty(), snapshot.nextPhysicalAdmissionElapsedTime());
    }

    @Test
    void shouldNeverAdmitPreloadedOrEmptyWorkAgain() {
        Fixture fixture = fixture(10, laterManifests());

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(12));
        fixture.coordinator().advance(clockAtSeconds(12));

        DspSupplySnapshot snapshot = fixture.coordinator().snapshot();
        assertEquals(1, serviceCentre(snapshot, "sc-preloaded").preloadedCount());
        assertEquals(
                PhysicalToteSupplyState.PRELOADED_IN_OSR,
                physicalTote(snapshot, "preloaded").state());
        assertTrue(snapshot.authorizedEmptyOrderSheetKeys().contains(fixture.laterEmptyKey()));
        assertEquals(4, snapshot.admittedAfterStartupCount());
    }

    @Test
    void shouldDeriveStoredAndDepartedStatesFromInventory() {
        Fixture fixture = fixture(10, laterManifests());

        fixture.coordinator().advance(clockAtSeconds(0));
        fixture.coordinator().advance(clockAtSeconds(3));
        fixture.bootstrapState().inventory().recordDeparture(
                fixture.laterManifests().getFirst().physicalToteId());

        assertEquals(
                PhysicalToteSupplyState.DEPARTED_FROM_OSR,
                physicalTote(fixture.coordinator().snapshot(), "adapted-1").state());

        fixture.coordinator().advance(clockAtSeconds(6));

        assertEquals(
                PhysicalToteSupplyState.STORED_IN_OSR,
                physicalTote(fixture.coordinator().snapshot(), "adapted-2").state());
    }

    private static Fixture fixture(
            int capacity,
            List<InboundToteManifest> laterManifests) {
        InboundToteManifest preloaded = manifest(
                "preloaded",
                "sc-preloaded",
                OrderType.FULL_PACK,
                0);
        OrderSheetKey preloadedEmpty = new OrderSheetKey("preloaded-empty", 1);
        OrderSheetKey laterEmpty = new OrderSheetKey("later-empty", 1);
        DspServiceCentreSupplyPlan plan = new DspServiceCentreSupplyPlan(
                List.of(
                        new ServiceCentreSupplyBatch(
                                "sc-preloaded",
                                999,
                                0,
                                true,
                                List.of(preloaded),
                                Set.of(preloadedEmpty)),
                        new ServiceCentreSupplyBatch(
                                "sc-later",
                                998,
                                laterManifests.stream()
                                        .mapToLong(InboundToteManifest::sourceSequenceNumber)
                                        .min()
                                        .orElseThrow(),
                                false,
                                laterManifests,
                                Set.of(laterEmpty))),
                List.of());
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(
                capacity,
                List.of("sc-preloaded"));
        OsrPhysicalInventory inventory = new OsrPhysicalInventory(inventoryConfig);
        inventory.store(preloaded);
        OsrBootstrapState bootstrapState = new OsrBootstrapState(
                inventory,
                Set.of(preloadedEmpty));
        return new Fixture(
                new DspServiceCentreSupplyCoordinator(
                        plan,
                        new ServiceCentreSupplyConfig(1),
                        FixedIntervalInboundToteArrivalPolicy.peak(),
                        bootstrapState),
                bootstrapState,
                laterManifests,
                laterEmpty);
    }

    private static List<InboundToteManifest> laterManifests() {
        OrderSheetKey repeatedSheet = new OrderSheetKey("adapted-sheet", 1);
        return List.of(
                manifest("adapted-1", repeatedSheet, "sc-later", OrderType.ADAPTED, 1),
                manifest("adapted-2", repeatedSheet, "sc-later", OrderType.ADAPTED, 2),
                manifest("full-1", "sc-later", OrderType.FULL_PACK, 3),
                manifest("associated-1", "sc-later", OrderType.ASSOCIATED, 4));
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String serviceCentreId,
            OrderType orderType,
            long sourceSequenceNumber) {
        return manifest(
                physicalToteId,
                new OrderSheetKey("order-" + physicalToteId, 1),
                serviceCentreId,
                orderType,
                sourceSequenceNumber);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            OrderType orderType,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                orderSheetKey,
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
                        orderSheetKey.orderId(),
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
            List<InboundToteManifest> laterManifests,
            OrderSheetKey laterEmptyKey) {
    }
}
