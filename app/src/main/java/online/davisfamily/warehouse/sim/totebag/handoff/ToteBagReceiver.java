package online.davisfamily.warehouse.sim.totebag.handoff;

public class ToteBagReceiver extends StoredBagReceiver {
    private final String toteId;

    public ToteBagReceiver(String id, String toteId, int capacity) {
        super(id, capacity);
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be blank");
        }
        this.toteId = toteId;
    }

    public String getToteId() {
        return toteId;
    }
}
