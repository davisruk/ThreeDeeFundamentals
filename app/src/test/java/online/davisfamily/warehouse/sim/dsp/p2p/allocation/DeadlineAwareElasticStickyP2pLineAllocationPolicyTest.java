package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationBlockReason;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;

class DeadlineAwareElasticStickyP2pLineAllocationPolicyTest {

    private static final String SERVICE_CENTRE = "SC-104";
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 24, 6, 0);

    private final DeadlineAwareElasticStickyP2pLineAllocationPolicy policy =
            new DeadlineAwareElasticStickyP2pLineAllocationPolicy();

    @Test
    void shouldPreservePharmacyAffinityAndConfiguredOrderWithinFeedingLines() {
        P2pLineLeaseCatalogSnapshot catalog = catalog(
                leased("line-1", SERVICE_CENTRE, Optional.of("other")),
                leased("line-2", SERVICE_CENTRE, Optional.of("pharmacy-1")),
                leased("line-3", SERVICE_CENTRE, Optional.of("pharmacy-1")));
        P2pElasticAllocationSnapshot allocation = allocation(
                catalog, 3, List.of("line-1", "line-2", "line-3"), List.of());

        P2pLineAllocationDecision decision = policy.allocate(request(
                catalog, allocation, true, allAdmissions(catalog, true)));

        assertEquals(new P2pLineId("line-2"), decision.assignment().orElseThrow().lineId());
        assertTrue(decision.activePharmacyAffinity());
    }

    @Test
    void shouldNeverFeedDrainingSurplusLine() {
        P2pLineLeaseCatalogSnapshot catalog = catalog(
                leased("line-1", SERVICE_CENTRE, Optional.empty()),
                leased("line-2", SERVICE_CENTRE, Optional.of("pharmacy-1")));
        P2pElasticAllocationSnapshot allocation = allocation(
                catalog, 1, List.of("line-1"), List.of("line-2"));
        Map<OperationalRouteDestination, Boolean> admissions = allAdmissions(catalog, true);
        admissions.put(catalog.lines().get(0).definition().destination(), false);

        P2pLineAllocationDecision decision = policy.allocate(request(
                catalog, allocation, true, admissions));

        assertFalse(decision.allocated());
        assertEquals(P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE,
                decision.blockReason().orElseThrow());
    }

    @Test
    void shouldAcquireOnlyQuiescentUnleasedLineWithinAdditionalSlotBudget() {
        P2pLineActivitySnapshot busy = new P2pLineActivitySnapshot(
                new P2pInputActivitySnapshot(1, 0, false, 0),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.empty());
        P2pLineLeaseCatalogSnapshot catalog = catalog(
                unleased("line-1", busy),
                unleased("line-2", P2pLineActivitySnapshot.idle()),
                leased("line-3", "SC-108", Optional.empty()));
        P2pElasticAllocationSnapshot allocation = allocation(
                catalog, 1, List.of(), List.of());

        P2pLineAllocationDecision decision = policy.allocate(request(
                catalog, allocation, true, allAdmissions(catalog, true)));

        assertEquals(new P2pLineId("line-2"), decision.assignment().orElseThrow().lineId());
        assertFalse(decision.activePharmacyAffinity());
    }

    @Test
    void shouldDistinguishNoBudgetFromNoPhysicallyCompatibleLine() {
        P2pLineLeaseCatalogSnapshot catalog = catalog(
                unleased("line-1", P2pLineActivitySnapshot.idle()));
        P2pElasticAllocationSnapshot noBudget = allocation(
                catalog, 0, List.of(), List.of());
        P2pLineAllocationDecision budgetDecision = policy.allocate(request(
                catalog, noBudget, true, allAdmissions(catalog, true)));
        assertEquals(P2pLineAllocationBlockReason.NO_ELASTIC_LINE_BUDGET,
                budgetDecision.blockReason().orElseThrow());

        P2pElasticAllocationSnapshot withBudget = allocation(
                catalog, 1, List.of(), List.of());
        P2pLineAllocationDecision physicalDecision = policy.allocate(request(
                catalog, withBudget, true, allAdmissions(catalog, false)));
        assertEquals(P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE,
                physicalDecision.blockReason().orElseThrow());
    }

    @Test
    void shouldIgnoreCurrentP2pAdmissionForEarlierStationRoute() {
        P2pLineLeaseCatalogSnapshot catalog = catalog(
                leased("line-1", SERVICE_CENTRE, Optional.of("pharmacy-1")));
        P2pElasticAllocationSnapshot allocation = allocation(
                catalog, 1, List.of("line-1"), List.of());

        P2pLineAllocationDecision decision = policy.allocate(request(
                catalog, allocation, false, allAdmissions(catalog, false)));

        assertTrue(decision.allocated());
        assertEquals(new P2pLineId("line-1"), decision.assignment().orElseThrow().lineId());
    }

    @Test
    void shouldFallBackFromClosedPreferredDirectDestination() {
        P2pLineLeaseCatalogSnapshot catalog = catalog(
                leased("line-1", SERVICE_CENTRE, Optional.of("pharmacy-1")),
                leased("line-2", SERVICE_CENTRE, Optional.empty()));
        P2pElasticAllocationSnapshot allocation = allocation(
                catalog, 2, List.of("line-1", "line-2"), List.of());
        Map<OperationalRouteDestination, Boolean> admissions = allAdmissions(catalog, true);
        admissions.put(catalog.lines().get(0).definition().destination(), false);

        P2pLineAllocationDecision decision = policy.allocate(request(
                catalog, allocation, true, admissions));

        assertEquals(new P2pLineId("line-2"), decision.assignment().orElseThrow().lineId());
        assertFalse(decision.activePharmacyAffinity());
    }

    @Test
    void shouldRequireMatchingElasticSnapshotButRetainLegacyRequestConstructor() {
        P2pLineLeaseCatalogSnapshot catalog = catalog(
                unleased("line-1", P2pLineActivitySnapshot.idle()));
        P2pLineAllocationRequest legacyRequest = new P2pLineAllocationRequest(
                new PhysicalToteId("physical-legacy"),
                SERVICE_CENTRE,
                List.of("pharmacy-1"),
                true,
                catalog,
                allAdmissions(catalog, true));

        assertTrue(legacyRequest.elasticAllocation().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> policy.allocate(legacyRequest));

        P2pLineLeaseCatalogSnapshot differentCatalog = catalog(
                unleased("different", P2pLineActivitySnapshot.idle()));
        P2pElasticAllocationSnapshot allocation = allocation(
                differentCatalog, 1, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequest(
                new PhysicalToteId("physical-1"),
                SERVICE_CENTRE,
                List.of("pharmacy-1"),
                true,
                catalog,
                allAdmissions(catalog, true),
                Optional.of(allocation)));
    }

    private static P2pLineAllocationRequest request(
            P2pLineLeaseCatalogSnapshot catalog,
            P2pElasticAllocationSnapshot allocation,
            boolean p2pFirst,
            Map<OperationalRouteDestination, Boolean> admissions) {
        return new P2pLineAllocationRequest(
                new PhysicalToteId("physical-1"),
                SERVICE_CENTRE,
                List.of("pharmacy-1"),
                p2pFirst,
                catalog,
                admissions,
                Optional.of(allocation));
    }

    private static P2pElasticAllocationSnapshot allocation(
            P2pLineLeaseCatalogSnapshot catalog,
            int desiredLines,
            List<String> feedingLineIds,
            List<String> drainingLineIds) {
        List<P2pLineId> feeding = feedingLineIds.stream().map(P2pLineId::new).toList();
        List<P2pLineId> draining = drainingLineIds.stream().map(P2pLineId::new).toList();
        P2pServiceCentreWorkloadSnapshot workload = new P2pServiceCentreWorkloadSnapshot(
                SERVICE_CENTRE,
                List.of(new PhysicalToteId("remaining-1")),
                0,
                List.of(),
                List.of(),
                Duration.ofHours(1));
        ServiceCentreDeadlineSnapshot deadline = new ServiceCentreDeadlineSnapshot(
                SERVICE_CENTRE,
                "Letchworth",
                999,
                EVALUATED_AT,
                EVALUATED_AT.plusHours(11),
                EVALUATED_AT.plusHours(10),
                EVALUATED_AT.plusHours(10),
                EVALUATED_AT.plusHours(10),
                Duration.ofHours(10),
                false,
                false);
        P2pServiceCentreLineDemandSnapshot demand =
                new P2pServiceCentreLineDemandSnapshot(
                        SERVICE_CENTRE,
                        999,
                        Duration.ZERO,
                        deadline,
                        workload,
                        Duration.ofHours(1),
                        1,
                        1,
                        desiredLines,
                        feeding,
                        draining,
                        Math.max(0, desiredLines - feeding.size()),
                        Math.max(0, 1 - desiredLines),
                        true,
                        List.of());
        return new P2pElasticAllocationSnapshot(
                P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                EVALUATED_AT,
                catalog.lines().stream().map(line -> line.definition().lineId()).toList(),
                Math.min(2, catalog.lines().size()),
                List.of(demand),
                List.of());
    }

    private static P2pLineLeaseCatalogSnapshot catalog(P2pLineLeaseSnapshot... lines) {
        return new P2pLineLeaseCatalogSnapshot(List.of(lines));
    }

    private static P2pLineLeaseSnapshot leased(
            String lineId,
            String serviceCentreId,
            Optional<String> activePharmacyId) {
        P2pLineDefinition definition = definition(lineId);
        P2pLineActivitySnapshot activity = activePharmacyId
                .map(pharmacyId -> new P2pLineActivitySnapshot(
                        P2pInputActivitySnapshot.idle(),
                        P2pPackPathActivitySnapshot.idle(),
                        P2pBaggingActivitySnapshot.idle(),
                        Optional.of(new OutboundToteSnapshot(
                                new PhysicalToteId("outbound-" + lineId),
                                definition.lineId(),
                                Optional.of(serviceCentreId),
                                Optional.of(pharmacyId),
                                10,
                                List.of(),
                                Optional.empty()))))
                .orElseGet(P2pLineActivitySnapshot::idle);
        return new P2pLineLeaseSnapshot(
                definition, Optional.of(serviceCentreId), activity, List.of());
    }

    private static P2pLineLeaseSnapshot unleased(
            String lineId,
            P2pLineActivitySnapshot activity) {
        return new P2pLineLeaseSnapshot(
                definition(lineId), Optional.empty(), activity, List.of());
    }

    private static P2pLineDefinition definition(String lineId) {
        return new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
    }

    private static Map<OperationalRouteDestination, Boolean> allAdmissions(
            P2pLineLeaseCatalogSnapshot catalog,
            boolean admissible) {
        Map<OperationalRouteDestination, Boolean> admissions = new LinkedHashMap<>();
        catalog.lines().forEach(line -> admissions.put(line.definition().destination(), admissible));
        return admissions;
    }
}
