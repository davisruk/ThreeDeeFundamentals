package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.control.SorterTipperDownstreamFlow;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;
import online.davisfamily.warehouse.sim.totebag.transfer.TippingDischargeTransfer;

class ToteToBagP2pLineActivityProbeTest {

    @Test
    void shouldReportAGenuinelyEmptyLineAsQuiescent() {
        Fixture fixture = fixture();

        P2pLineActivitySnapshot snapshot = fixture.probe.snapshot();

        assertEquals(P2pInputActivitySnapshot.idle(), snapshot.input());
        assertEquals(P2pPackPathActivitySnapshot.idle(), snapshot.packPath());
        assertEquals(P2pBaggingActivitySnapshot.idle(), snapshot.bagging());
        assertTrue(snapshot.processingDrained());
        assertTrue(snapshot.quiescent());
    }

    @Test
    void shouldReadQueueTipperPackPathBaggingAndOutboundActivity() {
        Fixture fixture = fixture();
        fixture.stationQueue.enqueue(routedTote("station-tote", fixture.definition.destination()));
        fixture.tipperInputQueue.enqueue(tipperPayload("tipper-queue-tote"));
        fixture.tipperFlow.active = true;
        fixture.tipperFlow.activeDischarges = List.of(
                new TippingDischargeTransfer(pack("tipper-pack", "tipper-group"), 1d));

        Pack sorterPack = pack("sorter-pack", "sorter-group");
        fixture.sortingMachine.receive(sorterPack);
        Pack pdcPack = pack("pdc-pack", "pdc-group");
        fixture.pdcConveyor.acceptIncomingPack(pdcPack);
        fixture.prl.assign(new online.davisfamily.warehouse.sim.totebag.assignment.PrlAssignmentPlan(
                fixture.prl.getId(), "prl-group", 1));
        fixture.prl.acceptPack(pack("prl-pack", "prl-group"));
        Pack pcrPack = pack("pcr-pack", "pcr-group");
        ReleasedPackGroup pcrGroup = group("pcr-group", pcrPack);
        fixture.pcrConveyor.startReceivingGroup(pcrGroup);
        fixture.pcrConveyor.acceptIncomingPack(pcrPack);

        ReleasedPackGroup baggerGroup = group(
                "bagger-group", pack("bagger-pack", "bagger-group"));
        fixture.baggingMachine.reserveIncomingGroup(baggerGroup);
        Bag receiverBag = bag("receiver-bag", "receiver-group");
        BagReservation receiverReservation = fixture.bagReceiver.reserveIncomingBag(receiverBag);
        fixture.bagReceiver.beginReceiving(receiverReservation);
        fixture.outboundSnapshot.set(outboundSnapshot(fixture.definition));

        P2pLineActivitySnapshot snapshot = fixture.probe.snapshot();

        assertEquals(1, snapshot.input().stationArrivalCount());
        assertEquals(1, snapshot.input().tipperInputCount());
        assertTrue(snapshot.input().activeTipperTote());
        assertEquals(1, snapshot.input().activeTipperDischargeCount());
        assertEquals(1, snapshot.packPath().sorterInputCount());
        assertEquals(1, snapshot.packPath().pdcPackCount());
        assertEquals(1, snapshot.packPath().nonIdlePrlCount());
        assertEquals(1, snapshot.packPath().prlPackCount());
        assertEquals(1, snapshot.packPath().pcrPackCount());
        assertEquals(1, snapshot.packPath().pcrTravellingGroupCount());
        assertTrue(snapshot.bagging().reservedBagGroup());
        assertTrue(snapshot.bagging().activeBagReservation());
        assertTrue(snapshot.bagging().receiverReservation());
        assertTrue(snapshot.bagging().receiverReceivingBag());
        assertTrue(snapshot.openOutboundTote().isPresent());
        assertFalse(snapshot.processingDrained());
        assertFalse(snapshot.quiescent());
    }

    @Test
    void shouldTrackSorterOwnershipAsPacksMoveThroughItsQueues() {
        Fixture fixture = fixture();
        fixture.sortingMachine.receive(pack("sorter-pack", "sorter-group"));

        assertEquals(1, fixture.probe.snapshot().packPath().sorterInputCount());

        fixture.sortingMachine.update(null, 1d);
        assertEquals(1, fixture.probe.snapshot().packPath().sorterOutputCount());

        fixture.sorterDownstreamFlow.update(0d);
        P2pPackPathActivitySnapshot pending = fixture.probe.snapshot().packPath();
        assertEquals(0, pending.sorterOutputCount());
        assertEquals(1, pending.pendingSorterOutfeedCount());
    }

    @Test
    void shouldTrackAssignedExpectedGroupsAndCompletedReceiverBags() {
        Fixture fixture = fixture();
        fixture.toteToBagFlowController.update(null, 0d);
        assertEquals(1,
                fixture.probe.snapshot().packPath().outstandingExpectedBagGroupCount());

        ReleasedPackGroup group = group(
                "waiting-group", pack("waiting-pack", "waiting-group"));
        fixture.baggingMachine.startBagging(group);
        fixture.baggingMachine.completeIncomingTransfer(group);

        Bag completedReceiverBag = bag("completed-receiver-bag", "completed-group");
        BagReservation reservation = fixture.bagReceiver.reserveIncomingBag(completedReceiverBag);
        fixture.bagReceiver.beginReceiving(reservation);
        fixture.bagReceiver.completeReceiving(reservation);

        fixture.baggingMachine.update(null, 0d);
        fixture.baggingMachine.update(null, 0d);
        fixture.baggingMachine.update(null, 0d);

        P2pBaggingActivitySnapshot bagging = fixture.probe.snapshot().bagging();
        assertEquals(1, bagging.receiverCompletedBagCount());
        assertEquals(1, bagging.pendingBagDischargeCount());
        assertFalse(fixture.probe.snapshot().quiescent());
    }

    @Test
    void shouldRejectMismatchedCompositionAndNullOutboundSnapshots() {
        Fixture fixture = fixture();
        P2pLineDefinition otherDefinition = new P2pLineDefinition(
                new P2pLineId("other-line"),
                new OperationalRouteDestination(StationType.P2P, "other-target"));

        assertThrows(IllegalArgumentException.class, () -> new ToteToBagP2pLineActivityProbe(
                otherDefinition,
                fixture.stationQueue,
                fixture.tipperInputQueue,
                fixture.tipperFlow,
                fixture.sortingMachine,
                fixture.sorterDownstreamFlow,
                fixture.toteToBagFlowController,
                fixture.pcrConveyor,
                fixture.baggingMachine,
                fixture.bagReceiver,
                fixture.outboundSnapshot::get));

        fixture.outboundSnapshot.set(null);
        assertThrows(IllegalStateException.class, fixture.probe::snapshot);
    }

    private static Fixture fixture() {
        P2pLineDefinition definition = new P2pLineDefinition(
                new P2pLineId("line-1"),
                new OperationalRouteDestination(StationType.P2P, "target-1"));
        StationRoutedToteArrivalQueue stationQueue =
                new StationRoutedToteArrivalQueue(definition.destination(), 2);
        TipperInputQueue tipperInputQueue = new TipperInputQueue("tipper-input", 2);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0d);
        SorterTipperDownstreamFlow sorterDownstreamFlow = new SorterTipperDownstreamFlow(
                sortingMachine,
                new RejectingPackTarget());
        TippingMachine tippingMachine = new TippingMachine("tipper", 0d, 0d, 0d);
        TestTipperFlowController tipperFlow = new TestTipperFlowController(
                tote("tipper-bootstrap"),
                loadPlan("tipper-bootstrap", "tipper-bootstrap-group"),
                routeSegment("tipper-segment"),
                tippingMachine,
                sorterDownstreamFlow);
        PdcConveyor pdcConveyor = new PdcConveyor(
                "pdc", occupancy(), 1f);
        PcrConveyor pcrConveyor = new PcrConveyor("pcr", occupancy(), 1d);
        PrlConveyor prl = new PrlConveyor("prl-1", 0f, occupancy());
        StoredBagReceiver bagReceiver = new StoredBagReceiver("bag-receiver", 1);
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger", bagSpec(), 0d, 0d, 0d, 1d, bagReceiver);
        ToteLoadPlan flowLoadPlan = loadPlan("flow-tote", "flow-group");
        ToteToBagFlowController flowController = new ToteToBagFlowController(
                flowLoadPlan,
                null,
                null,
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                List.of(prl));
        AtomicReference<OutboundAllocationSnapshot> outboundSnapshot =
                new AtomicReference<>(emptyOutboundSnapshot());
        ToteToBagP2pLineActivityProbe probe = new ToteToBagP2pLineActivityProbe(
                definition,
                stationQueue,
                tipperInputQueue,
                tipperFlow,
                sortingMachine,
                sorterDownstreamFlow,
                flowController,
                pcrConveyor,
                baggingMachine,
                bagReceiver,
                outboundSnapshot::get);
        return new Fixture(
                definition,
                stationQueue,
                tipperInputQueue,
                tipperFlow,
                sortingMachine,
                sorterDownstreamFlow,
                pdcConveyor,
                pcrConveyor,
                prl,
                baggingMachine,
                bagReceiver,
                flowController,
                outboundSnapshot,
                probe);
    }

    private static RoutedPhysicalTote routedTote(
            String physicalToteId,
            OperationalRouteDestination destination) {
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("line-" + physicalToteId, "product-1", 1)),
                0);
        OsrOutboundRouteLaunchRequest launchRequest = new OsrOutboundRouteLaunchRequest(
                new OsrProcessingReleaseRequest(manifest, Duration.ZERO),
                destination);
        Tote tote = tote(physicalToteId);
        return new RoutedPhysicalTote(
                launchRequest,
                new ToteLoadPlan(new PhysicalToteId(physicalToteId), List.of()),
                tote,
                tote.getRenderable());
    }

    private static TipperTotePayload tipperPayload(String toteId) {
        Tote tote = tote(toteId);
        return new TipperTotePayload(tote, tote.getRenderable(), 0f, Map.of());
    }

    private static ToteLoadPlan loadPlan(String toteId, String correlationId) {
        return new ToteLoadPlan(
                toteId,
                List.of(new PackPlan("pack-" + toteId, correlationId, dimensions())));
    }

    private static Pack pack(String packId, String correlationId) {
        return new Pack(packId, correlationId, dimensions());
    }

    private static ReleasedPackGroup group(String correlationId, Pack pack) {
        return new ReleasedPackGroup(
                correlationId,
                "prl-source",
                List.of(pack),
                pack.getDimensions().length());
    }

    private static Bag bag(String bagId, String correlationId) {
        return new Bag(
                bagId,
                correlationId,
                List.of(new PackPlan("pack-" + bagId, correlationId, dimensions())),
                bagSpec());
    }

    private static OutboundAllocationSnapshot emptyOutboundSnapshot() {
        return new OutboundAllocationSnapshot(Map.of(), List.of(), List.of());
    }

    private static OutboundAllocationSnapshot outboundSnapshot(P2pLineDefinition definition) {
        OutboundToteSnapshot openTote = new OutboundToteSnapshot(
                new PhysicalToteId("outbound-1"),
                definition.lineId(),
                Optional.of("104"),
                Optional.of("pharmacy-1"),
                10,
                List.of(),
                Optional.empty());
        return new OutboundAllocationSnapshot(
                Map.of(definition.lineId(), openTote),
                List.of(),
                List.of());
    }

    private static ConveyorOccupancyModel occupancy() {
        return new ConveyorOccupancyModel(2f, 0.05f, 0f);
    }

    private static PackDimensions dimensions() {
        return new PackDimensions(0.2f, 0.1f, 0.08f);
    }

    private static BagSpec bagSpec() {
        return new BagSpec(0.4f, 0.5f, 0.2f);
    }

    private static Tote tote(String toteId) {
        RenderableObject renderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                ignored -> 0,
                false);
        return new Tote(
                toteId,
                new RouteFollower(toteId, routeSegment("route-" + toteId), 0f, 1d),
                renderable,
                new Vec3(),
                0f);
    }

    private static RouteSegment routeSegment(String id) {
        return new RouteSegment(
                id,
                new LinearSegment3(new Vec3(), new Vec3(1f, 0f, 0f), false));
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] {{0, 1, 2}},
                "anchor");
    }

    private static final class TestTipperFlowController extends ToteTrackTipperFlowController {
        private boolean active;
        private List<TippingDischargeTransfer> activeDischarges = List.of();

        private TestTipperFlowController(
                Tote tote,
                ToteLoadPlan loadPlan,
                RouteSegment tipperSegment,
                TippingMachine tippingMachine,
                SorterTipperDownstreamFlow downstreamFlow) {
            super(tote, ignored -> loadPlan, tipperSegment, 0f, 0f,
                    tippingMachine, downstreamFlow, 1d);
        }

        @Override
        public boolean hasActiveTote() {
            return active;
        }

        @Override
        public List<TippingDischargeTransfer> getActiveDischarges() {
            return List.copyOf(activeDischarges);
        }
    }

    private static final class RejectingPackTarget implements PackReceiveTarget {
        private final PackHandoffPoint handoffPoint = new PackHandoffPoint(
                "rejecting-target", new Vec3(), 0f);

        @Override
        public PackHandoffPoint handoffPoint() {
            return handoffPoint;
        }

        @Override
        public boolean canAccept(Pack pack) {
            return false;
        }

        @Override
        public void accept(Pack pack) {
            throw new AssertionError("Rejecting target must not accept a pack");
        }
    }

    private record Fixture(
            P2pLineDefinition definition,
            StationRoutedToteArrivalQueue stationQueue,
            TipperInputQueue tipperInputQueue,
            TestTipperFlowController tipperFlow,
            SortingMachine sortingMachine,
            SorterTipperDownstreamFlow sorterDownstreamFlow,
            PdcConveyor pdcConveyor,
            PcrConveyor pcrConveyor,
            PrlConveyor prl,
            BaggingMachine baggingMachine,
            StoredBagReceiver bagReceiver,
            ToteToBagFlowController toteToBagFlowController,
            AtomicReference<OutboundAllocationSnapshot> outboundSnapshot,
            ToteToBagP2pLineActivityProbe probe) {
    }
}
