package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DspWarehouseTransportRuntimeTest {

    @Test
    void shouldExposeFreshOrderedSnapshotsAndCloseIdempotently() {
        DspWarehouseTransportRuntimeFactoryTest.RuntimeFixture fixture =
                DspWarehouseTransportRuntimeFactoryTest.createRuntimeFixture();
        DspWarehouseTransportRuntime runtime = fixture.runtime();

        assertEquals("entry", runtime.routeCatalogSnapshot().commonEntrySegmentLabel());
        assertEquals(0, runtime.outboundTransportSnapshot().occupancy());
        assertEquals(0, runtime.inFlightSnapshot().occupancy());
        assertEquals(1, runtime.stationArrivalSnapshots().size());
        assertEquals("p2p", runtime.stationArrivalSnapshots().get(0)
                .destination().targetId());

        fixture.sharedLaunchQueue().enqueue(fixture.source().launchRequest());
        fixture.simulationWorld().update(0d);

        assertEquals(1, runtime.inFlightSnapshot().occupancy());
        runtime.close();
        runtime.close();
    }

    @Test
    void shouldRejectMissingRuntimeDependencies() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspWarehouseTransportRuntime(
                        null, null, null, null, null, null, null));
    }
}
