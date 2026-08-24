package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class DeadlineAwareElasticP2pAllocationPlannerTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 24);
    private static final P2pWorkloadCostConfig COSTS = new P2pWorkloadCostConfig(
            Duration.ofMinutes(1), Duration.ZERO, Duration.ZERO);

    private final DeadlineAwareElasticP2pAllocationPlanner planner =
            new DeadlineAwareElasticP2pAllocationPlanner();
    private final DspOperationalClock clock = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(OPERATING_DATE));

    @Test
    void shouldAllocateSequentialDemandInAuthorizationOrderAndExcludeThirdCentre() {
        List<Centre> centres = List.of(
                centre("104", 999, Duration.ofMinutes(2), Duration.ofHours(40), 17),
                centre("108", 998, Duration.ofMinutes(1), Duration.ofHours(20), 17),
                centre("116", 997, Duration.ofMinutes(3), Duration.ofHours(10), 17));

        P2pLineLeaseCatalogSnapshot leases = catalog(
                unleased("line-1"),
                unleased("line-2"),
                unleased("line-3"),
                unleased("line-4"),
                leased("line-5", "116", false));
        P2pElasticAllocationSnapshot result = create(
                clock.initialSnapshot(), centres, leases, baseline());

        assertEquals(List.of("108", "104", "116"), result.serviceCentres().stream()
                .map(P2pServiceCentreLineDemandSnapshot::serviceCentreId)
                .toList());
        assertEquals(2, result.require("108").desiredLines());
        assertEquals(3, result.require("104").desiredLines());
        assertEquals(0, result.require("116").desiredLines());
        assertFalse(result.require("116").withinConcurrencyWindow());
        assertTrue(result.require("116").issues().contains(
                P2pElasticAllocationIssueType.OUTSIDE_CONCURRENT_SERVICE_CENTRE_WINDOW));
        assertTrue(result.require("116").issues().contains(
                P2pElasticAllocationIssueType.LEASE_OWNER_OUTSIDE_ACTIVE_WINDOW));
        assertEquals(List.of(new P2pLineId("line-5")),
                result.require("116").drainingSurplusLineIds());
        assertEquals(2, result.totalUnmetRequiredLines());
    }

    @Test
    void shouldResolveEqualAuthorizationByPriorityThenServiceCentreId() {
        List<Centre> centres = List.of(
                centre("B", 900, Duration.ZERO, Duration.ofHours(1), 17),
                centre("C", 901, Duration.ZERO, Duration.ofHours(1), 17),
                centre("A", 900, Duration.ZERO, Duration.ofHours(1), 17));

        P2pElasticAllocationSnapshot result = create(
                clock.initialSnapshot(), centres, catalog(), baseline());

        assertEquals(List.of("C", "A", "B"), result.serviceCentres().stream()
                .map(P2pServiceCentreLineDemandSnapshot::serviceCentreId)
                .toList());
    }

    @Test
    void shouldReportDemandAboveFiveAndPostDeadlineWithoutHidingClamping() {
        Centre centre = centre("104", 999, Duration.ZERO, Duration.ofHours(60), 17);

        P2pServiceCentreLineDemandSnapshot normal = create(
                clock.initialSnapshot(), List.of(centre), catalog(), baseline()).require("104");
        assertEquals(6, normal.rawRequiredLines());
        assertEquals(5, normal.requiredLines());
        assertTrue(normal.issues().contains(
                P2pElasticAllocationIssueType.DEMAND_EXCEEDS_LINE_CAPACITY));

        P2pServiceCentreLineDemandSnapshot late = create(
                clock.snapshotAt(Duration.ofHours(10)),
                List.of(centre),
                catalog(),
                baseline()).require("104");
        assertEquals(5, late.rawRequiredLines());
        assertEquals(5, late.requiredLines());
        assertTrue(late.issues().contains(
                P2pElasticAllocationIssueType.LATEST_ALLOWED_COMPLETION_PASSED));
    }

    @Test
    void shouldUseCeilingPermilleArithmeticAndMinimumReservation() {
        Centre centre = centre("104", 999, Duration.ZERO, Duration.ofHours(4), 17);
        P2pElasticAllocationConfig config = new P2pElasticAllocationConfig(
                5, 2, 2, 1250, 500, Duration.ofHours(1), COSTS);

        P2pServiceCentreLineDemandSnapshot demand = create(
                clock.initialSnapshot(), List.of(centre), catalog(), config).require("104");

        assertEquals(Duration.ofHours(10), demand.adjustedSingleLineWork());
        assertEquals(1, demand.requiredLines());
        assertEquals(2, demand.desiredLines());
        assertEquals(2, demand.additionalLineSlots());
    }

    @Test
    void shouldPreferOpenOwnedLineAndClassifySurplusWithoutMutatingLeases() {
        Centre centre = centre("104", 999, Duration.ZERO, Duration.ofHours(10), 17);
        P2pLineLeaseCatalogSnapshot leases = catalog(
                leased("line-1", "104", false),
                leased("line-2", "104", true),
                leased("line-3", "104", false),
                unleased("line-4"),
                unleased("line-5"));

        P2pServiceCentreLineDemandSnapshot demand = create(
                clock.initialSnapshot(), List.of(centre), leases, baseline()).require("104");

        assertEquals(List.of(new P2pLineId("line-2")), demand.feedingOwnedLineIds());
        assertEquals(List.of(new P2pLineId("line-1"), new P2pLineId("line-3")),
                demand.drainingSurplusLineIds());
        assertEquals(3, demand.ownedLineCount());
        assertEquals(0, demand.additionalLineSlots());
        assertEquals(Optional.of("104"), leases.lines().get(0).serviceCentreId());
    }

    @Test
    void shouldExposeRisingDemandAsAdditionalSlotsAndOmitCentresWithoutWork() {
        Centre noWork = centre("108", 998, Duration.ZERO, Duration.ZERO, 17);
        Centre low = centre("104", 999, Duration.ZERO, Duration.ofHours(10), 17);
        P2pLineLeaseCatalogSnapshot leases = catalog(
                leased("line-1", "104", false),
                unleased("line-2"),
                unleased("line-3"),
                unleased("line-4"),
                unleased("line-5"));

        P2pElasticAllocationSnapshot lowResult = create(
                clock.initialSnapshot(), List.of(low, noWork), leases, baseline());
        assertTrue(lowResult.find("108").isEmpty());
        assertEquals(0, lowResult.require("104").additionalLineSlots());

        Centre high = centre("104", 999, Duration.ZERO, Duration.ofHours(30), 17);
        P2pServiceCentreLineDemandSnapshot rising = create(
                clock.initialSnapshot(), List.of(high, noWork), leases, baseline()).require("104");
        assertEquals(3, rising.desiredLines());
        assertEquals(2, rising.additionalLineSlots());
    }

    @Test
    void shouldIdentifyUncalibratedProfileAndRejectInvalidInputs() {
        P2pElasticAllocationSnapshot result = create(
                clock.initialSnapshot(),
                List.of(centre("104", 999, Duration.ZERO, Duration.ofHours(1), 17)),
                catalog(),
                baseline());

        assertEquals(P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                result.profileId());
        assertEquals(P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                result.calibrationStatus());
        assertFalse(result.infeasible());
        assertThrows(IllegalArgumentException.class, () -> new P2pElasticAllocationConfig(
                5, 6, 1, 1000, 1000, Duration.ofHours(1), COSTS));
        assertThrows(IllegalArgumentException.class, () -> new P2pElasticAllocationConfig(
                5, 2, 1, 1000, 1001, Duration.ofHours(1), COSTS));
    }

    private P2pElasticAllocationSnapshot create(
            DspOperationalClockSnapshot clockSnapshot,
            List<Centre> centres,
            P2pLineLeaseCatalogSnapshot leases,
            P2pElasticAllocationConfig config) {
        return planner.create(
                clockSnapshot,
                supply(centres),
                workload(centres),
                timetable(centres),
                leases,
                config);
    }

    private static P2pElasticAllocationConfig baseline() {
        return P2pElasticAllocationConfig.productionBaseline(COSTS);
    }

    private static Centre centre(
            String id,
            int priority,
            Duration authorizationTime,
            Duration work,
            int departureHour) {
        return new Centre(id, priority, authorizationTime, work, departureHour);
    }

    private static DspSupplySnapshot supply(List<Centre> centres) {
        List<ServiceCentreSupplySnapshot> serviceCentres = centres.stream()
                .map(centre -> new ServiceCentreSupplySnapshot(
                        centre.id(),
                        centre.priority(),
                        ServiceCentreAuthorizationState.AUTHORIZED,
                        Optional.of(centre.authorizationTime()),
                        0,
                        0,
                        0,
                        0,
                        Set.of(),
                        List.of()))
                .toList();
        return new DspSupplySnapshot(
                "test-supply",
                0,
                1200,
                0,
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                serviceCentres,
                0);
    }

    private static P2pWorkloadSnapshot workload(List<Centre> centres) {
        return new P2pWorkloadSnapshot(centres.stream()
                .map(centre -> new P2pServiceCentreWorkloadSnapshot(
                        centre.id(),
                        centre.work().isZero()
                                ? List.of()
                                : List.of(new PhysicalToteId("tote-" + centre.id())),
                        0,
                        List.of(),
                        List.of(),
                        centre.work()))
                .toList());
    }

    private static DspServiceCentreTimetable timetable(List<Centre> centres) {
        return new DspServiceCentreTimetable(centres.stream()
                .map(centre -> new ServiceCentreSchedule(
                        centre.id(),
                        "Centre " + centre.id(),
                        centre.priority(),
                        OperationalDayTime.day0(LocalTime.of(centre.departureHour(), 0))))
                .toList());
    }

    private static P2pLineLeaseCatalogSnapshot catalog(P2pLineLeaseSnapshot... suppliedLines) {
        if (suppliedLines.length > 0) {
            return new P2pLineLeaseCatalogSnapshot(List.of(suppliedLines));
        }
        return catalog(
                unleased("line-1"),
                unleased("line-2"),
                unleased("line-3"),
                unleased("line-4"),
                unleased("line-5"));
    }

    private static P2pLineLeaseSnapshot unleased(String lineId) {
        return new P2pLineLeaseSnapshot(
                definition(lineId), Optional.empty(), P2pLineActivitySnapshot.idle(), List.of());
    }

    private static P2pLineLeaseSnapshot leased(
            String lineId,
            String serviceCentreId,
            boolean openOutput) {
        P2pLineDefinition definition = definition(lineId);
        P2pLineActivitySnapshot activity = openOutput
                ? new P2pLineActivitySnapshot(
                        P2pInputActivitySnapshot.idle(),
                        P2pPackPathActivitySnapshot.idle(),
                        P2pBaggingActivitySnapshot.idle(),
                        Optional.of(new OutboundToteSnapshot(
                                new PhysicalToteId("outbound-" + lineId),
                                definition.lineId(),
                                Optional.of(serviceCentreId),
                                Optional.of("pharmacy-1"),
                                10,
                                List.of(),
                                Optional.empty())))
                : P2pLineActivitySnapshot.idle();
        return new P2pLineLeaseSnapshot(
                definition, Optional.of(serviceCentreId), activity, List.of());
    }

    private static P2pLineDefinition definition(String lineId) {
        return new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
    }

    private record Centre(
            String id,
            int priority,
            Duration authorizationTime,
            Duration work,
            int departureHour) {
    }
}
