package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

class ThirdPartyStationProcessingControllerTest {

    @Test
    void shouldPublishOneContinueDispositionWithTheExactReplacementPlan() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-direct", 0d);
        fixture.target().accept(fixture.routedTote(), Duration.ofSeconds(1));
        SimulationContext context = context(1.2345678906d);

        fixture.controller().update(context, 0d);

        StationProcessingDisposition disposition = fixture.coordinator().peekDisposition().orElseThrow();
        ToteLoadPlan replacement = fixture.registry().getLoadPlanFor(fixture.routedTote().physicalToteId());
        assertEquals(StationProcessingDispositionType.CONTINUE, disposition.type());
        assertSame(replacement, disposition.currentLoadPlan());
        assertNotSame(fixture.routedTote().loadPlan(), replacement);
        assertEquals(List.of("pack-line-controller-direct-1"),
                replacement.getPackPlans().stream().map(PackPlan::packId).toList());
        assertEquals(Duration.ofNanos(Math.round(1.2345678906d * 1_000_000_000L)),
                disposition.completedAt());
        assertSame(fixture.routedTote(), disposition.claim().routedTote());
        assertFalse(fixture.routedTote().tote().areLidsOpen());
    }

    @Test
    void shouldPublishContinueForEmptyAndAdaptedWork() {
        Fixture empty = fixture(OrderType.EMPTY, OperationalPhysicalToteSource.AV02,
                DspOrderLineType.FULL_PACK, "controller-empty", 0d);
        Fixture adapted = fixture(OrderType.ADAPTED, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.ADAPTED, "controller-adapted", 0d);

        empty.target().accept(empty.routedTote(), Duration.ZERO);
        adapted.target().accept(adapted.routedTote(), Duration.ZERO);
        empty.controller().update(context(2d), 0d);
        adapted.controller().update(context(2d), 0d);

        assertEquals(StationProcessingDispositionType.CONTINUE,
                empty.coordinator().peekDisposition().orElseThrow().type());
        assertEquals(StationProcessingDispositionType.CONTINUE,
                adapted.coordinator().peekDisposition().orElseThrow().type());
        assertEquals(1, empty.registry().getLoadPlanFor(empty.routedTote().physicalToteId())
                .getPackPlans().size());
        assertEquals(1, adapted.registry().getLoadPlanFor(adapted.routedTote().physicalToteId())
                .getPackPlans().size());
    }

    @Test
    void shouldCompleteAtMostOneActiveClaimPerUpdateInClaimOrder() {
        OperationalRouteDestination firstDestination = destination("controller-first");
        OperationalRouteDestination secondDestination = destination("controller-second");
        NotionalToteOrder firstOrder = order("controller-first", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        NotionalToteOrder secondOrder = order("controller-second", OrderType.FULL_PACK,
                DspOrderLineType.FULL_PACK);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        ThirdPartyArea area = new ThirdPartyArea(new ThirdPartyAreaConfig(0, 2, 0d));
        ThirdPartyAreaController areaController = areaController(area, registry);
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        ThirdPartyStationProcessingTarget firstTarget = target(
                firstOrder, "controller-first-physical", firstDestination, registry, area, coordinator);
        ThirdPartyStationProcessingTarget secondTarget = target(
                secondOrder, "controller-second-physical", secondDestination, registry, area, coordinator);
        ThirdPartyStationProcessingController controller = new ThirdPartyStationProcessingController(
                "third-party-controller",
                new LinkedHashSet<>(List.of(firstDestination, secondDestination)),
                registry,
                areaController,
                coordinator);

        RoutedPhysicalTote firstTote = routedTote(
                firstOrder,
                new PhysicalToteId("controller-first-physical"),
                firstDestination,
                new ToteLoadPlan(new PhysicalToteId("controller-first-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        RoutedPhysicalTote secondTote = routedTote(
                secondOrder,
                new PhysicalToteId("controller-second-physical"),
                secondDestination,
                new ToteLoadPlan(new PhysicalToteId("controller-second-physical"), List.of()),
                OperationalPhysicalToteSource.OSR);
        registry.putLoadPlan(firstTote.loadPlan());
        registry.putLoadPlan(secondTote.loadPlan());
        firstTarget.accept(firstTote, Duration.ZERO);
        secondTarget.accept(secondTote, Duration.ZERO);

        controller.update(context(1d), 0d);

        assertEquals(List.of(new PhysicalToteId("controller-first-physical")),
                coordinator.snapshot().pendingDispositions().stream()
                        .map(pending -> pending.physicalToteId())
                        .toList());
        assertEquals(List.of(new PhysicalToteId("controller-second-physical")),
                coordinator.snapshot().activeClaims().stream()
                        .map(active -> active.physicalToteId())
                        .toList());

        controller.update(context(2d), 0d);

        assertEquals(List.of(
                        new PhysicalToteId("controller-first-physical"),
                        new PhysicalToteId("controller-second-physical")),
                coordinator.pendingDispositions().stream()
                        .map(StationProcessingDisposition::physicalToteId)
                        .toList());
    }

    @Test
    void shouldNotPublishTwiceWhenTheDomainCompletionRemainsObservable() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-repeat", 0d);
        fixture.target().accept(fixture.routedTote(), Duration.ZERO);

        fixture.controller().update(context(1d), 0d);
        StationProcessingDisposition first = fixture.coordinator().peekDisposition().orElseThrow();
        fixture.controller().update(context(2d), 0d);

        assertSame(first, fixture.coordinator().peekDisposition().orElseThrow());
        assertEquals(1, fixture.coordinator().pendingDispositions().size());
        assertTrue(fixture.coordinator().snapshot().activeClaims().isEmpty());
    }

    @Test
    void shouldRejectMismatchedCompletionIdentityBeforeBoundaryMutation() {
        Fixture wrongSheet = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-wrong-sheet", 0d);
        ThirdPartyVisit wrongSheetVisit = completionVisit(
                wrongSheet,
                wrongSheet.routedTote().physicalToteId(),
                new OrderSheetKey("different-order", 1),
                wrongSheet.order().serviceCentreId(),
                wrongSheet.order().orderType(),
                DspOrderLineType.FULL_PACK);
        assertRejectsCompletion(wrongSheet, new ThirdPartyCompletion(wrongSheetVisit),
                wrongSheet.registry());

        Fixture wrongPhysicalId = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-wrong-physical", 0d);
        ThirdPartyVisit wrongPhysicalIdVisit = completionVisit(
                wrongPhysicalId,
                new PhysicalToteId("different-physical"),
                wrongPhysicalId.order().orderSheetKey(),
                wrongPhysicalId.order().serviceCentreId(),
                wrongPhysicalId.order().orderType(),
                DspOrderLineType.FULL_PACK);
        assertRejectsCompletion(wrongPhysicalId, new ThirdPartyCompletion(wrongPhysicalIdVisit),
                wrongPhysicalId.registry());

        Fixture wrongType = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-wrong-type", 0d);
        ThirdPartyVisit wrongTypeVisit = completionVisit(
                wrongType,
                wrongType.routedTote().physicalToteId(),
                wrongType.order().orderSheetKey(),
                wrongType.order().serviceCentreId(),
                OrderType.ADAPTED,
                DspOrderLineType.ADAPTED);
        assertRejectsCompletion(wrongType, new ThirdPartyCompletion(wrongTypeVisit),
                wrongType.registry());

        Fixture wrongServiceCentre = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-wrong-service-centre", 0d);
        ThirdPartyVisit wrongServiceCentreVisit = completionVisit(
                wrongServiceCentre,
                wrongServiceCentre.routedTote().physicalToteId(),
                wrongServiceCentre.order().orderSheetKey(),
                "SC-OTHER",
                wrongServiceCentre.order().orderType(),
                DspOrderLineType.FULL_PACK);
        assertRejectsCompletion(wrongServiceCentre,
                new ThirdPartyCompletion(wrongServiceCentreVisit),
                wrongServiceCentre.registry());
    }

    @Test
    void shouldRejectMissingCurrentReplacementPlanBeforeBoundaryMutation() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-missing-plan", 0d);
        ThirdPartyVisit matchingVisit = completionVisit(
                fixture,
                fixture.routedTote().physicalToteId(),
                fixture.order().orderSheetKey(),
                fixture.order().serviceCentreId(),
                fixture.order().orderType(),
                DspOrderLineType.FULL_PACK);

        assertRejectsCompletion(
                fixture,
                new ThirdPartyCompletion(matchingVisit),
                new MapBackedToteLoadPlanRegistry());
    }

    @Test
    void shouldRejectAStalePlanAfterDomainCompletionObservation() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-stale", 0d);
        fixture.coordinator().claim(fixture.routedTote(), Duration.ZERO);
        ThirdPartyVisit visit = new ThirdPartyVisit(
                fixture.routedTote().physicalToteId(),
                new ThirdPartyVisitPlan(
                        fixture.order().orderSheetKey(),
                        fixture.order().serviceCentreId(),
                        fixture.order().orderType(),
                        List.of(lineWork("controller-stale", DspOrderLineType.FULL_PACK))));
        ThirdPartyAreaController fakeController = new FixedCompletionAreaController(
                fixture.area(),
                fixture.registry(),
                new ThirdPartyCompletion(visit));
        ThirdPartyStationProcessingController controller = new ThirdPartyStationProcessingController(
                "third-party-controller",
                Set.of(fixture.destination()),
                fixture.registry(),
                fakeController,
                fixture.coordinator());

        assertThrows(IllegalStateException.class, () -> controller.update(context(1d), 0d));
        assertTrue(fixture.coordinator().pendingDispositions().isEmpty());
        assertTrue(fixture.coordinator().snapshot().activeClaims().stream()
                .anyMatch(active -> active.physicalToteId().equals(fixture.routedTote().physicalToteId())));
    }

    @Test
    void shouldRequireThirdPartyDestinationsAndStableControllerId() {
        Fixture fixture = fixture(OrderType.FULL_PACK, OperationalPhysicalToteSource.OSR,
                DspOrderLineType.FULL_PACK, "controller-validation", 0d);
        assertThrows(IllegalArgumentException.class,
                () -> new ThirdPartyStationProcessingController(
                        " ", Set.of(fixture.destination()), fixture.registry(), fixture.areaController(),
                        fixture.coordinator()));
        assertThrows(IllegalArgumentException.class,
                () -> new ThirdPartyStationProcessingController(
                        "id", Set.of(), fixture.registry(), fixture.areaController(), fixture.coordinator()));
        assertThrows(IllegalArgumentException.class,
                () -> new ThirdPartyStationProcessingController(
                        "id",
                        Set.of(new OperationalRouteDestination(StationType.ADAPTING, "bench-1")),
                        fixture.registry(), fixture.areaController(), fixture.coordinator()));

        ThirdPartyStationProcessingController controller = new ThirdPartyStationProcessingController(
                "  controller-id  ",
                Set.of(fixture.destination()),
                fixture.registry(),
                fixture.areaController(),
                fixture.coordinator());
        assertEquals("controller-id", controller.processingControllerId());
        assertEquals(Set.of(fixture.destination()), controller.destinations());
    }

    private static ThirdPartyVisit completionVisit(
            Fixture fixture,
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            String serviceCentreId,
            OrderType orderType,
            DspOrderLineType lineType) {
        return new ThirdPartyVisit(
                physicalToteId,
                new ThirdPartyVisitPlan(
                        orderSheetKey,
                        serviceCentreId,
                        orderType,
                        List.of(lineWork(fixture.order().orderId(), lineType))));
    }

    private static void assertRejectsCompletion(
            Fixture fixture,
            ThirdPartyCompletion completion,
            MutableToteLoadPlanRegistry completionRegistry) {
        fixture.coordinator().claim(fixture.routedTote(), Duration.ZERO);
        var coordinatorBefore = fixture.coordinator().snapshot();
        var areaBefore = fixture.area().snapshot();
        ToteLoadPlan planBefore = fixture.registry().getLoadPlanFor(
                fixture.routedTote().physicalToteId());
        ThirdPartyAreaController fakeController = new FixedCompletionAreaController(
                fixture.area(),
                completionRegistry,
                completion);
        ThirdPartyStationProcessingController controller = new ThirdPartyStationProcessingController(
                "third-party-controller",
                Set.of(fixture.destination()),
                completionRegistry,
                fakeController,
                fixture.coordinator());

        assertThrows(IllegalStateException.class, () -> controller.update(context(1d), 0d));
        assertEquals(coordinatorBefore, fixture.coordinator().snapshot());
        assertEquals(areaBefore, fixture.area().snapshot());
        assertSame(planBefore,
                fixture.registry().getLoadPlanFor(fixture.routedTote().physicalToteId()));
        assertTrue(fixture.coordinator().pendingDispositions().isEmpty());
    }

    private static Fixture fixture(
            OrderType orderType,
            OperationalPhysicalToteSource source,
            DspOrderLineType lineType,
            String targetId,
            double processingDurationSeconds) {
        NotionalToteOrder order = order(targetId, orderType, lineType);
        MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
        ThirdPartyArea area = new ThirdPartyArea(
                new ThirdPartyAreaConfig(0, 1, processingDurationSeconds));
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        OperationalRouteDestination destination = destination(targetId);
        RoutedPhysicalTote routedTote = routedTote(
                order,
                new PhysicalToteId(targetId + "-physical"),
                destination,
                new ToteLoadPlan(new PhysicalToteId(targetId + "-physical"), List.of()),
                source);
        registry.putLoadPlan(routedTote.loadPlan());
        ThirdPartyStationProcessingTarget target = target(
                order,
                targetId + "-physical",
                destination,
                registry,
                area,
                coordinator);
        ThirdPartyAreaController areaController = areaController(area, registry);
        ThirdPartyStationProcessingController controller = new ThirdPartyStationProcessingController(
                "third-party-controller",
                Set.of(destination),
                registry,
                areaController,
                coordinator);
        return new Fixture(order, routedTote, registry, area, areaController, coordinator, target,
                controller, destination);
    }

    private static ThirdPartyStationProcessingTarget target(
            NotionalToteOrder order,
            String physicalToteId,
            OperationalRouteDestination destination,
            MapBackedToteLoadPlanRegistry registry,
            ThirdPartyArea area,
            StationProcessingCoordinator coordinator) {
        if (registry.getLoadPlanFor(new PhysicalToteId(physicalToteId)) == null) {
            PhysicalToteId id = new PhysicalToteId(physicalToteId);
            registry.putLoadPlan(new ToteLoadPlan(id, List.of()));
        }
        ThirdPartyVisitFactory visitFactory = new ThirdPartyVisitFactory(
                new InMemoryProductMasterRepository(List.of(new ProductMasterRecord(
                        "product-1",
                        "Third Party Product",
                        Optional.of("Y74"),
                        Optional.of(new PackDimensions(0.2f, 0.1f, 0.05f))))));
        return new ThirdPartyStationProcessingTarget(
                destination,
                new online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog(
                        List.of(order)),
                registry,
                visitFactory,
                area,
                coordinator);
    }

    private static ThirdPartyAreaController areaController(
            ThirdPartyArea area,
            MutableToteLoadPlanRegistry registry) {
        return new ThirdPartyAreaController(
                area,
                registry,
                (visit, lineWork, packOrdinal) -> new PackPlan(
                        "pack-" + lineWork.lineReference() + "-" + packOrdinal,
                        visit.orderSheetKey().orderId(),
                        new PackDimensions(0.2f, 0.1f, 0.05f)));
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            DspOrderLineType lineType) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "SC-1",
                1,
                orderType,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-1",
                        1,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        lineType,
                        orderId,
                        1,
                        0)),
                0,
                1);
    }

    private static ThirdPartyLineWork lineWork(String orderId, DspOrderLineType lineType) {
        return new ThirdPartyLineWork(
                new DspOrderItem(
                        "line-" + orderId,
                        "product-1",
                        1,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        lineType,
                        orderId,
                        1,
                        0),
                1,
                "Y74",
                lineType == DspOrderLineType.ADAPTED
                        ? ThirdPartyWorkType.ADAPTED_PREPARATION
                        : ThirdPartyWorkType.DIRECT_FULFILMENT);
    }

    private static OperationalRouteDestination destination(String targetId) {
        return new OperationalRouteDestination(StationType.THIRD_PARTY, targetId);
    }

    private static RoutedPhysicalTote routedTote(
            NotionalToteOrder order,
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination,
            ToteLoadPlan loadPlan,
            OperationalPhysicalToteSource source) {
        PhysicalToteRole role = source == OperationalPhysicalToteSource.AV02
                ? PhysicalToteRole.PRE_P2P
                : PhysicalToteRole.INBOUND_PACK;
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                source,
                physicalToteId,
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                role,
                0);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        identity,
                        List.of("pharmacy-1"),
                        Duration.ZERO,
                        Optional.empty());
        OperationalRouteLaunchRequest launchRequest = new OperationalRouteLaunchRequest(
                releaseRequest,
                destination);
        RenderableObject renderable = renderable(physicalToteId.value());
        RouteSegment segment = new RouteSegment(
                "segment-" + physicalToteId.value(),
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
        return new RoutedPhysicalTote(launchRequest, loadPlan, tote, renderable);
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
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
    }

    private static SimulationContext context(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }

    private record Fixture(
            NotionalToteOrder order,
            RoutedPhysicalTote routedTote,
            MapBackedToteLoadPlanRegistry registry,
            ThirdPartyArea area,
            ThirdPartyAreaController areaController,
            StationProcessingCoordinator coordinator,
            ThirdPartyStationProcessingTarget target,
            ThirdPartyStationProcessingController controller,
            OperationalRouteDestination destination) {
    }

    private static final class FixedCompletionAreaController extends ThirdPartyAreaController {
        private final ThirdPartyCompletion completion;

        private FixedCompletionAreaController(
                ThirdPartyArea area,
                MutableToteLoadPlanRegistry registry,
                ThirdPartyCompletion completion) {
            super(area, registry,
                    (visit, lineWork, packOrdinal) -> new PackPlan(
                            "unused-pack",
                            "unused-correlation",
                            new PackDimensions(0.2f, 0.1f, 0.05f)));
            this.completion = completion;
        }

        @Override
        public void update(double dtSeconds) {
            // The fixture provides the completion directly so the generic boundary can validate it.
        }

        @Override
        public Optional<ThirdPartyCompletion> completionForTote(PhysicalToteId physicalToteId) {
            return Optional.of(completion);
        }
    }
}
