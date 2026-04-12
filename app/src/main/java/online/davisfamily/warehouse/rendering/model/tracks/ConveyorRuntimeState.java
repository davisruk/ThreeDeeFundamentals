package online.davisfamily.warehouse.rendering.model.tracks;

public class ConveyorRuntimeState {
    private boolean running = true;
    private double speedScale = 1.0d;

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public double getSpeedScale() {
        return speedScale;
    }

    public void setSpeedScale(double speedScale) {
        if (speedScale < 0d) {
            throw new IllegalArgumentException("speedScale must be >= 0");
        }
        this.speedScale = speedScale;
    }

    public double resolveSpeed(double baseSpeedUnitsPerSecond) {
        if (!running) {
            return 0d;
        }
        return baseSpeedUnitsPerSecond * speedScale;
    }
}
