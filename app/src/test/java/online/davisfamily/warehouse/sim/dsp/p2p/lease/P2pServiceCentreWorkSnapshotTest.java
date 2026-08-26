package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
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

    @Test
    void shouldReplaceAuthorizedEmptyDiagnosticWithAllocatedAv02WorkUntilConsumed() {
        DspSchedulerOrderState empty = order(
                "empty-1", "SC-104", OrderType.EMPTY, DspOrderStatus.WAITING);
        Av02AllocatedTote allocated = av02Tote(empty, "av02-empty-1");
        PhysicalToteLifecycleLedger ledger = activeAv02Lifecycle(empty, allocated);
        P2pServiceCentreWorkSnapshotFactory factory = new P2pServiceCentreWorkSnapshotFactory();

        P2pServiceCentreWorkSnapshot active = factory.create(
                List.of(empty),
                new InboundToteManifestCatalog(List.of()),
                new Av02InventorySnapshot(1, List.of(), List.of(allocated)),
                ledger.snapshot(),
                Set.of(empty.order().orderSheetKey()));

        assertEquals(List.of(allocated.physicalToteId()), active.remainingToteIds("SC-104"));
        assertTrue(active.unallocatedEmptyOrdersByServiceCentre().isEmpty());

        ledger.terminateActiveAssignment(
                empty.order().orderSheetKey(),
                Duration.ofSeconds(1),
                online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        ledger.transitionTote(allocated.physicalToteId(), PhysicalToteLifecycleState.CONSUMED_AT_P2P);
        P2pServiceCentreWorkSnapshot consumed = factory.create(
                List.of(empty),
                new InboundToteManifestCatalog(List.of()),
                new Av02InventorySnapshot(1, List.of(), List.of(allocated)),
                ledger.snapshot(),
                Set.of(empty.order().orderSheetKey()));

        assertTrue(consumed.remainingToteIds("SC-104").isEmpty());
        assertTrue(consumed.unallocatedEmptyOrdersByServiceCentre().isEmpty());
    }

    @Test
    void shouldOmitUnauthorizedUnallocatedEmptyAndRejectActiveAssignmentWithoutAv02History() {
        DspSchedulerOrderState empty = order(
                "empty-1", "SC-104", OrderType.EMPTY, DspOrderStatus.WAITING);
        P2pServiceCentreWorkSnapshotFactory factory = new P2pServiceCentreWorkSnapshotFactory();

        P2pServiceCentreWorkSnapshot unauthorized = factory.create(
                List.of(empty),
                new InboundToteManifestCatalog(List.of()),
                new Av02InventorySnapshot(1, List.of(), List.of()),
                new PhysicalToteLifecycleLedger().snapshot(),
                Set.of());
        assertTrue(unauthorized.unallocatedEmptyOrdersByServiceCentre().isEmpty());

        PhysicalToteId physicalToteId = new PhysicalToteId("av02-empty-1");
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(PhysicalToteRecord.preP2p(physicalToteId));
        ledger.assign(
                empty.order().orderSheetKey(),
                physicalToteId,
                PhysicalToteAssignmentStage.PRE_P2P,
                Duration.ZERO);
        assertThrows(IllegalStateException.class, () -> factory.create(
                List.of(empty),
                new InboundToteManifestCatalog(List.of()),
                new Av02InventorySnapshot(1, List.of(), List.of()),
                ledger.snapshot(),
                Set.of(empty.order().orderSheetKey())));
    }

    private static Av02AllocatedTote av02Tote(
            DspSchedulerOrderState order,
            String physicalToteId) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        return new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        id,
                        order.order().orderSheetKey(),
                        OrderType.EMPTY,
                        order.order().serviceCentreId(),
                        online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole.PRE_P2P,
                        0),
                PhysicalToteRecord.preP2p(id),
                "pharmacy-104");
    }

    private static PhysicalToteLifecycleLedger activeAv02Lifecycle(
            DspSchedulerOrderState order,
            Av02AllocatedTote allocated) {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(allocated.physicalTote());
        ledger.assign(
                order.order().orderSheetKey(),
                allocated.physicalToteId(),
                PhysicalToteAssignmentStage.PRE_P2P,
                Duration.ZERO);
        return ledger;
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
