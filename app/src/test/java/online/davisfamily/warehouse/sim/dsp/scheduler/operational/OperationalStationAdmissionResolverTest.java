package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

class OperationalStationAdmissionResolverTest {

    @Test
    void shouldBlockRouteWithoutEntryStation() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", route(false, false, false, false, false));

        OperationalRouteEntryEvaluation evaluation = new OperationalRouteEntryAdmissionPolicy()
                .evaluate(candidate, snapshot(candidate, Map.of()));

        assertTrue(evaluation.routeEntry().isEmpty());
        assertEquals(OperationalReleaseBlockType.ROUTE_ENTRY, evaluation.blocks().get(0).type());
    }

    @Test
    void shouldCheckOnlyFirstRouteStationAdmission() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", route(true, false, false, true, false));
        StationAdmissionSnapshot thirdParty = admission(
                StationType.THIRD_PARTY, true, false, Optional.of("third-party-1"), "");
        StationAdmissionSnapshot blockedP2p = admission(
                StationType.P2P, false, false, Optional.of("p2p-1"), "P2P closed");

        OperationalRouteEntryEvaluation evaluation = new OperationalRouteEntryAdmissionPolicy()
                .evaluate(candidate, snapshot(candidate, Map.of(
                        StationType.THIRD_PARTY, thirdParty,
                        StationType.P2P, blockedP2p)));

        assertTrue(evaluation.blocks().isEmpty());
        assertEquals(
                new OperationalRouteEntry(StationType.THIRD_PARTY, "third-party-1"),
                evaluation.routeEntry().orElseThrow());
    }

    @Test
    void shouldBlockMissingClosedOrFullEntryAdmission() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", route(false, false, false, true, false));
        OperationalRouteEntryAdmissionPolicy policy = new OperationalRouteEntryAdmissionPolicy();

        OperationalRouteEntryEvaluation missing = policy.evaluate(
                candidate, snapshot(candidate, Map.of()));
        OperationalRouteEntryEvaluation closed = policy.evaluate(
                candidate,
                snapshot(candidate, Map.of(
                        StationType.P2P,
                        admission(
                                StationType.P2P,
                                false,
                                false,
                                Optional.of("p2p-1"),
                                "P2P intake closed"))));
        OperationalRouteEntryEvaluation full = policy.evaluate(
                candidate,
                snapshot(candidate, Map.of(
                        StationType.P2P,
                        admission(
                                StationType.P2P,
                                true,
                                true,
                                Optional.of("p2p-1"),
                                ""))));

        assertEquals(OperationalReleaseBlockType.STATION_ADMISSION, missing.blocks().get(0).type());
        assertEquals(OperationalReleaseBlockType.STATION_ADMISSION, closed.blocks().get(0).type());
        assertEquals("P2P intake closed", closed.blocks().get(0).reason());
        assertEquals(OperationalReleaseBlockType.STATION_ADMISSION, full.blocks().get(0).type());
        assertTrue(full.blocks().get(0).reason().contains("cannot accept"));
    }

    @Test
    void shouldRequireExplicitSelectedEntryTarget() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1", route(false, false, false, true, false));
        StationAdmissionSnapshot admissionWithoutTarget = admission(
                StationType.P2P, true, false, Optional.empty(), "");

        OperationalRouteEntryEvaluation evaluation = new OperationalRouteEntryAdmissionPolicy()
                .evaluate(
                        candidate,
                        snapshot(candidate, Map.of(StationType.P2P, admissionWithoutTarget)));

        assertTrue(evaluation.routeEntry().isEmpty());
        assertEquals(
                OperationalReleaseBlockType.TARGET_SELECTION,
                evaluation.blocks().get(0).type());
        assertThrows(UnsupportedOperationException.class, () -> evaluation.blocks().clear());
    }

    @Test
    void shouldSupportCandidateAwareAdmissionResolver() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-7", route(false, false, false, true, false));
        AtomicReference<PhysicalToteId> resolvedCandidateId = new AtomicReference<>();
        OperationalStationAdmissionResolver resolver = (stationType, resolvedCandidate, snapshot) -> {
            resolvedCandidateId.set(resolvedCandidate.physicalCandidate().physicalToteId());
            return admission(
                    stationType,
                    true,
                    false,
                    Optional.of("target-for-" + resolvedCandidateId.get().value()),
                    "");
        };
        OperationalRouteEntryAdmissionPolicy policy = new OperationalRouteEntryAdmissionPolicy(
                new OperationalRouteEntrySelector(), resolver);

        OperationalRouteEntryEvaluation evaluation = policy.evaluate(
                candidate, snapshot(candidate, Map.of()));

        assertEquals(new PhysicalToteId("tote-7"), resolvedCandidateId.get());
        assertEquals("target-for-tote-7", evaluation.routeEntry().orElseThrow().targetId());
        assertFalse(evaluation.routeEntry().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryAdmissionPolicy(null, resolver));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryAdmissionPolicy(
                        new OperationalRouteEntrySelector(), null));
    }

    private static DspOperationalReleaseSnapshot snapshot(
            DspOperationalReleaseCandidate candidate,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions) {
        return new DspOperationalReleaseSnapshot(
                List.of(candidate),
                List.of(new ServiceCentrePharmacyGroup("sc-1", "pharmacy-1", 0, 1)),
                stationAdmissions,
                Set.of());
    }

    private static DspOperationalReleaseCandidate candidate(
            String physicalToteId,
            RouteRequirements routeRequirements) {
        DspOrderItem item = new DspOrderItem(
                "line-1",
                "product-1",
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-1",
                DspOrderLineType.FULL_PACK,
                "order-1",
                1,
                1);
        NotionalToteOrder order = new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(item),
                999,
                1);
        DspSchedulerOrderState logicalState = new DspSchedulerOrderState(
                order, routeRequirements, DspOrderStatus.WAITING);
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                OrderType.FULL_PACK,
                "sc-1",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        return new DspOperationalReleaseCandidate(
                physicalCandidate, logicalState, List.of("pharmacy-1"));
    }

    private static StationAdmissionSnapshot admission(
            StationType stationType,
            boolean open,
            boolean full,
            Optional<String> selectedTargetId,
            String blockedReason) {
        return new StationAdmissionSnapshot(
                stationType,
                full ? new StationCapacity(1, 0) : new StationCapacity(1, 1),
                new StationSnapshot(stationType, full ? 1 : 0, 0),
                open,
                blockedReason,
                selectedTargetId);
    }

    private static RouteRequirements route(
            boolean thirdParty,
            boolean sortable,
            boolean manual,
            boolean p2p,
            boolean manualMerge) {
        return new RouteRequirements(
                thirdParty,
                sortable,
                manual,
                p2p,
                manualMerge,
                StartLocation.OSR);
    }
}
