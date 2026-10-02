package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @Test
    void shouldValidateWholeBatchBeforeAnyRegistration() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        PackSourceProvenance first = provenance("line-1", "product-1");
        PackSourceProvenance conflict = provenance("line-2", "product-2");
        registry.register("existing", first);
        Map<String, PackSourceProvenance> batch = new LinkedHashMap<>();
        batch.put("new", first);
        batch.put("existing", conflict);

        assertThrows(IllegalArgumentException.class, () -> registry.validateBatch(batch));
        assertThrows(IllegalArgumentException.class, () -> registry.registerBatch(batch));
        assertFalse(registry.find("new").isPresent());
        assertEquals(first, registry.find("existing").orElseThrow());

        Map<String, PackSourceProvenance> duplicateAfterNormalization = new LinkedHashMap<>();
        duplicateAfterNormalization.put("pack", first);
        duplicateAfterNormalization.put(" pack ", first);
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerBatch(duplicateAfterNormalization));
        assertFalse(registry.find("pack").isPresent());

        registry.validateBatch(Map.of("new", first));
        assertFalse(registry.find("new").isPresent());
        registry.registerBatch(Map.of("new", first));
        assertEquals(first, registry.find("new").orElseThrow());
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
