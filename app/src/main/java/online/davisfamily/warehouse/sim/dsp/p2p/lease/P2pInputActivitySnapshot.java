package online.davisfamily.warehouse.sim.dsp.p2p.lease;

public record P2pInputActivitySnapshot(
        int stationArrivalCount,
        int tipperInputCount,
        boolean activeTipperTote,
        int activeTipperDischargeCount) {

    public P2pInputActivitySnapshot {
        requireNonNegative(stationArrivalCount, "stationArrivalCount");
        requireNonNegative(tipperInputCount, "tipperInputCount");
        requireNonNegative(activeTipperDischargeCount, "activeTipperDischargeCount");
    }

    public static P2pInputActivitySnapshot idle() {
        return new P2pInputActivitySnapshot(0, 0, false, 0);
    }

    public boolean empty() {
        return stationArrivalCount == 0
                && tipperInputCount == 0
                && !activeTipperTote
                && activeTipperDischargeCount == 0;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
    }
}
