package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
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
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockController;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

class DspServiceCentreSupplyControllerTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);

    @Test
    void shouldUseTheClockControllerSnapshotWhenRegisteredFirst() {
        Fixture fixture = fixture();
        DspOperationalClockController clockController = new DspOperationalClockController(
                new DspOperationalClock(
                        DspOperationalClockConfig.productionBaseline(OPERATING_DATE)));
        DspServiceCentreSupplyController supplyController = new DspServiceCentreSupplyController(
                clockController::snapshot,
                fixture.coordinator());
        SimulationWorld world = new SimulationWorld();
        world.addController(clockController);
        world.addController(supplyController);

        world.update(1d);

        assertEquals(
                Optional.of(Duration.ofSeconds(1)),
                fixture.coordinator().snapshot().serviceCentres().get(1)
                        .authorizationElapsedTime());
    }

    @Test
    void shouldUseOneAbsoluteSnapshotPerUpdateAndIgnoreFrameDelta() {
        Fixture fixture = fixture();
        DspOperationalClockSnapshot suppliedSnapshot = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(OPERATING_DATE))
                .snapshotAt(Duration.ofSeconds(5));
        AtomicInteger supplierCalls = new AtomicInteger();
        DspServiceCentreSupplyController controller = new DspServiceCentreSupplyController(
                () -> {
                    supplierCalls.incrementAndGet();
                    return suppliedSnapshot;
                },
                fixture.coordinator());
        SimulationContext context = new SimulationContext();

        controller.update(context, 0.001d);
        DspSupplySnapshot afterFirstUpdate = controller.snapshot();
        controller.update(context, 999d);

        assertEquals(2, supplierCalls.get());
        assertEquals(afterFirstUpdate, controller.snapshot());
        assertEquals(
                Optional.of(Duration.ofSeconds(8)),
                controller.snapshot().nextPhysicalAdmissionElapsedTime());
    }

    @Test
    void shouldDelegateImmutableSnapshotsAndRejectInvalidInputs() {
        Fixture fixture = fixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> new DspServiceCentreSupplyController(null, fixture.coordinator()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspServiceCentreSupplyController(() -> null, null));

        DspServiceCentreSupplyController controller = new DspServiceCentreSupplyController(
                () -> new DspOperationalClock(
                        DspOperationalClockConfig.productionBaseline(OPERATING_DATE))
                        .initialSnapshot(),
                fixture.coordinator());
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.update(null, 0d));
        assertThrows(
                IllegalStateException.class,
                () -> new DspServiceCentreSupplyController(
                        () -> null,
                        fixture.coordinator()).update(new SimulationContext(), 0d));
        assertEquals(fixture.coordinator().snapshot(), controller.snapshot());
        assertThrows(
                UnsupportedOperationException.class,
                () -> controller.snapshot().serviceCentres().clear());
    }

    private static Fixture fixture() {
        InboundToteManifest preloaded = manifest("preloaded", "sc-preloaded", OrderType.FULL_PACK, 0);
        InboundToteManifest later = manifest("later", "sc-later", OrderType.ADAPTED, 1);
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
                                1,
                                false,
                                List.of(later),
                                Set.of(laterEmpty))),
                List.of());
        OsrInventoryConfig inventoryConfig = new OsrInventoryConfig(2, List.of("sc-preloaded"));
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
                        bootstrapState));
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

    private record Fixture(DspServiceCentreSupplyCoordinator coordinator) {
    }
}
