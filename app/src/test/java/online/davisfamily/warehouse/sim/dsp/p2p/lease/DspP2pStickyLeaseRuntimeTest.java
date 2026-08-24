package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
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
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

class DspP2pStickyLeaseRuntimeTest {

    @Test
    void shouldComposeFiveOrderedLinesAndExposeStrictCommitAndInspection() {
        Fixture fixture = fixture();

        DspP2pStickyLeaseRuntime runtime = new DspP2pStickyLeaseRuntimeFactory().create(
                fixture.world(),
                fixture.definitions(),
                fixture.probes(),
                fixture::schedulerSnapshot,
                fixture.manifestCatalog(),
                fixture.ledger()::snapshot,
                fixture.outboundAllocator(),
                fixture.bindings());

        assertEquals(fixture.definitions(), runtime.lineDefinitions());
        assertThrows(UnsupportedOperationException.class,
                () -> runtime.lineDefinitions().clear());
        assertEquals(5, runtime.snapshot().lines().size());
        assertTrue(runtime.snapshot().lines().stream()
                .allMatch(line -> line.lease().activity().quiescent()));

        P2pLineDefinition selectedLine = fixture.definitions().getFirst();
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                fixture.manifest().physicalToteId(),
                fixture.manifest().serviceCentreId(),
                selectedLine.lineId(),
                selectedLine.destination());
        ReleasePhysicalToteFromOsrCommand command = new ReleasePhysicalToteFromOsrCommand(
                fixture.manifest().physicalToteId(),
                fixture.manifest().orderSheetKey(),
                fixture.manifest().serviceCentreId(),
                selectedLine.destination().targetId(),
                Optional.of(assignment));

        runtime.releaseAssignmentCommitter().prepare(command, fixture.manifest()).commit();

        DspP2pStickyLineSnapshot line = runtime.snapshot()
                .findLine(selectedLine.lineId()).orElseThrow();
        assertEquals(Optional.of("104"), line.lease().serviceCentreId());
        assertEquals(List.of(assignment), line.lease().physicalAssignments());
        assertTrue(line.lastTransition().orElseThrow().details()
                .contains("ASSIGNMENT_COMMITTED"));
        assertTrue(line.latestClosedOutboundTote().isEmpty());

        runtime.close();
        runtime.close();
        assertTrue(runtime.isClosed());
    }

    @Test
    void shouldRejectInvalidFiveLineCompositionBeforeRegisteringControllers() {
        Fixture fixture = fixture();
        AtomicInteger updates = new AtomicInteger();
        fixture.world().addController((context, dtSeconds) -> updates.incrementAndGet());

        assertThrows(IllegalArgumentException.class, () ->
                new DspP2pStickyLeaseRuntimeFactory().create(
                        fixture.world(),
                        fixture.definitions().subList(0, 4),
                        fixture.probes(),
                        fixture::schedulerSnapshot,
                        fixture.manifestCatalog(),
                        fixture.ledger()::snapshot,
                        fixture.outboundAllocator(),
                        fixture.bindings()));

        fixture.world().update(0.1d);
        assertEquals(1, updates.get());

        Map<P2pLineId, P2pLineActivityProbe> missingProbe =
                new LinkedHashMap<>(fixture.probes());
        missingProbe.remove(fixture.definitions().getLast().lineId());
        assertThrows(IllegalArgumentException.class, () ->
                new DspP2pStickyLeaseRuntimeFactory().create(
                        new SimulationWorld(),
                        fixture.definitions(),
                        missingProbe,
                        fixture::schedulerSnapshot,
                        fixture.manifestCatalog(),
                        fixture.ledger()::snapshot,
                        fixture.outboundAllocator(),
                        fixture.bindings()));

        List<P2pStickyArrivalBinding> duplicateBindings =
                new ArrayList<>(fixture.bindings());
        duplicateBindings.set(4, duplicateBindings.getFirst());
        assertThrows(IllegalArgumentException.class, () ->
                new DspP2pStickyLeaseRuntimeFactory().create(
                        new SimulationWorld(),
                        fixture.definitions(),
                        fixture.probes(),
                        fixture::schedulerSnapshot,
                        fixture.manifestCatalog(),
                        fixture.ledger()::snapshot,
                        fixture.outboundAllocator(),
                        duplicateBindings));
    }

    private static Fixture fixture() {
        List<P2pLineDefinition> definitions = new ArrayList<>();
        Map<P2pLineId, P2pLineActivityProbe> probes = new LinkedHashMap<>();
        List<P2pStickyArrivalBinding> bindings = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            P2pLineDefinition definition = new P2pLineDefinition(
                    new P2pLineId("line-" + index),
                    new OperationalRouteDestination(
                            StationType.P2P, "p2p-" + index));
            definitions.add(definition);
            probes.put(definition.lineId(), P2pLineActivitySnapshot::idle);
            RouteSegment terminal = segment("terminal-" + index, index * 2f);
            RouteSegment tipper = segment("tipper-" + index, index * 2f + 1f);
            terminal.connectTo(tipper);
            StationRoutedToteArrivalQueue source =
                    new StationRoutedToteArrivalQueue(definition.destination(), 1);
            P2pTipperArrivalTarget target = new P2pTipperArrivalTarget(
                    definition.destination(),
                    new TipperInputQueue("input-" + index, 1));
            bindings.add(new P2pStickyArrivalBinding(
                    definition.lineId(),
                    source,
                    new P2pArrivalRouteBinding(terminal, tipper),
                    routedTote -> new TipperTotePayload(
                            routedTote.tote(), routedTote.renderable(), 0f, Map.of()),
                    target));
        }
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId("physical-1"),
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("item-1", "product-1", 1)),
                1);
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(PhysicalToteRecord.inboundPack(manifest.physicalToteId()));
        OutboundToteAllocator outboundAllocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(manifest.orderSheetKey())),
                new OutboundToteConfig(10));
        return new Fixture(
                new SimulationWorld(),
                List.copyOf(definitions),
                Map.copyOf(probes),
                List.copyOf(bindings),
                manifest,
                new InboundToteManifestCatalog(List.of(manifest)),
                ledger,
                outboundAllocator);
    }

    private static RouteSegment segment(String label, float x) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(x, 0f, 0f),
                        new Vec3(x + 1f, 0f, 0f),
                        false));
    }

    private record Fixture(
            SimulationWorld world,
            List<P2pLineDefinition> definitions,
            Map<P2pLineId, P2pLineActivityProbe> probes,
            List<P2pStickyArrivalBinding> bindings,
            InboundToteManifest manifest,
            InboundToteManifestCatalog manifestCatalog,
            PhysicalToteLifecycleLedger ledger,
            OutboundToteAllocator outboundAllocator) {

        private WarehouseSchedulerSnapshot schedulerSnapshot() {
            DspOrderItem item = manifest.items().getFirst();
            NotionalToteOrder order = new NotionalToteOrder(
                    manifest.orderSheetKey().orderId(),
                    "notional-1",
                    manifest.serviceCentreId(),
                    manifest.orderSheetKey().sheetNumber(),
                    manifest.orderType(),
                    List.of(item),
                    999,
                    1);
            return new WarehouseSchedulerSnapshot(
                    List.of(new DspSchedulerOrderState(
                            order,
                            new RouteRequirements(
                                    false, false, false, true, false, StartLocation.OSR),
                            DspOrderStatus.WAITING)),
                    Map.of(),
                    Set.of(),
                    Optional.of("104"));
        }
    }
}
