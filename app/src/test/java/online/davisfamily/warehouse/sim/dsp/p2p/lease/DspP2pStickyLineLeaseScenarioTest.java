package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestFactory;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DspP2pStickyLineLeaseScenarioTest {

    @Test
    void shouldRetainStickyOwnershipThroughBackpressureCloseReleaseAndReuse() {
        Scenario scenario = scenario();
        DspP2pStickyLeaseRuntime runtime = scenario.runtime();
        P2pLineDefinition firstLine = scenario.lines().get(0).definition();
        P2pLineDefinition secondLine = scenario.lines().get(1).definition();
        P2pPhysicalToteAssignment firstAssignment = assignment(
                scenario.firstManifest(), firstLine);
        P2pPhysicalToteAssignment secondAssignment = assignment(
                scenario.secondManifest(), secondLine);

        commit(runtime, scenario.firstManifest(), firstLine.destination().targetId(), firstAssignment);
        commit(runtime, scenario.secondManifest(), "third-party-entry", secondAssignment);

        assertTrue(scenario.lines().get(0).source().peek().isEmpty());
        assertTrue(scenario.lines().get(1).source().peek().isEmpty());
        assertEquals(Optional.of(firstAssignment), runtime.leaseSnapshot()
                .findAssignment(scenario.firstManifest().physicalToteId()));
        assertEquals(Optional.of(secondAssignment), runtime.leaseSnapshot()
                .findAssignment(scenario.secondManifest().physicalToteId()));

        Line first = scenario.lines().get(0);
        Line second = scenario.lines().get(1);
        RoutedPhysicalTote blocker = routed(
                "blocker", "104", first.definition().destination(), first.terminal(), Optional.empty());
        first.target().accept(blocker, payload(blocker));
        RoutedPhysicalTote firstArrival = routed(
                scenario.firstManifest(), first.definition().destination(), first.terminal(), firstAssignment);
        RoutedPhysicalTote secondArrival = routed(
                scenario.secondManifest(), second.definition().destination(), second.terminal(), secondAssignment);
        first.source().enqueue(firstArrival);
        second.source().enqueue(secondArrival);

        scenario.world().update(0.1d);

        assertTrue(first.source().contains(firstArrival.physicalToteId()));
        assertFalse(first.input().contains(firstArrival.physicalToteId().value()));
        assertFalse(second.source().contains(secondArrival.physicalToteId()));
        assertTrue(second.input().contains(secondArrival.physicalToteId().value()));
        assertEquals(Optional.of("104"), runtime.leaseSnapshot()
                .findLine(firstLine.lineId()).orElseThrow().serviceCentreId());

        first.input().dequeuePayload();
        scenario.world().update(0.1d);
        assertTrue(first.input().contains(firstArrival.physicalToteId().value()));

        first.input().dequeuePayload();
        second.input().dequeuePayload();
        consumeAtP2p(scenario.ledger(), scenario.firstManifest().physicalToteId());
        consumeAtP2p(scenario.ledger(), scenario.secondManifest().physicalToteId());
        scenario.outboundAllocator().allocate(
                firstLine.lineId(), bag("bag-1", "104", "pharmacy-a", scenario.firstManifest()),
                Duration.ofMillis(200));
        scenario.outboundAllocator().allocate(
                secondLine.lineId(), bag("bag-2", "108", "pharmacy-b", scenario.secondManifest()),
                Duration.ofMillis(200));

        for (int update = 0; update < 4; update++) {
            scenario.world().update(0.1d);
        }

        assertTrue(runtime.leaseSnapshot().findLine(firstLine.lineId())
                .orElseThrow().serviceCentreId().isEmpty());
        assertTrue(runtime.leaseSnapshot().findLine(secondLine.lineId())
                .orElseThrow().serviceCentreId().isEmpty());
        assertEquals(List.of("104", "108"), scenario.outboundAllocator().snapshot()
                .closedTotes().stream()
                .map(tote -> tote.serviceCentreId().orElseThrow())
                .toList());
        assertEquals(List.of("pharmacy-a", "pharmacy-b"), scenario.outboundAllocator().snapshot()
                .closedTotes().stream()
                .map(tote -> tote.pharmacyId().orElseThrow())
                .toList());
        assertTrue(runtime.snapshot().findLine(firstLine.lineId()).orElseThrow()
                .lastTransition().orElseThrow().details().contains("LEASE_RELEASED"));
        assertTrue(runtime.snapshot().findLine(firstLine.lineId()).orElseThrow()
                .latestClosedOutboundTote().isPresent());

        scenario.schedulerSnapshot().set(snapshot(List.of(
                scenario.firstState(), scenario.secondState(), scenario.reuseState())));
        P2pPhysicalToteAssignment reuseAssignment = assignment(
                scenario.reuseManifest(), firstLine);
        commit(runtime, scenario.reuseManifest(), firstLine.destination().targetId(), reuseAssignment);

        assertEquals(Optional.of("108"), runtime.leaseSnapshot()
                .findLine(firstLine.lineId()).orElseThrow().serviceCentreId());
        assertEquals(Optional.of(firstAssignment), runtime.leaseSnapshot()
                .findAssignment(scenario.firstManifest().physicalToteId()));
        assertEquals(Optional.of(reuseAssignment), runtime.leaseSnapshot()
                .findAssignment(scenario.reuseManifest().physicalToteId()));
    }

    private static Scenario scenario() {
        InboundToteManifest first = manifest("direct-104", "104", "pharmacy-a", 1);
        InboundToteManifest second = manifest("multi-108", "108", "pharmacy-b", 2);
        InboundToteManifest reuse = manifest("reuse-108", "108", "pharmacy-c", 3);
        DspSchedulerOrderState firstState = state(first, false);
        DspSchedulerOrderState secondState = state(second, true);
        DspSchedulerOrderState reuseState = state(reuse, false);
        AtomicReference<WarehouseSchedulerSnapshot> schedulerSnapshot =
                new AtomicReference<>(snapshot(List.of(firstState, secondState)));
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        List.of(first, second, reuse).forEach(manifest ->
                ledger.register(PhysicalToteRecord.inboundPack(manifest.physicalToteId())));
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(
                        first.orderSheetKey(), second.orderSheetKey(), reuse.orderSheetKey())),
                new OutboundToteConfig(10));

        List<Line> lines = new ArrayList<>();
        List<P2pLineDefinition> definitions = new ArrayList<>();
        Map<P2pLineId, P2pLineActivityProbe> probes = new LinkedHashMap<>();
        List<P2pStickyArrivalBinding> bindings = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            P2pLineDefinition definition = new P2pLineDefinition(
                    new P2pLineId("line-" + index),
                    new OperationalRouteDestination(StationType.P2P, "p2p-" + index));
            RouteSegment terminal = segment("terminal-" + index, index * 2f);
            StationRoutedToteArrivalQueue source =
                    new StationRoutedToteArrivalQueue(definition.destination(), 2);
            TipperInputQueue input = new TipperInputQueue("input-" + index, 1);
            P2pTipperArrivalTarget target =
                    new P2pTipperArrivalTarget(definition.destination(), input);
            Line line = new Line(definition, terminal, source, input, target);
            lines.add(line);
            definitions.add(definition);
            probes.put(definition.lineId(), () -> activity(line, allocator));
            bindings.add(new P2pStickyArrivalBinding(
                    definition.lineId(),
                    source,
                    new P2pArrivalRouteBinding(terminal, terminal),
                    DspP2pStickyLineLeaseScenarioTest::payload,
                    target));
        }
        SimulationWorld world = new SimulationWorld();
        DspP2pStickyLeaseRuntime runtime = new DspP2pStickyLeaseRuntimeFactory().create(
                world,
                definitions,
                probes,
                schedulerSnapshot::get,
                new InboundToteManifestCatalog(List.of(first, second, reuse)),
                ledger::snapshot,
                allocator,
                bindings);
        return new Scenario(
                world, lines, runtime, ledger, allocator, schedulerSnapshot,
                first, second, reuse, firstState, secondState, reuseState);
    }

    private static P2pLineActivitySnapshot activity(Line line, OutboundToteAllocator allocator) {
        return new P2pLineActivitySnapshot(
                new P2pInputActivitySnapshot(
                        line.source().snapshot().occupancy(),
                        line.input().snapshot().toteIds().size(),
                        false,
                        0),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                allocator.snapshot().openToteFor(line.definition().lineId()));
    }

    private static void commit(
            DspP2pStickyLeaseRuntime runtime,
            InboundToteManifest manifest,
            String releaseTargetId,
            P2pPhysicalToteAssignment assignment) {
        runtime.releaseAssignmentCommitter().prepare(
                new ReleasePhysicalToteFromOsrCommand(
                        manifest.physicalToteId(),
                        manifest.orderSheetKey(),
                        manifest.serviceCentreId(),
                        releaseTargetId,
                        Optional.of(assignment)),
                manifest).commit();
    }

    private static RoutedPhysicalTote routed(
            InboundToteManifest manifest,
            OperationalRouteDestination destination,
            RouteSegment terminal,
            P2pPhysicalToteAssignment assignment) {
        return routed(
                manifest.physicalToteId().value(),
                manifest.serviceCentreId(),
                destination,
                terminal,
                Optional.of(assignment),
                manifest);
    }

    private static RoutedPhysicalTote routed(
            String physicalToteId,
            String serviceCentreId,
            OperationalRouteDestination destination,
            RouteSegment terminal,
            Optional<P2pPhysicalToteAssignment> assignment) {
        InboundToteManifest manifest = manifest(
                physicalToteId, serviceCentreId, "pharmacy-blocker", 99);
        return routed(
                physicalToteId,
                serviceCentreId,
                destination,
                terminal,
                assignment,
                manifest);
    }

    private static RoutedPhysicalTote routed(
            String physicalToteId,
            String serviceCentreId,
            OperationalRouteDestination destination,
            RouteSegment terminal,
            Optional<P2pPhysicalToteAssignment> assignment,
            InboundToteManifest manifest) {
        RenderableObject renderable = RenderableObject.create(
                physicalToteId,
                null,
                new Mesh(
                        new Vec4[] { new Vec4(), new Vec4(), new Vec4() },
                        new int[][] { {0, 1, 2} },
                        "anchor"),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangle -> 0,
                false);
        Tote tote = new Tote(
                physicalToteId,
                new RouteFollower(physicalToteId, terminal, 0f, 0d),
                renderable,
                new Vec3(),
                0f);
        return new RoutedPhysicalTote(
                OperationalRouteLaunchRequestFactory.fromOsr(
                        new OsrProcessingReleaseRequest(manifest, Duration.ZERO, assignment),
                        destination),
                new ToteLoadPlan(new PhysicalToteId(physicalToteId), List.of()),
                tote,
                renderable);
    }

    private static TipperTotePayload payload(RoutedPhysicalTote routedTote) {
        return new TipperTotePayload(
                routedTote.tote(), routedTote.renderable(), 0f, Map.of());
    }

    private static void consumeAtP2p(
            PhysicalToteLifecycleLedger ledger,
            PhysicalToteId physicalToteId) {
        ledger.transitionTote(physicalToteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        ledger.transitionTote(physicalToteId, PhysicalToteLifecycleState.CONSUMED_AT_P2P);
    }

    private static PlannedBag bag(
            String id,
            String serviceCentreId,
            String pharmacyId,
            InboundToteManifest manifest) {
        return new PlannedBag(
                new BagKey("prescription-" + id, 1),
                serviceCentreId,
                pharmacyId,
                "patient-" + id,
                "prescription-" + id,
                List.of("pack-" + id),
                List.of(manifest.orderSheetKey()));
    }

    private static P2pPhysicalToteAssignment assignment(
            InboundToteManifest manifest,
            P2pLineDefinition line) {
        return new P2pPhysicalToteAssignment(
                manifest.physicalToteId(),
                manifest.serviceCentreId(),
                line.lineId(),
                line.destination());
    }

    private static InboundToteManifest manifest(
            String id,
            String serviceCentreId,
            String pharmacyId,
            long sequence) {
        return new InboundToteManifest(
                new PhysicalToteId(id),
                new OrderSheetKey("order-" + id, 1),
                OrderType.FULL_PACK,
                serviceCentreId,
                List.of(new DspOrderItem(
                        "line-" + id,
                        "product-" + id,
                        1,
                        pharmacyId,
                        "patient-" + id,
                        "prescription-" + id,
                        DspOrderLineType.FULL_PACK,
                        "reference-" + id,
                        1,
                        1)),
                sequence);
    }

    private static DspSchedulerOrderState state(
            InboundToteManifest manifest,
            boolean requiresThirdParty) {
        return new DspSchedulerOrderState(
                new NotionalToteOrder(
                        manifest.orderSheetKey().orderId(),
                        "notional-" + manifest.physicalToteId().value(),
                        manifest.serviceCentreId(),
                        1,
                        manifest.orderType(),
                        manifest.items(),
                        "104".equals(manifest.serviceCentreId()) ? 999 : 998,
                        manifest.sourceSequenceNumber()),
                new RouteRequirements(
                        requiresThirdParty,
                        false,
                        false,
                        true,
                        false,
                        StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static WarehouseSchedulerSnapshot snapshot(
            List<DspSchedulerOrderState> states) {
        return new WarehouseSchedulerSnapshot(
                states, Map.of(), Set.of(), Optional.of("104"));
    }

    private static RouteSegment segment(String label, float x) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(x, 0f, 0f), new Vec3(x + 1f, 0f, 0f), false));
    }

    private record Line(
            P2pLineDefinition definition,
            RouteSegment terminal,
            StationRoutedToteArrivalQueue source,
            TipperInputQueue input,
            P2pTipperArrivalTarget target) {
    }

    private record Scenario(
            SimulationWorld world,
            List<Line> lines,
            DspP2pStickyLeaseRuntime runtime,
            PhysicalToteLifecycleLedger ledger,
            OutboundToteAllocator outboundAllocator,
            AtomicReference<WarehouseSchedulerSnapshot> schedulerSnapshot,
            InboundToteManifest firstManifest,
            InboundToteManifest secondManifest,
            InboundToteManifest reuseManifest,
            DspSchedulerOrderState firstState,
            DspSchedulerOrderState secondState,
            DspSchedulerOrderState reuseState) {
    }
}
