package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

class OperationalCandidateRouteAdmissionFactoryTest {

    @Test
    void shouldCaptureDifferentTargetsForCandidatesAtSameStation() {
        DspOperationalReleaseCandidate first = OperationalRouteAdmissionTestSupport.candidate(
                "tote-1", OperationalRouteAdmissionTestSupport.route(StationType.ADAPTING));
        DspOperationalReleaseCandidate second = OperationalRouteAdmissionTestSupport.candidate(
                "tote-2", OperationalRouteAdmissionTestSupport.route(StationType.ADAPTING));
        OperationalRouteTargetRegistry targets = new OperationalRouteTargetRegistry(List.of(
                OperationalRouteAdmissionTestSupport.queue(
                        StationType.ADAPTING, "bench-1", 1),
                OperationalRouteAdmissionTestSupport.queue(
                        StationType.ADAPTING, "bench-2", 1)));
        StationAdmissionResolver liveResolver = (stationType, candidate, snapshot) ->
                OperationalRouteAdmissionTestSupport.openAdmission(
                        stationType,
                        candidate.order().orderId().endsWith("tote-1")
                                ? "bench-1"
                                : "bench-2");
        OperationalCandidateRouteAdmissionFactory factory = factory(liveResolver, targets);

        List<OperationalCandidateRouteAdmission> admissions = factory.create(
                List.of(first, second),
                OperationalRouteAdmissionTestSupport.logicalSnapshot(List.of(first, second)));

        assertEquals(List.of("bench-1", "bench-2"), admissions.stream()
                .map(admission -> admission.stationAdmission()
                        .selectedTargetId().orElseThrow())
                .toList());
        assertEquals(List.of("tote-1", "tote-2"), admissions.stream()
                .map(admission -> admission.physicalToteId().value())
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> admissions.clear());
    }

    @Test
    void shouldPreserveClosedStationReasonBeforeTargetValidation() {
        DspOperationalReleaseCandidate candidate = OperationalRouteAdmissionTestSupport.candidate(
                "tote-1", OperationalRouteAdmissionTestSupport.route(StationType.P2P));
        OperationalCandidateRouteAdmissionFactory factory = factory(
                (stationType, order, snapshot) ->
                        OperationalRouteAdmissionTestSupport.closedAdmission(
                                stationType, "unknown-target", "P2P intake closed"),
                new OperationalRouteTargetRegistry(List.of()));

        StationAdmissionSnapshot effective = factory.create(
                List.of(candidate),
                OperationalRouteAdmissionTestSupport.logicalSnapshot(List.of(candidate)))
                .get(0)
                .stationAdmission();

        assertEquals("P2P intake closed", effective.blockedReason());
        assertTrue(effective.selectedTargetId().isEmpty());
    }

    @Test
    void shouldCloseFullUnknownWrongStationAndTargetlessAdmission() {
        List<DspOperationalReleaseCandidate> candidates = List.of(
                candidate("full"),
                candidate("unknown"),
                candidate("wrong"),
                candidate("targetless"));
        OperationalRouteTargetRegistry targets = new OperationalRouteTargetRegistry(List.of(
                OperationalRouteAdmissionTestSupport.queue(
                        StationType.P2P, "full-target", 0),
                OperationalRouteAdmissionTestSupport.queue(
                        StationType.THIRD_PARTY, "wrong-target", 1)));
        StationAdmissionResolver resolver = (stationType, order, snapshot) -> {
            String orderId = order.order().orderId();
            if (orderId.endsWith("full")) {
                return OperationalRouteAdmissionTestSupport.openAdmission(
                        stationType, "full-target");
            }
            if (orderId.endsWith("unknown")) {
                return OperationalRouteAdmissionTestSupport.openAdmission(
                        stationType, "unknown-target");
            }
            if (orderId.endsWith("wrong")) {
                return OperationalRouteAdmissionTestSupport.openAdmission(
                        stationType, "wrong-target");
            }
            return OperationalRouteAdmissionTestSupport.openAdmission(stationType, null);
        };

        List<OperationalCandidateRouteAdmission> admissions = factory(resolver, targets).create(
                candidates,
                OperationalRouteAdmissionTestSupport.logicalSnapshot(candidates));

        assertTrue(admissions.get(0).stationAdmission().blockedReason()
                .contains("no waiting capacity"));
        assertTrue(admissions.get(1).stationAdmission().blockedReason()
                .contains("Unknown operational route admission target"));
        assertTrue(admissions.get(2).stationAdmission().blockedReason()
                .contains("belongs to station THIRD_PARTY"));
        assertTrue(admissions.get(3).stationAdmission().blockedReason()
                .contains("has no selected target"));
        assertTrue(admissions.stream().allMatch(admission ->
                admission.stationAdmission().selectedTargetId().isEmpty()));
    }

    @Test
    void shouldOmitMissingRouteOrMissingLiveAdmission() {
        DspOperationalReleaseCandidate noRoute = OperationalRouteAdmissionTestSupport.candidate(
                "no-route", OperationalRouteAdmissionTestSupport.noRoute());
        DspOperationalReleaseCandidate noAdmission = candidate("no-admission");
        AtomicInteger resolverCalls = new AtomicInteger();
        OperationalCandidateRouteAdmissionFactory factory = factory(
                (stationType, order, snapshot) -> {
                    resolverCalls.incrementAndGet();
                    return null;
                },
                new OperationalRouteTargetRegistry(List.of()));

        List<OperationalCandidateRouteAdmission> admissions = factory.create(
                List.of(noRoute, noAdmission),
                OperationalRouteAdmissionTestSupport.logicalSnapshot(
                        List.of(noRoute, noAdmission)));

        assertTrue(admissions.isEmpty());
        assertEquals(1, resolverCalls.get());
    }

    @Test
    void shouldCaptureImmutableTargetAdmissionsOnceForAllCandidates() {
        List<DspOperationalReleaseCandidate> candidates = List.of(
                candidate("one"),
                candidate("two"));
        AtomicInteger snapshotCalls = new AtomicInteger();
        OperationalRouteTargetAdmissionCatalog catalog =
                new OperationalRouteTargetAdmissionCatalog() {
                    @Override
                    public List<OperationalRouteTargetAdmissionSnapshot> snapshotAdmissions() {
                        snapshotCalls.incrementAndGet();
                        return List.of(new OperationalRouteTargetAdmissionSnapshot(
                                StationType.P2P, "p2p-1", 2, 0));
                    }

                    @Override
                    public OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry() {
                        return new OsrProcessingReleaseTargetRegistry(List.of());
                    }
                };
        StationAdmissionResolver resolver = (stationType, order, snapshot) ->
                OperationalRouteAdmissionTestSupport.openAdmission(stationType, "p2p-1");
        OperationalCandidateRouteAdmissionFactory factory = factory(resolver, catalog);

        List<OperationalCandidateRouteAdmission> admissions = factory.create(
                candidates,
                OperationalRouteAdmissionTestSupport.logicalSnapshot(candidates));

        assertEquals(2, admissions.size());
        assertEquals(1, snapshotCalls.get());
        assertTrue(admissions.stream().allMatch(admission ->
                admission.stationAdmission().selectedTargetId().orElseThrow()
                        .equals("p2p-1")));
    }

    @Test
    void shouldRejectDuplicateAdmissionIdsFromCatalogSnapshot() {
        OperationalRouteTargetAdmissionSnapshot admission =
                new OperationalRouteTargetAdmissionSnapshot(
                        StationType.P2P, "p2p-1", 1, 0);
        OperationalRouteTargetAdmissionCatalog catalog = catalog(List.of(admission, admission));
        DspOperationalReleaseCandidate candidate = candidate("one");

        assertThrows(IllegalStateException.class, () -> factory(
                (stationType, order, snapshot) ->
                        OperationalRouteAdmissionTestSupport.openAdmission(
                                stationType, "p2p-1"),
                catalog).create(
                        List.of(candidate),
                        OperationalRouteAdmissionTestSupport.logicalSnapshot(List.of(candidate))));
    }

    @Test
    void shouldValidateInputsAndResolvedStationType() {
        OperationalRouteTargetRegistry targets = new OperationalRouteTargetRegistry(List.of());
        StationAdmissionResolver resolver = (stationType, order, snapshot) ->
                OperationalRouteAdmissionTestSupport.closedAdmission(
                        StationType.THIRD_PARTY, null, "closed");
        OperationalCandidateRouteAdmissionFactory factory = factory(resolver, targets);
        DspOperationalReleaseCandidate candidate = candidate("tote-1");

        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalCandidateRouteAdmissionFactory(null, resolver, targets));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(), null, targets));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(), resolver, null));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                null,
                OperationalRouteAdmissionTestSupport.logicalSnapshot(List.of(candidate))));
        assertThrows(IllegalArgumentException.class, () -> factory.create(List.of(candidate), null));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                Arrays.asList(candidate, null),
                OperationalRouteAdmissionTestSupport.logicalSnapshot(List.of(candidate))));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                List.of(candidate),
                OperationalRouteAdmissionTestSupport.logicalSnapshot(List.of(candidate))));
    }

    private static DspOperationalReleaseCandidate candidate(String suffix) {
        return OperationalRouteAdmissionTestSupport.candidate(
                "tote-" + suffix,
                OperationalRouteAdmissionTestSupport.route(StationType.P2P));
    }

    private static OperationalCandidateRouteAdmissionFactory factory(
            StationAdmissionResolver resolver,
            OperationalRouteTargetAdmissionCatalog targets) {
        return new OperationalCandidateRouteAdmissionFactory(
                new OperationalRouteEntrySelector(), resolver, targets);
    }

    private static OperationalRouteTargetAdmissionCatalog catalog(
            List<OperationalRouteTargetAdmissionSnapshot> admissions) {
        return new OperationalRouteTargetAdmissionCatalog() {
            @Override
            public List<OperationalRouteTargetAdmissionSnapshot> snapshotAdmissions() {
                return admissions;
            }

            @Override
            public OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry() {
                return new OsrProcessingReleaseTargetRegistry(List.of());
            }
        };
    }
}
