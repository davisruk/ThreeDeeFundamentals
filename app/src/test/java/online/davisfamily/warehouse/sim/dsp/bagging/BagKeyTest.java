package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BagKeyTest {

    @Test
    void shouldCreateDeterministicBagCorrelationFromPrescriptionAndOrdinal() {
        BagKey bagKey = new BagKey(" prescription-27 ", 2);

        assertEquals("prescription-27", bagKey.prescriptionId());
        assertEquals(2, bagKey.bagOrdinal());
        assertEquals("prescription-27/bag-2", bagKey.correlationId());
    }

    @Test
    void shouldRejectInvalidBagIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new BagKey(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new BagKey(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> new BagKey("prescription-1", 0));
    }
}
