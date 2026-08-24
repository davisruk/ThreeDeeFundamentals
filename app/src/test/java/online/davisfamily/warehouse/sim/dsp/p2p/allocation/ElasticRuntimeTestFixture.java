package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetDefinition;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivityProbe;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pStickyArrivalBinding;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

public final class ElasticRuntimeTestFixture {

    private final SimulationWorld world = new SimulationWorld();
    private final List<P2pLineDefinition> definitions = new ArrayList<>();
    private final Map<P2pLineId, P2pLineActivityProbe> probes = new LinkedHashMap<>();
    private final List<P2pStickyArrivalBinding> bindings = new ArrayList<>();
    private final PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
    private final OutboundToteAllocator outboundAllocator = new OutboundToteAllocator(
            ledger,
            new DeterministicOutboundToteIdSource(),
            new OutputSheetAllocator(List.of()),
            new OutboundToteConfig(10));
    private final DspOperationalClock clock = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 8, 24)));

    public ElasticRuntimeTestFixture() {
        for (int index = 1; index <= 5; index++) {
            P2pLineDefinition definition = new P2pLineDefinition(
                    new P2pLineId("line-" + index),
                    new OperationalRouteDestination(StationType.P2P, "p2p-" + index));
            definitions.add(definition);
            probes.put(definition.lineId(), P2pLineActivitySnapshot::idle);
            RouteSegment terminal = segment("terminal-" + index, index * 2f);
            RouteSegment tipper = segment("tipper-" + index, index * 2f + 1f);
            terminal.connectTo(tipper);
            StationRoutedToteArrivalQueue source =
                    new StationRoutedToteArrivalQueue(definition.destination(), 1);
            bindings.add(new P2pStickyArrivalBinding(
                    definition.lineId(),
                    source,
                    new P2pArrivalRouteBinding(terminal, tipper),
                    routedTote -> new TipperTotePayload(
                            routedTote.tote(), routedTote.renderable(), 0f, Map.of()),
                    new P2pTipperArrivalTarget(
                            definition.destination(),
                            new TipperInputQueue("input-" + index, 1))));
        }
    }

    public DspP2pElasticAllocationRuntime createRuntime() {
        return createRuntime(this::supplySnapshot);
    }

    public DspP2pElasticAllocationRuntime createRuntime(
            Supplier<DspSupplySnapshot> supplySnapshotSupplier) {
        return new DspP2pElasticAllocationRuntimeFactory().create(
                world,
                definitions,
                Map.copyOf(probes),
                () -> new WarehouseSchedulerSnapshot(
                        List.of(), Map.of(), Set.of(), Optional.empty()),
                new InboundToteManifestCatalog(List.of()),
                ledger::snapshot,
                clock::initialSnapshot,
                supplySnapshotSupplier,
                timetable(),
                () -> new BagPlanningResult(List.of(), List.of(), List.of()),
                outboundAllocator,
                bindings,
                P2pElasticAllocationConfig.productionBaseline(
                        new P2pWorkloadCostConfig(
                                Duration.ofMinutes(1), Duration.ZERO, Duration.ZERO)));
    }

    public SimulationWorld world() {
        return world;
    }

    public List<P2pLineDefinition> definitions() {
        return List.copyOf(definitions);
    }

    public OperationalRouteTargetRegistry targetRegistry() {
        return new OperationalRouteTargetRegistry(definitions.stream()
                .map(definition -> new OperationalRouteEntryQueue(
                        new OperationalRouteTargetDefinition(
                                StationType.P2P,
                                definition.destination().targetId(),
                                1)))
                .toList());
    }

    private DspSupplySnapshot supplySnapshot() {
        return new DspSupplySnapshot(
                "test-supply",
                0,
                1200,
                0,
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                List.of(),
                0);
    }

    private static DspServiceCentreTimetable timetable() {
        return new DspServiceCentreTimetable(List.of(new ServiceCentreSchedule(
                "104",
                "Letchworth",
                999,
                OperationalDayTime.day0(LocalTime.of(17, 0)))));
    }

    private static RouteSegment segment(String label, float x) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(x, 0f, 0f),
                        new Vec3(x + 1f, 0f, 0f),
                        false));
    }
}
