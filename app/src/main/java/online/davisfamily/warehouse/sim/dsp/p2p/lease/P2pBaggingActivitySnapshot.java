package online.davisfamily.warehouse.sim.dsp.p2p.lease;

public record P2pBaggingActivitySnapshot(
        boolean currentBagGroup,
        boolean reservedBagGroup,
        boolean activeBagReservation,
        int pendingBagDischargeCount,
        boolean activeBagDischarge,
        boolean receiverReservation,
        boolean receiverReceivingBag,
        int receiverCompletedBagCount) {

    public P2pBaggingActivitySnapshot {
        requireNonNegative(pendingBagDischargeCount, "pendingBagDischargeCount");
        requireNonNegative(receiverCompletedBagCount, "receiverCompletedBagCount");
    }

    public static P2pBaggingActivitySnapshot idle() {
        return new P2pBaggingActivitySnapshot(false, false, false, 0, false, false, false, 0);
    }

    public boolean empty() {
        return !currentBagGroup
                && !reservedBagGroup
                && !activeBagReservation
                && pendingBagDischargeCount == 0
                && !activeBagDischarge
                && !receiverReservation
                && !receiverReceivingBag
                && receiverCompletedBagCount == 0;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
    }
}
