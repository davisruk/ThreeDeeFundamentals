package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationConfig;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventory;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.tote.Tote;

class OperationalLifecycleP2pToteCompletedListenerTest {

    @Test
    void shouldDelegateManifestFullPackAndAssociatedCompletionUnchanged() {
        for (OrderType orderType : List.of(OrderType.FULL_PACK, OrderType.ASSOCIATED)) {
            InboundToteManifest manifest = manifest("manifest-" + orderType, orderType);
            PhysicalToteLifecycleLedger inboundLedger = new PhysicalToteLifecycleLedger();
            InboundToteManifestCatalog manifestCatalog = new InboundToteManifestCatalog(List.of(manifest));
            InboundToteLifecycleController inboundLifecycle = new InboundToteLifecycleController(
                    inboundLedger,
                    manifestCatalog);
            inboundLifecycle.activate(manifest.physicalToteId(), Duration.ZERO);

            Av02ToteLifecycleController av02Lifecycle = emptyAv02Lifecycle();
            OperationalLifecycleP2pToteCompletedListener listener =
                    new OperationalLifecycleP2pToteCompletedListener(
                            manifestCatalog,
                            new InboundLifecycleP2pToteCompletedListener(inboundLifecycle),
                            emptyAv02Inventory(),
                            av02Lifecycle);
            Tote tote = tote(manifest.physicalToteId().value());
            SimulationContext context = context(2.5d);

            listener.onToteCompleted(tote, context);

            assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    inboundLifecycle.snapshot().totes().get(manifest.physicalToteId()).state());
            PhysicalToteAssignment assignment = inboundLifecycle.snapshot()
                    .assignmentHistoryFor(manifest.orderSheetKey())
                    .getLast();
            assertEquals(PhysicalToteAssignmentStage.PRE_P2P, assignment.stage());
            assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                    assignment.endReason().orElseThrow());
            assertEquals(Duration.ofMillis(2500), assignment.terminatedAt().orElseThrow());
        }
    }

    @Test
    void shouldConsumeDepartedAv02EmptyAtRoundedSimulationTime() {
        Av02Fixture fixture = av02Fixture("empty-1", "av02-000001", Duration.ofSeconds(2), true);
        PhysicalToteLifecycleSnapshot before = fixture.ledger().snapshot();
        Av02InventorySnapshot inventoryBefore = fixture.inventory().snapshot();
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                fixture.inventory(),
                fixture.lifecycle());

        listener.onToteCompleted(fixture.tote(), context(3.0000000006d));

        PhysicalToteLifecycleSnapshot after = fixture.ledger().snapshot();
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                after.totes().get(fixture.physicalToteId()).state());
        assertTrue(after.activeAssignmentFor(fixture.order().orderSheetKey()).isEmpty());
        PhysicalToteAssignment assignment = after
                .assignmentHistoryFor(fixture.order().orderSheetKey())
                .getFirst();
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                assignment.endReason().orElseThrow());
        assertEquals(Duration.ofNanos(3_000_000_001L), assignment.terminatedAt().orElseThrow());
        assertEquals(inventoryBefore, fixture.inventory().snapshot());
        assertEquals(List.of(fixture.allocatedTote()), fixture.inventory().snapshot().departedTotes());
        assertEquals(1, after.totes().size());
        assertTrue(after.totes().values().stream()
                .noneMatch(record -> record.role() == PhysicalToteRole.OUTBOUND_BAG));
        assertTrue(after.assignments().stream()
                .noneMatch(assignmentRecord -> assignmentRecord.stage() == PhysicalToteAssignmentStage.OUTBOUND_BAG
                        || assignmentRecord.stage() == PhysicalToteAssignmentStage.OUTBOUND));
        assertFalse(before.equals(after));
    }

    @Test
    void shouldRejectNullToteNullContextAndInvalidSimulationTimeWithoutMutation() {
        Av02Fixture fixture = av02Fixture("invalid-input", "av02-invalid-input", Duration.ZERO, true);
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                fixture.inventory(),
                fixture.lifecycle());

        assertCompletionRejectedWithoutMutation(listener, fixture, null, context(1d));
        assertCompletionRejectedWithoutMutation(listener, fixture, fixture.tote(), null);
        assertCompletionRejectedWithoutMutation(listener, fixture, fixture.tote(), context(Double.NaN));
        assertCompletionRejectedWithoutMutation(
                listener, fixture, fixture.tote(), context(Double.POSITIVE_INFINITY));
        assertCompletionRejectedWithoutMutation(listener, fixture, fixture.tote(), context(-1d));
    }

    @Test
    void shouldRejectWaitingAv02WithoutMutatingAnyLedger() {
        Av02Fixture fixture = av02Fixture("waiting", "av02-waiting", Duration.ZERO, false);
        PhysicalToteLifecycleSnapshot before = fixture.ledger().snapshot();
        Av02InventorySnapshot inventoryBefore = fixture.inventory().snapshot();
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                fixture.inventory(),
                fixture.lifecycle());

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(fixture.tote(), context(1d)));

        assertEquals(before, fixture.ledger().snapshot());
        assertEquals(inventoryBefore, fixture.inventory().snapshot());
    }

    @Test
    void shouldRejectUnknownPhysicalToteWithoutMutation() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        Av02PhysicalToteInventory inventory = emptyAv02Inventory();
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                inventory,
                new Av02ToteLifecycleController(ledger, () -> new PhysicalToteId("unused")));
        PhysicalToteLifecycleSnapshot before = ledger.snapshot();
        Av02InventorySnapshot inventoryBefore = inventory.snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> listener.onToteCompleted(tote("unknown"), context(1d)));

        assertEquals(before, ledger.snapshot());
        assertEquals(inventoryBefore, inventory.snapshot());
    }

    @Test
    void shouldRejectManifestAndAv02DualSourceWithoutMutation() {
        PhysicalToteId physicalToteId = new PhysicalToteId("shared-id");
        Av02Fixture fixture = av02Fixture("empty-shared", physicalToteId.value(), Duration.ZERO, true);
        InboundToteManifest manifest = manifest(physicalToteId.value(), OrderType.FULL_PACK);
        InboundToteManifestCatalog manifestCatalog = new InboundToteManifestCatalog(List.of(manifest));
        PhysicalToteLifecycleLedger inboundLedger = new PhysicalToteLifecycleLedger();
        InboundToteLifecycleController inboundLifecycle = new InboundToteLifecycleController(
                inboundLedger,
                manifestCatalog);
        OperationalLifecycleP2pToteCompletedListener listener =
                new OperationalLifecycleP2pToteCompletedListener(
                        manifestCatalog,
                        new InboundLifecycleP2pToteCompletedListener(inboundLifecycle),
                        fixture.inventory(),
                        fixture.lifecycle());
        PhysicalToteLifecycleSnapshot av02Before = fixture.ledger().snapshot();
        PhysicalToteLifecycleSnapshot inboundBefore = inboundLedger.snapshot();
        Av02InventorySnapshot inventoryBefore = fixture.inventory().snapshot();

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(fixture.tote(), context(1d)));

        assertEquals(av02Before, fixture.ledger().snapshot());
        assertEquals(inboundBefore, inboundLedger.snapshot());
        assertEquals(inventoryBefore, fixture.inventory().snapshot());
    }

    @Test
    void shouldRejectLiveLifecycleStateDivergenceWithoutMutation() {
        Av02Fixture fixture = av02Fixture("state-divergence", "av02-state", Duration.ZERO, true);
        fixture.ledger().transitionTote(
                fixture.physicalToteId(),
                PhysicalToteLifecycleState.CONSUMED_AT_P2P);
        PhysicalToteLifecycleSnapshot before = fixture.ledger().snapshot();
        Av02InventorySnapshot inventoryBefore = fixture.inventory().snapshot();
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                fixture.inventory(),
                fixture.lifecycle());

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(fixture.tote(), context(1d)));

        assertEquals(before, fixture.ledger().snapshot());
        assertEquals(inventoryBefore, fixture.inventory().snapshot());
    }

    @Test
    void shouldRejectDepartedAv02WithStaleConsumedPhysicalEvidenceWithoutMutation() {
        PhysicalToteId physicalToteId = new PhysicalToteId("av02-stale-evidence");
        NotionalToteOrder order = order("stale-evidence");
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        Av02ToteLifecycleController lifecycle = new Av02ToteLifecycleController(
                ledger,
                () -> physicalToteId);
        PhysicalToteRecord livePhysicalTote = lifecycle.allocateFor(
                order,
                Duration.ZERO,
                physicalToteId);
        PhysicalToteRecord staleEmbeddedPhysicalTote = new PhysicalToteRecord(
                physicalToteId,
                PhysicalToteRole.PRE_P2P,
                PhysicalToteLifecycleState.CONSUMED_AT_P2P);
        Av02AllocatedTote staleAllocatedTote = allocatedTote(
                physicalToteId,
                order.orderSheetKey(),
                staleEmbeddedPhysicalTote);
        Av02PhysicalToteInventory inventory = emptyAv02Inventory();
        inventory.store(staleAllocatedTote);
        inventory.recordDeparture(physicalToteId);

        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, livePhysicalTote.state());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                ledger.tote(physicalToteId).orElseThrow().state());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                staleAllocatedTote.physicalTote().state());

        PhysicalToteLifecycleSnapshot before = ledger.snapshot();
        Av02InventorySnapshot inventoryBefore = inventory.snapshot();
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                inventory,
                lifecycle);

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(tote(physicalToteId.value()), context(1d)));

        assertEquals(before, ledger.snapshot());
        assertEquals(inventoryBefore, inventory.snapshot());
    }

    @Test
    void shouldRejectAv02SheetEvidenceThatDivergesFromLifecycleWithoutMutation() {
        PhysicalToteId physicalToteId = new PhysicalToteId("av02-sheet");
        NotionalToteOrder liveOrder = order("live-sheet");
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        Av02ToteLifecycleController lifecycle = new Av02ToteLifecycleController(
                ledger,
                () -> physicalToteId);
        PhysicalToteRecord physicalTote = lifecycle.allocateFor(
                liveOrder,
                Duration.ZERO,
                physicalToteId);
        Av02AllocatedTote mismatchedSheet = allocatedTote(
                physicalToteId,
                new OrderSheetKey("different-sheet", 1),
                physicalTote);
        Av02PhysicalToteInventory inventory = emptyAv02Inventory();
        inventory.store(mismatchedSheet);
        inventory.recordDeparture(physicalToteId);
        PhysicalToteLifecycleSnapshot before = ledger.snapshot();
        Av02InventorySnapshot inventoryBefore = inventory.snapshot();
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                inventory,
                lifecycle);

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(tote(physicalToteId.value()), context(1d)));

        assertEquals(before, ledger.snapshot());
        assertEquals(inventoryBefore, inventory.snapshot());
    }

    @Test
    void shouldRejectCompletionBeforeActivationAndRepeatedCompletionWithoutMutation() {
        Av02Fixture fixture = av02Fixture("timing", "av02-timing", Duration.ofSeconds(5), true);
        OperationalLifecycleP2pToteCompletedListener listener = listener(
                new InboundToteManifestCatalog(List.of()),
                fixture.inventory(),
                fixture.lifecycle());
        PhysicalToteLifecycleSnapshot before = fixture.ledger().snapshot();
        Av02InventorySnapshot inventoryBefore = fixture.inventory().snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> listener.onToteCompleted(fixture.tote(), context(4.999999999d)));
        assertEquals(before, fixture.ledger().snapshot());
        assertEquals(inventoryBefore, fixture.inventory().snapshot());

        listener.onToteCompleted(fixture.tote(), context(6d));
        PhysicalToteLifecycleSnapshot afterFirst = fixture.ledger().snapshot();
        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(fixture.tote(), context(7d)));
        assertEquals(afterFirst, fixture.ledger().snapshot());
        assertEquals(inventoryBefore, fixture.inventory().snapshot());
    }

    @Test
    void shouldValidateConstructorArguments() {
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of());
        InboundLifecycleP2pToteCompletedListener inboundListener =
                new InboundLifecycleP2pToteCompletedListener(
                        new InboundToteLifecycleController(
                                new PhysicalToteLifecycleLedger(), catalog));
        Av02PhysicalToteInventory inventory = emptyAv02Inventory();
        Av02ToteLifecycleController lifecycle = emptyAv02Lifecycle();

        assertThrows(IllegalArgumentException.class,
                () -> new OperationalLifecycleP2pToteCompletedListener(
                        null, inboundListener, inventory, lifecycle));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalLifecycleP2pToteCompletedListener(
                        catalog, null, inventory, lifecycle));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalLifecycleP2pToteCompletedListener(
                        catalog, inboundListener, null, lifecycle));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalLifecycleP2pToteCompletedListener(
                        catalog, inboundListener, inventory, null));
    }

    private static OperationalLifecycleP2pToteCompletedListener listener(
            InboundToteManifestCatalog manifestCatalog,
            Av02PhysicalToteInventory inventory,
            Av02ToteLifecycleController lifecycle) {
        InboundToteLifecycleController inboundLifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                manifestCatalog);
        return new OperationalLifecycleP2pToteCompletedListener(
                manifestCatalog,
                new InboundLifecycleP2pToteCompletedListener(inboundLifecycle),
                inventory,
                lifecycle);
    }

    private static Av02Fixture av02Fixture(
            String orderId,
            String physicalToteId,
            Duration activationTime,
            boolean departed) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        NotionalToteOrder order = order(orderId);
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        Av02ToteLifecycleController lifecycle = new Av02ToteLifecycleController(
                ledger,
                () -> id);
        PhysicalToteRecord physicalTote = lifecycle.allocateFor(order, activationTime, id);
        Av02AllocatedTote allocatedTote = allocatedTote(id, order.orderSheetKey(), physicalTote);
        Av02PhysicalToteInventory inventory = emptyAv02Inventory();
        inventory.store(allocatedTote);
        if (departed) {
            inventory.recordDeparture(id);
        }
        return new Av02Fixture(order, id, ledger, lifecycle, inventory, allocatedTote);
    }

    private static Av02AllocatedTote allocatedTote(
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey,
            PhysicalToteRecord physicalTote) {
        return new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        physicalToteId,
                        orderSheetKey,
                        OrderType.EMPTY,
                        "104",
                        PhysicalToteRole.PRE_P2P,
                        1),
                physicalTote,
                "pharmacy-empty");
    }

    private static NotionalToteOrder order(String orderId) {
        return new NotionalToteOrder(
                orderId,
                orderId,
                "104",
                1,
                OrderType.EMPTY,
                List.of(new DspOrderItem("line-" + orderId, "product-1", 1)),
                0);
    }

    private static InboundToteManifest manifest(String physicalToteId, OrderType orderType) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                orderType,
                "104",
                List.of(new DspOrderItem("line-" + physicalToteId, "product-1", 1)),
                0);
    }

    private static Av02PhysicalToteInventory emptyAv02Inventory() {
        return new Av02PhysicalToteInventory(new Av02AllocationConfig(1));
    }

    private static Av02ToteLifecycleController emptyAv02Lifecycle() {
        return new Av02ToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                () -> new PhysicalToteId("unused"));
    }

    private static void assertCompletionRejectedWithoutMutation(
            OperationalLifecycleP2pToteCompletedListener listener,
            Av02Fixture fixture,
            Tote tote,
            SimulationContext context) {
        PhysicalToteLifecycleSnapshot before = fixture.ledger().snapshot();
        Av02InventorySnapshot inventoryBefore = fixture.inventory().snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> listener.onToteCompleted(tote, context));

        assertEquals(before, fixture.ledger().snapshot());
        assertEquals(inventoryBefore, fixture.inventory().snapshot());
    }

    private static SimulationContext context(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }

    private static Tote tote(String toteId) {
        RouteSegment segment = new RouteSegment(
                "segment-" + toteId,
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
        RenderableObject renderable = RenderableObject.create(
                toteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                ignored -> 0,
                false);
        return new Tote(toteId, new RouteFollower(toteId, segment, 0f, 1d),
                renderable, new Vec3(0f, 0f, 0f), 0f);
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {new Vec4(), new Vec4(), new Vec4()},
                new int[][] {{0, 1, 2}},
                "anchor");
    }

    private record Av02Fixture(
            NotionalToteOrder order,
            PhysicalToteId physicalToteId,
            PhysicalToteLifecycleLedger ledger,
            Av02ToteLifecycleController lifecycle,
            Av02PhysicalToteInventory inventory,
            Av02AllocatedTote allocatedTote) {

        private Tote tote() {
            return OperationalLifecycleP2pToteCompletedListenerTest.tote(physicalToteId.value());
        }
    }
}
