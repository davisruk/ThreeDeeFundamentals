package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ServiceCentreSupplyConfigTest {

    @Test
    void shouldRequireNonnegativeLowWaterMark() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceCentreSupplyConfig(-1));
        assertEquals(0, new ServiceCentreSupplyConfig(0).lowWaterMark());
        assertEquals(10, new ServiceCentreSupplyConfig(10).lowWaterMark());
    }
}
