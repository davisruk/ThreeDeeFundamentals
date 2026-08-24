package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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

class StickyP2pLineAllocationPolicyTest {
    private final StickyP2pLineAllocationPolicy policy = new StickyP2pLineAllocationPolicy();

    @Test
    void shouldSelectMatchingActivePharmacyAcrossFiveLinesDeterministically() {
        P2pLineLeaseSnapshot line1 = leased("line-1", "SC-104", Optional.of("pharmacy-other"));
        P2pLineLeaseSnapshot line2 = leased("line-2", "SC-108", Optional.of("pharmacy-1"));
        P2pLineLeaseSnapshot line3 = unleased("line-3", P2pLineActivitySnapshot.idle());
        P2pLineLeaseSnapshot line4 = leased("line-4", "SC-104", Optional.of("pharmacy-2"));
        P2pLineLeaseSnapshot line5 = leased("line-5", "SC-104", Optional.of("pharmacy-2"));
        P2pLineLeaseCatalogSnapshot catalog = catalog(line1, line2, line3, line4, line5);

        P2pLineAllocationDecision decision = policy.allocate(request(
                "physical-1", "SC-104", List.of("pharmacy-1", "pharmacy-2"),
                true, catalog, allAdmissions(catalog, true)));

        assertTrue(decision.allocated());
        assertTrue(decision.activePharmacyAffinity());
        assertEquals(line4.definition().lineId(), decision.assignment().orElseThrow().lineId());
        assertTrue(decision.blockReason().isEmpty());
    }

    @Test
    void shouldReuseSameOwnerBeforeEarlierUnleasedLine() {
        P2pLineLeaseSnapshot unleased = unleased("line-1", P2pLineActivitySnapshot.idle());
        P2pLineLeaseSnapshot sameOwner = leased("line-2", "SC-104", Optional.empty());
        P2pLineLeaseCatalogSnapshot catalog = catalog(unleased, sameOwner);

        P2pLineAllocationDecision decision = policy.allocate(request(
                "physical-1", "SC-104", List.of("pharmacy-1"),
                true, catalog, allAdmissions(catalog, true)));

        assertEquals(sameOwner.definition().lineId(), decision.assignment().orElseThrow().lineId());
        assertFalse(decision.activePharmacyAffinity());
    }

    @Test
    void shouldSkipFullPreferredDirectTargetAndUseNextCompatibleLine() {
        P2pLineLeaseSnapshot preferred = leased(
                "line-1", "SC-104", Optional.of("pharmacy-1"));
        P2pLineLeaseSnapshot fallback = leased(
                "line-2", "SC-104", Optional.empty());
        P2pLineLeaseCatalogSnapshot catalog = catalog(preferred, fallback);
        Map<OperationalRouteDestination, Boolean> admissions = allAdmissions(catalog, true);
        admissions.put(preferred.definition().destination(), false);

        P2pLineAllocationDecision decision = policy.allocate(request(
                "physical-1", "SC-104", List.of("pharmacy-1"),
                true, catalog, admissions));

        assertEquals(fallback.definition().lineId(), decision.assignment().orElseThrow().lineId());
        assertFalse(decision.activePharmacyAffinity());
    }

    @Test
    void shouldIgnoreCurrentP2pCapacityForMultiStationRoute() {
        P2pLineLeaseSnapshot preferred = leased(
                "line-1", "SC-104", Optional.of("pharmacy-1"));
        P2pLineLeaseSnapshot fallback = unleased("line-2", P2pLineActivitySnapshot.idle());
        P2pLineLeaseCatalogSnapshot catalog = catalog(preferred, fallback);

        P2pLineAllocationDecision decision = policy.allocate(request(
                "physical-1", "SC-104", List.of("pharmacy-1"),
                false, catalog, allAdmissions(catalog, false)));

        assertEquals(preferred.definition().lineId(), decision.assignment().orElseThrow().lineId());
        assertTrue(decision.activePharmacyAffinity());
    }

    @Test
    void shouldExcludeOtherOwnersAndBusyUnleasedLines() {
        P2pLineLeaseSnapshot otherOwner = leased(
                "line-1", "SC-108", Optional.of("pharmacy-1"));
        P2pLineActivitySnapshot busyInput = new P2pLineActivitySnapshot(
                new P2pInputActivitySnapshot(1, 0, false, 0),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.empty());
        P2pLineLeaseSnapshot busyUnleased = unleased("line-2", busyInput);
        P2pLineLeaseCatalogSnapshot catalog = catalog(otherOwner, busyUnleased);

        P2pLineAllocationDecision decision = policy.allocate(request(
                "physical-1", "SC-104", List.of("pharmacy-1"),
                false, catalog, allAdmissions(catalog, true)));

        assertFalse(decision.allocated());
        assertFalse(decision.activePharmacyAffinity());
        assertEquals(P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE,
                decision.blockReason().orElseThrow());
    }

    @Test
    void shouldRemainPureAndDetachRequestCollections() {
        P2pLineDefinition definition = definition("line-1");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(definition));
        P2pLineLeaseCatalogSnapshot catalog = registry.snapshot(
                Map.of(definition.lineId(), P2pLineActivitySnapshot.idle()));
        List<String> pharmacies = new ArrayList<>(List.of("pharmacy-1"));
        Map<OperationalRouteDestination, Boolean> admissions = allAdmissions(catalog, true);
        P2pLineAllocationRequest request = request(
                "physical-1", "SC-104", pharmacies, true, catalog, admissions);
        pharmacies.clear();
        admissions.clear();

        P2pLineAllocationDecision decision = policy.allocate(request);

        assertEquals(definition.lineId(), decision.assignment().orElseThrow().lineId());
        assertTrue(registry.ownerFor(definition.lineId()).isEmpty());
        assertTrue(registry.findAssignment(new PhysicalToteId("physical-1")).isEmpty());
        assertEquals(List.of("pharmacy-1"), request.pharmacyIds());
        assertThrows(UnsupportedOperationException.class,
                () -> request.routeAdmissionByDestination().clear());
    }

    @Test
    void shouldValidateAllocationContracts() {
        P2pLineLeaseSnapshot line = unleased("line-1", P2pLineActivitySnapshot.idle());
        P2pLineLeaseCatalogSnapshot catalog = catalog(line);
        assertThrows(IllegalArgumentException.class, () -> policy.allocate(null));
        assertThrows(IllegalArgumentException.class, () -> request(
                "physical-1", "SC-104", List.of("pharmacy-1", "pharmacy-1"),
                true, catalog, allAdmissions(catalog, true)));
        assertThrows(IllegalArgumentException.class, () -> request(
                "physical-1", "SC-104", List.of("pharmacy-1"),
                true, catalog, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationDecision(
                Optional.empty(), true,
                Optional.of(P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE)));
        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationDecision(
                Optional.empty(), false, Optional.empty()));
    }

    private static P2pLineAllocationRequest request(
            String physicalToteId,
            String serviceCentreId,
            List<String> pharmacyIds,
            boolean p2pFirst,
            P2pLineLeaseCatalogSnapshot catalog,
            Map<OperationalRouteDestination, Boolean> admissions) {
        return new P2pLineAllocationRequest(
                new PhysicalToteId(physicalToteId),
                serviceCentreId,
                pharmacyIds,
                p2pFirst,
                catalog,
                admissions);
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
