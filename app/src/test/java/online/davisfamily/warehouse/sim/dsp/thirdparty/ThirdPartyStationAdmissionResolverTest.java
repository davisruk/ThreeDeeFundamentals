package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class ThirdPartyStationAdmissionResolverTest {
    @Test
    void reusesPlanButReadsLiveCapacityAndDelegatesOtherStations() {
        NotionalToteOrder order = order();
        CountingFactory factory = new CountingFactory();
        ThirdPartyVisitPlanCatalog catalog = new ThirdPartyVisitPlanCatalog(List.of(order), factory);
        AtomicInteger lookups = new AtomicInteger();
        ThirdPartyVisitPlanSource source = candidate -> {
            lookups.incrementAndGet();
            return catalog.planFor(candidate);
        };
        ThirdPartyAreaConfig config = new ThirdPartyAreaConfig(0, 1, 10d);
        AtomicReference<ThirdPartyAreaSnapshot> liveArea = new AtomicReference<>(
                new ThirdPartyAreaSnapshot(config, List.of(), List.of(), List.of()));
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger delegates = new AtomicInteger();
        StationAdmissionSnapshot delegated = new StationAdmissionSnapshot(
                StationType.P2P, new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0), true, "");
        StationAdmissionResolver fallback = (type, candidate, snapshot) -> {
            delegates.incrementAndGet();
            return delegated;
        };
        ThirdPartyStationAdmissionResolver resolver = new ThirdPartyStationAdmissionResolver(
                fallback, source, () -> { reads.incrementAndGet(); return liveArea.get(); }, "third-party-ingress");
        DspSchedulerOrderState candidate = candidate(order);
        WarehouseSchedulerSnapshot snapshot = snapshot(candidate);

        StationAdmissionSnapshot open = resolver.admissionFor(StationType.THIRD_PARTY, candidate, snapshot);
        assertTrue(open.admissionOpen());
        assertEquals("third-party-ingress", open.selectedTargetId().orElseThrow());
        liveArea.set(new ThirdPartyAreaSnapshot(config, List.of(), List.of(), List.of(
                new ThirdPartyVisitState(order.orderSheetKey(),
                        new online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId("occupied"),
                        1, 1, 10d))));
        StationAdmissionSnapshot full = resolver.admissionFor(StationType.THIRD_PARTY, candidate, snapshot);
        assertFalse(full.admissionOpen());
        assertEquals("Third Party area has no capacity", full.blockedReason());
        assertTrue(full.selectedTargetId().isEmpty());
        assertEquals(2, reads.get());
        assertEquals(2, lookups.get());
        assertEquals(1, factory.calls);
        assertSame(delegated, resolver.admissionFor(StationType.P2P, candidate, snapshot));
        assertEquals(1, delegates.get());
        assertEquals(2, reads.get());
        assertEquals(2, lookups.get());
    }

    @Test
    void existingConstructorStillDerivesPlanForEveryRequestAndRejectsNullFactory() {
        NotionalToteOrder order = order();
        CountingFactory factory = new CountingFactory();
        ThirdPartyStationAdmissionResolver resolver = new ThirdPartyStationAdmissionResolver(
                (type, candidate, snapshot) -> { throw new AssertionError("unexpected fallback"); },
                factory,
                () -> new ThirdPartyAreaSnapshot(new ThirdPartyAreaConfig(0, 1, 10d),
                        List.of(), List.of(), List.of()),
                "third-party-ingress");
        DspSchedulerOrderState candidate = candidate(order);
        WarehouseSchedulerSnapshot snapshot = snapshot(candidate);
        resolver.admissionFor(StationType.THIRD_PARTY, candidate, snapshot);
        resolver.admissionFor(StationType.THIRD_PARTY, candidate, snapshot);
        assertEquals(2, factory.calls);
        assertThrows(IllegalArgumentException.class, () -> new ThirdPartyStationAdmissionResolver(
                (type, state, world) -> null, (ThirdPartyVisitFactory) null,
                () -> new ThirdPartyAreaSnapshot(new ThirdPartyAreaConfig(0, 1, 10d),
                        List.of(), List.of(), List.of()), "third-party-ingress"));
    }

    private static NotionalToteOrder order() {
        return new NotionalToteOrder("order-1", "tote-1", "104", 1, OrderType.FULL_PACK,
                List.of(new DspOrderItem("line-1", "third", 1, "0000310",
                        DspOrderLineType.FULL_PACK, "prescription-1", 1, 0)), 0L);
    }

    private static DspSchedulerOrderState candidate(NotionalToteOrder order) {
        return new DspSchedulerOrderState(order,
                new RouteRequirements(true, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static WarehouseSchedulerSnapshot snapshot(DspSchedulerOrderState candidate) {
        return new WarehouseSchedulerSnapshot(List.of(candidate), java.util.Map.of(), Set.of(), Optional.empty());
    }

    private static final class CountingFactory extends ThirdPartyVisitFactory {
        private int calls;

        private CountingFactory() {
            super(new InMemoryProductMasterRepository(List.of(
                    new ProductMasterRecord("third", "Third", Optional.of("Y74"), Optional.empty()))));
        }

        @Override public Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order) {
            calls++;
            return super.planFor(order);
        }
    }
}
