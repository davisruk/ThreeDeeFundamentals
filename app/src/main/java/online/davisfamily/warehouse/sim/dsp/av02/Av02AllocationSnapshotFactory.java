package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;

public final class Av02AllocationSnapshotFactory {
    private final DspDependencyEvaluator dependencyEvaluator;

    public Av02AllocationSnapshotFactory() {
        this(new DspDependencyEvaluator());
    }

    public Av02AllocationSnapshotFactory(DspDependencyEvaluator dependencyEvaluator) {
        if (dependencyEvaluator == null) {
            throw new IllegalArgumentException("dependencyEvaluator must not be null");
        }
        this.dependencyEvaluator = dependencyEvaluator;
    }

    public Av02AllocationSnapshot create(
            long snapshotSequence,
            WarehouseSchedulerSnapshot schedulerSnapshot,
            DspSupplySnapshot supplySnapshot,
            Av02InventorySnapshot inventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        if (snapshotSequence < 0) {
            throw new IllegalArgumentException("snapshotSequence must be >= 0");
        }
        if (schedulerSnapshot == null || supplySnapshot == null
                || inventorySnapshot == null || lifecycleSnapshot == null) {
            throw new IllegalArgumentException("AV02 allocation snapshot inputs must not be null");
        }

        validateLogicalOrderIdentities(schedulerSnapshot.orderStates());
        Set<OrderSheetKey> waitingSheets = new LinkedHashSet<>();
        inventorySnapshot.waitingTotes().forEach(tote -> waitingSheets.add(tote.orderSheetKey()));

        List<Av02AllocationCandidate> candidates = new ArrayList<>();
        for (DspSchedulerOrderState orderState : schedulerSnapshot.orderStates()) {
            NotionalToteOrder order = orderState.order();
            if (order.orderType() != OrderType.EMPTY || terminal(orderState.status())
                    || waitingSheets.contains(order.orderSheetKey())) {
                continue;
            }

            List<Av02AllocationBlockReason> blockReasons = new ArrayList<>();
            if (inventorySnapshot.full()) {
                blockReasons.add(Av02AllocationBlockReason.NO_AV02_CAPACITY);
            }
            if (!supplySnapshot.authorizedEmptyOrderSheetKeys().contains(order.orderSheetKey())) {
                blockReasons.add(Av02AllocationBlockReason.EMPTY_NOT_AUTHORIZED);
            }
            if (!dependencyEvaluator.findBlocks(orderState, schedulerSnapshot).isEmpty()) {
                blockReasons.add(Av02AllocationBlockReason.DEPENDENCY_NOT_READY);
            }
            if (lifecycleSnapshot.activeAssignmentFor(order.orderSheetKey()).isPresent()) {
                blockReasons.add(Av02AllocationBlockReason.ACTIVE_PHYSICAL_ASSIGNMENT);
            }
            candidates.add(new Av02AllocationCandidate(
                    order,
                    pharmacyId(order),
                    blockReasons));
        }

        List<Av02AllocationCandidate> orderedCandidates = orderCandidates(candidates);
        Optional<Av02AllocationCandidate> selected = selectCandidate(orderedCandidates);
        Optional<AllocateEmptyToteAtAv02Command> command = selected.map(candidate ->
                new AllocateEmptyToteAtAv02Command(
                        snapshotSequence,
                        candidate.orderSheetKey(),
                        candidate.serviceCentreId()));
        return new Av02AllocationSnapshot(
                snapshotSequence,
                inventorySnapshot,
                orderedCandidates,
                command);
    }

    private static void validateLogicalOrderIdentities(List<DspSchedulerOrderState> orderStates) {
        Set<OrderSheetKey> sheetKeys = new LinkedHashSet<>();
        Map<String, Integer> priorityByServiceCentre = new LinkedHashMap<>();
        for (DspSchedulerOrderState orderState : orderStates) {
            if (orderState == null) {
                throw new IllegalArgumentException("scheduler order states must not contain null");
            }
            NotionalToteOrder order = orderState.order();
            if (!sheetKeys.add(order.orderSheetKey())) {
                throw new IllegalArgumentException(
                        "Duplicate logical order state for sheet " + order.orderSheetKey());
            }
            String serviceCentreId = order.serviceCentreId().trim();
            Integer existingPriority = priorityByServiceCentre.putIfAbsent(
                    serviceCentreId, order.orderPriority());
            if (existingPriority != null && existingPriority != order.orderPriority()) {
                throw new IllegalArgumentException(
                        "Logical orders for service centre " + serviceCentreId
                                + " must have one consistent order priority");
            }
        }
    }

    private static List<Av02AllocationCandidate> orderCandidates(
            List<Av02AllocationCandidate> candidates) {
        Map<String, Map<String, Integer>> pharmacyGroupByServiceCentre = new LinkedHashMap<>();
        List<Av02AllocationCandidate> sourceOrdered = candidates.stream()
                .sorted(sourceComparator())
                .toList();
        for (Av02AllocationCandidate candidate : sourceOrdered) {
            Map<String, Integer> pharmacyGroups = pharmacyGroupByServiceCentre.computeIfAbsent(
                    candidate.serviceCentreId(), ignored -> new LinkedHashMap<>());
            pharmacyGroups.computeIfAbsent(candidate.pharmacyId(), ignored -> pharmacyGroups.size());
        }

        Comparator<Av02AllocationCandidate> comparator = Comparator
                .comparingInt(Av02AllocationCandidate::orderPriority)
                .reversed()
                .thenComparing(Av02AllocationCandidate::serviceCentreId)
                .thenComparingInt(candidate -> pharmacyGroupByServiceCentre
                        .get(candidate.serviceCentreId())
                        .get(candidate.pharmacyId()))
                .thenComparing(sourceComparator());
        return candidates.stream().sorted(comparator).toList();
    }

    private static Optional<Av02AllocationCandidate> selectCandidate(
            List<Av02AllocationCandidate> orderedCandidates) {
        Optional<Av02AllocationCandidate> firstEligible = orderedCandidates.stream()
                .filter(Av02AllocationCandidate::eligible)
                .findFirst();
        if (firstEligible.isEmpty()) {
            return Optional.empty();
        }
        String selectedServiceCentreId = firstEligible.orElseThrow().serviceCentreId();
        return orderedCandidates.stream()
                .filter(Av02AllocationCandidate::eligible)
                .filter(candidate -> candidate.serviceCentreId().equals(selectedServiceCentreId))
                .findFirst();
    }

    private static Comparator<Av02AllocationCandidate> sourceComparator() {
        return Comparator
                .comparingLong(Av02AllocationCandidate::sourceSequenceNumber)
                .thenComparingInt(candidate -> candidate.order().sheetNumber())
                .thenComparing(candidate -> candidate.order().orderId());
    }

    private static String pharmacyId(NotionalToteOrder order) {
        Set<String> pharmacyIds = new LinkedHashSet<>();
        order.items().forEach(item -> pharmacyIds.add(item.pharmacyId()));
        if (pharmacyIds.size() != 1) {
            throw new IllegalArgumentException(
                    "EMPTY order must contain exactly one pharmacy: " + order.orderSheetKey());
        }
        return pharmacyIds.iterator().next();
    }

    private static boolean terminal(DspOrderStatus status) {
        return status == DspOrderStatus.RELEASED || status == DspOrderStatus.COMPLETED;
    }
}
