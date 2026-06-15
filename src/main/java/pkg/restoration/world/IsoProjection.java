package pkg.restoration.world;

import javafx.geometry.Point2D;

public final class IsoProjection {

    private final double originX;
    private final double originY;
    private final double tileWidth;
    private final double tileHeight;

    public IsoProjection(double originX, double originY, double tileWidth, double tileHeight) {
        this.originX = originX;
        this.originY = originY;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    public Point2D toScreen(IsoPoint point) {
        double screenX = originX + (point.x() - point.y()) * tileWidth / 2.0;
        double screenY = originY + (point.x() + point.y()) * tileHeight / 2.0;
        return new Point2D(screenX, screenY);
    }

    public double tileWidth() {
        return tileWidth;
    }

    public double tileHeight() {
        return tileHeight;
    }
}
