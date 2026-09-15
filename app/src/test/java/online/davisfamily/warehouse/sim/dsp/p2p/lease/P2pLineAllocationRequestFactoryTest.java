package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationCalibrationStatus;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirement;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirementCatalog;

class P2pLineAllocationRequestFactoryTest {

    @Test
    void shouldValidateBatchInputsOnceAndReuseImmutableValuesAcrossCandidates() {
        P2pLineLeaseCatalogSnapshot lineCatalog = catalog("line-1");
        OperationalRouteDestination destination = lineCatalog.lines().getFirst()
                .definition().destination();
        Map<OperationalRouteDestination, Boolean> admissions = new LinkedHashMap<>();
        admissions.put(destination, true);
        Optional<P2pElasticAllocationSnapshot> elasticAllocation = Optional.of(
                elasticAllocation(lineCatalog));
        P2pBagCorrelationAssignmentSnapshot assignments =
                P2pBagCorrelationAssignmentSnapshot.empty();

        PhysicalToteId osrPhysicalToteId = new PhysicalToteId("osr-1");
        OrderSheetKey osrOrderSheetKey = new OrderSheetKey("order-osr", 1);
        P2pBagCorrelationRequirement osrRequirement =
                new P2pBagCorrelationRequirement("bag-osr", 2);
        PhysicalToteId av02PhysicalToteId = new PhysicalToteId("av02-1");
        OrderSheetKey av02OrderSheetKey = new OrderSheetKey("order-av02", 1);
        P2pBagCorrelationRequirement av02Requirement =
                new P2pBagCorrelationRequirement("bag-av02", 3);
        P2pBagCorrelationRequirementCatalog requirementCatalog =
                new P2pBagCorrelationRequirementCatalog(
                        Map.of(osrPhysicalToteId, Set.of(osrRequirement)),
                        Map.of(av02OrderSheetKey, Set.of(av02Requirement)));

        P2pLineAllocationRequestFactory factory = new P2pLineAllocationRequestFactory(
                lineCatalog,
                admissions,
                elasticAllocation,
                assignments,
                requirementCatalog);
        List<String> pharmacies = new ArrayList<>(List.of(" pharmacy-a "));

        P2pLineAllocationRequest osrRequest = factory.create(
                OperationalPhysicalToteSource.OSR,
                osrPhysicalToteId,
                osrOrderSheetKey,
                " SC-104 ",
                pharmacies,
                false);
        P2pLineAllocationRequest av02Request = factory.create(
                OperationalPhysicalToteSource.AV02,
                av02PhysicalToteId,
                av02OrderSheetKey,
                "SC-104",
                List.of("pharmacy-b"),
                true);

        assertSame(lineCatalog, osrRequest.lineCatalog());
        assertSame(lineCatalog, av02Request.lineCatalog());
        assertSame(osrRequest.routeAdmissionByDestination(),
                av02Request.routeAdmissionByDestination());
        assertSame(elasticAllocation, osrRequest.elasticAllocation());
        assertSame(elasticAllocation, av02Request.elasticAllocation());
        assertSame(assignments, osrRequest.correlationAssignmentSnapshot());
        assertSame(assignments, av02Request.correlationAssignmentSnapshot());
        assertSame(requirementCatalog.requirementsFor(osrPhysicalToteId),
                osrRequest.requiredBagCorrelations());
        assertSame(requirementCatalog.requirementsFor(av02OrderSheetKey),
                av02Request.requiredBagCorrelations());
        assertEquals(Set.of(osrRequirement), osrRequest.requiredBagCorrelations());
        assertEquals(Set.of(av02Requirement), av02Request.requiredBagCorrelations());
        assertEquals("SC-104", osrRequest.serviceCentreId());
        assertEquals(List.of("pharmacy-a"), osrRequest.pharmacyIds());
        assertNotSame(osrRequest.pharmacyIds(), av02Request.pharmacyIds());

        pharmacies.add("pharmacy-c");
        admissions.put(destination, false);
        assertEquals(List.of("pharmacy-a"), osrRequest.pharmacyIds());
        assertTrue(osrRequest.routeAdmissible(destination));
        assertTrue(av02Request.routeAdmissible(destination));
        assertThrows(UnsupportedOperationException.class,
                () -> osrRequest.routeAdmissionByDestination().clear());
    }

    @Test
    void shouldRetainPublicRequestValueSemanticsAndDefensiveCopies() {
        P2pLineLeaseCatalogSnapshot lineCatalog = catalog("line-1");
        OperationalRouteDestination destination = lineCatalog.lines().getFirst()
                .definition().destination();
        List<String> pharmacies = new ArrayList<>(List.of(" pharmacy-a "));
        Map<OperationalRouteDestination, Boolean> admissions = new LinkedHashMap<>();
        admissions.put(destination, true);
        P2pBagCorrelationRequirement requirement =
                new P2pBagCorrelationRequirement("bag-a", 1);
        PhysicalToteId physicalToteId = new PhysicalToteId("physical-1");
        P2pLineAllocationRequest request = new P2pLineAllocationRequest(
                physicalToteId,
                " SC-104 ",
                pharmacies,
                true,
                lineCatalog,
                admissions,
                Optional.empty(),
                Set.of(requirement),
                P2pBagCorrelationAssignmentSnapshot.empty());
        pharmacies.clear();
        admissions.clear();

        P2pLineAllocationRequest equivalent = new P2pLineAllocationRequest(
                physicalToteId,
                "SC-104",
                List.of("pharmacy-a"),
                true,
                lineCatalog,
                Map.of(destination, true),
                Optional.empty(),
                Set.of(requirement),
                P2pBagCorrelationAssignmentSnapshot.empty());

        assertEquals(equivalent, request);
        assertEquals(equivalent.hashCode(), request.hashCode());
        assertEquals(
                "P2pLineAllocationRequest[physicalToteId=" + physicalToteId
                        + ", serviceCentreId=SC-104, "
                        + "pharmacyIds=[pharmacy-a], p2pFirstRouteStation=true, lineCatalog="
                        + lineCatalog + ", routeAdmissionByDestination={" + destination + "=true}, "
                        + "elasticAllocation=Optional.empty, bagCorrelationRequirements=[" + requirement
                        + "], bagCorrelationAssignments="
                        + P2pBagCorrelationAssignmentSnapshot.empty() + "]",
                request.toString());
        assertEquals(List.of("pharmacy-a"), request.pharmacyIds());
        assertTrue(request.routeAdmissible(destination));
        assertThrows(UnsupportedOperationException.class,
                () -> request.requiredBagCorrelations().clear());
        assertFalse(request.equals(null));
    }

    @Test
    void shouldKeepFactoryBatchStateIndependentAcrossFactories() {
        P2pLineLeaseCatalogSnapshot lineCatalog = catalog("line-1");
        OperationalRouteDestination destination = lineCatalog.lines().getFirst()
                .definition().destination();
        Map<OperationalRouteDestination, Boolean> firstAdmissions = new LinkedHashMap<>();
        firstAdmissions.put(destination, true);
        Map<OperationalRouteDestination, Boolean> secondAdmissions = new LinkedHashMap<>();
        secondAdmissions.put(destination, true);
        P2pBagCorrelationRequirementCatalog requirementCatalog =
                P2pBagCorrelationRequirementCatalog.empty();
        P2pBagCorrelationAssignmentSnapshot assignments =
                P2pBagCorrelationAssignmentSnapshot.empty();
        P2pLineAllocationRequestFactory firstFactory = new P2pLineAllocationRequestFactory(
                lineCatalog, firstAdmissions, Optional.empty(), assignments, requirementCatalog);
        P2pLineAllocationRequestFactory secondFactory = new P2pLineAllocationRequestFactory(
                lineCatalog, secondAdmissions, Optional.empty(), assignments, requirementCatalog);

        P2pLineAllocationRequest firstRequest = firstFactory.create(
                OperationalPhysicalToteSource.OSR,
                new PhysicalToteId("physical-first"),
                new OrderSheetKey("order-first", 1),
                "SC-104",
                List.of("pharmacy-1"),
                true);
        P2pLineAllocationRequest secondRequest = secondFactory.create(
                OperationalPhysicalToteSource.OSR,
                new PhysicalToteId("physical-second"),
                new OrderSheetKey("order-second", 1),
                "SC-104",
                List.of("pharmacy-1"),
                true);

        firstAdmissions.put(destination, false);
        assertTrue(firstRequest.routeAdmissible(destination));
        assertTrue(secondRequest.routeAdmissible(destination));
        assertNotSame(firstRequest.routeAdmissionByDestination(),
                secondRequest.routeAdmissionByDestination());
    }

    @Test
    void shouldRejectFactoryInputsAndCandidatesLikePublicRequestConstruction() {
        P2pLineLeaseCatalogSnapshot lineCatalog = catalog("line-1");
        OperationalRouteDestination destination = lineCatalog.lines().getFirst()
                .definition().destination();
        Map<OperationalRouteDestination, Boolean> admissions = Map.of(destination, true);
        P2pBagCorrelationAssignmentSnapshot assignments =
                P2pBagCorrelationAssignmentSnapshot.empty();
        P2pBagCorrelationRequirementCatalog requirementCatalog =
                P2pBagCorrelationRequirementCatalog.empty();
        Optional<P2pElasticAllocationSnapshot> validElastic = Optional.of(
                elasticAllocation(lineCatalog));
        Optional<P2pElasticAllocationSnapshot> mismatchedElastic = Optional.of(
                elasticAllocation(catalog("line-2")));

        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequestFactory(
                lineCatalog, null, validElastic, assignments, requirementCatalog));
        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequest(
                new PhysicalToteId("physical-1"), "SC-104", List.of("pharmacy-1"), true,
                lineCatalog, null, validElastic, Set.of(), assignments));

        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequestFactory(
                lineCatalog, admissions, null, assignments, requirementCatalog));
        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequest(
                new PhysicalToteId("physical-1"), "SC-104", List.of("pharmacy-1"), true,
                lineCatalog, admissions, null, Set.of(), assignments));

        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequestFactory(
                lineCatalog, admissions, mismatchedElastic, assignments, requirementCatalog));
        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequest(
                new PhysicalToteId("physical-1"), "SC-104", List.of("pharmacy-1"), true,
                lineCatalog, admissions, mismatchedElastic, Set.of(), assignments));

        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequestFactory(
                lineCatalog, admissions, validElastic, null, requirementCatalog));
        assertThrows(IllegalArgumentException.class, () -> new P2pLineAllocationRequest(
                new PhysicalToteId("physical-1"), "SC-104", List.of("pharmacy-1"), true,
                lineCatalog, admissions, validElastic, Set.of(), null));

        P2pLineAllocationRequestFactory factory = new P2pLineAllocationRequestFactory(
                lineCatalog, admissions, validElastic, assignments, requirementCatalog);
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                null,
                new PhysicalToteId("physical-1"),
                new OrderSheetKey("order-1", 1),
                "SC-104",
                List.of("pharmacy-1"),
                true));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                OperationalPhysicalToteSource.OSR,
                null,
                new OrderSheetKey("order-1", 1),
                "SC-104",
                List.of("pharmacy-1"),
                true));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                OperationalPhysicalToteSource.OSR,
                new PhysicalToteId("physical-1"),
                new OrderSheetKey("order-1", 1),
                "SC-104",
                List.of("pharmacy-1", "pharmacy-1"),
                true));
    }

    private static P2pLineLeaseCatalogSnapshot catalog(String lineId) {
        P2pLineDefinition definition = new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
        return new P2pLineLeaseCatalogSnapshot(List.of(
                new P2pLineLeaseSnapshot(
                        definition,
                        Optional.empty(),
                        P2pLineActivitySnapshot.idle(),
                        List.of())));
    }

    private static P2pElasticAllocationSnapshot elasticAllocation(
            P2pLineLeaseCatalogSnapshot catalog) {
        return new P2pElasticAllocationSnapshot(
                P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                LocalDateTime.of(2026, 8, 24, 6, 0),
                catalog.lines().stream().map(line -> line.definition().lineId()).toList(),
                1,
                List.of(),
                List.of());
    }
}
