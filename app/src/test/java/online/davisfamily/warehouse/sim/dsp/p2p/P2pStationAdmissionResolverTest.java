package online.davisfamily.warehouse.sim.dsp.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.SnapshotStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

class P2pStationAdmissionResolverTest {

    @Test
    void shouldResolveP2pAdmissionForCandidateOrder() {
        P2pStationAdmissionResolver resolver = new P2pStationAdmissionResolver(
                new SnapshotStationAdmissionResolver(),
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                this::p2pSnapshot,
                new StationCapacity(1, 1),
                () -> new StationSnapshot(StationType.P2P, 0, 0));

        StationAdmissionSnapshot admission = resolver.admissionFor(
                StationType.P2P,
                candidate("order-1"),
                snapshot());

        assertNotNull(admission);
        assertEquals(StationType.P2P, admission.stationType());
        assertTrue(admission.canAccept());
    }

    @Test
    void shouldDelegateNonP2pStationsToFallbackResolver() {
        StationAdmissionSnapshot manualAdmission = new StationAdmissionSnapshot(
                StationType.MANUAL,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.MANUAL, 0, 0),
                true,
                "");
        StationAdmissionResolver fallbackResolver = (stationType, candidate, snapshot) -> manualAdmission;
        P2pStationAdmissionResolver resolver = new P2pStationAdmissionResolver(
                fallbackResolver,
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                this::p2pSnapshot,
                new StationCapacity(1, 1),
                () -> new StationSnapshot(StationType.P2P, 0, 0));

        StationAdmissionSnapshot admission = resolver.admissionFor(
                StationType.MANUAL,
                candidate("order-1"),
                snapshot());

        assertSame(manualAdmission, admission);
    }

    @Test
    void shouldUseLatestSupplierValuesForEachCall() {
        AtomicInteger calls = new AtomicInteger();
        P2pStationAdmissionResolver resolver = new P2pStationAdmissionResolver(
                new SnapshotStationAdmissionResolver(),
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                () -> new P2pAdmissionSnapshot(
                        "p2p-1",
                        calls.incrementAndGet(),
                        Set.of("bag-a"),
                        Set.of("bag-a"),
                        true),
                new StationCapacity(1, 1),
                () -> new StationSnapshot(StationType.P2P, 0, 0));

        resolver.admissionFor(StationType.P2P, candidate("order-1"), snapshot());
        resolver.admissionFor(StationType.P2P, candidate("order-1"), snapshot());

        assertEquals(2, calls.get());
    }

    @Test
    void shouldRejectNullInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new P2pStationAdmissionResolver(
                        null,
                        new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                        this::p2pSnapshot,
                        new StationCapacity(1, 1),
                        () -> new StationSnapshot(StationType.P2P, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pStationAdmissionResolver(
                        new SnapshotStationAdmissionResolver(),
                        null,
                        this::p2pSnapshot,
                        new StationCapacity(1, 1),
                        () -> new StationSnapshot(StationType.P2P, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pStationAdmissionResolver(
                        new SnapshotStationAdmissionResolver(),
                        new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                        null,
                        new StationCapacity(1, 1),
                        () -> new StationSnapshot(StationType.P2P, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pStationAdmissionResolver(
                        new SnapshotStationAdmissionResolver(),
                        new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                        this::p2pSnapshot,
                        null,
                        () -> new StationSnapshot(StationType.P2P, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pStationAdmissionResolver(
                        new SnapshotStationAdmissionResolver(),
                        new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                        this::p2pSnapshot,
                        new StationCapacity(1, 1),
                        null));
    }

    private P2pAdmissionSnapshot p2pSnapshot() {
        return new P2pAdmissionSnapshot(
                "p2p-1",
                1,
                Set.of("bag-a"),
                Set.of("bag-a"),
                true);
    }

    private static DspSchedulerOrderState candidate(String orderId) {
        return new DspSchedulerOrderState(
                new NotionalToteOrder(
                        orderId,
                        "notional-" + orderId,
                        "sc-1",
                        1,
                        OrderType.ASSOCIATED,
                        List.of(new DspOrderItem("item-" + orderId, "product-1", 1)),
                        0),
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static WarehouseSchedulerSnapshot snapshot() {
        return new WarehouseSchedulerSnapshot(
                List.of(candidate("order-1")),
                Map.of(),
                Set.of(),
                Optional.empty());
    }
}
