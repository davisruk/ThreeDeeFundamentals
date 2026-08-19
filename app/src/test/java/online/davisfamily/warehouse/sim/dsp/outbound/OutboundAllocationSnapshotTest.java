package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OutboundAllocationSnapshotTest {

    @Test
    void shouldRepresentAssignedOpenAndClosedOutboundTotes() {
        P2pLineId openLine = new P2pLineId("p2p-1");
        P2pLineId closedLine = new P2pLineId("p2p-2");
        AllocatedOutboundBag openBag = allocatedBag("rx-1", "patient-1", "outbound-1", "order-1");
        AllocatedOutboundBag closedBag = allocatedBag("rx-2", "patient-2", "outbound-2", "order-2");
        OutboundToteSnapshot openTote = tote(openLine, "outbound-1", List.of(openBag), Optional.empty(), 3);
        OutboundToteSnapshot closedTote = tote(
                closedLine,
                "outbound-2",
                List.of(closedBag),
                Optional.of(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE),
                3);

        OutboundAllocationSnapshot snapshot = new OutboundAllocationSnapshot(
                Map.of(openLine, openTote),
                List.of(closedTote),
                List.of(openBag, closedBag));

        assertTrue(openTote.open());
        assertFalse(closedTote.open());
        assertTrue(openTote.assigned());
        assertEquals(openTote, snapshot.openToteFor(openLine).orElseThrow());
        assertEquals(closedTote, snapshot.findTote(new PhysicalToteId("outbound-2")).orElseThrow());
    }

    @Test
    void shouldExposeRemainingCapacityAndPatientPresence() {
        AllocatedOutboundBag firstBag = allocatedBag("rx-1", "patient-1", "outbound-1", "order-1");
        AllocatedOutboundBag secondBag = allocatedBag("rx-2", "patient-2", "outbound-1", "order-2");
        OutboundToteSnapshot tote = tote(
                new P2pLineId("p2p-1"),
                "outbound-1",
                List.of(firstBag, secondBag),
                Optional.empty(),
                3);

        assertEquals(2, tote.bagCount());
        assertEquals(1, tote.remainingBagCapacity());
        assertTrue(tote.containsPatient(" patient-1 "));
        assertFalse(tote.containsPatient("patient-3"));
    }

    @Test
    void shouldProvideImmutableLineToteAndBagLookups() {
        P2pLineId lineId = new P2pLineId("p2p-1");
        AllocatedOutboundBag bag = allocatedBag("rx-1", "patient-1", "outbound-1", "order-1");
        OutboundToteSnapshot tote = tote(lineId, "outbound-1", List.of(bag), Optional.empty(), 2);
        LinkedHashMap<P2pLineId, OutboundToteSnapshot> sourceOpenTotes = new LinkedHashMap<>();
        sourceOpenTotes.put(lineId, tote);

        OutboundAllocationSnapshot snapshot = new OutboundAllocationSnapshot(
                sourceOpenTotes, List.of(), List.of(bag));
        sourceOpenTotes.clear();

        assertEquals(tote, snapshot.openToteFor(lineId).orElseThrow());
        assertEquals(tote, snapshot.findTote(tote.physicalToteId()).orElseThrow());
        assertEquals(bag, snapshot.findAllocatedBag(bag.bagKey()).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.openTotesByLine().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.closedTotes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.allocatedBags().clear());
        assertThrows(UnsupportedOperationException.class, () -> tote.allocatedBags().clear());
    }

    @Test
    void shouldRejectPurityCapacityOrDuplicateIdentityViolations() {
        P2pLineId lineId = new P2pLineId("p2p-1");
        AllocatedOutboundBag matching = allocatedBag("rx-1", "patient-1", "outbound-1", "order-1");
        AllocatedOutboundBag otherPharmacy = allocatedBag(
                "rx-2", "patient-2", "other-pharmacy", "SC-1", "outbound-1", "order-2");

        assertThrows(IllegalArgumentException.class,
                () -> tote(lineId, "outbound-1", List.of(matching, otherPharmacy), Optional.empty(), 3));
        assertThrows(IllegalArgumentException.class,
                () -> tote(lineId, "outbound-1",
                        List.of(matching, allocatedBag("rx-2", "patient-2", "outbound-1", "order-2")),
                        Optional.empty(),
                        1));

        OutboundToteSnapshot open = tote(lineId, "outbound-1", List.of(matching), Optional.empty(), 2);
        OutboundToteSnapshot duplicateIdClosed = tote(
                new P2pLineId("p2p-2"),
                "outbound-1",
                List.of(matching),
                Optional.of(OutboundToteClosureReason.HARD_CUTOFF),
                2);
        assertThrows(IllegalArgumentException.class,
                () -> new OutboundAllocationSnapshot(
                        Map.of(lineId, open), List.of(duplicateIdClosed), List.of(matching)));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboundAllocationSnapshot(
                        Map.of(lineId, open), List.of(), List.of(matching, matching)));
    }

    private static OutboundToteSnapshot tote(
            P2pLineId lineId,
            String toteId,
            List<AllocatedOutboundBag> bags,
            Optional<OutboundToteClosureReason> closureReason,
            int capacity) {
        return new OutboundToteSnapshot(
                new PhysicalToteId(toteId),
                lineId,
                Optional.of("SC-1"),
                Optional.of("pharmacy-1"),
                capacity,
                bags,
                closureReason);
    }

    private static AllocatedOutboundBag allocatedBag(
            String prescriptionId,
            String patientId,
            String toteId,
            String orderId) {
        return allocatedBag(prescriptionId, patientId, "pharmacy-1", "SC-1", toteId, orderId);
    }

    private static AllocatedOutboundBag allocatedBag(
            String prescriptionId,
            String patientId,
            String pharmacyId,
            String serviceCentreId,
            String toteId,
            String orderId) {
        OrderSheetKey sheet = new OrderSheetKey(orderId, 1);
        PlannedBag plannedBag = new PlannedBag(
                new BagKey(prescriptionId, 1),
                serviceCentreId,
                pharmacyId,
                patientId,
                prescriptionId,
                List.of("pack-" + prescriptionId),
                List.of(sheet));
        return new AllocatedOutboundBag(
                plannedBag,
                new PhysicalToteId(toteId),
                List.of(new OutputSheetAllocation(sheet, sheet)));
    }
}
