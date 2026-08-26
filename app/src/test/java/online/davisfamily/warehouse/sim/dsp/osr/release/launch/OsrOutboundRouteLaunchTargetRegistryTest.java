package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

class OsrOutboundRouteLaunchTargetRegistryTest {

    @Test
    void shouldPreserveDestinationOrderAndPublishExactTargets() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 3);
        List<OperationalRouteDestination> destinations = List.of(
                destination(StationType.THIRD_PARTY, "third-party-1"),
                destination(StationType.ADAPTING, "adapting-1"),
                destination(StationType.P2P, "p2p-1"));

        OsrOutboundRouteLaunchTargetRegistry registry =
                new OsrOutboundRouteLaunchTargetRegistry(queue, destinations);

        assertEquals(destinations, registry.destinations());
        assertSame(
                destinations.get(1),
                registry.findDestination("  adapting-1  ").orElseThrow());
        assertTrue(registry.findDestination("missing").isEmpty());
        assertEquals(
                List.of("third-party-1", "adapting-1", "p2p-1"),
                registry.targets().stream()
                        .map(OsrProcessingReleaseTarget::targetId)
                        .toList());
        for (int i = 0; i < registry.targets().size(); i++) {
            assertSame(
                    registry.targets().get(i),
                    registry.processingReleaseTargetRegistry()
                            .find(destinations.get(i).targetId())
                            .orElseThrow());
        }
    }

    @Test
    void shouldFeedSeveralTargetsIntoOneGlobalFifo() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 3);
        OsrOutboundRouteLaunchTargetRegistry registry =
                new OsrOutboundRouteLaunchTargetRegistry(queue, List.of(
                        destination(StationType.THIRD_PARTY, "third-party-1"),
                        destination(StationType.ADAPTING, "adapting-1"),
                        destination(StationType.P2P, "p2p-1")));
        OsrProcessingReleaseRequest first = request("tote-1", 1);
        OsrProcessingReleaseRequest second = request("tote-2", 2);
        OsrProcessingReleaseRequest third = request("tote-3", 3);

        assertTrue(registry.processingReleaseTargetRegistry()
                .find("p2p-1").orElseThrow().accept(first).applied());
        assertTrue(registry.processingReleaseTargetRegistry()
                .find("third-party-1").orElseThrow().accept(second).applied());
        assertTrue(registry.processingReleaseTargetRegistry()
                .find("adapting-1").orElseThrow().accept(third).applied());

        assertEquals(first.manifest().physicalToteId(), queue.dequeue().orElseThrow().physicalToteId());
        assertEquals(second.manifest().physicalToteId(), queue.dequeue().orElseThrow().physicalToteId());
        assertEquals(third.manifest().physicalToteId(), queue.dequeue().orElseThrow().physicalToteId());
    }

    @Test
    void shouldExposeFreshSharedQueueSnapshotsAndSharedCapacity() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 1);
        OsrOutboundRouteLaunchTargetRegistry registry =
                new OsrOutboundRouteLaunchTargetRegistry(queue, List.of(
                        destination(StationType.P2P, "p2p-1"),
                        destination(StationType.ADAPTING, "adapting-1")));

        assertEquals(0, registry.launchQueueSnapshot().occupancy());
        List<OperationalRouteTargetAdmissionSnapshot> openAdmissions =
                registry.snapshotAdmissions();
        assertEquals(List.of("p2p-1", "adapting-1"), openAdmissions.stream()
                .map(OperationalRouteTargetAdmissionSnapshot::targetId)
                .toList());
        assertTrue(openAdmissions.stream().allMatch(
                OperationalRouteTargetAdmissionSnapshot::canAccept));
        assertTrue(registry.processingReleaseTargetRegistry()
                .find("p2p-1").orElseThrow().accept(request("tote-1", 1)).applied());
        assertEquals(1, registry.launchQueueSnapshot().occupancy());

        List<OperationalRouteTargetAdmissionSnapshot> fullAdmissions =
                registry.snapshotAdmissions();
        assertEquals(List.of(1, 1), fullAdmissions.stream()
                .map(OperationalRouteTargetAdmissionSnapshot::capacity)
                .toList());
        assertEquals(List.of(1, 1), fullAdmissions.stream()
                .map(OperationalRouteTargetAdmissionSnapshot::occupancy)
                .toList());
        assertTrue(fullAdmissions.stream().noneMatch(
                OperationalRouteTargetAdmissionSnapshot::canAccept));
        assertThrows(UnsupportedOperationException.class, () -> fullAdmissions.clear());
        assertTrue(openAdmissions.stream().allMatch(
                OperationalRouteTargetAdmissionSnapshot::canAccept));

        SchedulerCommandApplicationResult deferred = registry.processingReleaseTargetRegistry()
                .find("adapting-1").orElseThrow().accept(request("tote-2", 2));
        assertTrue(deferred.deferred());
        assertEquals(1, registry.launchQueueSnapshot().occupancy());
    }

    @Test
    void shouldRejectInvalidOrDuplicateDestinationConfiguration() {
        OsrOutboundRouteLaunchQueue queue = new OsrOutboundRouteLaunchQueue("outbound", 1);
        OperationalRouteDestination destination =
                destination(StationType.P2P, "shared-target");

        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchTargetRegistry(null, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchTargetRegistry(queue, null));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchTargetRegistry(
                        queue, Arrays.asList(destination, null)));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchTargetRegistry(queue, List.of(
                        destination,
                        destination(StationType.ADAPTING, "shared-target"))));

        OsrOutboundRouteLaunchTargetRegistry empty =
                new OsrOutboundRouteLaunchTargetRegistry(queue, List.of());
        assertThrows(IllegalArgumentException.class, () -> empty.findDestination(null));
        assertThrows(IllegalArgumentException.class, () -> empty.findDestination(" "));
        assertThrows(UnsupportedOperationException.class, () -> empty.destinations().clear());
        assertThrows(UnsupportedOperationException.class, () -> empty.targets().clear());
        assertThrows(UnsupportedOperationException.class, () -> empty.av02Targets().clear());
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
    }

    private static OsrProcessingReleaseRequest request(
            String physicalToteId,
            long sourceSequence) {
        return new OsrProcessingReleaseRequest(
                new InboundToteManifest(
                        new PhysicalToteId(physicalToteId),
                        new OrderSheetKey("order-" + sourceSequence, 1),
                        OrderType.FULL_PACK,
                        "104",
                        List.of(new DspOrderItem(
                                "line-" + sourceSequence,
                                "product-" + sourceSequence,
                                1)),
                        sourceSequence),
                Duration.ofSeconds(sourceSequence));
    }
}
