package online.davisfamily.warehouse.sim.dsp.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OutboundToteAllocator {
    private final PhysicalToteLifecycleLedger lifecycleLedger;
    private final OutboundToteIdSource toteIdSource;
    private final OutputSheetAllocator outputSheetAllocator;
    private final OutboundToteConfig config;
    private final Map<P2pLineId, MutableOutboundTote> openTotesByLine = new LinkedHashMap<>();
    private final Set<P2pLineId> lineOrder = new LinkedHashSet<>();
    private final List<OutboundToteSnapshot> closedTotes = new ArrayList<>();
    private final List<AllocatedOutboundBag> allocatedBags = new ArrayList<>();
    private final Set<BagKey> allocatedBagKeys = new LinkedHashSet<>();
    private final Map<P2pLineId, Duration> lastMutationTimeByLine = new LinkedHashMap<>();

    public OutboundToteAllocator(
            PhysicalToteLifecycleLedger lifecycleLedger,
            OutboundToteIdSource toteIdSource,
            OutputSheetAllocator outputSheetAllocator,
            OutboundToteConfig config) {
        if (lifecycleLedger == null
                || toteIdSource == null
                || outputSheetAllocator == null
                || config == null) {
            throw new IllegalArgumentException("Outbound tote allocator inputs must not be null");
        }
        this.lifecycleLedger = lifecycleLedger;
        this.toteIdSource = toteIdSource;
        this.outputSheetAllocator = outputSheetAllocator;
        this.config = config;
    }

    public AllocatedOutboundBag allocate(
            P2pLineId lineId,
            PlannedBag bag,
            Duration allocationTime) {
        requireLineAndTime(lineId, allocationTime);
        if (bag == null) {
            throw new IllegalArgumentException("bag must not be null");
        }
        if (allocatedBagKeys.contains(bag.bagKey())) {
            throw new IllegalStateException("Planned bag is already allocated: " + bag.bagKey());
        }
        rejectActiveNonOutboundSourceAssignments(bag);

        MutableOutboundTote currentTote = openTotesByLine.get(lineId);
        OutboundToteClosureReason mismatchReason = mismatchReason(currentTote, bag);
        if (mismatchReason != null) {
            closeCurrentTote(lineId, mismatchReason, allocationTime);
            currentTote = null;
        }

        if (currentTote == null) {
            PhysicalToteId toteId = toteIdSource.nextId(lineId);
            if (lifecycleLedger.tote(toteId).isPresent()) {
                throw new IllegalStateException("Outbound physical tote ID is already registered: " + toteId.value());
            }
            List<OutputSheetAllocation> outputSheets = outputSheetAllocator.resolve(
                    bag.owningOrderSheetKeys(), toteId, lifecycleLedger.snapshot());
            lifecycleLedger.register(PhysicalToteRecord.outboundBag(toteId));
            currentTote = new MutableOutboundTote(toteId, lineId, config.maximumBagCount());
            openTotesByLine.put(lineId, currentTote);
            lineOrder.add(lineId);
            return completeAllocation(currentTote, bag, outputSheets, allocationTime);
        }

        List<OutputSheetAllocation> outputSheets = outputSheetAllocator.resolve(
                bag.owningOrderSheetKeys(), currentTote.physicalToteId, lifecycleLedger.snapshot());
        return completeAllocation(currentTote, bag, outputSheets, allocationTime);
    }

    public Optional<OutboundToteSnapshot> closeForApplicableWorkCompletion(
            P2pLineId lineId,
            Duration time) {
        return closeExplicitly(lineId, OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE, time);
    }

    public Optional<OutboundToteSnapshot> closeForServiceCentreChange(
            P2pLineId lineId,
            Duration time) {
        return closeExplicitly(lineId, OutboundToteClosureReason.SERVICE_CENTRE_CHANGED, time);
    }

    public Optional<OutboundToteSnapshot> closeForHardCutoff(P2pLineId lineId, Duration time) {
        return closeExplicitly(lineId, OutboundToteClosureReason.HARD_CUTOFF, time);
    }

    public OutboundAllocationSnapshot snapshot() {
        Map<P2pLineId, OutboundToteSnapshot> openSnapshots = new LinkedHashMap<>();
        for (P2pLineId lineId : lineOrder) {
            MutableOutboundTote tote = openTotesByLine.get(lineId);
            if (tote != null) {
                openSnapshots.put(lineId, tote.snapshot(Optional.empty()));
            }
        }
        return new OutboundAllocationSnapshot(openSnapshots, closedTotes, allocatedBags);
    }

    private AllocatedOutboundBag completeAllocation(
            MutableOutboundTote currentTote,
            PlannedBag bag,
            List<OutputSheetAllocation> outputSheets,
            Duration allocationTime) {
        validateOutputAssignments(currentTote.physicalToteId, outputSheets);
        for (OutputSheetAllocation outputSheet : outputSheets) {
            if (lifecycleLedger.activeAssignmentFor(outputSheet.outputSheetKey()).isEmpty()) {
                lifecycleLedger.assign(
                        outputSheet.outputSheetKey(),
                        currentTote.physicalToteId,
                        PhysicalToteAssignmentStage.OUTBOUND_BAG,
                        allocationTime);
            }
        }

        AllocatedOutboundBag allocatedBag = new AllocatedOutboundBag(
                bag,
                currentTote.physicalToteId,
                outputSheets);
        currentTote.add(allocatedBag);
        allocatedBags.add(allocatedBag);
        allocatedBagKeys.add(bag.bagKey());
        lastMutationTimeByLine.put(currentTote.p2pLineId, allocationTime);

        if (currentTote.full()) {
            closeCurrentTote(
                    currentTote.p2pLineId,
                    OutboundToteClosureReason.CAPACITY_REACHED,
                    allocationTime);
        }
        return allocatedBag;
    }

    private Optional<OutboundToteSnapshot> closeExplicitly(
            P2pLineId lineId,
            OutboundToteClosureReason reason,
            Duration time) {
        requireLineAndTime(lineId, time);
        if (!openTotesByLine.containsKey(lineId)) {
            return Optional.empty();
        }
        return Optional.of(closeCurrentTote(lineId, reason, time));
    }

    private OutboundToteSnapshot closeCurrentTote(
            P2pLineId lineId,
            OutboundToteClosureReason reason,
            Duration time) {
        MutableOutboundTote tote = openTotesByLine.get(lineId);
        if (tote == null) {
            throw new IllegalStateException("P2P line has no open outbound tote: " + lineId.value());
        }
        if (tote.allocatedBags.isEmpty()) {
            throw new IllegalStateException("Cannot close an unassigned outbound tote");
        }

        List<PhysicalToteAssignment> assignments = lifecycleLedger
                .activeAssignmentsFor(tote.physicalToteId);
        for (PhysicalToteAssignment assignment : assignments) {
            if (assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND_BAG) {
                throw new IllegalStateException(
                        "Open outbound tote has an assignment outside OUTBOUND_BAG: " + assignment.orderSheetKey());
            }
        }
        for (PhysicalToteAssignment assignment : assignments) {
            lifecycleLedger.terminateActiveAssignment(
                    assignment.orderSheetKey(),
                    time,
                    PhysicalToteAssignmentEndReason.OUTBOUND_TOTE_CLOSED);
            lifecycleLedger.assign(
                    assignment.orderSheetKey(),
                    tote.physicalToteId,
                    PhysicalToteAssignmentStage.OUTBOUND,
                    time);
        }
        lifecycleLedger.transitionTote(tote.physicalToteId, PhysicalToteLifecycleState.OUTBOUND);

        OutboundToteSnapshot closedSnapshot = tote.snapshot(Optional.of(reason));
        closedTotes.add(closedSnapshot);
        openTotesByLine.remove(lineId);
        lastMutationTimeByLine.put(lineId, time);
        return closedSnapshot;
    }

    private void validateOutputAssignments(
            PhysicalToteId targetToteId,
            List<OutputSheetAllocation> outputSheets) {
        for (OutputSheetAllocation outputSheet : outputSheets) {
            Optional<PhysicalToteAssignment> active = lifecycleLedger
                    .activeAssignmentFor(outputSheet.outputSheetKey());
            if (active.isEmpty()) {
                continue;
            }
            PhysicalToteAssignment assignment = active.orElseThrow();
            if (!assignment.physicalToteId().equals(targetToteId)
                    || (assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND_BAG
                            && assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND)) {
                throw new IllegalStateException(
                        "Output sheet is already assigned to another physical tote or stage: "
                                + outputSheet.outputSheetKey());
            }
        }
    }

    private void rejectActiveNonOutboundSourceAssignments(PlannedBag bag) {
        for (var sourceSheet : bag.owningOrderSheetKeys()) {
            lifecycleLedger.activeAssignmentFor(sourceSheet).ifPresent(assignment -> {
                if (assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND_BAG
                        && assignment.stage() != PhysicalToteAssignmentStage.OUTBOUND) {
                    throw new IllegalStateException(
                            "Source sheet still has an active non-outbound assignment: " + sourceSheet);
                }
            });
        }
    }

    private OutboundToteClosureReason mismatchReason(MutableOutboundTote tote, PlannedBag bag) {
        if (tote == null || !tote.assigned()) {
            return null;
        }
        if (!tote.serviceCentreId.equals(bag.serviceCentreId())) {
            return OutboundToteClosureReason.SERVICE_CENTRE_CHANGED;
        }
        if (!tote.pharmacyId.equals(bag.pharmacyId())) {
            return OutboundToteClosureReason.PHARMACY_CHANGED;
        }
        return null;
    }

    private void requireLineAndTime(P2pLineId lineId, Duration time) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        if (time == null || time.isNegative()) {
            throw new IllegalArgumentException("time must not be null or negative");
        }
        Duration lastMutationTime = lastMutationTimeByLine.get(lineId);
        if (lastMutationTime != null && time.compareTo(lastMutationTime) < 0) {
            throw new IllegalArgumentException("time must not precede the last mutation on the P2P line");
        }
    }

    private static final class MutableOutboundTote {
        private final PhysicalToteId physicalToteId;
        private final P2pLineId p2pLineId;
        private final int maximumBagCount;
        private final List<AllocatedOutboundBag> allocatedBags = new ArrayList<>();
        private String serviceCentreId;
        private String pharmacyId;

        private MutableOutboundTote(
                PhysicalToteId physicalToteId,
                P2pLineId p2pLineId,
                int maximumBagCount) {
            this.physicalToteId = physicalToteId;
            this.p2pLineId = p2pLineId;
            this.maximumBagCount = maximumBagCount;
        }

        private void add(AllocatedOutboundBag bag) {
            if (full()) {
                throw new IllegalStateException("Outbound tote is already full");
            }
            if (!assigned()) {
                serviceCentreId = bag.plannedBag().serviceCentreId();
                pharmacyId = bag.plannedBag().pharmacyId();
            } else if (!serviceCentreId.equals(bag.plannedBag().serviceCentreId())
                    || !pharmacyId.equals(bag.plannedBag().pharmacyId())) {
                throw new IllegalStateException("Outbound tote purity violation");
            }
            allocatedBags.add(bag);
        }

        private boolean assigned() {
            return serviceCentreId != null;
        }

        private boolean full() {
            return allocatedBags.size() >= maximumBagCount;
        }

        private OutboundToteSnapshot snapshot(Optional<OutboundToteClosureReason> closureReason) {
            return new OutboundToteSnapshot(
                    physicalToteId,
                    p2pLineId,
                    Optional.ofNullable(serviceCentreId),
                    Optional.ofNullable(pharmacyId),
                    maximumBagCount,
                    allocatedBags,
                    closureReason);
        }
    }
}
