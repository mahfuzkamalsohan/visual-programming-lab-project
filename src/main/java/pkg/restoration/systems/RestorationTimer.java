package pkg.restoration.systems;

public final class RestorationTimer {

    private final double maxSeconds;
    private double currentSeconds;
    private double elapsedSeconds;

    public RestorationTimer(double initialSeconds, double maxSeconds) {
        this.currentSeconds = initialSeconds;
        this.maxSeconds = maxSeconds;
    }

    public void tick(double tpf) {
        elapsedSeconds += tpf;
        currentSeconds = Math.max(0, currentSeconds - tpf);
    }

    public void applyDelta(double seconds) {
        currentSeconds = Math.max(0, Math.min(maxSeconds, currentSeconds + seconds));
    }

    public double currentSeconds() {
        return currentSeconds;
    }

    public double maxSeconds() {
        return maxSeconds;
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public double restorationRatio() {
        return maxSeconds == 0 ? 0 : currentSeconds / maxSeconds;
    }

    public boolean isEmpty() {
        return currentSeconds <= 0.0;
    }
}
