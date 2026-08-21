package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

class ThirdPartyStationAdmissionAdapterTest {

    @Test
    void shouldPublishConfiguredTargetWhenAdmissionIsOpen() {
        ThirdPartyStationAdmissionAdapter adapter =
                new ThirdPartyStationAdmissionAdapter("  third-party-ingress  ");

        StationAdmissionSnapshot admission = adapter.admissionFor(
                Optional.of(visitPlan()),
                areaSnapshot(new ThirdPartyAreaConfig(0, 1, 1d)));

        assertTrue(admission.canAccept());
        assertEquals("third-party-ingress", admission.selectedTargetId().orElseThrow());
    }

    @Test
    void shouldClearConfiguredTargetWhenAdmissionIsClosed() {
        ThirdPartyStationAdmissionAdapter adapter =
                new ThirdPartyStationAdmissionAdapter("third-party-ingress");

        StationAdmissionSnapshot admission = adapter.admissionFor(
                Optional.of(visitPlan()),
                fullAreaSnapshot());

        assertFalse(admission.canAccept());
        assertEquals("Third Party area has no capacity", admission.blockedReason());
        assertTrue(admission.selectedTargetId().isEmpty());
    }

    @Test
    void shouldKeepCompatibilityAdmissionTargetless() {
        ThirdPartyStationAdmissionAdapter adapter = new ThirdPartyStationAdmissionAdapter();

        StationAdmissionSnapshot admission = adapter.admissionFor(
                Optional.empty(),
                areaSnapshot(new ThirdPartyAreaConfig(0, 1, 1d)));

        assertTrue(admission.canAccept());
        assertTrue(admission.selectedTargetId().isEmpty());
    }

    @Test
    void shouldValidateConfiguredTargetId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ThirdPartyStationAdmissionAdapter(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ThirdPartyStationAdmissionAdapter(" "));
    }

    private static ThirdPartyAreaSnapshot areaSnapshot(ThirdPartyAreaConfig config) {
        return new ThirdPartyAreaSnapshot(config, List.of(), List.of(), List.of());
    }

    private static ThirdPartyAreaSnapshot fullAreaSnapshot() {
        return new ThirdPartyAreaSnapshot(
                new ThirdPartyAreaConfig(0, 1, 1d),
                List.of(),
                List.of(),
                List.of(new ThirdPartyVisitState(
                        new OrderSheetKey("active-order", 1),
                        new PhysicalToteId("active-tote"),
                        1,
                        1,
                        1d)));
    }

    private static ThirdPartyVisitPlan visitPlan() {
        DspOrderItem line = new DspOrderItem("line-1", "product-1", 1);
        return new ThirdPartyVisitPlan(
                new OrderSheetKey("order-1", 1),
                "104",
                OrderType.FULL_PACK,
                List.of(new ThirdPartyLineWork(
                        line,
                        1,
                        "Y74",
                        ThirdPartyWorkType.DIRECT_FULFILMENT)));
    }
}
