package online.davisfamily.warehouse.sim.dsp.p2p.lease;

public record P2pPackPathActivitySnapshot(
        int sorterInputCount,
        int sorterOutputCount,
        int pendingSorterOutfeedCount,
        int pdcPackCount,
        int activePdcTransferCount,
        int nonIdlePrlCount,
        int prlPackCount,
        int activePrlToPcrTransferCount,
        int pcrPackCount,
        int pcrTravellingGroupCount,
        int pcrReleasedGroupCount,
        int outstandingExpectedBagGroupCount) {

    public P2pPackPathActivitySnapshot {
        requireNonNegative(sorterInputCount, "sorterInputCount");
        requireNonNegative(sorterOutputCount, "sorterOutputCount");
        requireNonNegative(pendingSorterOutfeedCount, "pendingSorterOutfeedCount");
        requireNonNegative(pdcPackCount, "pdcPackCount");
        requireNonNegative(activePdcTransferCount, "activePdcTransferCount");
        requireNonNegative(nonIdlePrlCount, "nonIdlePrlCount");
        requireNonNegative(prlPackCount, "prlPackCount");
        requireNonNegative(activePrlToPcrTransferCount, "activePrlToPcrTransferCount");
        requireNonNegative(pcrPackCount, "pcrPackCount");
        requireNonNegative(pcrTravellingGroupCount, "pcrTravellingGroupCount");
        requireNonNegative(pcrReleasedGroupCount, "pcrReleasedGroupCount");
        requireNonNegative(outstandingExpectedBagGroupCount, "outstandingExpectedBagGroupCount");
    }

    public static P2pPackPathActivitySnapshot idle() {
        return new P2pPackPathActivitySnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public boolean empty() {
        return sorterInputCount == 0
                && sorterOutputCount == 0
                && pendingSorterOutfeedCount == 0
                && pdcPackCount == 0
                && activePdcTransferCount == 0
                && nonIdlePrlCount == 0
                && prlPackCount == 0
                && activePrlToPcrTransferCount == 0
                && pcrPackCount == 0
                && pcrTravellingGroupCount == 0
                && pcrReleasedGroupCount == 0
                && outstandingExpectedBagGroupCount == 0;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
    }
}
