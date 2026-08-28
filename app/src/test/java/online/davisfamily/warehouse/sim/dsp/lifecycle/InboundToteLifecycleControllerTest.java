package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class InboundToteLifecycleControllerTest {

    @Test
    void shouldRegisterManifestsWithoutActivatingThem() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest first = manifest("tote-1", sheet("order-1"), OrderType.ADAPTED, 0);
        InboundToteManifest second = manifest("tote-2", sheet("order-2"), OrderType.FULL_PACK, 1);

        InboundToteLifecycleController controller = controller(ledger, first, second);

        assertEquals(2, controller.snapshot().totes().size());
        assertTrue(controller.snapshot().assignments().isEmpty());
        assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE,
                controller.snapshot().totes().get(first.physicalToteId()).state());
    }

    @Test
    void shouldActivateAndAdvanceInboundToteToPreP2p() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest manifest = manifest("tote-1", sheet("order-1"), OrderType.ASSOCIATED, 0);
        InboundToteLifecycleController controller = controller(ledger, manifest);

        PhysicalToteAssignment inbound = controller.activate(manifest.physicalToteId(), Duration.ofSeconds(1));
        PhysicalToteAssignment preP2p = controller.advanceToPreP2p(
                manifest.physicalToteId(), Duration.ofSeconds(2));

        assertEquals(PhysicalToteAssignmentStage.INBOUND_PACK, inbound.stage());
        assertEquals(PhysicalToteAssignmentStage.PRE_P2P, preP2p.stage());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                controller.snapshot().totes().get(manifest.physicalToteId()).state());
        List<PhysicalToteAssignment> history = controller.snapshot()
                .assignmentHistoryFor(manifest.orderSheetKey());
        assertEquals(2, history.size());
        assertFalse(history.getFirst().active());
        assertEquals(PhysicalToteAssignmentEndReason.ADVANCED_TO_NEXT_STAGE,
                history.getFirst().endReason().orElseThrow());
        assertTrue(history.get(1).active());
    }

    @Test
    void shouldConsumeAdaptedToteAtAdapting() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest manifest = manifest("tote-1", sheet("order-1"), OrderType.ADAPTED, 0);
        InboundToteLifecycleController controller = controller(ledger, manifest);
        controller.activate(manifest.physicalToteId(), Duration.ZERO);

        PhysicalToteRecord consumed = controller.consumeAtAdapting(
                manifest.physicalToteId(), Duration.ofSeconds(3));

        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING, consumed.state());
        PhysicalToteAssignment assignment = controller.snapshot()
                .assignmentHistoryFor(manifest.orderSheetKey()).getFirst();
        assertFalse(assignment.active());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_ADAPTING,
                assignment.endReason().orElseThrow());
    }

    @Test
    void shouldValidateAdaptingConsumptionWithoutMutationBeforeApplyingIt() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest manifest = manifest("tote-validate", sheet("order-validate"),
                OrderType.ADAPTED, 0);
        InboundToteLifecycleController controller = controller(ledger, manifest);
        controller.activate(manifest.physicalToteId(), Duration.ofSeconds(2));
        PhysicalToteLifecycleSnapshot before = controller.snapshot();

        controller.validateConsumeAtAdapting(
                manifest.physicalToteId(), Duration.ofSeconds(3));

        assertEquals(before, controller.snapshot());
        assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE,
                controller.snapshot().totes().get(manifest.physicalToteId()).state());

        PhysicalToteRecord consumed = controller.consumeAtAdapting(
                manifest.physicalToteId(), Duration.ofSeconds(3));
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING, consumed.state());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_ADAPTING,
                controller.snapshot().assignmentHistoryFor(manifest.orderSheetKey())
                        .getFirst().endReason().orElseThrow());
    }

    @Test
    void shouldRejectDistinctInvalidAdaptingConsumptionStatesWithoutMutation() {
        InboundToteManifest unknownManifest = manifest(
                "tote-known", sheet("order-known"), OrderType.ADAPTED, 0);
        InboundToteLifecycleController unknownController = controller(
                new PhysicalToteLifecycleLedger(), unknownManifest);
        PhysicalToteLifecycleSnapshot unknownBefore = unknownController.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> unknownController.validateConsumeAtAdapting(
                        new PhysicalToteId("unknown"), Duration.ZERO));
        assertEquals(unknownBefore, unknownController.snapshot());

        InboundToteManifest fullPack = manifest(
                "tote-full", sheet("order-full"), OrderType.FULL_PACK, 0);
        InboundToteLifecycleController wrongTypeController = controller(
                new PhysicalToteLifecycleLedger(), fullPack);
        wrongTypeController.activate(fullPack.physicalToteId(), Duration.ZERO);
        PhysicalToteLifecycleSnapshot wrongTypeBefore = wrongTypeController.snapshot();
        assertThrows(IllegalStateException.class,
                () -> wrongTypeController.validateConsumeAtAdapting(
                        fullPack.physicalToteId(), Duration.ofSeconds(1)));
        assertEquals(wrongTypeBefore, wrongTypeController.snapshot());

        InboundToteManifest missingAssignment = manifest(
                "tote-missing-assignment", sheet("order-missing-assignment"),
                OrderType.ADAPTED, 0);
        InboundToteLifecycleController missingAssignmentController = controller(
                new PhysicalToteLifecycleLedger(), missingAssignment);
        PhysicalToteLifecycleSnapshot missingBefore = missingAssignmentController.snapshot();
        assertThrows(IllegalStateException.class,
                () -> missingAssignmentController.validateConsumeAtAdapting(
                        missingAssignment.physicalToteId(), Duration.ZERO));
        assertEquals(missingBefore, missingAssignmentController.snapshot());

        InboundToteManifest beforeActivation = manifest(
                "tote-before", sheet("order-before"), OrderType.ADAPTED, 0);
        InboundToteLifecycleController beforeController = controller(
                new PhysicalToteLifecycleLedger(), beforeActivation);
        beforeController.activate(beforeActivation.physicalToteId(), Duration.ofSeconds(5));
        PhysicalToteLifecycleSnapshot beforeTimeSnapshot = beforeController.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> beforeController.validateConsumeAtAdapting(
                        beforeActivation.physicalToteId(), Duration.ofSeconds(4)));
        assertEquals(beforeTimeSnapshot, beforeController.snapshot());

        InboundToteManifest repeated = manifest(
                "tote-repeated", sheet("order-repeated"), OrderType.ADAPTED, 0);
        InboundToteLifecycleController repeatedController = controller(
                new PhysicalToteLifecycleLedger(), repeated);
        repeatedController.activate(repeated.physicalToteId(), Duration.ZERO);
        repeatedController.consumeAtAdapting(repeated.physicalToteId(), Duration.ofSeconds(1));
        PhysicalToteLifecycleSnapshot repeatedSnapshot = repeatedController.snapshot();
        assertThrows(IllegalStateException.class,
                () -> repeatedController.validateConsumeAtAdapting(
                        repeated.physicalToteId(), Duration.ofSeconds(2)));
        assertEquals(repeatedSnapshot, repeatedController.snapshot());
    }

    @Test
    void shouldShareNullAndNegativeTimeValidationBetweenAdaptingEntryPoints() {
        InboundToteManifest manifest = manifest(
                "tote-arguments", sheet("order-arguments"), OrderType.ADAPTED, 0);
        InboundToteLifecycleController controller = controller(
                new PhysicalToteLifecycleLedger(), manifest);

        assertThrows(IllegalArgumentException.class,
                () -> controller.validateConsumeAtAdapting(manifest.physicalToteId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtAdapting(manifest.physicalToteId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> controller.validateConsumeAtAdapting(
                        manifest.physicalToteId(), Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtAdapting(
                        manifest.physicalToteId(), Duration.ofSeconds(-1)));
    }

    @Test
    void shouldConsumeAssociatedAndFullPackTotesAtP2p() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest associated = manifest(
                "tote-associated", sheet("order-associated"), OrderType.ASSOCIATED, 0);
        InboundToteManifest fullPack = manifest(
                "tote-full-pack", sheet("order-full-pack"), OrderType.FULL_PACK, 1);
        InboundToteLifecycleController controller = controller(ledger, associated, fullPack);

        activateAndAdvance(controller, associated, Duration.ZERO);
        activateAndAdvance(controller, fullPack, Duration.ofSeconds(3));

        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                controller.consumeAtP2p(associated.physicalToteId(), Duration.ofSeconds(2)).state());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                controller.consumeAtP2p(fullPack.physicalToteId(), Duration.ofSeconds(5)).state());
    }

    @Test
    void shouldRejectWrongOrderTypeConsumption() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest adapted = manifest("tote-adapted", sheet("order-adapted"), OrderType.ADAPTED, 0);
        InboundToteManifest fullPack = manifest(
                "tote-full-pack", sheet("order-full-pack"), OrderType.FULL_PACK, 1);
        InboundToteLifecycleController controller = controller(ledger, adapted, fullPack);
        controller.activate(adapted.physicalToteId(), Duration.ZERO);
        controller.activate(fullPack.physicalToteId(), Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> controller.consumeAtP2p(adapted.physicalToteId(), Duration.ofSeconds(1)));
        assertThrows(IllegalStateException.class,
                () -> controller.consumeAtAdapting(fullPack.physicalToteId(), Duration.ofSeconds(1)));
    }

    @Test
    void shouldPreventTwoManifestsForOneSheetBeingActiveTogether() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OrderSheetKey sheet = sheet("order-1");
        InboundToteManifest first = manifest("tote-1", sheet, OrderType.ADAPTED, 0);
        InboundToteManifest second = manifest("tote-2", sheet, OrderType.ADAPTED, 1);
        InboundToteLifecycleController controller = controller(ledger, first, second);
        controller.activate(first.physicalToteId(), Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> controller.activate(second.physicalToteId(), Duration.ofSeconds(1)));
        assertEquals(first.physicalToteId(), controller.snapshot().activeAssignmentFor(sheet)
                .orElseThrow().physicalToteId());
    }

    @Test
    void shouldAllowNextManifestAfterEarlierManifestTerminates() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OrderSheetKey sheet = sheet("order-1");
        InboundToteManifest first = manifest("tote-1", sheet, OrderType.ADAPTED, 0);
        InboundToteManifest second = manifest("tote-2", sheet, OrderType.ADAPTED, 1);
        InboundToteLifecycleController controller = controller(ledger, first, second);
        controller.activate(first.physicalToteId(), Duration.ZERO);
        controller.consumeAtAdapting(first.physicalToteId(), Duration.ofSeconds(1));

        PhysicalToteAssignment next = controller.activate(
                second.physicalToteId(), Duration.ofSeconds(2));

        assertTrue(next.active());
        assertEquals(second.physicalToteId(), next.physicalToteId());
        assertEquals(2, controller.snapshot().assignmentHistoryFor(sheet).size());
    }

    @Test
    void shouldValidateActivationWithoutCreatingAssignment() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.FULL_PACK, 0);
        InboundToteLifecycleController controller = controller(ledger, manifest);
        PhysicalToteLifecycleSnapshot before = controller.snapshot();

        controller.validateActivation(manifest.physicalToteId(), Duration.ofSeconds(3));

        assertEquals(before, controller.snapshot());
        assertTrue(controller.snapshot().assignments().isEmpty());
    }

    @Test
    void shouldShareValidationBetweenValidateAndActivate() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest manifest = manifest(
                "tote-1", sheet("order-1"), OrderType.ASSOCIATED, 0);
        InboundToteLifecycleController controller = controller(ledger, manifest);
        controller.activate(manifest.physicalToteId(), Duration.ZERO);

        IllegalStateException validationFailure = assertThrows(
                IllegalStateException.class,
                () -> controller.validateActivation(
                        manifest.physicalToteId(), Duration.ofSeconds(1)));
        IllegalStateException activationFailure = assertThrows(
                IllegalStateException.class,
                () -> controller.activate(
                        manifest.physicalToteId(), Duration.ofSeconds(1)));

        assertEquals(validationFailure.getMessage(), activationFailure.getMessage());
        assertEquals(1, controller.snapshot().assignments().size());
    }

    @Test
    void shouldRejectValidationWhileAnotherManifestForSheetIsActive() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OrderSheetKey sharedSheet = sheet("order-1");
        InboundToteManifest first = manifest(
                "tote-1", sharedSheet, OrderType.ADAPTED, 0);
        InboundToteManifest second = manifest(
                "tote-2", sharedSheet, OrderType.ADAPTED, 1);
        InboundToteLifecycleController controller = controller(ledger, first, second);
        controller.activate(first.physicalToteId(), Duration.ZERO);

        assertThrows(
                IllegalStateException.class,
                () -> controller.validateActivation(
                        second.physicalToteId(), Duration.ofSeconds(1)));
        assertEquals(first.physicalToteId(), controller.snapshot()
                .activeAssignmentFor(sharedSheet)
                .orElseThrow()
                .physicalToteId());
    }

    @Test
    void shouldLeaveLifecycleSnapshotUnchangedAfterRejectedValidation() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OrderSheetKey sharedSheet = sheet("order-1");
        InboundToteManifest first = manifest(
                "tote-1", sharedSheet, OrderType.ADAPTED, 0);
        InboundToteManifest second = manifest(
                "tote-2", sharedSheet, OrderType.ADAPTED, 1);
        InboundToteLifecycleController controller = controller(ledger, first, second);
        controller.activate(first.physicalToteId(), Duration.ofSeconds(2));
        PhysicalToteLifecycleSnapshot before = controller.snapshot();

        assertThrows(
                IllegalStateException.class,
                () -> controller.validateActivation(
                        second.physicalToteId(), Duration.ofSeconds(3)));

        assertEquals(before, controller.snapshot());
    }

    @Test
    void shouldNotPartiallyMutateOnRejectedTransition() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        InboundToteManifest manifest = manifest("tote-1", sheet("order-1"), OrderType.ASSOCIATED, 0);
        InboundToteLifecycleController controller = controller(ledger, manifest);
        controller.activate(manifest.physicalToteId(), Duration.ofSeconds(5));
        PhysicalToteLifecycleSnapshot before = controller.snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> controller.advanceToPreP2p(manifest.physicalToteId(), Duration.ofSeconds(4)));

        assertEquals(before, controller.snapshot());
    }

    private static void activateAndAdvance(
            InboundToteLifecycleController controller,
            InboundToteManifest manifest,
            Duration activationTime) {
        controller.activate(manifest.physicalToteId(), activationTime);
        controller.advanceToPreP2p(manifest.physicalToteId(), activationTime.plusSeconds(1));
    }

    private static InboundToteLifecycleController controller(
            PhysicalToteLifecycleLedger ledger,
            InboundToteManifest... manifests) {
        return new InboundToteLifecycleController(
                ledger,
                new InboundToteManifestCatalog(List.of(manifests)));
    }

    private static InboundToteManifest manifest(
            String toteId,
            OrderSheetKey orderSheetKey,
            OrderType orderType,
            long sourceSequenceNumber) {
        return new InboundToteManifest(
                new PhysicalToteId(toteId),
                orderSheetKey,
                orderType,
                "104",
                List.of(new DspOrderItem("line-" + toteId, "product-1", 1)),
                sourceSequenceNumber);
    }

    private static OrderSheetKey sheet(String orderId) {
        return new OrderSheetKey(orderId, 1);
    }
}
