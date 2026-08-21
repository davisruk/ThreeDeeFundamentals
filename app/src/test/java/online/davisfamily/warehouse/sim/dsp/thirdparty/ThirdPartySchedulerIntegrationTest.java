package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.SnapshotStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class ThirdPartySchedulerIntegrationTest {

    @Test
    void shouldBlockQualifyingOrderWhenThirdPartyAreaIsFullWithoutMutatingArea() {
        Fixture fixture = fixture(new ThirdPartyAreaConfig(0, 1, 10d));
        fixture.area().submitVisit(fixture.visitFactory().create(
                new PhysicalToteId("physical-active-order"),
                fullPackOrder("active-order")).orElseThrow());
        ThirdPartyAreaSnapshot before = fixture.area().snapshot();
        DspSchedulerOrderState candidate = state(
                fullPackOrder("candidate-order"),
                route(true, false, true));

        SchedulerEvaluation evaluation = fixture.scheduler().evaluate(snapshot(List.of(candidate), Set.of()));

        assertTrue(evaluation.blockedDecision().isPresent());
        assertTrue(evaluation.blockedDecision().orElseThrow().blockReasons().stream()
                .anyMatch(reason -> reason.contains("Third Party area has no capacity")));
        assertEquals(before, fixture.area().snapshot());
    }

    @Test
    void shouldLeaveNonThirdPartyOrderUnaffectedByFullArea() {
        Fixture fixture = fixture(new ThirdPartyAreaConfig(0, 1, 10d));
        fixture.area().submitVisit(fixture.visitFactory().create(
                new PhysicalToteId("physical-active-order"),
                fullPackOrder("active-order")).orElseThrow());
        DspSchedulerOrderState candidate = state(
                nonThirdPartyOrder("regular-order"),
                route(false, false, true));

        SchedulerEvaluation evaluation = fixture.scheduler().evaluate(snapshot(List.of(candidate), Set.of()));

        assertTrue(evaluation.releaseDecision().isPresent());
        assertEquals("regular-order", evaluation.releaseDecision().orElseThrow().orderId());
    }

    @Test
    void shouldPreserveAdaptedDependencyBlockThenReleaseWhenPrepared() {
        Fixture fixture = fixture(new ThirdPartyAreaConfig(1, 1, 10d));
        NotionalToteOrder order = mixedAssociatedOrder("associated-order");
        DspSchedulerOrderState candidate = state(order, route(true, true, true));

        SchedulerEvaluation blocked = fixture.scheduler().evaluate(snapshot(List.of(candidate), Set.of()));

        assertTrue(blocked.blockedDecision().isPresent());
        assertTrue(blocked.blockedDecision().orElseThrow().blockReasons().stream()
                .anyMatch(reason -> reason.contains("Adapted work is not complete")));

        PreparedLineKey preparedLineKey = new PreparedLineKey(order.orderId(), "adapted-line");
        SchedulerEvaluation ready = fixture.scheduler().evaluate(snapshot(
                List.of(candidate),
                Set.of(preparedLineKey)));

        assertTrue(ready.releaseDecision().isPresent());
        assertFalse(ready.blockedDecision().isPresent());
        assertEquals("associated-order", ready.releaseDecision().orElseThrow().orderId());
    }

    @Test
    void shouldReportOpenAdmissionWhenCandidateHasNoThirdPartyVisit() {
        Fixture fixture = fixture(new ThirdPartyAreaConfig(1, 1, 10d));
        DspSchedulerOrderState candidate = state(
                nonThirdPartyOrder("regular-order"),
                route(false, false, true));

        StationAdmissionSnapshot admission = fixture.resolver().admissionFor(
                StationType.THIRD_PARTY,
                candidate,
                snapshot(List.of(candidate), Set.of()));

        assertTrue(admission.admissionOpen());
        assertEquals("third-party-ingress", admission.selectedTargetId().orElseThrow());
    }

    private Fixture fixture(ThirdPartyAreaConfig config) {
        InMemoryProductMasterRepository products = new InMemoryProductMasterRepository(List.of(
                product("third-party-product", "Y74"),
                product("regular-product", null),
                product("adapted-product", null)));
        ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(products);
        ThirdPartyArea area = new ThirdPartyArea(config);
        ThirdPartyStationAdmissionResolver resolver = new ThirdPartyStationAdmissionResolver(
                new SnapshotStationAdmissionResolver(),
                visitFactory,
                area::snapshot,
                "third-party-ingress");
        DspReleaseScheduler scheduler = new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("sc-1"))),
                new DspDependencyEvaluator(),
                resolver);
        return new Fixture(area, visitFactory, resolver, scheduler);
    }

    private WarehouseSchedulerSnapshot snapshot(
            List<DspSchedulerOrderState> orderStates,
            Set<PreparedLineKey> preparedLineKeys) {
        Map<StationType, StationAdmissionSnapshot> admissions = new LinkedHashMap<>();
        admissions.put(StationType.ADAPTING, openAdmission(StationType.ADAPTING));
        admissions.put(StationType.P2P, openAdmission(StationType.P2P));
        return new WarehouseSchedulerSnapshot(orderStates, admissions, preparedLineKeys, Optional.empty());
    }

    private StationAdmissionSnapshot openAdmission(StationType stationType) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                true,
                "");
    }

    private DspSchedulerOrderState state(NotionalToteOrder order, RouteRequirements routeRequirements) {
        return new DspSchedulerOrderState(order, routeRequirements, DspOrderStatus.WAITING);
    }

    private RouteRequirements route(boolean thirdParty, boolean adapting, boolean p2p) {
        return new RouteRequirements(thirdParty, adapting, false, p2p, false, StartLocation.OSR);
    }

    private NotionalToteOrder fullPackOrder(String orderId) {
        return order(
                orderId,
                OrderType.FULL_PACK,
                line("line-" + orderId, "third-party-product", DspOrderLineType.FULL_PACK));
    }

    private NotionalToteOrder nonThirdPartyOrder(String orderId) {
        return order(
                orderId,
                OrderType.FULL_PACK,
                line("line-" + orderId, "regular-product", DspOrderLineType.FULL_PACK));
    }

    private NotionalToteOrder mixedAssociatedOrder(String orderId) {
        return order(
                orderId,
                OrderType.ASSOCIATED,
                line("direct-line", "third-party-product", DspOrderLineType.FULL_PACK),
                line("adapted-line", "adapted-product", DspOrderLineType.ADAPTED));
    }

    private NotionalToteOrder order(String orderId, OrderType orderType, DspOrderItem... items) {
        return new NotionalToteOrder(
                orderId,
                "tote-" + orderId,
                "sc-1",
                1,
                orderType,
                List.of(items),
                0L);
    }

    private DspOrderItem line(String lineReference, String productId, DspOrderLineType lineType) {
        return new DspOrderItem(
                lineReference,
                productId,
                1,
                "0000310",
                lineType,
                lineReference,
                1,
                0);
    }

    private ProductMasterRecord product(String productId, String thirdPartyLocation) {
        return new ProductMasterRecord(
                productId,
                productId,
                Optional.ofNullable(thirdPartyLocation),
                Optional.empty());
    }

    private record Fixture(
            ThirdPartyArea area,
            ThirdPartyVisitFactory visitFactory,
            ThirdPartyStationAdmissionResolver resolver,
            DspReleaseScheduler scheduler) {
    }
}
