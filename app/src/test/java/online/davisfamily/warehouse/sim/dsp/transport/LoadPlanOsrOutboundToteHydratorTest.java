package online.davisfamily.warehouse.sim.dsp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class LoadPlanOsrOutboundToteHydratorTest {

    @Test
    void shouldResolveAndBuildOnceWithExactRequestAndPlan() {
        OperationalRouteLaunchRequest request = request(
                "tote-1", StationType.P2P, "p2p-1");
        ToteLoadPlan loadPlan = new ToteLoadPlan("tote-1", List.of());
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicReference<String> requestedToteId = new AtomicReference<>();
        AtomicReference<OperationalRouteLaunchRequest> factoryRequest =
                new AtomicReference<>();
        AtomicReference<ToteLoadPlan> factoryPlan = new AtomicReference<>();
        LoadPlanOsrOutboundToteHydrator hydrator = new LoadPlanOsrOutboundToteHydrator(
                physicalToteId -> {
                    providerCalls.incrementAndGet();
                    requestedToteId.set(physicalToteId);
                    return loadPlan;
                },
                (actualRequest, actualPlan) -> {
                    factoryCalls.incrementAndGet();
                    factoryRequest.set(actualRequest);
                    factoryPlan.set(actualPlan);
                    return routedTote(actualRequest, actualPlan);
                });

        RoutedPhysicalTote routedTote = hydrator.hydrate(request);

        assertEquals(1, providerCalls.get());
        assertEquals(1, factoryCalls.get());
        assertEquals("tote-1", requestedToteId.get());
        assertSame(request, factoryRequest.get());
        assertSame(loadPlan, factoryPlan.get());
        assertSame(request, routedTote.launchRequest());
        assertSame(loadPlan, routedTote.loadPlan());
        assertSame(request.destination(), routedTote.destination());
        assertFalse(routedTote.tote().areLidsOpen());
    }

    @Test
    void shouldHydrateEverySupportedDestinationWithoutStationDependencies() {
        for (StationType stationType : List.of(
                StationType.THIRD_PARTY,
                StationType.ADAPTING,
                StationType.P2P)) {
            OperationalRouteLaunchRequest request = request(
                    "tote-" + stationType.name().toLowerCase(Locale.ROOT),
                    stationType,
                    "target-" + stationType.name().toLowerCase(Locale.ROOT));
            ToteLoadPlan loadPlan = new ToteLoadPlan(request.physicalToteId(), List.of());
            LoadPlanOsrOutboundToteHydrator hydrator = new LoadPlanOsrOutboundToteHydrator(
                    physicalToteId -> loadPlan,
                    LoadPlanOsrOutboundToteHydratorTest::routedTote);

            RoutedPhysicalTote routedTote = hydrator.hydrate(request);

            assertSame(request.destination(), routedTote.destination());
            assertSame(request, routedTote.launchRequest());
            assertFalse(routedTote.tote().areLidsOpen());
        }
    }

    @Test
    void shouldBlockMissingOrMismatchedLoadPlanWithoutCallingFactory() {
        OperationalRouteLaunchRequest request = request(
                "tote-1", StationType.P2P, "p2p-1");
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger factoryCalls = new AtomicInteger();
        LoadPlanOsrOutboundToteHydrator missingPlanHydrator =
                new LoadPlanOsrOutboundToteHydrator(
                        physicalToteId -> {
                            providerCalls.incrementAndGet();
                            return null;
                        },
                        (actualRequest, actualPlan) -> {
                            factoryCalls.incrementAndGet();
                            return routedTote(actualRequest, actualPlan);
                        });

        OsrOutboundToteHydrationException missing = assertThrows(
                OsrOutboundToteHydrationException.class,
                () -> missingPlanHydrator.hydrate(request));

        assertTrue(missing.getMessage().contains("tote-1"));
        assertEquals(1, providerCalls.get());
        assertEquals(0, factoryCalls.get());

        LoadPlanOsrOutboundToteHydrator mismatchedPlanHydrator =
                new LoadPlanOsrOutboundToteHydrator(
                        physicalToteId -> new ToteLoadPlan("other", List.of()),
                        (actualRequest, actualPlan) -> {
                            factoryCalls.incrementAndGet();
                            return routedTote(actualRequest, actualPlan);
                        });
        OsrOutboundToteHydrationException mismatched = assertThrows(
                OsrOutboundToteHydrationException.class,
                () -> mismatchedPlanHydrator.hydrate(request));

        assertTrue(mismatched.getMessage().contains("tote-1"));
        assertEquals(0, factoryCalls.get());
    }

    @Test
    void shouldRejectNullAndMismatchedFactoryResults() {
        OperationalRouteLaunchRequest request = request(
                "tote-1", StationType.ADAPTING, "adapting-1");
        ToteLoadPlan loadPlan = new ToteLoadPlan("tote-1", List.of());

        LoadPlanOsrOutboundToteHydrator nullHydrator = hydrator(
                loadPlan, (actualRequest, actualPlan) -> null);
        assertThrows(
                OsrOutboundToteHydrationException.class,
                () -> nullHydrator.hydrate(request));

        OperationalRouteLaunchRequest copiedRequest = request(
                "tote-1", StationType.ADAPTING, "adapting-1");
        LoadPlanOsrOutboundToteHydrator copiedRequestHydrator = hydrator(
                loadPlan,
                (actualRequest, actualPlan) -> routedTote(copiedRequest, actualPlan));
        assertThrows(
                OsrOutboundToteHydrationException.class,
                () -> copiedRequestHydrator.hydrate(request));

        LoadPlanOsrOutboundToteHydrator copiedPlanHydrator = hydrator(
                loadPlan,
                (actualRequest, actualPlan) -> routedTote(
                        actualRequest,
                        new ToteLoadPlan(actualPlan.physicalToteId(), List.of())));
        assertThrows(
                OsrOutboundToteHydrationException.class,
                () -> copiedPlanHydrator.hydrate(request));
    }

    @Test
    void shouldRejectOpenLidsAndWrapInvalidFactoryPayloadCause() {
        OperationalRouteLaunchRequest request = request(
                "tote-1", StationType.THIRD_PARTY, "third-party-1");
        ToteLoadPlan loadPlan = new ToteLoadPlan("tote-1", List.of());
        LoadPlanOsrOutboundToteHydrator openLidHydrator = hydrator(
                loadPlan,
                (actualRequest, actualPlan) -> {
                    RoutedPhysicalTote routedTote = routedTote(actualRequest, actualPlan);
                    routedTote.tote().openLids();
                    return routedTote;
                });

        OsrOutboundToteHydrationException openLids = assertThrows(
                OsrOutboundToteHydrationException.class,
                () -> openLidHydrator.hydrate(request));
        assertTrue(openLids.getMessage().contains("tote-1"));

        IllegalArgumentException invalidPayload =
                new IllegalArgumentException("invalid payload");
        LoadPlanOsrOutboundToteHydrator invalidPayloadHydrator = hydrator(
                loadPlan,
                (actualRequest, actualPlan) -> {
                    throw invalidPayload;
                });
        OsrOutboundToteHydrationException wrapped = assertThrows(
                OsrOutboundToteHydrationException.class,
                () -> invalidPayloadHydrator.hydrate(request));
        assertSame(invalidPayload, wrapped.getCause());
        assertTrue(wrapped.getMessage().contains("tote-1"));
    }

    @Test
    void shouldPropagateUnexpectedRuntimeFailureAndValidateInputs() {
        RuntimeException unexpected = new IllegalStateException("unexpected");
        ToteLoadPlan loadPlan = new ToteLoadPlan("tote-1", List.of());
        OperationalRouteLaunchRequest request = request(
                "tote-1", StationType.P2P, "p2p-1");
        LoadPlanOsrOutboundToteHydrator hydrator = hydrator(
                loadPlan,
                (actualRequest, actualPlan) -> {
                    throw unexpected;
                });

        assertSame(unexpected, assertThrows(
                IllegalStateException.class,
                () -> hydrator.hydrate(request)));
        assertThrows(IllegalArgumentException.class, () -> hydrator.hydrate(null));
        assertThrows(IllegalArgumentException.class, () ->
                new LoadPlanOsrOutboundToteHydrator(null, (r, p) -> null));
        assertThrows(IllegalArgumentException.class, () ->
                new LoadPlanOsrOutboundToteHydrator(toteId -> loadPlan, null));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundToteHydrationException(null, "failure"));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundToteHydrationException(
                        new PhysicalToteId("tote-1"), " "));
    }

    private static LoadPlanOsrOutboundToteHydrator hydrator(
            ToteLoadPlan loadPlan,
            DetachedOutboundToteFactory factory) {
        return new LoadPlanOsrOutboundToteHydrator(
                physicalToteId -> loadPlan,
                factory);
    }

    private static OperationalRouteLaunchRequest request(
            String physicalToteId,
            StationType stationType,
            String targetId) {
        return RoutedPhysicalToteTestFixtures.launchRequest(
                physicalToteId, stationType, targetId);
    }

    private static RoutedPhysicalTote routedTote(
            OperationalRouteLaunchRequest request,
            ToteLoadPlan loadPlan) {
        String physicalToteId = request.physicalToteId().value();
        Tote tote = RoutedPhysicalToteTestFixtures.tote(
                physicalToteId, physicalToteId, physicalToteId);
        return new RoutedPhysicalTote(
                request,
                loadPlan,
                tote,
                tote.getRenderable());
    }
}
