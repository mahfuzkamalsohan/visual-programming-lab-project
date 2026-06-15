package pkg.restoration.world;

import static com.almasb.fxgl.dsl.FXGL.image;

import java.util.List;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import pkg.restoration.GameConstants;
import pkg.restoration.assets.AssetCatalog;

public final class WorldRenderer {

    private final Canvas canvas;
    private final IsoProjection projection;
    private final Image dustTile;
    private final Image recoveringTile;
    private final Image greenTile;
    private final Image pathTile;
    private final Image boundaryWall;
    private double lastRatio = -1;
    private int lastCurrentLevelIndex = -1;

    public WorldRenderer(double width, double height, IsoProjection projection) {
        this.canvas = new Canvas(width, height);
        this.projection = projection;
        this.dustTile = image(AssetCatalog.TILE_DUST);
        this.recoveringTile = image(AssetCatalog.TILE_RECOVERING);
        this.greenTile = image(AssetCatalog.TILE_GREEN);
        this.pathTile = image(AssetCatalog.TILE_PATH);
        this.boundaryWall = image(AssetCatalog.BOUNDARY_WALL);
    }

    public Canvas canvas() {
        return canvas;
    }

    public void render(List<LevelDefinition> levels, int currentLevelIndex, double restorationRatio) {
        if (lastCurrentLevelIndex == currentLevelIndex && Math.abs(restorationRatio - lastRatio) < 0.025) {
            return;
        }

        lastCurrentLevelIndex = currentLevelIndex;
        lastRatio = restorationRatio;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawAtmosphere(gc, restorationRatio);

        for (int index = 0; index < levels.size(); index++) {
            drawDistrict(gc, levels.get(index), index, currentLevelIndex, restorationRatio);
        }
    }

    private void drawAtmosphere(GraphicsContext gc, double ratio) {
        Color lowTop = Color.web("#2a2825");
        Color lowBottom = Color.web("#5b4f42");
        Color highTop = Color.web("#173044");
        Color highBottom = Color.web("#6ba56b");

        Color top = lowTop.interpolate(highTop, ratio);
        Color bottom = lowBottom.interpolate(highBottom, ratio);

        gc.setFill(new LinearGradient(0, 0, 0, 1, true, null,
                new Stop(0, top),
                new Stop(0.65, top.interpolate(bottom, 0.55)),
                new Stop(1, bottom)));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.setFill(Color.web("#f1d090", 0.10 + ratio * 0.18));
        gc.fillOval(canvas.getWidth() - 260, 48, 110, 110);
    }

    private void drawDistrict(GraphicsContext gc, LevelDefinition level, int index, int currentLevelIndex, double ratio) {
        double districtRatio = districtRestorationRatio(index, currentLevelIndex, ratio);
        Image primary = districtRatio > 0.62 ? greenTile : districtRatio > 0.34 ? recoveringTile : dustTile;

        for (GridPoint tilePoint : level.shape().tiles()) {
            int x = tilePoint.x();
            int y = tilePoint.y();
            Point2D center = projection.toScreen(new IsoPoint(x + 0.5, y + 0.5));
            Image tile = isMainPath(level, x, y) ? pathTile : primary;
            gc.drawImage(tile, center.getX() - GameConstants.TILE_WIDTH / 2.0, center.getY() - GameConstants.TILE_HEIGHT / 2.0);

            drawCityDetail(gc, level, x, y, center, districtRatio);

            if (districtRatio < 0.28 && Math.floorMod(x * 31 + y * 17, 7) == 0) {
                gc.setFill(Color.web("#32261d", 0.22));
                gc.fillOval(center.getX() - 14, center.getY() - 4, 28, 10);
            }

            if (districtRatio > 0.68 && Math.floorMod(x * 19 + y * 23, 8) == 0) {
                gc.setFill(Color.web("#bfe878", 0.45));
                gc.fillOval(center.getX() - 12, center.getY() - 8, 24, 12);
            }
        }

        drawDistrictBoundary(gc, level, index, currentLevelIndex);
        drawDistrictTitle(gc, level, index, currentLevelIndex);
    }

    private boolean isMainPath(LevelDefinition level, int x, int y) {
        IsoPoint tile = new IsoPoint(x + 0.5, y + 0.5);
        double closestDistance = Double.MAX_VALUE;

        for (GateDefinition gate : level.gates()) {
            closestDistance = Math.min(closestDistance, distanceToSegment(tile, level.playerSpawn(), gate.position()));
        }

        return closestDistance <= 1.45;
    }

    private double districtRestorationRatio(int index, int currentLevelIndex, double ratio) {
        if (index < currentLevelIndex) {
            return Math.min(1.0, ratio + 0.18);
        }

        if (index == currentLevelIndex) {
            return ratio;
        }

        return Math.max(0.12, ratio * 0.55);
    }

    private void drawCityDetail(GraphicsContext gc, LevelDefinition level, int x, int y, Point2D center, double ratio) {
        int hash = Math.floorMod(level.id().hashCode() + x * 37 + y * 53, 19);

        if (level.id().contains("market") && hash == 0) {
            drawStall(gc, center, ratio);
        } else if (level.id().contains("orchard") && hash <= 1) {
            drawPlanter(gc, center, ratio);
        } else if (level.id().contains("reservoir") && hash <= 2) {
            drawWaterChannel(gc, center, ratio);
        } else if (level.id().contains("core") && hash <= 1) {
            drawCanopyPatch(gc, center, ratio);
        } else if (hash == 3 && !isMainPath(level, x, y)) {
            drawCivicBlock(gc, center, ratio);
        }
    }

    private void drawStall(GraphicsContext gc, Point2D center, double ratio) {
        gc.setFill(Color.web("#7a5144", 0.34 + ratio * 0.18));
        gc.fillRoundRect(center.getX() - 22, center.getY() - 28, 44, 20, 6, 6);
        gc.setFill(Color.web("#e0c56f", 0.35 + ratio * 0.2));
        gc.fillRoundRect(center.getX() - 18, center.getY() - 34, 36, 8, 5, 5);
    }

    private void drawPlanter(GraphicsContext gc, Point2D center, double ratio) {
        gc.setFill(Color.web("#385940", 0.35 + ratio * 0.22));
        gc.fillOval(center.getX() - 19, center.getY() - 24, 38, 18);
        gc.setFill(Color.web("#bfe878", 0.32 + ratio * 0.35));
        gc.fillOval(center.getX() - 11, center.getY() - 32, 22, 18);
    }

    private void drawWaterChannel(GraphicsContext gc, Point2D center, double ratio) {
        gc.setFill(Color.web("#5ca6b8", 0.22 + ratio * 0.22));
        gc.fillPolygon(
                new double[] {center.getX() - 32, center.getX(), center.getX() + 32, center.getX()},
                new double[] {center.getY(), center.getY() - 10, center.getY(), center.getY() + 10},
                4
        );
    }

    private void drawCanopyPatch(GraphicsContext gc, Point2D center, double ratio) {
        gc.setFill(Color.web("#78c65f", 0.28 + ratio * 0.32));
        gc.fillOval(center.getX() - 24, center.getY() - 38, 48, 26);
        gc.setFill(Color.web("#e8f6a0", 0.18 + ratio * 0.18));
        gc.fillOval(center.getX() - 12, center.getY() - 42, 24, 12);
    }

    private void drawCivicBlock(GraphicsContext gc, Point2D center, double ratio) {
        gc.setFill(Color.web("#6b7368", 0.26 + ratio * 0.12));
        gc.fillRoundRect(center.getX() - 20, center.getY() - 30, 40, 18, 5, 5);
        gc.setFill(Color.web("#d6e27d", 0.12 + ratio * 0.14));
        gc.fillRoundRect(center.getX() - 14, center.getY() - 35, 28, 6, 4, 4);
    }

    private void drawDistrictBoundary(GraphicsContext gc, LevelDefinition level, int index, int currentLevelIndex) {
        for (GridPoint tile : level.shape().tiles()) {
            if (!level.shape().hasTile(tile.north().x(), tile.north().y())) {
                drawWall(gc, tile.x() + 0.5, tile.y() + 0.05, index, currentLevelIndex);
            }
            if (!level.shape().hasTile(tile.south().x(), tile.south().y())) {
                drawWall(gc, tile.x() + 0.5, tile.y() + 0.95, index, currentLevelIndex);
            }
            if (!level.shape().hasTile(tile.west().x(), tile.west().y())) {
                drawWall(gc, tile.x() + 0.05, tile.y() + 0.5, index, currentLevelIndex);
            }
            if (!level.shape().hasTile(tile.east().x(), tile.east().y())) {
                drawWall(gc, tile.x() + 0.95, tile.y() + 0.5, index, currentLevelIndex);
            }
        }
    }

    private void drawWall(GraphicsContext gc, double x, double y, int index, int currentLevelIndex) {
        Point2D screen = projection.toScreen(new IsoPoint(x, y));
        gc.setGlobalAlpha(index <= currentLevelIndex ? 1.0 : 0.54);
        gc.drawImage(boundaryWall, screen.getX() - 48, screen.getY() - 72);
        gc.setGlobalAlpha(1.0);
    }

    private void drawDistrictTitle(GraphicsContext gc, LevelDefinition level, int index, int currentLevelIndex) {
        Point2D screen = projection.toScreen(level.bounds().center());
        gc.setFill(index == currentLevelIndex ? Color.web("#f7f4dc", 0.82) : Color.web("#f7f4dc", 0.28));
        gc.fillText(level.title(), screen.getX() - 48, screen.getY() - 56);
    }

    private static double distanceToSegment(IsoPoint point, IsoPoint start, IsoPoint end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double lengthSquared = dx * dx + dy * dy;

        if (lengthSquared == 0) {
            return point.distance(start);
        }

        double t = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        IsoPoint closest = new IsoPoint(start.x() + t * dx, start.y() + t * dy);
        return point.distance(closest);
    }
}
