package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;

public final class P2pServiceCentreWorkSnapshotFactory {

    private ValidationCache lastValidationCache;

    public P2pServiceCentreWorkSnapshot create(
            List<DspSchedulerOrderState> orderStates,
            InboundToteManifestCatalog manifestCatalog,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        return create(
                orderStates,
                manifestCatalog,
                new Av02InventorySnapshot(1, List.of(), List.of()),
                lifecycleSnapshot,
                compatibilityAuthorizedEmptyOrderSheetKeys(orderStates));
    }

    public P2pServiceCentreWorkSnapshot create(
            List<DspSchedulerOrderState> orderStates,
            InboundToteManifestCatalog manifestCatalog,
            Av02InventorySnapshot av02InventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot,
            Set<OrderSheetKey> authorizedEmptyOrderSheetKeys) {
        if (lastValidationCache != null && lastValidationCache.matches(
                orderStates,
                manifestCatalog,
                av02InventorySnapshot,
                lifecycleSnapshot,
                authorizedEmptyOrderSheetKeys)) {
            return lastValidationCache.snapshot();
        }
        if (orderStates == null || manifestCatalog == null || lifecycleSnapshot == null) {
            throw new IllegalArgumentException("P2P work snapshot inputs must not be null");
        }
        if (av02InventorySnapshot == null || authorizedEmptyOrderSheetKeys == null
                || authorizedEmptyOrderSheetKeys.stream().anyMatch(key -> key == null)) {
            throw new IllegalArgumentException("P2P work snapshot AV02 inputs must be valid");
        }

        Map<String, List<PhysicalToteId>> remaining = new LinkedHashMap<>();
        Map<String, List<OrderSheetKey>> emptyDiagnostics = new LinkedHashMap<>();
        Set<OrderSheetKey> seenSheets = new LinkedHashSet<>();
        Map<OrderSheetKey, List<PhysicalToteAssignment>> activeAssignmentsBySheet =
                indexActiveAssignmentsBySheet(lifecycleSnapshot);
        for (DspSchedulerOrderState orderState : orderStates) {
            if (orderState == null) {
                throw new IllegalArgumentException("orderStates must not contain null");
            }
            OrderSheetKey sheet = orderState.order().orderSheetKey();
            if (!seenSheets.add(sheet)) {
                throw new IllegalArgumentException("Duplicate scheduler order sheet: " + sheet);
            }
            if (!orderState.routeRequirements().requiresP2p()) {
                continue;
            }
            String serviceCentreId = orderState.order().serviceCentreId();
            if (orderState.order().orderType() == OrderType.EMPTY) {
                Av02AllocatedTote allocated = allocatedAv02Tote(
                        av02InventorySnapshot, sheet);
                if (allocated == null) {
                    if (!activeAssignmentsForSheet(activeAssignmentsBySheet, sheet).isEmpty()) {
                        throw new IllegalStateException(
                                "EMPTY sheet has an active lifecycle assignment without AV02 history: "
                                        + sheet);
                    }
                    if (authorizedEmptyOrderSheetKeys.contains(sheet)) {
                        emptyDiagnostics.computeIfAbsent(
                                serviceCentreId, ignored -> new ArrayList<>()).add(sheet);
                    }
                    continue;
                }

                if (manifestCatalog.findByPhysicalToteId(allocated.physicalToteId()).isPresent()) {
                    throw new IllegalStateException(
                            "AV02 physical tote is also present in the OSR manifest catalog: "
                                    + allocated.physicalToteId().value());
                }
                validateAv02Allocation(
                        orderState,
                        allocated,
                        av02InventorySnapshot,
                        lifecycleSnapshot,
                        activeAssignmentsBySheet,
                        sheet);
                PhysicalToteLifecycleState state = lifecycleSnapshot.totes()
                        .get(allocated.physicalToteId()).state();
                if (state == PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
                    remaining.computeIfAbsent(serviceCentreId, ignored -> new ArrayList<>())
                            .add(allocated.physicalToteId());
                }
                continue;
            }

            List<InboundToteManifest> manifests = manifestCatalog.manifestsFor(sheet);
            if (manifests.isEmpty()) {
                throw new IllegalStateException("P2P-required order has no inbound tote manifest: " + sheet);
            }
            for (InboundToteManifest manifest : manifests) {
                if (av02InventorySnapshot.findTote(manifest.physicalToteId()).isPresent()) {
                    throw new IllegalStateException(
                            "Physical tote is present in both OSR and AV02 sources: "
                                    + manifest.physicalToteId().value());
                }
                validateManifest(orderState, manifest);
                var tote = lifecycleSnapshot.totes().get(manifest.physicalToteId());
                if (tote == null) {
                    throw new IllegalStateException(
                            "Inbound manifest has no physical lifecycle record: "
                                    + manifest.physicalToteId().value());
                }
                if (!tote.id().equals(manifest.physicalToteId())
                        || tote.role() != PhysicalToteRole.INBOUND_PACK) {
                    throw new IllegalStateException(
                            "Inbound manifest lifecycle record has invalid physical identity or role: "
                                    + manifest.physicalToteId().value());
                }
                if (tote.state() != PhysicalToteLifecycleState.CONSUMED_AT_P2P) {
                    remaining.computeIfAbsent(serviceCentreId, ignored -> new ArrayList<>())
                            .add(manifest.physicalToteId());
                }
            }
        }
        P2pServiceCentreWorkSnapshot replacement = new P2pServiceCentreWorkSnapshot(
                remaining, emptyDiagnostics);
        lastValidationCache = new ValidationCache(
                orderStates,
                manifestCatalog,
                av02InventorySnapshot,
                lifecycleSnapshot,
                authorizedEmptyOrderSheetKeys,
                replacement);
        return replacement;
    }

    private static Av02AllocatedTote allocatedAv02Tote(
            Av02InventorySnapshot snapshot,
            OrderSheetKey sheet) {
        return snapshot.findTote(sheet).orElse(null);
    }

    private static void validateAv02Allocation(
            DspSchedulerOrderState orderState,
            Av02AllocatedTote allocated,
            Av02InventorySnapshot av02InventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot,
            Map<OrderSheetKey, List<PhysicalToteAssignment>> activeAssignmentsBySheet,
            OrderSheetKey sheet) {
        if (allocated.identity().source() != OperationalPhysicalToteSource.AV02
                || allocated.identity().orderType() != OrderType.EMPTY
                || allocated.identity().physicalToteRole() != PhysicalToteRole.PRE_P2P
                || !allocated.identity().orderSheetKey().equals(sheet)
                || !allocated.serviceCentreId().equals(orderState.order().serviceCentreId())) {
            throw new IllegalStateException("AV02 physical identity does not match EMPTY order state");
        }
        if (av02InventorySnapshot.findTote(allocated.physicalToteId()).orElse(null) != allocated) {
            throw new IllegalStateException("AV02 physical identity index is inconsistent");
        }

        var lifecycleRecord = lifecycleSnapshot.totes().get(allocated.physicalToteId());
        if (lifecycleRecord == null
                || !lifecycleRecord.id().equals(allocated.physicalToteId())
                || lifecycleRecord.role() != PhysicalToteRole.PRE_P2P) {
            throw new IllegalStateException(
                    "AV02 physical tote has no matching PRE_P2P lifecycle record: "
                            + allocated.physicalToteId().value());
        }

        List<PhysicalToteAssignment> activeForPhysical = lifecycleSnapshot
                .activeAssignmentsFor(allocated.physicalToteId());
        List<PhysicalToteAssignment> activeForSheet = activeAssignmentsForSheet(
                activeAssignmentsBySheet, sheet);
        if (lifecycleRecord.state() == PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
            if (activeForPhysical.size() != 1 || activeForSheet.size() != 1) {
                throw new IllegalStateException(
                        "Active AV02 physical tote must have exactly one active PRE_P2P assignment");
            }
            PhysicalToteAssignment assignment = activeForPhysical.getFirst();
            if (!assignment.orderSheetKey().equals(sheet)
                    || assignment.stage() != PhysicalToteAssignmentStage.PRE_P2P
                    || !activeForSheet.getFirst().physicalToteId()
                            .equals(allocated.physicalToteId())) {
                throw new IllegalStateException(
                        "Active AV02 physical tote has an invalid PRE_P2P assignment");
            }
            return;
        }
        if (lifecycleRecord.state() == PhysicalToteLifecycleState.CONSUMED_AT_P2P) {
            if (!activeForPhysical.isEmpty() || !activeForSheet.isEmpty()) {
                throw new IllegalStateException(
                        "Consumed AV02 physical tote must not have an active assignment");
            }
            return;
        }
        throw new IllegalStateException(
                "AV02 physical tote has an invalid lifecycle state: " + lifecycleRecord.state());
    }

    private static List<PhysicalToteAssignment> activeAssignmentsForSheet(
            Map<OrderSheetKey, List<PhysicalToteAssignment>> activeAssignmentsBySheet,
            OrderSheetKey sheet) {
        return activeAssignmentsBySheet.getOrDefault(sheet, List.of());
    }

    private static Map<OrderSheetKey, List<PhysicalToteAssignment>> indexActiveAssignmentsBySheet(
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        Map<OrderSheetKey, List<PhysicalToteAssignment>> result = new LinkedHashMap<>();
        for (PhysicalToteAssignment assignment : lifecycleSnapshot.assignments()) {
            if (assignment.active()) {
                result.computeIfAbsent(assignment.orderSheetKey(), ignored -> new ArrayList<>())
                        .add(assignment);
            }
        }
        result.replaceAll((sheet, assignments) -> List.copyOf(assignments));
        return Collections.unmodifiableMap(result);
    }

    private static Set<OrderSheetKey> compatibilityAuthorizedEmptyOrderSheetKeys(
            List<DspSchedulerOrderState> orderStates) {
        Set<OrderSheetKey> result = new LinkedHashSet<>();
        if (orderStates == null) {
            return result;
        }
        for (DspSchedulerOrderState orderState : orderStates) {
            if (orderState != null
                    && orderState.order().orderType() == OrderType.EMPTY
                    && orderState.routeRequirements().requiresP2p()
                    && orderState.status() != DspOrderStatus.COMPLETED) {
                result.add(orderState.order().orderSheetKey());
            }
        }
        return result;
    }

    private static void validateManifest(
            DspSchedulerOrderState orderState,
            InboundToteManifest manifest) {
        if (manifest.orderType() != orderState.order().orderType()) {
            throw new IllegalStateException("Inbound manifest order type does not match scheduler order");
        }
        if (!manifest.serviceCentreId().equals(orderState.order().serviceCentreId())) {
            throw new IllegalStateException("Inbound manifest service centre does not match scheduler order");
        }
    }

    private record ValidationCache(
            List<DspSchedulerOrderState> orderStates,
            InboundToteManifestCatalog manifestCatalog,
            Av02InventorySnapshot av02InventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot,
            Set<OrderSheetKey> authorizedEmptyOrderSheetKeys,
            P2pServiceCentreWorkSnapshot snapshot) {

        private boolean matches(
                List<DspSchedulerOrderState> orderStates,
                InboundToteManifestCatalog manifestCatalog,
                Av02InventorySnapshot av02InventorySnapshot,
                PhysicalToteLifecycleSnapshot lifecycleSnapshot,
                Set<OrderSheetKey> authorizedEmptyOrderSheetKeys) {
            return this.orderStates == orderStates
                    && this.manifestCatalog == manifestCatalog
                    && this.av02InventorySnapshot == av02InventorySnapshot
                    && this.lifecycleSnapshot == lifecycleSnapshot
                    && this.authorizedEmptyOrderSheetKeys == authorizedEmptyOrderSheetKeys;
        }
    }
}
