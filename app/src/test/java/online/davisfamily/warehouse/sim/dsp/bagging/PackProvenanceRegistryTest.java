package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

class PackProvenanceRegistryTest {

    @Test
    void shouldRegisterAndSnapshotPackSourceProvenance() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        PackSourceProvenance second = provenance(" line-2 ", " product-2 ");
        PackSourceProvenance first = provenance("line-1", "product-1");

        registry.register(" pack-2 ", second);
        registry.register("pack-1", first);
        PackProvenanceSnapshot snapshot = registry.snapshot();
        registry.register("pack-3", provenance("line-3", "product-3"));

        assertEquals("line-2", second.lineReference());
        assertEquals("product-2", second.productId());
        assertEquals("service-centre-1", second.serviceCentreId());
        assertEquals("pharmacy-1", second.pharmacyId());
        assertEquals("patient-1", second.patientId());
        assertEquals("prescription-1", second.prescriptionId());
        assertEquals(second, registry.find("pack-2").orElseThrow());
        assertEquals(first, snapshot.find(" pack-1 ").orElseThrow());
        assertEquals(List.of("pack-2", "pack-1"),
                List.copyOf(snapshot.provenanceByPackId().keySet()));
        assertFalse(snapshot.find("pack-3").isPresent());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.provenanceByPackId().put("pack-4", first));
    }

    @Test
    void shouldAllowIdenticalRegistrationButRejectConflictingPackProvenance() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        PackSourceProvenance original = provenance("line-1", "product-1");

        registry.register("pack-1", original);
        registry.register(" pack-1 ", original);

        IllegalArgumentException conflict = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("pack-1", provenance("line-1", "product-2")));

        assertTrue(conflict.getMessage().contains("pack-1"));
        assertEquals(original, registry.find("pack-1").orElseThrow());
        assertEquals(1, registry.snapshot().provenanceByPackId().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(" ", original));
        assertThrows(IllegalArgumentException.class, () -> registry.register("pack-2", null));
    }

    private static PackSourceProvenance provenance(String lineReference, String productId) {
        return new PackSourceProvenance(
                new OrderSheetKey("source-order-1", 2),
                lineReference,
                productId,
                " service-centre-1 ",
                " pharmacy-1 ",
                " patient-1 ",
                " prescription-1 ");
    }
}
