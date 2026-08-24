package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;

public final class P2pServiceCentreWorkSnapshotFactory {

    public P2pServiceCentreWorkSnapshot create(
            List<DspSchedulerOrderState> orderStates,
            InboundToteManifestCatalog manifestCatalog,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        if (orderStates == null || manifestCatalog == null || lifecycleSnapshot == null) {
            throw new IllegalArgumentException("P2P work snapshot inputs must not be null");
        }

        Map<String, List<PhysicalToteId>> remaining = new LinkedHashMap<>();
        Map<String, List<OrderSheetKey>> emptyDiagnostics = new LinkedHashMap<>();
        Set<OrderSheetKey> seenSheets = new LinkedHashSet<>();
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
                emptyDiagnostics.computeIfAbsent(serviceCentreId, ignored -> new ArrayList<>()).add(sheet);
                continue;
            }

            List<InboundToteManifest> manifests = manifestCatalog.manifestsFor(sheet);
            if (manifests.isEmpty()) {
                throw new IllegalStateException("P2P-required order has no inbound tote manifest: " + sheet);
            }
            for (InboundToteManifest manifest : manifests) {
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
        return new P2pServiceCentreWorkSnapshot(remaining, emptyDiagnostics);
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
}
