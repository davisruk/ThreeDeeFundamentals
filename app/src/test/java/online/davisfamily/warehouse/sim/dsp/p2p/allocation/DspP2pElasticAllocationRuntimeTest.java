package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DspP2pElasticAllocationRuntimeTest {

    @Test
    void shouldComposeFiveLineUncalibratedProfileAndResetByReconstruction() {
        ElasticRuntimeTestFixture firstFixture = new ElasticRuntimeTestFixture();
        DspP2pElasticAllocationRuntime first = firstFixture.createRuntime();

        assertEquals(5, first.lineDefinitions().size());
        assertEquals(P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                first.allocationSnapshot().profileId());
        assertEquals(P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                first.allocationSnapshot().calibrationStatus());
        assertEquals(first.leaseSnapshot().lines(), first.operationalSnapshot().leases().lines());

        first.close();
        assertTrue(first.isClosed());

        ElasticRuntimeTestFixture secondFixture = new ElasticRuntimeTestFixture();
        DspP2pElasticAllocationRuntime second = secondFixture.createRuntime();
        assertNotSame(first, second);
        assertEquals(first.allocationSnapshot(), second.allocationSnapshot());
        second.close();
    }

    @Test
    void shouldRejectNullSupplierResultBeforeComposition() {
        ElasticRuntimeTestFixture fixture = new ElasticRuntimeTestFixture();
        assertThrows(IllegalStateException.class, () -> fixture.createRuntime(() -> null));
    }
}
