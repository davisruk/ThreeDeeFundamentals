package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class PhysicalToteRecordTest {

    @Test
    void shouldCreateEachPhysicalToteRoleInItsInitialState() {
        PhysicalToteRecord inbound = PhysicalToteRecord.inboundPack(id("inbound-1"));
        PhysicalToteRecord preP2p = PhysicalToteRecord.preP2p(id("pre-p2p-1"));
        PhysicalToteRecord outbound = PhysicalToteRecord.outboundBag(id("outbound-1"));

        assertEquals(PhysicalToteRole.INBOUND_PACK, inbound.role());
        assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE, inbound.state());
        assertEquals(PhysicalToteRole.PRE_P2P, preP2p.role());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, preP2p.state());
        assertEquals(PhysicalToteRole.OUTBOUND_BAG, outbound.role());
        assertEquals(PhysicalToteLifecycleState.OUTBOUND_BAG_TOTE, outbound.state());
        assertFalse(inbound.terminal());
        assertFalse(preP2p.terminal());
        assertFalse(outbound.terminal());
    }

    @Test
    void shouldAdvanceInboundToteToPreP2pAndConsumedAtP2p() {
        PhysicalToteRecord inbound = PhysicalToteRecord.inboundPack(id("inbound-1"));

        PhysicalToteRecord preP2p = inbound.transitionTo(PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        PhysicalToteRecord consumed = preP2p.transitionTo(PhysicalToteLifecycleState.CONSUMED_AT_P2P);

        assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE, inbound.state());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, preP2p.state());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P, consumed.state());
        assertTrue(consumed.terminal());
    }

    @Test
    void shouldAllowAdaptedInboundToteToBeConsumedAtAdapting() {
        PhysicalToteRecord consumedDirectly = PhysicalToteRecord.inboundPack(id("adapted-1"))
                .transitionTo(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING);
        PhysicalToteRecord consumedAfterPreparation = PhysicalToteRecord.inboundPack(id("adapted-2"))
                .transitionTo(PhysicalToteLifecycleState.ACTIVE_PRE_P2P)
                .transitionTo(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING);

        assertTrue(consumedDirectly.terminal());
        assertTrue(consumedAfterPreparation.terminal());
    }

    @Test
    void shouldAdvanceOutboundBagToteToOutbound() {
        PhysicalToteRecord outbound = PhysicalToteRecord.outboundBag(id("outbound-1"))
                .transitionTo(PhysicalToteLifecycleState.OUTBOUND);

        assertEquals(PhysicalToteLifecycleState.OUTBOUND, outbound.state());
        assertTrue(outbound.terminal());
    }

    @Test
    void shouldRejectRoleStateMismatchAndIllegalTransition() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteRecord(null, PhysicalToteRole.INBOUND_PACK,
                        PhysicalToteLifecycleState.INBOUND_PACK_TOTE));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteRecord(id("tote-1"), null,
                        PhysicalToteLifecycleState.INBOUND_PACK_TOTE));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteRecord(id("tote-1"), PhysicalToteRole.INBOUND_PACK, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteRecord(id("tote-1"), PhysicalToteRole.OUTBOUND_BAG,
                        PhysicalToteLifecycleState.INBOUND_PACK_TOTE));

        PhysicalToteRecord inbound = PhysicalToteRecord.inboundPack(id("inbound-1"));
        assertThrows(IllegalArgumentException.class, () -> inbound.transitionTo(null));
        assertThrows(IllegalStateException.class,
                () -> inbound.transitionTo(PhysicalToteLifecycleState.INBOUND_PACK_TOTE));
        assertThrows(IllegalStateException.class,
                () -> inbound.transitionTo(PhysicalToteLifecycleState.CONSUMED_AT_P2P));
    }

    @Test
    void shouldRejectTransitionFromTerminalState() {
        PhysicalToteRecord consumed = PhysicalToteRecord.inboundPack(id("adapted-1"))
                .transitionTo(PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING);

        assertThrows(IllegalStateException.class,
                () -> consumed.transitionTo(PhysicalToteLifecycleState.ACTIVE_PRE_P2P));
    }

    private static PhysicalToteId id(String value) {
        return new PhysicalToteId(value);
    }
}
