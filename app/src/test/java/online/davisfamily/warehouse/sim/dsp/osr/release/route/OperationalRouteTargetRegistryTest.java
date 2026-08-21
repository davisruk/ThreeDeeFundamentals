package online.davisfamily.warehouse.sim.dsp.osr.release.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTarget;

class OperationalRouteTargetRegistryTest {

    @Test
    void shouldPreserveConfiguredOrderAndResolveNormalizedTargetId() {
        OperationalRouteEntryQueue thirdParty = queue(
                StationType.THIRD_PARTY, "third-party-ingress", 1);
        OperationalRouteEntryQueue adaptingOne = queue(
                StationType.ADAPTING, "bench-1", 2);
        OperationalRouteEntryQueue adaptingTwo = queue(
                StationType.ADAPTING, "bench-2", 2);
        OperationalRouteEntryQueue p2p = queue(
                StationType.P2P, "p2p-ingress", 3);

        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(
                List.of(thirdParty, adaptingOne, adaptingTwo, p2p));

        assertEquals(
                List.of(thirdParty, adaptingOne, adaptingTwo, p2p),
                registry.queues());
        assertSame(adaptingOne, registry.find("  bench-1  ").orElseThrow());
        assertTrue(registry.find("missing").isEmpty());
        assertEquals(
                List.of(adaptingOne, adaptingTwo),
                registry.queuesFor(StationType.ADAPTING));
        assertEquals(
                List.of(adaptingOne.definition(), adaptingTwo.definition()),
                registry.definitionsFor(StationType.ADAPTING));
        assertTrue(registry.queuesFor(StationType.P2P).contains(p2p));
    }

    @Test
    void shouldBuildExistingHandlerRegistryFromExactPublishedTargets() {
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(List.of(
                queue(StationType.THIRD_PARTY, "third-party-ingress", 1),
                queue(StationType.P2P, "p2p-ingress", 1)));

        List<OsrProcessingReleaseTarget> targets = registry.releaseTargets();

        assertEquals(
                List.of("third-party-ingress", "p2p-ingress"),
                targets.stream().map(OsrProcessingReleaseTarget::targetId).toList());
        assertSame(
                targets.get(0),
                registry.processingReleaseTargetRegistry()
                        .find("third-party-ingress")
                        .orElseThrow());
        assertSame(
                targets.get(1),
                registry.processingReleaseTargetRegistry()
                        .find("p2p-ingress")
                        .orElseThrow());
    }

    @Test
    void shouldPublishFreshImmutableSnapshotsInConfiguredOrder() {
        OperationalRouteEntryQueue first = queue(
                StationType.THIRD_PARTY, "third-party-ingress", 1);
        OperationalRouteEntryQueue second = queue(
                StationType.P2P, "p2p-ingress", 2);
        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(
                List.of(first, second));

        List<OperationalRouteEntryQueueSnapshot> snapshots = registry.snapshots();

        assertEquals(
                List.of("third-party-ingress", "p2p-ingress"),
                snapshots.stream()
                        .map(OperationalRouteEntryQueueSnapshot::targetId)
                        .toList());
        assertEquals(List.of(1, 2), snapshots.stream()
                .map(OperationalRouteEntryQueueSnapshot::capacity)
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> snapshots.clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.queues().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.releaseTargets().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.queuesFor(StationType.P2P).clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.definitionsFor(StationType.P2P).clear());
    }

    @Test
    void shouldRejectInvalidOrDuplicateQueueConfiguration() {
        OperationalRouteEntryQueue queue = queue(
                StationType.THIRD_PARTY, "shared-target", 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetRegistry(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetRegistry(Arrays.asList(queue, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetRegistry(List.of(queue, queue)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetRegistry(List.of(
                        queue,
                        queue(StationType.P2P, "shared-target", 1))));

        OperationalRouteTargetRegistry registry = new OperationalRouteTargetRegistry(List.of());
        assertThrows(IllegalArgumentException.class, () -> registry.find(null));
        assertThrows(IllegalArgumentException.class, () -> registry.find(" "));
        assertThrows(IllegalArgumentException.class, () -> registry.queuesFor(null));
        assertThrows(IllegalArgumentException.class, () -> registry.definitionsFor(null));
    }

    private static OperationalRouteEntryQueue queue(
            StationType stationType,
            String targetId,
            int capacity) {
        return new OperationalRouteEntryQueue(new OperationalRouteTargetDefinition(
                stationType,
                targetId,
                capacity));
    }
}
