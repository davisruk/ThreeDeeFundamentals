package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSelection;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageMap;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitFactory;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingClaim;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class OperationalStationRouteContinuationTargetResolverTest {

    @Test
    void shouldSelectExactPreferredAdaptingBenchWithoutMutatingArea() {
        AdaptingAreaFixture fixture = areaFixture();
        NotionalToteOrder order = order("adapted", OrderType.ADAPTED, "pharmacy-b");
        StationProcessingDisposition disposition = disposition(order, "adapted-physical", null);
        var before = fixture.area().stationSnapshot();

        StationRouteContinuationDecision decision = fixture.resolver().resolve(
                disposition, order, StationType.ADAPTING);

        assertEquals(StationRouteContinuationDecision.continueTo(
                new OperationalRouteDestination(StationType.ADAPTING, "bench-b")), decision);
        assertEquals(before, fixture.area().stationSnapshot());
    }

    @Test
    void shouldDeferWhenEveryAdaptingBenchIsFullWithoutMutatingArea() {
        AdaptingAreaFixture fixture = areaFixture();
        NotionalToteOrder occupantOrder = order("occupant", OrderType.ADAPTED, "pharmacy-a");
        fixture.area().submitVisitTo(
                new AdaptingBenchId("bench-a"),
                fixture.visitFactory().create(new PhysicalToteId("occupant"), occupantOrder));
        fixture.area().bench(new AdaptingBenchId("bench-a")).startProcessing();
        fixture.area().submitVisitTo(
                new AdaptingBenchId("bench-b"),
                fixture.visitFactory().create(new PhysicalToteId("occupant-2"), occupantOrder));
        fixture.area().bench(new AdaptingBenchId("bench-b")).startProcessing();

        NotionalToteOrder candidateOrder = order("candidate", OrderType.ADAPTED, "pharmacy-a");
        StationProcessingDisposition disposition = disposition(candidateOrder, "candidate", null);
        var before = fixture.area().stationSnapshot();

        StationRouteContinuationDecision decision = fixture.resolver().resolve(
                disposition, candidateOrder, StationType.ADAPTING);

        assertTrue(decision.deferred());
        assertEquals(Optional.of("No adapting bench has queue or processing capacity"),
                decision.deferralReason());
        assertEquals(before, fixture.area().stationSnapshot());
    }

    @Test
    void shouldReturnExactPinnedP2pAssignmentDestination() {
        AdaptingAreaFixture fixture = areaFixture();
        NotionalToteOrder order = order("empty", OrderType.EMPTY, "pharmacy-a");
        OperationalRouteDestination p2pDestination =
                new OperationalRouteDestination(StationType.P2P, "p2p-7");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                new PhysicalToteId("empty-physical"),
                order.serviceCentreId(),
                new P2pLineId("line-7"),
                p2pDestination);
        StationProcessingDisposition disposition = disposition(order, "empty-physical", assignment);

        StationRouteContinuationDecision decision = fixture.resolver().resolve(
                disposition, order, StationType.P2P);

        assertEquals(StationRouteContinuationDecision.continueTo(p2pDestination), decision);
        assertSame(p2pDestination, decision.destination().orElseThrow());
    }

    @Test
    void shouldRejectMissingAndStaleP2pAssignmentsAndUnsupportedStations() {
        AdaptingAreaFixture fixture = areaFixture();
        NotionalToteOrder order = order("empty", OrderType.EMPTY, "pharmacy-a");
        StationProcessingDisposition missing = disposition(order, "empty-physical", null);

        assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(missing, order, StationType.P2P));

        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                new PhysicalToteId("empty-physical"),
                order.serviceCentreId(),
                new P2pLineId("line-1"),
                new OperationalRouteDestination(StationType.P2P, "p2p-1"));
        StationProcessingDisposition valid = disposition(order, "empty-physical", assignment);
        NotionalToteOrder wrongServiceCentre = order("empty", OrderType.EMPTY, "pharmacy-a", "SC-OTHER");
        assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(valid, wrongServiceCentre, StationType.P2P));

        assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(valid, order, StationType.MANUAL));
        assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(valid, order, StationType.MANUAL_MERGE));
        assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(valid, order, StationType.THIRD_PARTY));
    }

    @Test
    void shouldRejectNullResolverInputs() {
        AdaptingAreaFixture fixture = areaFixture();
        NotionalToteOrder order = order("empty", OrderType.EMPTY, "pharmacy-a");
        StationProcessingDisposition disposition = disposition(order, "empty-physical", null);

        assertThrows(IllegalArgumentException.class,
                () -> fixture.resolver().resolve(null, order, StationType.P2P));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.resolver().resolve(disposition, null, StationType.P2P));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.resolver().resolve(disposition, order, null));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalStationRouteContinuationTargetResolver(null,
                        fixture.visitFactory()));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalStationRouteContinuationTargetResolver(fixture.area(), null));
    }

    @Test
    void shouldRejectOrderIdentityAndTypeMismatchesBeforeTargetSelection() {
        AdaptingAreaFixture fixture = areaFixture();
        NotionalToteOrder sourceOrder = order("source", OrderType.ADAPTED, "pharmacy-a");
        StationProcessingDisposition disposition = disposition(sourceOrder, "source-physical", null);

        NotionalToteOrder wrongSheet = order("different", OrderType.ADAPTED, "pharmacy-a");
        NotionalToteOrder wrongType = order("source", OrderType.ASSOCIATED, "pharmacy-a");
        assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(disposition, wrongSheet, StationType.ADAPTING));
        assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(disposition, wrongType, StationType.ADAPTING));
    }

    private static AdaptingAreaFixture areaFixture() {
        AdaptingStorageMap storageMap = new AdaptingStorageMap();
        AdaptedLineStore storeA = new AdaptedLineStore();
        AdaptedLineStore storeB = new AdaptedLineStore();
        AdaptingArea area = new AdaptingArea(
                List.of(
                        new AdaptingBench("bench-a", storeA, 100d),
                        new AdaptingBench("bench-b", storeB, 100d)),
                0,
                storageMap);
        storageMap.assignPharmacyToBench("pharmacy-a", new AdaptingBenchId("bench-a"));
        storageMap.assignPharmacyToBench("pharmacy-b", new AdaptingBenchId("bench-b"));
        AdaptingVisitFactory visitFactory = new AdaptingVisitFactory();
        return new AdaptingAreaFixture(
                area,
                visitFactory,
                new OperationalStationRouteContinuationTargetResolver(area, visitFactory));
    }

    private static StationProcessingDisposition disposition(
            NotionalToteOrder order,
            String physicalToteId,
            P2pPhysicalToteAssignment assignment) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        boolean empty = order.orderType() == OrderType.EMPTY;
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                empty ? OperationalPhysicalToteSource.AV02 : OperationalPhysicalToteSource.OSR,
                id,
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                empty ? PhysicalToteRole.PRE_P2P : PhysicalToteRole.INBOUND_PACK,
                1);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        identity,
                        List.of(order.items().getFirst().pharmacyId()),
                        Duration.ZERO,
                        Optional.ofNullable(assignment));
        RoutedValues routedValues = routedValues(
                new OperationalRouteLaunchRequest(
                        releaseRequest,
                        new OperationalRouteDestination(StationType.THIRD_PARTY, "source")),
                id);
        StationProcessingClaim claim = new StationProcessingClaim(routedValues.routed(), Duration.ZERO);
        return new StationProcessingDisposition(
                claim,
                StationProcessingDispositionType.CONTINUE,
                routedValues.plan(),
                Duration.ofSeconds(1));
    }

    private static RoutedValues routedValues(
            OperationalRouteLaunchRequest launchRequest,
            PhysicalToteId physicalToteId) {
        RenderableObject renderable = RenderableObject.create(
                physicalToteId.value(),
                null,
                new Mesh(
                        new Vec4[] {
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f)
                        },
                        new int[][] {{0, 1, 2}},
                        "anchor"),
                new Mat4.ObjectTransformation(
                        0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        RouteSegment segment = new RouteSegment(
                "route-" + physicalToteId.value(),
                new online.davisfamily.threedee.path.LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
        Tote tote = new Tote(
                physicalToteId.value(),
                new RouteFollower(physicalToteId.value(), segment, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        ToteLoadPlan plan = new ToteLoadPlan(physicalToteId, List.of());
        return new RoutedValues(
                new online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote(
                        launchRequest, plan, tote, renderable),
                plan);
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            String pharmacyId) {
        return order(orderId, orderType, pharmacyId, "SC-1");
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            String pharmacyId,
            String serviceCentreId) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-1",
                        1,
                        pharmacyId,
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        orderType == OrderType.ADAPTED
                                ? DspOrderLineType.ADAPTED
                                : DspOrderLineType.ADAPTED,
                        orderId,
                        1,
                        0)),
                0,
                1);
    }

    private record AdaptingAreaFixture(
            AdaptingArea area,
            AdaptingVisitFactory visitFactory,
            OperationalStationRouteContinuationTargetResolver resolver) {
    }

    private record RoutedValues(
            online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote routed,
            ToteLoadPlan plan) {
    }
}
