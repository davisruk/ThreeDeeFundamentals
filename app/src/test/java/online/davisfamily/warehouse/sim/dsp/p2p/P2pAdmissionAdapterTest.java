package online.davisfamily.warehouse.sim.dsp.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

class P2pAdmissionAdapterTest {

    @Test
    void shouldOpenP2pAdmissionWhenCapacityAndP2pAdmissionAllow() {
        P2pCapacityStationAdapter adapter = new P2pCapacityStationAdapter(
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                validP2pSnapshot(),
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0),
                "  p2p-ingress  ");

        StationAdmissionSnapshot admission = adapter.admissionFor(validOrder());

        assertTrue(admission.canAccept());
        assertEquals(StationType.P2P, admission.stationType());
        assertTrue(admission.blockedReason().isBlank());
        assertEquals("p2p-ingress", admission.selectedTargetId().orElseThrow());
    }

    @Test
    void shouldCloseP2pAdmissionWhenCapacityIsFull() {
        P2pCapacityStationAdapter adapter = new P2pCapacityStationAdapter(
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                validP2pSnapshot(),
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 1, 1),
                "p2p-ingress");

        StationAdmissionSnapshot admission = adapter.admissionFor(validOrder());

        assertFalse(admission.canAccept());
        assertEquals("Station P2P has no capacity", admission.blockedReason());
        assertTrue(admission.selectedTargetId().isEmpty());
    }

    @Test
    void shouldCloseP2pAdmissionWhenP2pRejectsOrder() {
        P2pCapacityStationAdapter adapter = new P2pCapacityStationAdapter(
                new StaticP2pAdmission(P2pAdmissionResult.rejectedResult("No idle PRL available")),
                validP2pSnapshot(),
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0),
                "p2p-ingress");

        StationAdmissionSnapshot admission = adapter.admissionFor(validOrder());

        assertFalse(admission.canAccept());
        assertEquals("No idle PRL available", admission.blockedReason());
        assertTrue(admission.selectedTargetId().isEmpty());
    }

    @Test
    void shouldKeepCompatibilityAdmissionTargetless() {
        P2pCapacityStationAdapter adapter = new P2pCapacityStationAdapter(
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                validP2pSnapshot(),
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0));

        StationAdmissionSnapshot admission = adapter.admissionFor(validOrder());

        assertTrue(admission.canAccept());
        assertTrue(admission.selectedTargetId().isEmpty());
    }

    @Test
    void shouldDefensivelyCopyP2pAdmissionSnapshotSets() {
        Set<String> activeCorrelations = new LinkedHashSet<>();
        activeCorrelations.add("bag-a");
        Set<String> admissibleCorrelations = new LinkedHashSet<>();
        admissibleCorrelations.add("bag-b");

        P2pAdmissionSnapshot snapshot = new P2pAdmissionSnapshot(
                "p2p-1",
                2,
                activeCorrelations,
                admissibleCorrelations,
                true);

        activeCorrelations.add("bag-c");
        admissibleCorrelations.add("bag-d");

        assertEquals(Set.of("bag-a"), snapshot.activeBagCorrelations());
        assertEquals(Set.of("bag-b"), snapshot.admissibleKnownCorrelations());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.activeBagCorrelations().add("bag-x"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.admissibleKnownCorrelations().add("bag-y"));
    }

    @Test
    void shouldRejectInvalidP2pSnapshotAndResultInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new P2pAdmissionSnapshot("", 1, Set.of(), Set.of(), true));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pAdmissionSnapshot("p2p-1", -1, Set.of(), Set.of(), true));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pAdmissionSnapshot("p2p-1", 1, null, Set.of(), true));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pAdmissionSnapshot("p2p-1", 1, Set.of(), null, true));
        assertThrows(IllegalArgumentException.class,
                () -> P2pAdmissionResult.rejectedResult(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pCapacityStationAdapter(
                        new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                        validP2pSnapshot(),
                        new StationCapacity(1, 1),
                        new StationSnapshot(StationType.MANUAL, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pCapacityStationAdapter(
                        new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                        validP2pSnapshot(),
                        new StationCapacity(1, 1),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        " "));
    }

    private static P2pAdmissionSnapshot validP2pSnapshot() {
        return new P2pAdmissionSnapshot(
                "p2p-1",
                2,
                Set.of("bag-a"),
                Set.of("bag-b"),
                true);
    }

    private static NotionalToteOrder validOrder() {
        return new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem("item-1", "product-1", 1)),
                0);
    }
}
