package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AdaptingBinIdTest {

    @Test
    void shouldRetainStoreOrderAndOrdinalAsIdentity() {
        AdaptingBinId id = new AdaptingBinId(" 0000310 ", " target ", 3);

        assertEquals("0000310", id.storeId());
        assertEquals("target", id.referenceOrderId());
        assertEquals(3, id.ordinal());
        assertNotEquals(id, new AdaptingBinId("0000388", "target", 3));
        assertNotEquals(id, new AdaptingBinId("0000310", "other", 3));
    }

    @Test
    void shouldRejectInvalidIdentityFields() {
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBinId(null, "target", 1));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBinId("  ", "target", 1));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBinId("0000310", null, 1));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBinId("0000310", " ", 1));
        assertThrows(IllegalArgumentException.class, () -> new AdaptingBinId("0000310", "target", 0));
    }
}
