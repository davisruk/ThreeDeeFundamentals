package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class InboundToteLifecycleController {
    private final PhysicalToteLifecycleLedger ledger;
    private final InboundToteManifestCatalog catalog;

    public InboundToteLifecycleController(
            PhysicalToteLifecycleLedger ledger,
            InboundToteManifestCatalog catalog) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger must not be null");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        for (InboundToteManifest manifest : catalog.manifests()) {
            if (ledger.tote(manifest.physicalToteId()).isPresent()) {
                throw new IllegalArgumentException(
                        "Physical tote is already registered: " + manifest.physicalToteId().value());
            }
        }

        this.ledger = ledger;
        this.catalog = catalog;
        for (InboundToteManifest manifest : catalog.manifests()) {
            ledger.register(PhysicalToteRecord.inboundPack(manifest.physicalToteId()));
        }
    }

    public PhysicalToteAssignment activate(
            PhysicalToteId toteId,
            Duration activationTime) {
        validateActivation(toteId, activationTime);
        InboundToteManifest manifest = requireManifest(toteId);
        return ledger.assign(
                manifest.orderSheetKey(),
                toteId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                activationTime);
    }

    public void validateActivation(
            PhysicalToteId toteId,
            Duration activationTime) {
        InboundToteManifest manifest = requireManifest(toteId);
        requireNonNegative(activationTime, "activationTime");
        requireState(toteId, PhysicalToteLifecycleState.INBOUND_PACK_TOTE);
        if (ledger.activeAssignmentFor(manifest.orderSheetKey()).isPresent()) {
            throw new IllegalStateException(
                    "Logical sheet already has an active physical tote assignment: "
                            + manifest.orderSheetKey());
        }
        if (!ledger.activeAssignmentsFor(toteId).isEmpty()) {
            throw new IllegalStateException(
                    "Physical tote already has an active logical sheet assignment: "
                            + toteId.value());
        }
    }

    public PhysicalToteAssignment advanceToPreP2p(
            PhysicalToteId toteId,
            Duration transitionTime) {
        InboundToteManifest manifest = requireManifest(toteId);
        requireNonNegative(transitionTime, "transitionTime");
        requireState(toteId, PhysicalToteLifecycleState.INBOUND_PACK_TOTE);
        PhysicalToteAssignment activeAssignment = requireActiveAssignment(
                manifest,
                PhysicalToteAssignmentStage.INBOUND_PACK);
        requireNotBeforeActivation(transitionTime, activeAssignment);

        ledger.terminateActiveAssignment(
                manifest.orderSheetKey(),
                transitionTime,
                PhysicalToteAssignmentEndReason.ADVANCED_TO_NEXT_STAGE);
        ledger.transitionTote(toteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        return ledger.assign(
                manifest.orderSheetKey(),
                toteId,
                PhysicalToteAssignmentStage.PRE_P2P,
                transitionTime);
    }

    public PhysicalToteRecord consumeAtAdapting(
            PhysicalToteId toteId,
            Duration consumptionTime) {
        validateConsumeAtAdapting(toteId, consumptionTime);
        InboundToteManifest manifest = requireManifest(toteId);
        ledger.terminateActiveAssignment(
                manifest.orderSheetKey(),
                consumptionTime,
                PhysicalToteAssignmentEndReason.CONSUMED_AT_ADAPTING);
        return ledger.transitionTote(toteId, PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING);
    }

    public void validateConsumeAtAdapting(
            PhysicalToteId toteId,
            Duration consumptionTime) {
        InboundToteManifest manifest = requireManifest(toteId);
        requireNonNegative(consumptionTime, "consumptionTime");
        if (manifest.orderType() != OrderType.ADAPTED) {
            throw new IllegalStateException("Only ADAPTED inbound totes may be consumed at Adapting");
        }
        PhysicalToteRecord tote = requireTote(toteId);
        if (tote.state() != PhysicalToteLifecycleState.INBOUND_PACK_TOTE
                && tote.state() != PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
            throw new IllegalStateException(
                    "Inbound tote cannot be consumed at Adapting from state " + tote.state());
        }
        PhysicalToteAssignment activeAssignment = requireActiveAssignment(manifest, null);
        requireNotBeforeActivation(consumptionTime, activeAssignment);
    }

    public PhysicalToteRecord consumeAtP2p(
            PhysicalToteId toteId,
            Duration consumptionTime) {
        InboundToteManifest manifest = requireManifest(toteId);
        requireNonNegative(consumptionTime, "consumptionTime");
        if (manifest.orderType() != OrderType.ASSOCIATED
                && manifest.orderType() != OrderType.FULL_PACK) {
            throw new IllegalStateException(
                    "Only ASSOCIATED or FULL_PACK inbound totes may be consumed at P2P");
        }
        requireState(toteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        PhysicalToteAssignment activeAssignment = requireActiveAssignment(
                manifest,
                PhysicalToteAssignmentStage.PRE_P2P);
        requireNotBeforeActivation(consumptionTime, activeAssignment);

        ledger.terminateActiveAssignment(
                manifest.orderSheetKey(),
                consumptionTime,
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        return ledger.transitionTote(toteId, PhysicalToteLifecycleState.CONSUMED_AT_P2P);
    }

    public PhysicalToteLifecycleSnapshot snapshot() {
        return ledger.snapshot();
    }

    public InboundToteManifest manifestFor(PhysicalToteId toteId) {
        return requireManifest(toteId);
    }

    private InboundToteManifest requireManifest(PhysicalToteId toteId) {
        if (toteId == null) {
            throw new IllegalArgumentException("toteId must not be null");
        }
        return catalog.findByPhysicalToteId(toteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown inbound tote manifest: " + toteId.value()));
    }

    private PhysicalToteRecord requireTote(PhysicalToteId toteId) {
        return ledger.tote(toteId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inbound tote is not registered: " + toteId.value()));
    }

    private void requireState(PhysicalToteId toteId, PhysicalToteLifecycleState expectedState) {
        PhysicalToteLifecycleState actualState = requireTote(toteId).state();
        if (actualState != expectedState) {
            throw new IllegalStateException(
                    "Inbound tote " + toteId.value() + " must be in state " + expectedState
                            + " but was " + actualState);
        }
    }

    private PhysicalToteAssignment requireActiveAssignment(
            InboundToteManifest manifest,
            PhysicalToteAssignmentStage expectedStage) {
        PhysicalToteAssignment assignment = ledger.activeAssignmentFor(manifest.orderSheetKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Logical sheet has no active inbound tote assignment: "
                                + manifest.orderSheetKey()));
        if (!assignment.physicalToteId().equals(manifest.physicalToteId())) {
            throw new IllegalStateException(
                    "Logical sheet is active on a different physical tote: "
                            + assignment.physicalToteId().value());
        }
        if (expectedStage != null && assignment.stage() != expectedStage) {
            throw new IllegalStateException(
                    "Active assignment must be at stage " + expectedStage
                            + " but was " + assignment.stage());
        }
        return assignment;
    }

    private static void requireNotBeforeActivation(
            Duration operationTime,
            PhysicalToteAssignment assignment) {
        if (operationTime.compareTo(assignment.activatedAt()) < 0) {
            throw new IllegalArgumentException("Operation time must not precede assignment activation");
        }
    }

    private static void requireNonNegative(Duration value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
