package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseReleaseController;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivityProbe;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class DspDeadlineAwareElasticLineAllocationScenarioTest {

    private static final String EARLIER = "104";
    private static final String LATER = "108";
    private static final String WAITING = "116";

    @Test
    void shouldReallocateOnlyDrainedSurplusLinesAsDeadlineAwareDemandChanges() {
        Scenario scenario = new Scenario();

        P2pElasticAllocationSnapshot initial = scenario.allocation();
        assertEquals(List.of(EARLIER, LATER, WAITING), initial.serviceCentres().stream()
                .map(P2pServiceCentreLineDemandSnapshot::serviceCentreId)
                .toList());
        assertEquals(3, initial.require(EARLIER).desiredLines());
        assertEquals(1, initial.require(LATER).desiredLines());
        assertEquals(0, initial.require(WAITING).desiredLines());
        assertFalse(initial.require(WAITING).withinConcurrencyWindow());
        assertTrue(initial.require(WAITING).issues().contains(
                P2pElasticAllocationIssueType.OUTSIDE_CONCURRENT_SERVICE_CENTRE_WINDOW));
        assertTrue(initial.infeasible());
        assertTrue(scenario.remainingWork.get().remainingToteIds(EARLIER)
                .contains(tote("dependency-blocked")));

        P2pLineAllocationDecision affinity = scenario.allocate(
                "affinity-direct", EARLIER, "pharmacy-a", true,
                Map.of(scenario.destination(1), true));
        assertEquals(scenario.lineId(1), affinity.assignment().orElseThrow().lineId());
        assertTrue(affinity.activePharmacyAffinity());

        P2pLineAllocationDecision backpressured = scenario.allocate(
                "backpressured-direct", EARLIER, "pharmacy-c", true,
                Map.of(scenario.destination(1), false));
        assertEquals(scenario.lineId(2), backpressured.assignment().orElseThrow().lineId());

        P2pLineAllocationDecision earlierStation = scenario.allocate(
                "third-party-first", EARLIER, "pharmacy-a", false,
                scenario.allAdmissions(false));
        assertEquals(scenario.lineId(1), earlierStation.assignment().orElseThrow().lineId());
        assertTrue(earlierStation.activePharmacyAffinity());
        assertEquals(0, scenario.p2pArrivalCount());

        P2pPhysicalToteAssignment pinned = scenario.commitPinnedAssignment();
        P2pPhysicalToteAssignment pinnedBeforeShrink = scenario.registry
                .findAssignment(pinned.physicalToteId()).orElseThrow();

        scenario.shrinkEarlierDemand();
        P2pElasticAllocationSnapshot shrunk = scenario.allocation();
        assertEquals(List.of(scenario.lineId(1)),
                shrunk.require(EARLIER).feedingOwnedLineIds());
        assertEquals(List.of(scenario.lineId(2), scenario.lineId(3)),
                shrunk.require(EARLIER).drainingSurplusLineIds());

        P2pLineAllocationDecision afterShrink = scenario.allocate(
                "after-shrink", EARLIER, "pharmacy-c", true,
                scenario.allAdmissions(true));
        assertEquals(scenario.lineId(1), afterShrink.assignment().orElseThrow().lineId());

        scenario.setLineTwoBusy(true);
        scenario.world.update(0.1d);
        assertEquals(Optional.of(EARLIER), scenario.owner(2));
        assertTrue(scenario.outbound.snapshot().openToteFor(scenario.lineId(2)).isPresent());
        assertEquals(Optional.of(EARLIER), scenario.owner(3));
        assertEquals(pinnedBeforeShrink,
                scenario.registry.findAssignment(pinned.physicalToteId()).orElseThrow());

        scenario.world.update(0.1d);
        assertEquals(Optional.of(EARLIER), scenario.owner(3));
        assertEquals(pinnedBeforeShrink,
                scenario.registry.findAssignment(pinned.physicalToteId()).orElseThrow());

        scenario.setLineTwoBusy(false);
        scenario.world.update(0.1d);
        assertEquals(Optional.of(EARLIER), scenario.owner(2));
        assertTrue(scenario.outbound.snapshot().openToteFor(scenario.lineId(2)).isEmpty());
        assertEquals(OutboundToteClosureReason.SERVICE_CENTRE_CHANGED,
                scenario.outbound.snapshot().closedTotes().getFirst()
                        .closureReason().orElseThrow());

        scenario.world.update(0.1d);
        assertTrue(scenario.owner(2).isEmpty());
        assertEquals(Optional.of(EARLIER), scenario.owner(3));
        assertEquals(pinnedBeforeShrink,
                scenario.registry.findAssignment(pinned.physicalToteId()).orElseThrow());

        scenario.world.update(0.1d);
        assertEquals(Optional.of(EARLIER), scenario.owner(3));
        assertEquals(pinnedBeforeShrink,
                scenario.registry.findAssignment(pinned.physicalToteId()).orElseThrow());

        scenario.advanceTimeAndRaiseLaterDemand();
        P2pElasticAllocationSnapshot risen = scenario.allocation();
        assertEquals(2, risen.require(LATER).desiredLines());
        assertEquals(1, risen.require(LATER).additionalLineSlots());

        P2pLineAllocationDecision acquired = scenario.allocate(
                "later-acquires-surplus", LATER, "pharmacy-b", true,
                Map.of(scenario.destination(4), false));
        assertEquals(scenario.lineId(2), acquired.assignment().orElseThrow().lineId());
        scenario.acquireAndCommit(acquired.assignment().orElseThrow());
        assertEquals(Optional.of(LATER), scenario.owner(2));
        assertEquals(Optional.of(LATER), scenario.owner(4));
        assertFalse(risen.require(WAITING).withinConcurrencyWindow());

        scenario.assertOutboundOwnershipIsPure();
    }

    private static final class Scenario {
        private final List<P2pLineDefinition> definitions = definitions();
        private final P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(definitions);
        private final Map<P2pLineId, AtomicReference<P2pInputActivitySnapshot>> inputs =
                new LinkedHashMap<>();
        private final PhysicalToteLifecycleLedger lifecycle = new PhysicalToteLifecycleLedger();
        private final OutboundToteAllocator outbound = new OutboundToteAllocator(
                lifecycle,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of()),
                new OutboundToteConfig(10));
        private final AtomicReference<DspOperationalClockSnapshot> clock =
                new AtomicReference<>();
        private final AtomicReference<P2pWorkloadSnapshot> workload =
                new AtomicReference<>();
        private final AtomicReference<P2pServiceCentreWorkSnapshot> remainingWork =
                new AtomicReference<>();
        private final DspOperationalClock operationalClock = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 8, 24)));
        private final DeadlineAwareElasticP2pAllocationPlanner planner =
                new DeadlineAwareElasticP2pAllocationPlanner();
        private final DeadlineAwareElasticStickyP2pLineAllocationPolicy allocationPolicy =
                new DeadlineAwareElasticStickyP2pLineAllocationPolicy();
        private final P2pElasticAllocationConfig config =
                P2pElasticAllocationConfig.productionBaseline(
                        new P2pWorkloadCostConfig(
                                Duration.ofMinutes(1), Duration.ZERO, Duration.ZERO));
        private final SimulationWorld world = new SimulationWorld();

        private Scenario() {
            definitions.forEach(definition -> inputs.put(
                    definition.lineId(),
                    new AtomicReference<>(P2pInputActivitySnapshot.idle())));
            registry.acquireLease(lineId(1), EARLIER, P2pLineActivitySnapshot.idle());
            registry.acquireLease(lineId(2), EARLIER, P2pLineActivitySnapshot.idle());
            registry.acquireLease(lineId(3), EARLIER, P2pLineActivitySnapshot.idle());
            registry.acquireLease(lineId(4), LATER, P2pLineActivitySnapshot.idle());

            outbound.allocate(lineId(1), bag("bag-a", EARLIER, "pharmacy-a"), Duration.ZERO);
            outbound.allocate(lineId(2), bag("bag-c", EARLIER, "pharmacy-c"), Duration.ZERO);
            outbound.allocate(lineId(4), bag("bag-b", LATER, "pharmacy-b"), Duration.ZERO);

            clock.set(operationalClock.initialSnapshot());
            workload.set(workload(Duration.ofHours(30), Duration.ofHours(10),
                    Duration.ofHours(24)));
            remainingWork.set(work(
                    List.of(tote("dependency-blocked"), tote("pinned-in-transit")),
                    List.of(tote("later-work")),
                    List.of(tote("waiting-work"))));

            List<P2pLineActivityProbe> probes = definitions.stream()
                    .map(definition -> (P2pLineActivityProbe)
                            () -> activity(definition.lineId()))
                    .toList();
            world.addController(new P2pLeaseReleaseController(
                    remainingWork::get,
                    probes,
                    registry,
                    outbound,
                    new ElasticP2pLeaseRetentionPolicy(this::allocation)));
        }

        private P2pElasticAllocationSnapshot allocation() {
            return planner.create(
                    clock.get(), supply(), workload.get(), timetable(), leaseSnapshot(), config);
        }

        private P2pLineAllocationDecision allocate(
                String toteId,
                String serviceCentreId,
                String pharmacyId,
                boolean p2pFirst,
                Map<OperationalRouteDestination, Boolean> overrides) {
            Map<OperationalRouteDestination, Boolean> admissions = allAdmissions(true);
            admissions.putAll(overrides);
            return allocationPolicy.allocate(new P2pLineAllocationRequest(
                    tote(toteId),
                    serviceCentreId,
                    List.of(pharmacyId),
                    p2pFirst,
                    leaseSnapshot(),
                    admissions,
                    Optional.of(allocation())));
        }

        private P2pPhysicalToteAssignment commitPinnedAssignment() {
            P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                    tote("pinned-in-transit"), EARLIER, lineId(3), destination(3));
            registry.commitAssignment(assignment);
            return assignment;
        }

        private void acquireAndCommit(P2pPhysicalToteAssignment assignment) {
            registry.acquireLease(
                    assignment.lineId(), assignment.serviceCentreId(),
                    P2pLineActivitySnapshot.idle());
            registry.commitAssignment(assignment);
        }

        private void shrinkEarlierDemand() {
            workload.set(workload(Duration.ofHours(10), Duration.ofHours(10),
                    Duration.ofHours(24)));
        }

        private void advanceTimeAndRaiseLaterDemand() {
            clock.set(operationalClock.snapshotAt(Duration.ofHours(1)));
            workload.set(workload(Duration.ofHours(9), Duration.ofHours(18),
                    Duration.ofHours(24)));
        }

        private void setLineTwoBusy(boolean busy) {
            inputs.get(lineId(2)).set(busy
                    ? new P2pInputActivitySnapshot(1, 0, false, 0)
                    : P2pInputActivitySnapshot.idle());
        }

        private Optional<String> owner(int lineNumber) {
            return registry.ownerFor(lineId(lineNumber));
        }

        private int p2pArrivalCount() {
            return leaseSnapshot().lines().stream()
                    .mapToInt(line -> line.activity().input().stationArrivalCount())
                    .sum();
        }

        private P2pLineLeaseCatalogSnapshot leaseSnapshot() {
            Map<P2pLineId, P2pLineActivitySnapshot> activities = new LinkedHashMap<>();
            definitions.forEach(definition -> activities.put(
                    definition.lineId(), activity(definition.lineId())));
            return registry.snapshot(activities);
        }

        private P2pLineActivitySnapshot activity(P2pLineId lineId) {
            return new P2pLineActivitySnapshot(
                    inputs.get(lineId).get(),
                    P2pPackPathActivitySnapshot.idle(),
                    P2pBaggingActivitySnapshot.idle(),
                    outbound.snapshot().openToteFor(lineId));
        }

        private Map<OperationalRouteDestination, Boolean> allAdmissions(boolean admitted) {
            Map<OperationalRouteDestination, Boolean> admissions = new LinkedHashMap<>();
            definitions.forEach(definition -> admissions.put(definition.destination(), admitted));
            return admissions;
        }

        private P2pLineId lineId(int number) {
            return definitions.get(number - 1).lineId();
        }

        private OperationalRouteDestination destination(int number) {
            return definitions.get(number - 1).destination();
        }

        private void assertOutboundOwnershipIsPure() {
            OutboundAllocationSnapshot snapshot = outbound.snapshot();
            var totes = new ArrayList<>(snapshot.closedTotes());
            totes.addAll(snapshot.openTotesByLine().values());
            totes.forEach(tote -> tote.allocatedBags().forEach(allocated ->
                    assertEquals(
                            tote.serviceCentreId().orElseThrow(),
                            allocated.plannedBag().serviceCentreId())));
        }
    }

    private static List<P2pLineDefinition> definitions() {
        List<P2pLineDefinition> definitions = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            definitions.add(new P2pLineDefinition(
                    new P2pLineId("line-" + index),
                    new OperationalRouteDestination(StationType.P2P, "p2p-" + index)));
        }
        return List.copyOf(definitions);
    }

    private static P2pWorkloadSnapshot workload(
            Duration earlier,
            Duration later,
            Duration waiting) {
        return new P2pWorkloadSnapshot(List.of(
                workload(EARLIER, earlier),
                workload(LATER, later),
                workload(WAITING, waiting)));
    }

    private static P2pServiceCentreWorkloadSnapshot workload(
            String serviceCentreId,
            Duration duration) {
        return new P2pServiceCentreWorkloadSnapshot(
                serviceCentreId,
                duration.isZero() ? List.of() : List.of(tote("work-" + serviceCentreId)),
                0,
                List.of(),
                List.of(),
                duration);
    }

    private static P2pServiceCentreWorkSnapshot work(
            List<PhysicalToteId> earlier,
            List<PhysicalToteId> later,
            List<PhysicalToteId> waiting) {
        return new P2pServiceCentreWorkSnapshot(
                Map.of(EARLIER, earlier, LATER, later, WAITING, waiting), Map.of());
    }

    private static DspSupplySnapshot supply() {
        return new DspSupplySnapshot(
                "scenario-supply",
                0,
                1200,
                0,
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                List.of(
                        supplied(EARLIER, 999, Duration.ZERO),
                        supplied(LATER, 998, Duration.ofMinutes(5)),
                        supplied(WAITING, 997, Duration.ofMinutes(10))),
                0);
    }

    private static ServiceCentreSupplySnapshot supplied(
            String serviceCentreId,
            int priority,
            Duration authorizationTime) {
        return new ServiceCentreSupplySnapshot(
                serviceCentreId,
                priority,
                ServiceCentreAuthorizationState.AUTHORIZED,
                Optional.of(authorizationTime),
                0,
                0,
                0,
                0,
                Set.of(),
                List.of());
    }

    private static DspServiceCentreTimetable timetable() {
        return new DspServiceCentreTimetable(List.of(
                schedule(EARLIER, "Letchworth", 999, 17),
                schedule(LATER, "Swansea", 998, 17),
                schedule(WAITING, "Exeter", 997, 19)));
    }

    private static ServiceCentreSchedule schedule(
            String serviceCentreId,
            String name,
            int priority,
            int departureHour) {
        return new ServiceCentreSchedule(
                serviceCentreId,
                name,
                priority,
                OperationalDayTime.day0(LocalTime.of(departureHour, 0)));
    }

    private static PlannedBag bag(
            String id,
            String serviceCentreId,
            String pharmacyId) {
        String prescriptionId = "rx-" + id;
        return new PlannedBag(
                new BagKey(prescriptionId, 1),
                serviceCentreId,
                pharmacyId,
                "patient-" + id,
                prescriptionId,
                List.of("pack-" + id),
                List.of(new OrderSheetKey("order-" + id, 1)));
    }

    private static PhysicalToteId tote(String value) {
        return new PhysicalToteId(value);
    }
}
