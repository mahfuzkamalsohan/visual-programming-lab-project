package pkg.restoration.world;

public record IsoPoint(double x, double y) {

    public IsoPoint add(double dx, double dy) {
        return new IsoPoint(x + dx, y + dy);
    }

    public double distance(IsoPoint other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
