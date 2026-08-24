package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public final class DspOperationalReleaseSnapshotFactory {

    public DspOperationalReleaseSnapshot create(
            OsrProcessingReleaseSnapshot physicalSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            WarehouseSchedulerSnapshot logicalSnapshot) {
        List<DspOperationalReleaseCandidate> joinedCandidates = joinCandidates(
                physicalSnapshot, manifestCatalog, logicalSnapshot);
        return createSnapshot(
                joinedCandidates,
                manifestCatalog,
                logicalSnapshot,
                deriveCompatibilityAdmissions(joinedCandidates, logicalSnapshot));
    }

    public DspOperationalReleaseSnapshot create(
            OsrProcessingReleaseSnapshot physicalSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            WarehouseSchedulerSnapshot logicalSnapshot,
            OperationalCandidateRouteAdmissionFactory routeAdmissionFactory) {
        if (routeAdmissionFactory == null) {
            throw new IllegalArgumentException("routeAdmissionFactory must not be null");
        }
        List<DspOperationalReleaseCandidate> joinedCandidates = joinCandidates(
                physicalSnapshot, manifestCatalog, logicalSnapshot);
        List<OperationalCandidateRouteAdmission> routeAdmissions =
                routeAdmissionFactory.create(joinedCandidates, logicalSnapshot);
        return createSnapshot(
                joinedCandidates,
                manifestCatalog,
                logicalSnapshot,
                routeAdmissions);
    }

    public DspOperationalReleaseSnapshot create(
            OsrProcessingReleaseSnapshot physicalSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            WarehouseSchedulerSnapshot logicalSnapshot,
            OperationalCandidateRouteAdmissionFactory routeAdmissionFactory,
            P2pLineLeaseCatalogSnapshot p2pLineLeases,
            List<OperationalRouteTargetAdmissionSnapshot> p2pTargetAdmissions) {
        if (routeAdmissionFactory == null) {
            throw new IllegalArgumentException("routeAdmissionFactory must not be null");
        }
        if (p2pLineLeases == null || p2pTargetAdmissions == null) {
            throw new IllegalArgumentException("P2P operational snapshot inputs must not be null");
        }
        List<DspOperationalReleaseCandidate> joinedCandidates = joinCandidates(
                physicalSnapshot, manifestCatalog, logicalSnapshot);
        List<OperationalCandidateRouteAdmission> routeAdmissions =
                routeAdmissionFactory.create(joinedCandidates, logicalSnapshot);
        return createSnapshot(
                joinedCandidates,
                manifestCatalog,
                logicalSnapshot,
                routeAdmissions,
                p2pLineLeases,
                p2pTargetAdmissions);
    }

    private static List<DspOperationalReleaseCandidate> joinCandidates(
            OsrProcessingReleaseSnapshot physicalSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            WarehouseSchedulerSnapshot logicalSnapshot) {
        if (physicalSnapshot == null) {
            throw new IllegalArgumentException("physicalSnapshot must not be null");
        }
        if (manifestCatalog == null) {
            throw new IllegalArgumentException("manifestCatalog must not be null");
        }
        if (logicalSnapshot == null) {
            throw new IllegalArgumentException("logicalSnapshot must not be null");
        }

        Map<OrderSheetKey, DspSchedulerOrderState> logicalStatesBySheet =
                indexLogicalStates(logicalSnapshot.orderStates());
        validateServiceCentrePriorities(logicalSnapshot.orderStates());
        List<DspOperationalReleaseCandidate> joinedCandidates = new ArrayList<>();
        for (OsrProcessingReleaseCandidate physicalCandidate : physicalSnapshot.candidates()) {
            InboundToteManifest manifest = manifestCatalog
                    .findByPhysicalToteId(physicalCandidate.physicalToteId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No inbound manifest for physical tote "
                                    + physicalCandidate.physicalToteId().value()));
            DspSchedulerOrderState logicalState = logicalStatesBySheet.get(
                    physicalCandidate.orderSheetKey());
            if (logicalState == null) {
                throw new IllegalArgumentException(
                        "No logical order state for sheet " + physicalCandidate.orderSheetKey());
            }

            validateJoinedIdentity(physicalCandidate, manifest, logicalState);
            validateLogicalStatus(logicalState, physicalCandidate.physicalToteId());
            validateManifestLines(manifest, logicalState);
            joinedCandidates.add(new DspOperationalReleaseCandidate(
                    physicalCandidate,
                    logicalState,
                    distinctPharmacyIds(manifest.items())));
        }

        return List.copyOf(joinedCandidates);
    }

    private static DspOperationalReleaseSnapshot createSnapshot(
            List<DspOperationalReleaseCandidate> joinedCandidates,
            InboundToteManifestCatalog manifestCatalog,
            WarehouseSchedulerSnapshot logicalSnapshot,
            List<OperationalCandidateRouteAdmission> routeAdmissions) {
        return new DspOperationalReleaseSnapshot(
                joinedCandidates,
                buildPharmacyGroups(manifestCatalog),
                logicalSnapshot.stationAdmissions(),
                logicalSnapshot.preparedLineKeys(),
                routeAdmissions);
    }

    private static DspOperationalReleaseSnapshot createSnapshot(
            List<DspOperationalReleaseCandidate> joinedCandidates,
            InboundToteManifestCatalog manifestCatalog,
            WarehouseSchedulerSnapshot logicalSnapshot,
            List<OperationalCandidateRouteAdmission> routeAdmissions,
            P2pLineLeaseCatalogSnapshot p2pLineLeases,
            List<OperationalRouteTargetAdmissionSnapshot> p2pTargetAdmissions) {
        Map<OperationalRouteDestination, Boolean> targetAdmissions = new LinkedHashMap<>();
        for (OperationalRouteTargetAdmissionSnapshot admission : p2pTargetAdmissions) {
            if (admission == null) {
                throw new IllegalArgumentException("p2pTargetAdmissions must not contain null");
            }
            if (admission.stationType() != StationType.P2P) {
                throw new IllegalArgumentException("p2pTargetAdmissions must identify P2P targets");
            }
            OperationalRouteDestination destination = new OperationalRouteDestination(
                    admission.stationType(), admission.targetId());
            if (targetAdmissions.putIfAbsent(destination, admission.canAccept()) != null) {
                throw new IllegalArgumentException("Duplicate P2P target admission: " + destination);
            }
        }
        return new DspOperationalReleaseSnapshot(
                joinedCandidates,
                buildPharmacyGroups(manifestCatalog),
                logicalSnapshot.stationAdmissions(),
                logicalSnapshot.preparedLineKeys(),
                routeAdmissions,
                p2pLineLeases,
                targetAdmissions);
    }

    private static List<OperationalCandidateRouteAdmission> deriveCompatibilityAdmissions(
            List<DspOperationalReleaseCandidate> candidates,
            WarehouseSchedulerSnapshot logicalSnapshot) {
        OperationalRouteEntrySelector routeEntrySelector = new OperationalRouteEntrySelector();
        List<OperationalCandidateRouteAdmission> admissions = new ArrayList<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            routeEntrySelector.firstStation(candidate.logicalOrderState().routeRequirements())
                    .map(logicalSnapshot.stationAdmissions()::get)
                    .filter(admission -> !admission.canAccept()
                            || admission.selectedTargetId().isPresent())
                    .map(admission -> new OperationalCandidateRouteAdmission(
                            candidate.physicalCandidate().physicalToteId(), admission))
                    .ifPresent(admissions::add);
        }
        return List.copyOf(admissions);
    }

    private static Map<OrderSheetKey, DspSchedulerOrderState> indexLogicalStates(
            List<DspSchedulerOrderState> logicalStates) {
        Map<OrderSheetKey, DspSchedulerOrderState> statesBySheet = new LinkedHashMap<>();
        for (DspSchedulerOrderState logicalState : logicalStates) {
            if (logicalState == null) {
                throw new IllegalArgumentException("logical order states must not contain null");
            }
            OrderSheetKey orderSheetKey = logicalState.order().orderSheetKey();
            if (statesBySheet.putIfAbsent(orderSheetKey, logicalState) != null) {
                throw new IllegalArgumentException(
                        "Duplicate logical order state for sheet " + orderSheetKey);
            }
        }
        return statesBySheet;
    }

    private static void validateServiceCentrePriorities(
            List<DspSchedulerOrderState> logicalStates) {
        Map<String, Integer> priorityByServiceCentre = new LinkedHashMap<>();
        for (DspSchedulerOrderState logicalState : logicalStates) {
            String serviceCentreId = logicalState.order().serviceCentreId().trim();
            int orderPriority = logicalState.order().orderPriority();
            Integer existingPriority = priorityByServiceCentre.putIfAbsent(
                    serviceCentreId, orderPriority);
            if (existingPriority != null && existingPriority != orderPriority) {
                throw new IllegalArgumentException(
                        "Logical orders for service centre " + serviceCentreId
                                + " must have one consistent order priority");
            }
        }
    }

    private static List<ServiceCentrePharmacyGroup> buildPharmacyGroups(
            InboundToteManifestCatalog manifestCatalog) {
        List<IndexedManifest> indexedManifests = new ArrayList<>();
        List<InboundToteManifest> manifests = manifestCatalog.manifests();
        for (int index = 0; index < manifests.size(); index++) {
            indexedManifests.add(new IndexedManifest(index, manifests.get(index)));
        }
        indexedManifests.sort(Comparator
                .comparingLong((IndexedManifest value) -> value.manifest().sourceSequenceNumber())
                .thenComparingInt(IndexedManifest::catalogIndex)
                .thenComparing(value -> value.manifest().physicalToteId().value()));

        Map<String, Set<String>> pharmaciesByServiceCentre = new LinkedHashMap<>();
        Map<String, Integer> nextGroupIndexByServiceCentre = new LinkedHashMap<>();
        List<ServiceCentrePharmacyGroup> groups = new ArrayList<>();
        for (IndexedManifest indexedManifest : indexedManifests) {
            InboundToteManifest manifest = indexedManifest.manifest();
            Set<String> encounteredPharmacies = pharmaciesByServiceCentre.computeIfAbsent(
                    manifest.serviceCentreId(), ignored -> new LinkedHashSet<>());
            for (DspOrderItem item : manifest.items()) {
                if (encounteredPharmacies.add(item.pharmacyId())) {
                    int groupIndex = nextGroupIndexByServiceCentre.getOrDefault(
                            manifest.serviceCentreId(), 0);
                    groups.add(new ServiceCentrePharmacyGroup(
                            manifest.serviceCentreId(),
                            item.pharmacyId(),
                            groupIndex,
                            manifest.sourceSequenceNumber()));
                    nextGroupIndexByServiceCentre.put(
                            manifest.serviceCentreId(), groupIndex + 1);
                }
            }
        }
        return List.copyOf(groups);
    }

    private static void validateJoinedIdentity(
            OsrProcessingReleaseCandidate physicalCandidate,
            InboundToteManifest manifest,
            DspSchedulerOrderState logicalState) {
        if (!physicalCandidate.physicalToteId().equals(manifest.physicalToteId())) {
            throw new IllegalArgumentException("Physical candidate and manifest tote ID must match");
        }
        if (!physicalCandidate.orderSheetKey().equals(manifest.orderSheetKey())) {
            throw new IllegalArgumentException("Physical candidate and manifest sheet must match");
        }
        if (physicalCandidate.orderType() != manifest.orderType()) {
            throw new IllegalArgumentException("Physical candidate and manifest order type must match");
        }
        if (!physicalCandidate.serviceCentreId().equals(manifest.serviceCentreId())) {
            throw new IllegalArgumentException(
                    "Physical candidate and manifest service centre must match");
        }

        if (!manifest.orderSheetKey().equals(logicalState.order().orderSheetKey())) {
            throw new IllegalArgumentException("Manifest and logical order sheet must match");
        }
        if (manifest.orderType() != logicalState.order().orderType()) {
            throw new IllegalArgumentException("Manifest and logical order type must match");
        }
        if (!manifest.serviceCentreId().equals(logicalState.order().serviceCentreId().trim())) {
            throw new IllegalArgumentException("Manifest and logical service centre must match");
        }
    }

    private static void validateLogicalStatus(
            DspSchedulerOrderState logicalState,
            PhysicalToteId physicalToteId) {
        if (logicalState.status() != DspOrderStatus.WAITING
                && logicalState.status() != DspOrderStatus.BLOCKED) {
            throw new IllegalArgumentException(
                    "Stored physical tote " + physicalToteId.value()
                            + " cannot join logical status " + logicalState.status());
        }
    }

    private static void validateManifestLines(
            InboundToteManifest manifest,
            DspSchedulerOrderState logicalState) {
        Map<String, DspOrderItem> logicalItemsByLineReference = new LinkedHashMap<>();
        for (DspOrderItem logicalItem : logicalState.order().items()) {
            if (logicalItemsByLineReference.putIfAbsent(
                    logicalItem.lineReference(), logicalItem) != null) {
                throw new IllegalArgumentException(
                        "Duplicate logical line reference " + logicalItem.lineReference()
                                + " for sheet " + logicalState.order().orderSheetKey());
            }
        }
        for (DspOrderItem manifestItem : manifest.items()) {
            DspOrderItem logicalItem = logicalItemsByLineReference.get(
                    manifestItem.lineReference());
            if (logicalItem == null) {
                throw new IllegalArgumentException(
                        "Manifest line " + manifestItem.lineReference()
                                + " is absent from logical sheet "
                                + logicalState.order().orderSheetKey());
            }
            if (!manifestItem.equals(logicalItem)) {
                throw new IllegalArgumentException(
                        "Manifest line " + manifestItem.lineReference()
                                + " contradicts logical sheet "
                                + logicalState.order().orderSheetKey());
            }
        }
    }

    private static List<String> distinctPharmacyIds(List<DspOrderItem> items) {
        Set<String> pharmacyIds = new LinkedHashSet<>();
        for (DspOrderItem item : items) {
            pharmacyIds.add(item.pharmacyId());
        }
        return List.copyOf(pharmacyIds);
    }

    private record IndexedManifest(int catalogIndex, InboundToteManifest manifest) {}
}
