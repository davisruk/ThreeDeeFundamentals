package online.davisfamily.warehouse.sim.totebag.transfer;

import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class TippingDischargeTransfer {
    private final Pack pack;
    private final double durationSeconds;
    private final float spinRateX;
    private final float spinRateY;
    private final float spinRateZ;
    private double elapsedSeconds;

    public TippingDischargeTransfer(Pack pack, double durationSeconds) {
        if (pack == null) {
            throw new IllegalArgumentException("pack must not be null");
        }
        if (durationSeconds <= 0d) {
            throw new IllegalArgumentException("durationSeconds must be > 0");
        }
        this.pack = pack;
        this.durationSeconds = durationSeconds;
        int hash = Math.abs(pack.getId().hashCode());
        this.spinRateX = 1.5f + ((hash & 0x7) * 0.22f);
        this.spinRateY = 0.7f + (((hash >> 3) & 0x7) * 0.17f);
        this.spinRateZ = 1.1f + (((hash >> 6) & 0x7) * 0.19f);
    }

    public Pack getPack() {
        return pack;
    }

    public void advance(double dtSeconds) {
        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + Math.max(0d, dtSeconds));
    }

    public boolean isComplete() {
        return elapsedSeconds >= durationSeconds;
    }

    public double getProgress() {
        return durationSeconds <= 0d ? 1d : Math.min(1d, elapsedSeconds / durationSeconds);
    }

    public float getSpinAngleX() {
        return (float) (elapsedSeconds * spinRateX);
    }

    public float getSpinAngleY() {
        return (float) (elapsedSeconds * spinRateY);
    }

    public float getSpinAngleZ() {
        return (float) (elapsedSeconds * spinRateZ);
    }
}
