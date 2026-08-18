package online.davisfamily.warehouse.sim.dsp.lifecycle;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record PhysicalToteRecord(
        PhysicalToteId id,
        PhysicalToteRole role,
        PhysicalToteLifecycleState state) {

    public PhysicalToteRecord {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (!supports(role, state)) {
            throw new IllegalArgumentException("State " + state + " is not valid for physical tote role " + role);
        }
    }

    public static PhysicalToteRecord inboundPack(PhysicalToteId id) {
        return new PhysicalToteRecord(id, PhysicalToteRole.INBOUND_PACK, PhysicalToteLifecycleState.INBOUND_PACK_TOTE);
    }

    public static PhysicalToteRecord preP2p(PhysicalToteId id) {
        return new PhysicalToteRecord(id, PhysicalToteRole.PRE_P2P, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
    }

    public static PhysicalToteRecord outboundBag(PhysicalToteId id) {
        return new PhysicalToteRecord(id, PhysicalToteRole.OUTBOUND_BAG, PhysicalToteLifecycleState.OUTBOUND_BAG_TOTE);
    }

    public PhysicalToteRecord transitionTo(PhysicalToteLifecycleState nextState) {
        if (nextState == null) {
            throw new IllegalArgumentException("nextState must not be null");
        }
        if (!isAllowedTransition(state, nextState)) {
            throw new IllegalStateException("Cannot transition physical tote from " + state + " to " + nextState);
        }
        return new PhysicalToteRecord(id, role, nextState);
    }

    public boolean terminal() {
        return state == PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING
                || state == PhysicalToteLifecycleState.CONSUMED_AT_P2P
                || state == PhysicalToteLifecycleState.OUTBOUND;
    }

    private static boolean supports(PhysicalToteRole role, PhysicalToteLifecycleState state) {
        return switch (role) {
            case INBOUND_PACK -> state == PhysicalToteLifecycleState.INBOUND_PACK_TOTE
                    || state == PhysicalToteLifecycleState.ACTIVE_PRE_P2P
                    || state == PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING
                    || state == PhysicalToteLifecycleState.CONSUMED_AT_P2P;
            case PRE_P2P -> state == PhysicalToteLifecycleState.ACTIVE_PRE_P2P
                    || state == PhysicalToteLifecycleState.CONSUMED_AT_P2P;
            case OUTBOUND_BAG -> state == PhysicalToteLifecycleState.OUTBOUND_BAG_TOTE
                    || state == PhysicalToteLifecycleState.OUTBOUND;
        };
    }

    private static boolean isAllowedTransition(
            PhysicalToteLifecycleState currentState,
            PhysicalToteLifecycleState nextState) {
        return switch (currentState) {
            case INBOUND_PACK_TOTE -> nextState == PhysicalToteLifecycleState.ACTIVE_PRE_P2P
                    || nextState == PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING;
            case ACTIVE_PRE_P2P -> nextState == PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING
                    || nextState == PhysicalToteLifecycleState.CONSUMED_AT_P2P;
            case OUTBOUND_BAG_TOTE -> nextState == PhysicalToteLifecycleState.OUTBOUND;
            case CONSUMED_AT_ADAPTING, CONSUMED_AT_P2P, OUTBOUND -> false;
        };
    }
}
