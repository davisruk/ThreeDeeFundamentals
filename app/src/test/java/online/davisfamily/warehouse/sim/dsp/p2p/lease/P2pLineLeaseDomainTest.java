package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

class P2pLineLeaseDomainTest {

    @Test
    void shouldValidateLineDefinitionsAndPhysicalAssignments() {
        P2pLineDefinition definition = definition("p2p-line-1", "p2p-target-1");
        P2pPhysicalToteAssignment assignment = assignment(
                "physical-1", " SC-104 ", definition);

        assertEquals("SC-104", assignment.serviceCentreId());
        assertEquals(definition.lineId(), assignment.lineId());
        assertEquals(definition.destination(), assignment.destination());

        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineDefinition(null, definition.destination()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineDefinition(definition.lineId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineDefinition(
                        definition.lineId(),
                        new OperationalRouteDestination(StationType.ADAPTING, "adapting-1")));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pPhysicalToteAssignment(
                        null, "SC-104", definition.lineId(), definition.destination()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pPhysicalToteAssignment(
                        new PhysicalToteId("physical-1"), " ",
                        definition.lineId(), definition.destination()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pPhysicalToteAssignment(
                        new PhysicalToteId("physical-1"), "SC-104",
                        null, definition.destination()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pPhysicalToteAssignment(
                        new PhysicalToteId("physical-1"), "SC-104",
                        definition.lineId(),
                        new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party")));
    }

    @Test
    void shouldRequireEveryInputActivityToDrain() {
        List<P2pInputActivitySnapshot> blockers = List.of(
                new P2pInputActivitySnapshot(1, 0, false, 0),
                new P2pInputActivitySnapshot(0, 1, false, 0),
                new P2pInputActivitySnapshot(0, 0, true, 0),
                new P2pInputActivitySnapshot(0, 0, false, 1));

        assertTrue(P2pInputActivitySnapshot.idle().empty());
        blockers.forEach(blocker -> {
            assertFalse(blocker.empty());
            assertFalse(activity(blocker, idlePackPath(), idleBagging()).processingDrained());
        });
        assertThrows(IllegalArgumentException.class,
                () -> new P2pInputActivitySnapshot(-1, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pInputActivitySnapshot(0, -1, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pInputActivitySnapshot(0, 0, false, -1));
    }

    @Test
    void shouldRequireEveryPackPathActivityToDrain() {
        List<P2pPackPathActivitySnapshot> blockers = List.of(
                packPath(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                packPath(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                packPath(0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                packPath(0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0),
                packPath(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0),
                packPath(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0),
                packPath(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0),
                packPath(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0),
                packPath(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0),
                packPath(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0),
                packPath(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
                packPath(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1));

        assertTrue(idlePackPath().empty());
        blockers.forEach(blocker -> {
            assertFalse(blocker.empty());
            assertFalse(activity(P2pInputActivitySnapshot.idle(), blocker, idleBagging())
                    .processingDrained());
        });
        assertThrows(IllegalArgumentException.class,
                () -> packPath(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1));
    }

    @Test
    void shouldRequireEveryBaggingActivityToDrain() {
        List<P2pBaggingActivitySnapshot> blockers = List.of(
                bagging(true, false, false, 0, false, false, false, 0),
                bagging(false, true, false, 0, false, false, false, 0),
                bagging(false, false, true, 0, false, false, false, 0),
                bagging(false, false, false, 1, false, false, false, 0),
                bagging(false, false, false, 0, true, false, false, 0),
                bagging(false, false, false, 0, false, true, false, 0),
                bagging(false, false, false, 0, false, false, true, 0),
                bagging(false, false, false, 0, false, false, false, 1));

        assertTrue(idleBagging().empty());
        blockers.forEach(blocker -> {
            assertFalse(blocker.empty());
            assertFalse(activity(P2pInputActivitySnapshot.idle(), idlePackPath(), blocker)
                    .processingDrained());
        });
        assertThrows(IllegalArgumentException.class,
                () -> bagging(false, false, false, -1, false, false, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> bagging(false, false, false, 0, false, false, false, -1));
    }

    @Test
    void shouldDistinguishProcessingDrainFromOutboundToteQuiescence() {
        P2pLineDefinition definition = definition("p2p-line-1", "p2p-target-1");
        OutboundToteSnapshot openTote = outboundTote(
                "outbound-1", definition.lineId(), "SC-104", "pharmacy-1", Optional.empty());
        P2pLineActivitySnapshot withOpenTote = new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                idlePackPath(),
                idleBagging(),
                Optional.of(openTote));

        assertTrue(P2pLineActivitySnapshot.idle().processingDrained());
        assertTrue(P2pLineActivitySnapshot.idle().quiescent());
        assertTrue(withOpenTote.processingDrained());
        assertFalse(withOpenTote.quiescent());

        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineActivitySnapshot(null, idlePackPath(), idleBagging(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineActivitySnapshot(
                        P2pInputActivitySnapshot.idle(), null, idleBagging(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineActivitySnapshot(
                        P2pInputActivitySnapshot.idle(), idlePackPath(), null, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineActivitySnapshot(
                        P2pInputActivitySnapshot.idle(), idlePackPath(), idleBagging(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineActivitySnapshot(
                        P2pInputActivitySnapshot.idle(), idlePackPath(), idleBagging(),
                        Optional.of(outboundTote(
                                "outbound-closed", definition.lineId(), "SC-104", "pharmacy-1",
                                Optional.of(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE)))));
    }

    @Test
    void shouldValidateLeaseOwnershipAndDeriveActivePharmacy() {
        P2pLineDefinition definition = definition("p2p-line-1", "p2p-target-1");
        P2pPhysicalToteAssignment current = assignment("physical-1", "SC-104", definition);
        P2pPhysicalToteAssignment historical = assignment("physical-old", "SC-108", definition);
        List<P2pPhysicalToteAssignment> sourceAssignments = new ArrayList<>(
                List.of(historical, current));
        P2pLineActivitySnapshot activity = activityWithOpenTote(outboundTote(
                "outbound-1", definition.lineId(), "SC-104", "pharmacy-1", Optional.empty()));

        P2pLineLeaseSnapshot snapshot = new P2pLineLeaseSnapshot(
                definition,
                Optional.of(" SC-104 "),
                activity,
                sourceAssignments);
        sourceAssignments.clear();

        assertTrue(snapshot.leased());
        assertEquals(Optional.of("SC-104"), snapshot.serviceCentreId());
        assertEquals(Optional.of("pharmacy-1"), snapshot.activePharmacyId());
        assertEquals(List.of(historical, current), snapshot.physicalAssignments());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.physicalAssignments().clear());

        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseSnapshot(
                        definition, Optional.empty(), activity, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseSnapshot(
                        definition,
                        Optional.of("SC-108"),
                        activity,
                        List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseSnapshot(
                        definition,
                        Optional.of("SC-104"),
                        activityWithOpenTote(outboundTote(
                                "outbound-2", new P2pLineId("other-line"),
                                "SC-104", "pharmacy-1", Optional.empty())),
                        List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseSnapshot(
                        definition,
                        Optional.of("SC-104"),
                        P2pLineActivitySnapshot.idle(),
                        List.of(assignment("physical-2", "SC-104",
                                definition("other-line", "other-target")))));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseSnapshot(
                        definition,
                        Optional.of("SC-104"),
                        P2pLineActivitySnapshot.idle(),
                        List.of(current, current)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseSnapshot(
                        definition,
                        Optional.of("SC-104"),
                        P2pLineActivitySnapshot.idle(),
                        java.util.Arrays.asList(current, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseSnapshot(
                        definition,
                        Optional.of("SC-104"),
                        activityWithOpenTote(unassignedOutboundTote(
                                "outbound-unassigned", definition.lineId())),
                        List.of()));
    }

    @Test
    void shouldPreserveCatalogOrderAndRejectDuplicateIdentities() {
        P2pLineDefinition firstDefinition = definition("p2p-line-1", "p2p-target-1");
        P2pLineDefinition secondDefinition = definition("p2p-line-2", "p2p-target-2");
        P2pPhysicalToteAssignment firstAssignment = assignment(
                "physical-1", "SC-104", firstDefinition);
        P2pPhysicalToteAssignment secondAssignment = assignment(
                "physical-2", "SC-108", secondDefinition);
        P2pLineLeaseSnapshot first = lease(firstDefinition, "SC-104", firstAssignment);
        P2pLineLeaseSnapshot second = lease(secondDefinition, "SC-108", secondAssignment);
        List<P2pLineLeaseSnapshot> sourceLines = new ArrayList<>(List.of(second, first));

        P2pLineLeaseCatalogSnapshot catalog = new P2pLineLeaseCatalogSnapshot(sourceLines);
        sourceLines.clear();

        assertEquals(List.of(second, first), catalog.lines());
        assertEquals(Optional.of(first), catalog.findLine(firstDefinition.lineId()));
        assertEquals(Optional.of(second), catalog.findDestination(secondDefinition.destination()));
        assertEquals(Optional.of(firstAssignment),
                catalog.findAssignment(firstAssignment.physicalToteId()));
        assertTrue(catalog.findLine(new P2pLineId("missing")).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> catalog.lines().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseCatalogSnapshot(List.of(first, first)));

        P2pLineDefinition duplicateDestination = new P2pLineDefinition(
                new P2pLineId("p2p-line-3"), firstDefinition.destination());
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseCatalogSnapshot(List.of(
                        first,
                        lease(duplicateDestination, "SC-108",
                                assignment("physical-3", "SC-108", duplicateDestination)))));

        P2pPhysicalToteAssignment duplicatePhysicalId = assignment(
                "physical-1", "SC-108", secondDefinition);
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseCatalogSnapshot(List.of(
                        first,
                        lease(secondDefinition, "SC-108", duplicatePhysicalId))));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.findLine(null));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.findDestination(null));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.findAssignment(null));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pLineLeaseCatalogSnapshot(
                        java.util.Arrays.asList(first, null)));
    }

    private static P2pLineLeaseSnapshot lease(
            P2pLineDefinition definition,
            String serviceCentreId,
            P2pPhysicalToteAssignment assignment) {
        return new P2pLineLeaseSnapshot(
                definition,
                Optional.of(serviceCentreId),
                P2pLineActivitySnapshot.idle(),
                List.of(assignment));
    }

    private static P2pLineDefinition definition(String lineId, String targetId) {
        return new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, targetId));
    }

    private static P2pPhysicalToteAssignment assignment(
            String physicalToteId,
            String serviceCentreId,
            P2pLineDefinition definition) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalToteId),
                serviceCentreId,
                definition.lineId(),
                definition.destination());
    }

    private static P2pLineActivitySnapshot activity(
            P2pInputActivitySnapshot input,
            P2pPackPathActivitySnapshot packPath,
            P2pBaggingActivitySnapshot bagging) {
        return new P2pLineActivitySnapshot(input, packPath, bagging, Optional.empty());
    }

    private static P2pLineActivitySnapshot activityWithOpenTote(OutboundToteSnapshot tote) {
        return new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                idlePackPath(),
                idleBagging(),
                Optional.of(tote));
    }

    private static P2pPackPathActivitySnapshot idlePackPath() {
        return P2pPackPathActivitySnapshot.idle();
    }

    private static P2pBaggingActivitySnapshot idleBagging() {
        return P2pBaggingActivitySnapshot.idle();
    }

    private static P2pPackPathActivitySnapshot packPath(
            int sorterInputCount,
            int sorterOutputCount,
            int pendingSorterOutfeedCount,
            int pdcPackCount,
            int activePdcTransferCount,
            int nonIdlePrlCount,
            int prlPackCount,
            int activePrlToPcrTransferCount,
            int pcrPackCount,
            int pcrTravellingGroupCount,
            int pcrReleasedGroupCount,
            int outstandingExpectedBagGroupCount) {
        return new P2pPackPathActivitySnapshot(
                sorterInputCount,
                sorterOutputCount,
                pendingSorterOutfeedCount,
                pdcPackCount,
                activePdcTransferCount,
                nonIdlePrlCount,
                prlPackCount,
                activePrlToPcrTransferCount,
                pcrPackCount,
                pcrTravellingGroupCount,
                pcrReleasedGroupCount,
                outstandingExpectedBagGroupCount);
    }

    private static P2pBaggingActivitySnapshot bagging(
            boolean currentBagGroup,
            boolean reservedBagGroup,
            boolean activeBagReservation,
            int pendingBagDischargeCount,
            boolean activeBagDischarge,
            boolean receiverReservation,
            boolean receiverReceivingBag,
            int receiverCompletedBagCount) {
        return new P2pBaggingActivitySnapshot(
                currentBagGroup,
                reservedBagGroup,
                activeBagReservation,
                pendingBagDischargeCount,
                activeBagDischarge,
                receiverReservation,
                receiverReceivingBag,
                receiverCompletedBagCount);
    }

    private static OutboundToteSnapshot outboundTote(
            String toteId,
            P2pLineId lineId,
            String serviceCentreId,
            String pharmacyId,
            Optional<OutboundToteClosureReason> closureReason) {
        return new OutboundToteSnapshot(
                new PhysicalToteId(toteId),
                lineId,
                Optional.of(serviceCentreId),
                Optional.of(pharmacyId),
                10,
                List.of(),
                closureReason);
    }

    private static OutboundToteSnapshot unassignedOutboundTote(
            String toteId,
            P2pLineId lineId) {
        return new OutboundToteSnapshot(
                new PhysicalToteId(toteId),
                lineId,
                Optional.empty(),
                Optional.empty(),
                10,
                List.of(),
                Optional.empty());
    }
}
