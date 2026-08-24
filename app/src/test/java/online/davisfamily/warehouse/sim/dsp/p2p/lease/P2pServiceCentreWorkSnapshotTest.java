package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;

class P2pServiceCentreWorkSnapshotTest {

    @Test
    void shouldRetainEveryPhysicalManifestUntilConsumedRegardlessOfLogicalStatus() {
        DspSchedulerOrderState waiting = order("order-1", "SC-104", OrderType.FULL_PACK, DspOrderStatus.WAITING);
        InboundToteManifest first = manifest("physical-1", waiting, 0);
        InboundToteManifest second = manifest("physical-2", waiting, 1);
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of(first, second));
        InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(), catalog);
        lifecycle.activate(first.physicalToteId(), Duration.ZERO);
        lifecycle.advanceToPreP2p(first.physicalToteId(), Duration.ofSeconds(1));
        lifecycle.consumeAtP2p(first.physicalToteId(), Duration.ofSeconds(2));

        P2pServiceCentreWorkSnapshotFactory factory = new P2pServiceCentreWorkSnapshotFactory();
        P2pServiceCentreWorkSnapshot waitingSnapshot = factory.create(
                List.of(waiting), catalog, lifecycle.snapshot());
        P2pServiceCentreWorkSnapshot completedSnapshot = factory.create(
                List.of(waiting.withStatus(DspOrderStatus.COMPLETED)), catalog, lifecycle.snapshot());

        assertEquals(List.of(second.physicalToteId()), waitingSnapshot.remainingToteIds("SC-104"));
        assertEquals(waitingSnapshot, completedSnapshot);
        assertTrue(waitingSnapshot.hasRemainingWork("SC-104"));
        assertFalse(waitingSnapshot.hasRemainingWork("SC-108"));
    }

    @Test
    void shouldReportEmptyOrdersWithoutBlockingAndRejectMissingNonEmptyManifest() {
        DspSchedulerOrderState empty = order("empty-1", "SC-104", OrderType.EMPTY, DspOrderStatus.WAITING);
        P2pServiceCentreWorkSnapshotFactory factory = new P2pServiceCentreWorkSnapshotFactory();

        P2pServiceCentreWorkSnapshot snapshot = factory.create(
                List.of(empty),
                new InboundToteManifestCatalog(List.of()),
                new PhysicalToteLifecycleLedger().snapshot());

        assertFalse(snapshot.hasRemainingWork("SC-104"));
        assertEquals(List.of(empty.order().orderSheetKey()),
                snapshot.unallocatedEmptyOrdersByServiceCentre().get("SC-104"));
        assertThrows(IllegalStateException.class, () -> factory.create(
                List.of(order("full-1", "SC-104", OrderType.FULL_PACK, DspOrderStatus.WAITING)),
                new InboundToteManifestCatalog(List.of()),
                new PhysicalToteLifecycleLedger().snapshot()));
    }

    private static DspSchedulerOrderState order(
            String orderId,
            String serviceCentreId,
            OrderType orderType,
            DspOrderStatus status) {
        return new DspSchedulerOrderState(
                new NotionalToteOrder(
                        orderId, "notional-" + orderId, serviceCentreId, 1, orderType,
                        List.of(new DspOrderItem("line-" + orderId, "product-1", 1)),
                        999, 0),
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                status);
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            DspSchedulerOrderState order,
            long sequence) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey(order.order().orderId(), order.order().sheetNumber()),
                order.order().orderType(),
                order.order().serviceCentreId(),
                order.order().items(),
                sequence);
    }
}
