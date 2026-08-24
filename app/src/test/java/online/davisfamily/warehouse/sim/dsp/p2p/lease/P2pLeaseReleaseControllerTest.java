package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

class P2pLeaseReleaseControllerTest {

    @Test
    void shouldCloseOpenOutboundToteBeforeReleasingLeaseOnLaterUpdate() {
        P2pLineDefinition line = definition("line-1");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(line));
        registry.acquireLease(line.lineId(), "SC-104", P2pLineActivitySnapshot.idle());
        OutboundToteAllocator allocator = allocator(line.lineId());
        allocator.allocate(line.lineId(), bag(), Duration.ofSeconds(1));
        P2pLineActivityProbe probe = () -> new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                allocator.snapshot().openToteFor(line.lineId()));
        P2pLeaseReleaseController controller = new P2pLeaseReleaseController(
                P2pServiceCentreWorkSnapshot::empty,
                List.of(probe),
                registry,
                allocator);
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(2d);

        controller.update(context, 0.1d);

        assertEquals(Optional.of("SC-104"), registry.ownerFor(line.lineId()));
        assertEquals(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE,
                allocator.snapshot().closedTotes().getFirst().closureReason().orElseThrow());

        controller.update(context, 0.1d);

        assertTrue(registry.ownerFor(line.lineId()).isEmpty());
    }

    @Test
    void shouldRetainLeaseForRemainingWorkOrBusyProcessingAndReleaseInConfiguredOrder() {
        P2pLineDefinition first = definition("line-1");
        P2pLineDefinition second = definition("line-2");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(second, first));
        registry.acquireLease(first.lineId(), "SC-104", P2pLineActivitySnapshot.idle());
        registry.acquireLease(second.lineId(), "SC-108", P2pLineActivitySnapshot.idle());
        P2pServiceCentreWorkSnapshot remaining = new P2pServiceCentreWorkSnapshot(
                java.util.Map.of("SC-104", List.of(new PhysicalToteId("physical-1"))),
                java.util.Map.of());
        P2pLeaseReleaseController blocked = new P2pLeaseReleaseController(
                () -> remaining,
                List.of(P2pLineActivitySnapshot::idle, P2pLineActivitySnapshot::idle),
                registry,
                allocator(first.lineId()));

        blocked.update(new SimulationContext(), 0d);

        assertTrue(registry.ownerFor(first.lineId()).isPresent());
        assertTrue(registry.ownerFor(second.lineId()).isEmpty());

        P2pLineActivitySnapshot busy = new P2pLineActivitySnapshot(
                new P2pInputActivitySnapshot(1, 0, false, 0),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.empty());
        P2pLeaseReleaseController processingBusy = new P2pLeaseReleaseController(
                P2pServiceCentreWorkSnapshot::empty,
                List.of(P2pLineActivitySnapshot::idle, () -> busy),
                registry,
                allocator(first.lineId()));

        processingBusy.update(new SimulationContext(), 0d);

        assertTrue(registry.ownerFor(first.lineId()).isPresent());
    }

    @Test
    void shouldRejectMismatchedOutboundToteOwnershipAndDoNothingWhenUnleased() {
        P2pLineDefinition line = definition("line-1");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(line));
        OutboundToteAllocator allocator = allocator(line.lineId());
        P2pLeaseReleaseController idle = new P2pLeaseReleaseController(
                P2pServiceCentreWorkSnapshot::empty,
                List.of(P2pLineActivitySnapshot::idle),
                registry,
                allocator);
        idle.update(new SimulationContext(), 0d);
        assertTrue(registry.ownerFor(line.lineId()).isEmpty());

        registry.acquireLease(line.lineId(), "SC-104", P2pLineActivitySnapshot.idle());
        P2pLineActivitySnapshot mismatched = new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.of(new OutboundToteSnapshot(
                        new PhysicalToteId("outbound-1"),
                        line.lineId(),
                        Optional.of("SC-108"),
                        Optional.of("pharmacy-1"),
                        10,
                        List.of(),
                        Optional.empty())));
        P2pLeaseReleaseController invalid = new P2pLeaseReleaseController(
                P2pServiceCentreWorkSnapshot::empty,
                List.of(() -> mismatched),
                registry,
                allocator);

        assertThrows(IllegalArgumentException.class,
                () -> invalid.update(new SimulationContext(), 0d));
        assertEquals(Optional.of("SC-104"), registry.ownerFor(line.lineId()));
    }

    @Test
    void shouldValidateControllerAndUpdateInputs() {
        P2pLineDefinition line = definition("line-1");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(line));
        OutboundToteAllocator allocator = allocator(line.lineId());
        assertThrows(IllegalArgumentException.class, () -> new P2pLeaseReleaseController(
                P2pServiceCentreWorkSnapshot::empty, List.of(), registry, allocator));
        P2pLeaseReleaseController nullWork = new P2pLeaseReleaseController(
                () -> null, List.of(P2pLineActivitySnapshot::idle), registry, allocator);
        assertThrows(IllegalArgumentException.class, () -> nullWork.update(null, 0d));
        assertThrows(IllegalArgumentException.class,
                () -> nullWork.update(new SimulationContext(), -1d));
        assertThrows(IllegalStateException.class,
                () -> nullWork.update(new SimulationContext(), 0d));
    }

    private static P2pLineDefinition definition(String lineId) {
        return new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
    }

    private static OutboundToteAllocator allocator(P2pLineId lineId) {
        return new OutboundToteAllocator(
                new PhysicalToteLifecycleLedger(),
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(new OrderSheetKey("order-1", 1))),
                new OutboundToteConfig(10));
    }

    private static PlannedBag bag() {
        return new PlannedBag(
                new BagKey("rx-1", 1),
                "SC-104", "pharmacy-1", "patient-1", "rx-1",
                List.of("pack-1"),
                List.of(new OrderSheetKey("order-1", 1)));
    }
}
