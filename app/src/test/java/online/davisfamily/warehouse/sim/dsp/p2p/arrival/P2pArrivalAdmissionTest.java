package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class P2pArrivalAdmissionTest {

    @Test
    void shouldBuildImmutableRequestFromManifestWithOrderedDistinctPharmacies() {
        InboundToteManifest manifest = manifest();

        P2pArrivalAdmissionRequest request = P2pArrivalAdmissionRequest.from(
                manifest,
                p2pDestination());

        assertEquals(new PhysicalToteId("tote-1"), request.physicalToteId());
        assertEquals(p2pDestination(), request.destination());
        assertEquals("104", request.serviceCentreId());
        assertEquals(new OrderSheetKey("order-1", 2), request.orderSheetKey());
        assertEquals(OrderType.ASSOCIATED, request.orderType());
        assertEquals(List.of("pharmacy-b", "pharmacy-a"), request.pharmacyIds());
        assertThrows(UnsupportedOperationException.class, () -> request.pharmacyIds().add("pharmacy-c"));
    }

    @Test
    void shouldCopyAndNormalizeDirectRequestValues() {
        List<String> pharmacyIds = new ArrayList<>(List.of(
                " pharmacy-b ", "pharmacy-a", "pharmacy-b"));

        P2pArrivalAdmissionRequest request = new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"),
                p2pDestination(),
                " 104 ",
                new OrderSheetKey("order-1", 2),
                OrderType.ASSOCIATED,
                pharmacyIds);
        pharmacyIds.add("pharmacy-c");

        assertEquals("104", request.serviceCentreId());
        assertEquals(List.of("pharmacy-b", "pharmacy-a"), request.pharmacyIds());
    }

    @Test
    void shouldRejectInvalidRequestValues() {
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                null, p2pDestination(), "104", new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK, List.of("pharmacy-a")));
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"), null, "104", new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK, List.of("pharmacy-a")));
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"),
                new OperationalRouteDestination(StationType.ADAPTING, "bench-1"),
                "104", new OrderSheetKey("order-1", 1), OrderType.FULL_PACK,
                List.of("pharmacy-a")));
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"), p2pDestination(), " ",
                new OrderSheetKey("order-1", 1), OrderType.FULL_PACK,
                List.of("pharmacy-a")));
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"), p2pDestination(), "104", null,
                OrderType.FULL_PACK, List.of("pharmacy-a")));
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"), p2pDestination(), "104",
                new OrderSheetKey("order-1", 1), null, List.of("pharmacy-a")));
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"), p2pDestination(), "104",
                new OrderSheetKey("order-1", 1), OrderType.FULL_PACK, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new P2pArrivalAdmissionRequest(
                new PhysicalToteId("tote-1"), p2pDestination(), "104",
                new OrderSheetKey("order-1", 1), OrderType.FULL_PACK, List.of(" ")));
        assertThrows(IllegalArgumentException.class,
                () -> P2pArrivalAdmissionRequest.from(null, p2pDestination()));
    }

    @Test
    void shouldCreatePermittedAndDeferredDecisions() {
        P2pArrivalAdmissionDecision permitted = P2pArrivalAdmissionDecision.permit();
        P2pArrivalAdmissionDecision deferred = P2pArrivalAdmissionDecision.defer(" line leased ");

        assertTrue(permitted.permitted());
        assertEquals("", permitted.reason());
        assertFalse(deferred.permitted());
        assertEquals("line leased", deferred.reason());
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalAdmissionDecision(true, "unexpected"));
        assertThrows(IllegalArgumentException.class,
                () -> P2pArrivalAdmissionDecision.defer(" "));
    }

    @Test
    void shouldAllowEveryValidRequestWithStatelessPolicy() {
        AllowAllP2pArrivalAdmissionPolicy policy = new AllowAllP2pArrivalAdmissionPolicy();
        P2pArrivalAdmissionRequest request = P2pArrivalAdmissionRequest.from(
                manifest(),
                p2pDestination());

        assertEquals(P2pArrivalAdmissionDecision.permit(), policy.evaluate(request));
        assertEquals(P2pArrivalAdmissionDecision.permit(), policy.evaluate(request));
        assertThrows(IllegalArgumentException.class, () -> policy.evaluate(null));
    }

    private static InboundToteManifest manifest() {
        return new InboundToteManifest(
                new PhysicalToteId("tote-1"),
                new OrderSheetKey("order-1", 2),
                OrderType.ASSOCIATED,
                "104",
                List.of(
                        item("line-1", "pharmacy-b"),
                        item("line-2", "pharmacy-a"),
                        item("line-3", "pharmacy-b")),
                7L);
    }

    private static DspOrderItem item(String lineReference, String pharmacyId) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                DspOrderLineType.FULL_PACK,
                "order-1",
                1,
                1);
    }

    private static OperationalRouteDestination p2pDestination() {
        return new OperationalRouteDestination(StationType.P2P, "p2p-1");
    }
}
