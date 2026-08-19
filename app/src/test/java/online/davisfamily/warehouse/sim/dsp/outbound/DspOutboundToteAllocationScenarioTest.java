package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class DspOutboundToteAllocationScenarioTest {
    private static final P2pLineId LINE = new P2pLineId("p2p-1");
    private static final OrderSheetKey CAPACITY_SHEET = sheet("order-capacity", 1);
    private static final OrderSheetKey COMPANION_SHEET = sheet("order-companion", 1);
    private static final OrderSheetKey OTHER_PHARMACY_SHEET = sheet("order-other-pharmacy", 1);

    @Test
    void shouldAllocatePlannedBagsToIndependentOutboundPhysicalTotes() {
        Scenario scenario = createScenario();

        assertEquals(
                scenario.planningResult().plannedBags().stream().map(PlannedBag::bagKey).toList(),
                scenario.snapshot().allocatedBags().stream().map(AllocatedOutboundBag::bagKey).toList());
        assertEquals(
                List.of(
                        "outbound-p2p-1-1",
                        "outbound-p2p-1-1",
                        "outbound-p2p-1-2",
                        "outbound-p2p-1-3"),
                scenario.snapshot().allocatedBags().stream()
                        .map(allocation -> allocation.outboundPhysicalToteId().value())
                        .toList());
        assertTrue(scenario.receiver().getReceivedBags().isEmpty());
        assertEquals(3, scenario.snapshot().closedTotes().size());
    }

    @Test
    void shouldPreservePurityCapacityAndDeterministicOutputSheets() {
        Scenario scenario = createScenario();

        for (OutboundToteSnapshot tote : scenario.snapshot().closedTotes()) {
            assertTrue(tote.bagCount() <= tote.maximumBagCount());
            assertEquals(1, tote.allocatedBags().stream()
                    .map(allocation -> allocation.plannedBag().serviceCentreId())
                    .distinct()
                    .count());
            assertEquals(1, tote.allocatedBags().stream()
                    .map(allocation -> allocation.plannedBag().pharmacyId())
                    .distinct()
                    .count());
        }
        assertEquals(
                List.of(
                        OutboundToteClosureReason.CAPACITY_REACHED,
                        OutboundToteClosureReason.PHARMACY_CHANGED,
                        OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE),
                scenario.snapshot().closedTotes().stream()
                        .map(tote -> tote.closureReason().orElseThrow())
                        .toList());
        assertEquals(
                List.of(
                        CAPACITY_SHEET,
                        COMPANION_SHEET,
                        sheet("order-capacity", 2),
                        OTHER_PHARMACY_SHEET),
                scenario.snapshot().allocatedBags().stream()
                        .map(allocation -> allocation.outputSheetAllocations().getFirst().outputSheetKey())
                        .toList());
        assertEquals(
                List.of(
                        CAPACITY_SHEET,
                        COMPANION_SHEET,
                        CAPACITY_SHEET,
                        OTHER_PHARMACY_SHEET),
                scenario.snapshot().allocatedBags().stream()
                        .map(allocation -> allocation.outputSheetAllocations().getFirst().sourceOwningSheetKey())
                        .toList());
    }

    @Test
    void shouldRetainBagPackAndLogicalProvenanceAcrossP2pBoundary() {
        Scenario scenario = createScenario();

        for (AllocatedOutboundBag allocation : scenario.snapshot().allocatedBags()) {
            PlannedBag plannedBag = scenario.planningResult()
                    .findBag(allocation.bagKey())
                    .orElseThrow();
            assertEquals(plannedBag, allocation.plannedBag());
            assertEquals(plannedBag.owningOrderSheetKeys(), allocation.outputSheetAllocations().stream()
                    .map(OutputSheetAllocation::sourceOwningSheetKey)
                    .toList());
            for (String packId : plannedBag.physicalPackIds()) {
                PlannedPackTrace trace = scenario.planningResult().findPackTrace(packId).orElseThrow();
                assertEquals(plannedBag.bagKey(), trace.bagKey());
                assertEquals(plannedBag.owningOrderSheetKeys().getFirst(), trace.fulfilmentOrderSheetKey());
                assertEquals(trace.fulfilmentOrderSheetKey(),
                        trace.sourceProvenance().sourceOrderSheetKey());
            }
        }
        PlannedPackTrace overflowTrace = scenario.planningResult()
                .findPackTrace("pack-capacity-2")
                .orElseThrow();
        assertEquals(CAPACITY_SHEET, overflowTrace.fulfilmentOrderSheetKey());
        assertEquals(CAPACITY_SHEET, overflowTrace.sourceProvenance().sourceOrderSheetKey());
        assertEquals(sheet("order-capacity", 2), scenario.snapshot().allocatedBags().get(2)
                .outputSheetAllocations().getFirst().outputSheetKey());
    }

    @Test
    void shouldKeepInboundAndOutboundPhysicalToteLifecyclesSeparate() {
        Scenario scenario = createScenario();
        Set<PhysicalToteId> inboundIds = Set.copyOf(scenario.inboundToteIds());
        Set<PhysicalToteId> outboundIds = scenario.snapshot().closedTotes().stream()
                .map(OutboundToteSnapshot::physicalToteId)
                .collect(Collectors.toSet());

        assertTrue(inboundIds.stream().noneMatch(outboundIds::contains));
        for (PhysicalToteId inboundId : inboundIds) {
            assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    scenario.ledger().tote(inboundId).orElseThrow().state());
        }
        for (PhysicalToteId outboundId : outboundIds) {
            assertEquals(PhysicalToteLifecycleState.OUTBOUND,
                    scenario.ledger().tote(outboundId).orElseThrow().state());
        }
        assertEquals(PhysicalToteAssignmentStage.PRE_P2P,
                scenario.ledger().assignmentHistoryFor(CAPACITY_SHEET).getFirst().stage());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                scenario.ledger().assignmentHistoryFor(CAPACITY_SHEET).getFirst()
                        .endReason().orElseThrow());
        assertEquals(PhysicalToteAssignmentStage.OUTBOUND,
                scenario.ledger().activeAssignmentFor(CAPACITY_SHEET).orElseThrow().stage());
        assertFalse(scenario.ledger().activeAssignmentFor(sheet("order-capacity", 2)).isEmpty());
    }

    private static Scenario createScenario() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        List<PhysicalToteId> inboundToteIds = List.of(
                new PhysicalToteId("inbound-capacity"),
                new PhysicalToteId("inbound-companion"),
                new PhysicalToteId("inbound-other-pharmacy"));
        consumeAtP2p(ledger, CAPACITY_SHEET, inboundToteIds.get(0), Duration.ofSeconds(1));
        consumeAtP2p(ledger, COMPANION_SHEET, inboundToteIds.get(1), Duration.ofSeconds(2));
        consumeAtP2p(ledger, OTHER_PHARMACY_SHEET, inboundToteIds.get(2), Duration.ofSeconds(3));

        List<PlannedBag> plannedBags = List.of(
                plannedBag("rx-capacity", 1, "pharmacy-1", CAPACITY_SHEET, "pack-capacity-1"),
                plannedBag("rx-companion", 1, "pharmacy-1", COMPANION_SHEET, "pack-companion-1"),
                plannedBag("rx-capacity", 2, "pharmacy-1", CAPACITY_SHEET, "pack-capacity-2"),
                plannedBag("rx-other", 1, "pharmacy-2", OTHER_PHARMACY_SHEET, "pack-other-1"));
        List<PlannedPackTrace> packTraces = List.of(
                trace(plannedBags.get(0), CAPACITY_SHEET, inboundToteIds.get(0), "line-capacity-1"),
                trace(plannedBags.get(1), COMPANION_SHEET, inboundToteIds.get(1), "line-companion-1"),
                trace(plannedBags.get(2), CAPACITY_SHEET, inboundToteIds.get(0), "line-capacity-2"),
                trace(plannedBags.get(3), OTHER_PHARMACY_SHEET, inboundToteIds.get(2), "line-other-1"));
        BagPlanningResult planningResult = new BagPlanningResult(
                plannedBags, List.of(), packTraces);
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(
                        CAPACITY_SHEET,
                        COMPANION_SHEET,
                        OTHER_PHARMACY_SHEET)),
                new OutboundToteConfig(2));
        StoredBagReceiver receiver = new StoredBagReceiver("completed-bags");
        for (PlannedBag plannedBag : plannedBags) {
            receive(receiver, runtimeBag(plannedBag));
        }
        OutboundToteAllocationController controller = new OutboundToteAllocationController(
                LINE, receiver, planningResult, allocator);
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(10d);
        controller.update(context, 0.1d);
        allocator.closeForApplicableWorkCompletion(LINE, Duration.ofSeconds(11));

        return new Scenario(
                ledger,
                planningResult,
                receiver,
                inboundToteIds,
                allocator.snapshot());
    }

    private static void consumeAtP2p(
            PhysicalToteLifecycleLedger ledger,
            OrderSheetKey sheet,
            PhysicalToteId toteId,
            Duration time) {
        ledger.register(PhysicalToteRecord.inboundPack(toteId));
        ledger.transitionTote(toteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        ledger.assign(sheet, toteId, PhysicalToteAssignmentStage.PRE_P2P, time);
        ledger.terminateActiveAssignment(
                sheet,
                time.plusSeconds(1),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        ledger.transitionTote(toteId, PhysicalToteLifecycleState.CONSUMED_AT_P2P);
    }

    private static PlannedBag plannedBag(
            String prescriptionId,
            int bagOrdinal,
            String pharmacyId,
            OrderSheetKey owningSheet,
            String packId) {
        return new PlannedBag(
                new BagKey(prescriptionId, bagOrdinal),
                "SC-104",
                pharmacyId,
                "patient-1",
                prescriptionId,
                List.of(packId),
                List.of(owningSheet));
    }

    private static PlannedPackTrace trace(
            PlannedBag bag,
            OrderSheetKey sourceSheet,
            PhysicalToteId inputToteId,
            String lineReference) {
        PackSourceProvenance provenance = new PackSourceProvenance(
                sourceSheet,
                lineReference,
                "product-" + lineReference,
                bag.serviceCentreId(),
                bag.pharmacyId(),
                bag.patientId(),
                bag.prescriptionId());
        return new PlannedPackTrace(
                bag.physicalPackIds().getFirst(),
                provenance,
                inputToteId,
                bag.owningOrderSheetKeys().getFirst(),
                bag.bagKey());
    }

    private static Bag runtimeBag(PlannedBag plannedBag) {
        return new Bag(
                "runtime-" + plannedBag.bagKey().correlationId(),
                plannedBag.bagKey().correlationId(),
                plannedBag.physicalPackIds().stream()
                        .map(packId -> new PackPlan(
                                packId,
                                plannedBag.bagKey().correlationId(),
                                new PackDimensions(0.20f, 0.10f, 0.08f)))
                        .toList(),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }

    private static void receive(StoredBagReceiver receiver, Bag bag) {
        BagReservation reservation = receiver.reserveIncomingBag(bag);
        receiver.beginReceiving(reservation);
        receiver.completeReceiving(reservation);
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }

    private record Scenario(
            PhysicalToteLifecycleLedger ledger,
            BagPlanningResult planningResult,
            StoredBagReceiver receiver,
            List<PhysicalToteId> inboundToteIds,
            OutboundAllocationSnapshot snapshot) {
    }
}
