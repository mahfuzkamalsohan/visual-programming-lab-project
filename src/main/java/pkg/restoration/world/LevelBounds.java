package pkg.restoration.world;

public record LevelBounds(double minX, double minY, double width, double height) {

    public LevelBounds(double width, double height) {
        this(0, 0, width, height);
    }

    public IsoPoint clamp(IsoPoint point, double margin) {
        double clampedX = clamp(point.x(), minX + margin, maxX() - margin);
        double clampedY = clamp(point.y(), minY + margin, maxY() - margin);
        return new IsoPoint(clampedX, clampedY);
    }

    public double maxX() {
        return minX + width;
    }

    public double maxY() {
        return minY + height;
    }

    public IsoPoint center() {
        return new IsoPoint(minX + width / 2.0, minY + height / 2.0);
    }

    public boolean contains(IsoPoint point, double margin) {
        return point.x() >= minX + margin
                && point.x() <= maxX() - margin
                && point.y() >= minY + margin
                && point.y() <= maxY() - margin;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
