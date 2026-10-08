package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentrePolicySnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperatingPhase;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class WholeServiceCentreP2pAllocationPlannerTest {
    static final List<P2pLineDefinition> LINES = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new P2pLineDefinition(new P2pLineId("line-" + i),
                    new OperationalRouteDestination(StationType.P2P, "target-" + i))).toList();

    @Test
    void shouldReportAllAvailableCapacityWithoutDeadlineInfeasibilityAndReuseMetadata() {
        var planner = new WholeServiceCentreP2pAllocationPlanner();
        var leases = idleCatalog();
        var releases = releases(3, 2, Map.of());
        var first = planner.create(clock(6), supply(true), workload(Duration.ofSeconds(1)),
                timetable(17), leases, releases, Duration.ofHours(1));
        var later = planner.create(clock(21), supply(true), workload(Duration.ofDays(10)),
                timetable(7), leases, releases, Duration.ofHours(2));
        var demand = first.require("A");
        assertEquals(5, demand.rawRequiredLines());
        assertEquals(5, demand.requiredLines());
        assertEquals(5, demand.desiredLines());
        assertEquals(5, demand.additionalLineSlots());
        assertEquals(0, demand.unmetRequiredLines());
        assertTrue(demand.feedingOwnedLineIds().isEmpty());
        assertTrue(demand.drainingSurplusLineIds().isEmpty());
        assertTrue(demand.withinConcurrencyWindow());
        assertEquals(1, first.maximumConcurrentServiceCentres());
        assertEquals(Optional.of("A"), first.wholeServiceCentrePolicy().orElseThrow().eligibleServiceCentreId());
        assertSame(first.wholeServiceCentrePolicy().orElseThrow(), later.wholeServiceCentrePolicy().orElseThrow());
        assertNotEquals(first.evaluatedAt(), later.evaluatedAt());
        assertTrue(later.require("A").deadline().latestAllowedCompletionPassed());
        assertEquals(Duration.ofDays(10), later.require("A").adjustedSingleLineWork());
        assertFalse(later.infeasible());
        assertFalse(later.require("A").infeasible());
        assertEquals(demand.desiredLines(), later.require("A").desiredLines());
        assertTrue(first.find("B").isEmpty());
        assertThrows(UnsupportedOperationException.class, () ->
                first.wholeServiceCentrePolicy().orElseThrow().availableUnleasedLineIds().clear());
    }

    @Test
    void shouldNeverSkipUnsuppliedCurrentCentreAndPauseWithoutNextLine() {
        var planner = new WholeServiceCentreP2pAllocationPlanner();
        var releases = releases(1, 1, Map.of());
        var held = planner.create(clock(6), supply(false), workload(Duration.ofHours(1)),
                timetable(17), idleCatalog(), releases, Duration.ofHours(1));
        assertTrue(held.serviceCentres().isEmpty());
        assertTrue(held.wholeServiceCentrePolicy().orElseThrow().eligibleServiceCentreId().isEmpty());
        var foreign = new P2pLineLeaseCatalogSnapshot(LINES.stream().map(line ->
                new P2pLineLeaseSnapshot(line, Optional.of("B"), P2pLineActivitySnapshot.idle(), List.of())).toList());
        var paused = planner.create(clock(6), supply(true), workload(Duration.ofHours(1)),
                timetable(17), foreign, releases, Duration.ofHours(1));
        assertTrue(paused.wholeServiceCentrePolicy().orElseThrow().eligibleServiceCentreId().isEmpty());
        assertEquals(0, paused.require("A").desiredLines());
        assertEquals(5, paused.require("A").unmetRequiredLines());
        assertTrue(paused.issues().isEmpty());
        var oneOwned = new ArrayList<>(foreign.lines());
        oneOwned.set(2, new P2pLineLeaseSnapshot(LINES.get(2), Optional.of("A"),
                P2pLineActivitySnapshot.idle(), List.of()));
        var resumed = planner.create(clock(6), supply(true), workload(Duration.ofHours(1)),
                timetable(17), new P2pLineLeaseCatalogSnapshot(oneOwned), releases, Duration.ofHours(1));
        assertEquals(Optional.of("A"), resumed.wholeServiceCentrePolicy().orElseThrow().eligibleServiceCentreId());
        assertEquals(List.of(LINES.get(2).lineId()), resumed.require("A").feedingOwnedLineIds());
        assertEquals(1, resumed.require("A").desiredLines());
        assertNotSame(paused.wholeServiceCentrePolicy().orElseThrow(), resumed.wholeServiceCentrePolicy().orElseThrow());
    }

    @Test
    void shouldInvalidateOnlyChangedMetadataFactsAndNotPublishRetiredDemand() {
        var planner = new WholeServiceCentreP2pAllocationPlanner();
        var releases = releases(1, 1, Map.of());
        var first = plan(planner, idleCatalog(), releases);
        var equalDistinct = plan(planner, idleCatalog(), releases);
        assertSame(first.wholeServiceCentrePolicy().orElseThrow(), equalDistinct.wholeServiceCentrePolicy().orElseThrow());
        var replacedRelease = plan(planner, idleCatalog(), releases(1, 1, Map.of()));
        assertNotSame(first.wholeServiceCentrePolicy().orElseThrow(), replacedRelease.wholeServiceCentrePolicy().orElseThrow());
        var lines = new ArrayList<>(idleCatalog().lines());
        lines.set(0, new P2pLineLeaseSnapshot(LINES.get(0), Optional.of("A"),
                P2pLineActivitySnapshot.idle(), List.of()));
        var changedLines = plan(planner, new P2pLineLeaseCatalogSnapshot(lines), releases);
        assertNotSame(replacedRelease.wholeServiceCentrePolicy().orElseThrow(), changedLines.wholeServiceCentrePolicy().orElseThrow());
        assertEquals(4, changedLines.wholeServiceCentrePolicy().orElseThrow().availableUnleasedLineIds().size());
        var retired = plan(planner, new P2pLineLeaseCatalogSnapshot(lines), releases(0, 1, Map.of()));
        assertEquals(List.of("B"), retired.serviceCentres().stream().map(P2pServiceCentreLineDemandSnapshot::serviceCentreId).toList());
        assertEquals(4, retired.require("B").desiredLines());
        var done = plan(planner, idleCatalog(), releases(0, 0, Map.of()));
        assertTrue(done.serviceCentres().isEmpty());
        assertTrue(done.wholeServiceCentrePolicy().orElseThrow().eligibleServiceCentreId().isEmpty());
    }

    @Test
    void shouldGateAdaptedWithoutFabricatingZeroWorkDemandAndValidateAuthorization() {
        var planner = new WholeServiceCentreP2pAllocationPlanner();
        var emptyWork = new P2pWorkloadSnapshot(List.of(
                new P2pServiceCentreWorkloadSnapshot("A", List.of(), 0, List.of(), List.of(), Duration.ZERO)));
        var allocation = planner.create(clock(6), supply(true), emptyWork, timetable(17),
                idleCatalog(), releases(1, 0, Map.of()), Duration.ofHours(1));
        assertTrue(allocation.serviceCentres().isEmpty());
        assertEquals(Optional.of("A"), allocation.wholeServiceCentrePolicy().orElseThrow().eligibleServiceCentreId());
        var noP2p = planner.create(clock(6), supply(true), P2pWorkloadSnapshot.empty(), timetable(17),
                idleCatalog(), releases(1, 0, Map.of()), Duration.ofHours(1));
        assertTrue(noP2p.serviceCentres().isEmpty());
        assertEquals(Optional.of("A"), noP2p.wholeServiceCentrePolicy().orElseThrow().eligibleServiceCentreId());
        var malformed = new DspSupplySnapshot("test", 0, 10, 0, Optional.empty(), Optional.empty(),
                Set.of(), List.of(new ServiceCentreSupplySnapshot("A", 999,
                        ServiceCentreAuthorizationState.AUTHORIZED, Optional.empty(),
                        0, 0, 0, 0, Set.of(), List.of())), 0);
        assertThrows(IllegalArgumentException.class, () -> planner.create(clock(6), malformed,
                emptyWork, timetable(17), idleCatalog(), releases(1, 0, Map.of()), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> planner.create(null, supply(true),
                emptyWork, timetable(17), idleCatalog(), releases(1, 0, Map.of()), Duration.ofHours(1)));
    }

    @Test
    void shouldKeepLegacyConstructorAndRejectMixedOrMalformedMetadata() {
        var legacy = new P2pElasticAllocationSnapshot(DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES.name(),
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED, clock(6).businessDateTime(),
                LINES.stream().map(P2pLineDefinition::lineId).toList(), 2, List.of(), List.of());
        assertTrue(legacy.wholeServiceCentrePolicy().isEmpty());
        var metadata = new WholeServiceCentrePolicySnapshot(releases(1, 1, Map.of()), Optional.of("A"),
                List.of(LINES.get(0).lineId()));
        assertThrows(IllegalArgumentException.class, () -> envelope(legacy.profileId(), Optional.of(metadata)));
        assertThrows(IllegalArgumentException.class, () -> envelope(DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER.name(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> envelope("UNKNOWN", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentrePolicySnapshot(
                metadata.releases(), Optional.of("B"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentrePolicySnapshot(
                metadata.releases(), Optional.empty(), List.of(LINES.get(0).lineId(), LINES.get(0).lineId())));
        assertThrows(IllegalArgumentException.class, () -> envelope(DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER.name(),
                Optional.of(new WholeServiceCentrePolicySnapshot(metadata.releases(), Optional.empty(), List.of(new P2pLineId("unknown"))))));
    }

    private static P2pElasticAllocationSnapshot envelope(String id, Optional<WholeServiceCentrePolicySnapshot> metadata) {
        return new P2pElasticAllocationSnapshot(id, P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                clock(6).businessDateTime(), LINES.stream().map(P2pLineDefinition::lineId).toList(),
                1, List.of(), List.of(), metadata);
    }

    static P2pElasticAllocationSnapshot plan(WholeServiceCentreP2pAllocationPlanner planner,
            P2pLineLeaseCatalogSnapshot leases, WholeServiceCentreReleaseSnapshot releases) {
        return planner.create(clock(6), supply(true), workload(Duration.ofHours(1)), timetable(17),
                leases, releases, Duration.ofHours(1));
    }

    static P2pLineLeaseCatalogSnapshot idleCatalog() {
        return new P2pLineLeaseCatalogSnapshot(LINES.stream().map(line -> new P2pLineLeaseSnapshot(
                line, Optional.empty(), P2pLineActivitySnapshot.idle(), List.of())).toList());
    }

    static WholeServiceCentreReleaseSnapshot releases(int a, int b, Map<P2pLineId, Integer> committedA) {
        Map<P2pLineId, Integer> countsA = new LinkedHashMap<>();
        Map<P2pLineId, Integer> countsB = new LinkedHashMap<>();
        LINES.forEach(line -> {
            countsA.put(line.lineId(), committedA.getOrDefault(line.lineId(), 0));
            countsB.put(line.lineId(), 0);
        });
        return new WholeServiceCentreReleaseSnapshot(0, List.of("A", "B"),
                a > 0 ? Optional.of("A") : b > 0 ? Optional.of("B") : Optional.empty(),
                Map.of("A", a, "B", b), Map.of("A", 0, "B", 0), Map.of("A", countsA, "B", countsB));
    }

    static DspSupplySnapshot supply(boolean authorizeA) {
        return new DspSupplySnapshot("test", 0, 10, 0, Optional.empty(), Optional.empty(), Set.of(),
                List.of(centre("A", 999, authorizeA), centre("B", 998, true)), 0);
    }

    private static ServiceCentreSupplySnapshot centre(String id, int priority, boolean authorized) {
        return new ServiceCentreSupplySnapshot(id, priority,
                authorized ? ServiceCentreAuthorizationState.PRELOADED : ServiceCentreAuthorizationState.HELD_UPSTREAM,
                authorized ? Optional.of(Duration.ZERO) : Optional.empty(), 0, 0, 0, 0, Set.of(), List.of());
    }

    static P2pWorkloadSnapshot workload(Duration estimate) {
        return new P2pWorkloadSnapshot(List.of("A", "B").stream().map(id ->
                new P2pServiceCentreWorkloadSnapshot(id, List.of(new PhysicalToteId("remaining-" + id)),
                        0, List.of(), List.of(), estimate)).toList());
    }

    static DspServiceCentreTimetable timetable(int hour) {
        return new DspServiceCentreTimetable(List.of(
                new ServiceCentreSchedule("A", "A", 999, OperationalDayTime.day0(LocalTime.of(hour, 0))),
                new ServiceCentreSchedule("B", "B", 998, OperationalDayTime.day0(LocalTime.of(hour, 0)))));
    }

    static DspOperationalClockSnapshot clock(int hour) {
        LocalDate date = LocalDate.of(2026, 9, 2);
        var dayTime = OperationalDayTime.day0(LocalTime.of(hour, 0));
        return new DspOperationalClockSnapshot(Duration.ofHours(hour - 6), date, dayTime.onOperatingDate(date),
                dayTime, DspOperatingPhase.NORMAL_OPERATIONS, date.atTime(22, 0), date.plusDays(1).atStartOfDay());
    }
}
