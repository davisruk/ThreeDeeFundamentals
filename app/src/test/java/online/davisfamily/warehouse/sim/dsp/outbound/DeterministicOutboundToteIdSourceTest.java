package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeterministicOutboundToteIdSourceTest {

    @Test
    void shouldAllocateStableOneBasedIdsPerP2pLine() {
        DeterministicOutboundToteIdSource source = new DeterministicOutboundToteIdSource();
        P2pLineId lineId = new P2pLineId("p2p-1");

        assertEquals("outbound-p2p-1-1", source.nextId(lineId).value());
        assertEquals("outbound-p2p-1-2", source.nextId(lineId).value());
        assertEquals("outbound-p2p-1-3", source.nextId(lineId).value());
        assertThrows(IllegalArgumentException.class, () -> source.nextId(null));
    }

    @Test
    void shouldKeepLineSequencesIndependent() {
        DeterministicOutboundToteIdSource source = new DeterministicOutboundToteIdSource();
        P2pLineId firstLine = new P2pLineId("p2p-1");
        P2pLineId secondLine = new P2pLineId("p2p-2");

        assertEquals("outbound-p2p-1-1", source.nextId(firstLine).value());
        assertEquals("outbound-p2p-2-1", source.nextId(secondLine).value());
        assertEquals("outbound-p2p-1-2", source.nextId(firstLine).value());
        assertEquals("outbound-p2p-2-2", source.nextId(secondLine).value());
    }
}
