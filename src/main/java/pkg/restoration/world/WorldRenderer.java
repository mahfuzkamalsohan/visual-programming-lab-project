package pkg.restoration.world;

import static com.almasb.fxgl.dsl.FXGL.image;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final CityMap cityMap;
    private final Image dustTile;
    private final Image recoveringTile;
    private final Image greenTile;
    private final Image pathTile;
    private final Image boundaryWall;
    private double lastRatio = -1;
    private int lastCurrentLevelIndex = -1;

    public WorldRenderer(double width, double height, IsoProjection projection, CityMap cityMap) {
        this.canvas = new Canvas(width, height);
        this.projection = projection;
        this.cityMap = cityMap;
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
        drawCity(gc, restorationRatio);

        for (int index = 0; index < levels.size(); index++) {
            drawDistrictPerimeter(gc, levels.get(index), index, currentLevelIndex);
            drawDistrictTitle(gc, levels.get(index), index, currentLevelIndex);
        }
    }

    private void drawAtmosphere(GraphicsContext gc, double ratio) {
        Color lowTop = Color.web("#4a4c49");
        Color lowBottom = Color.web("#756f63");
        Color highTop = Color.web("#3aa0c8");
        Color highBottom = Color.web("#91c76d");

        Color top = lowTop.interpolate(highTop, ratio);
        Color bottom = lowBottom.interpolate(highBottom, ratio);

        gc.setFill(new LinearGradient(0, 0, 0, 1, true, null,
                new Stop(0, top),
                new Stop(0.58, top.interpolate(bottom, 0.55)),
                new Stop(1, bottom)));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawCity(GraphicsContext gc, double ratio) {
        List<GridPoint> tiles = new ArrayList<>(cityMap.tilePositions());
        tiles.sort(Comparator
                .comparingInt((GridPoint point) -> point.x() + point.y())
                .thenComparingInt(GridPoint::y)
                .thenComparingInt(GridPoint::x));

        for (GridPoint tile : tiles) {
            CityTileType type = cityMap.tileAt(tile.x(), tile.y());
            Point2D center = projection.toScreen(new IsoPoint(tile.x() + 0.5, tile.y() + 0.5));
            drawCityGround(gc, type, center, ratio);
        }

        for (GridPoint tile : tiles) {
            CityTileType type = cityMap.tileAt(tile.x(), tile.y());
            Point2D center = projection.toScreen(new IsoPoint(tile.x() + 0.5, tile.y() + 0.5));
            drawCityDetail(gc, type, tile.x(), tile.y(), center, ratio);
        }
    }

    private void drawCityGround(GraphicsContext gc, CityTileType type, Point2D center, double ratio) {
        switch (type) {
            case WATER -> drawWater(gc, center, ratio);
            case PIER -> drawPier(gc, center);
            case ROAD -> drawPathTile(gc, center);
            case PLAZA -> drawPlaza(gc, center, ratio);
            case PARK -> drawParkGround(gc, center, ratio);
            case BUILDING_LOW, BUILDING_HIGH -> drawBuildingGround(gc, center, ratio);
        }
    }

    private void drawCityDetail(GraphicsContext gc, CityTileType type, int x, int y, Point2D center, double ratio) {
        switch (type) {
            case ROAD -> drawRoadMarking(gc, center, x, y);
            case PARK -> drawParkDetail(gc, center, x, y, ratio);
            case BUILDING_LOW, BUILDING_HIGH -> drawBuildingStructure(gc, type, center, x, y, ratio);
            case WATER, PIER, PLAZA -> {
            }
        }
    }

    private void drawWater(GraphicsContext gc, Point2D center, double ratio) {
        drawDiamond(gc, center, Color.web("#2f88b5").interpolate(Color.web("#45acd1"), ratio), Color.web("#6fc8df", 0.48));
        gc.setFill(Color.web("#d9fbff", 0.12 + ratio * 0.08));
        gc.fillOval(center.getX() - 20, center.getY() - 6, 40, 7);
    }

    private void drawPier(GraphicsContext gc, Point2D center) {
        drawDiamond(gc, center, Color.web("#8a6b48"), Color.web("#c7a66d", 0.58));
        gc.setStroke(Color.web("#4e3828", 0.45));
        gc.strokeLine(center.getX() - 28, center.getY(), center.getX() + 28, center.getY());
    }

    private void drawPathTile(GraphicsContext gc, Point2D center) {
        gc.drawImage(pathTile,
                center.getX() - GameConstants.TILE_WIDTH / 2.0,
                center.getY() - GameConstants.TILE_HEIGHT / 2.0);
    }

    private void drawRoadMarking(GraphicsContext gc, Point2D center, int x, int y) {
        if (Math.floorMod(x + y, 2) == 0) {
            gc.setStroke(Color.web("#f2f1dc", 0.55));
            gc.setLineWidth(2);
            gc.strokeLine(center.getX() - 18, center.getY(), center.getX() + 18, center.getY());
        }
    }

    private void drawPlaza(GraphicsContext gc, Point2D center, double ratio) {
        drawPathTile(gc, center);
        gc.setFill(Color.web("#e6d58d", 0.18 + ratio * 0.16));
        gc.fillPolygon(
                new double[] {center.getX(), center.getX() + 24, center.getX(), center.getX() - 24},
                new double[] {center.getY() - 12, center.getY(), center.getY() + 12, center.getY()},
                4
        );
    }

    private void drawParkGround(GraphicsContext gc, Point2D center, double ratio) {
        gc.drawImage(ratio > 0.45 ? greenTile : recoveringTile,
                center.getX() - GameConstants.TILE_WIDTH / 2.0,
                center.getY() - GameConstants.TILE_HEIGHT / 2.0);
    }

    private void drawParkDetail(GraphicsContext gc, Point2D center, int x, int y, double ratio) {
        if (Math.floorMod(x * 17 + y * 11, 3) != 0) {
            drawTree(gc, center, ratio);
        }
    }

    private void drawBuildingGround(GraphicsContext gc, Point2D center, double ratio) {
        gc.drawImage(ratio > 0.5 ? recoveringTile : dustTile,
                center.getX() - GameConstants.TILE_WIDTH / 2.0,
                center.getY() - GameConstants.TILE_HEIGHT / 2.0);
    }

    private void drawBuildingStructure(GraphicsContext gc, CityTileType type, Point2D center, int x, int y, double ratio) {
        int hash = Math.floorMod(x * 31 + y * 47, 8);
        double width = type == CityTileType.BUILDING_HIGH ? 52 : 46;
        double depth = type == CityTileType.BUILDING_HIGH ? 28 : 24;
        double height = type == CityTileType.BUILDING_HIGH ? 62 + hash * 6 : 30 + hash * 3;

        Color wall = Color.web(hash % 2 == 0 ? "#d8dfdb" : "#b9d5df");
        Color side = wall.darker();
        Color roof = Color.web(hash % 3 == 0 ? "#734241" : "#304f5d");

        double left = center.getX() - width / 2.0;
        double top = center.getY() - height;

        gc.setFill(side);
        gc.fillPolygon(
                new double[] {left + width, left + width + depth / 2.0, left + width + depth / 2.0, left + width},
                new double[] {top + 8, top + depth / 2.0 + 8, center.getY() + depth / 2.0, center.getY()},
                4
        );

        gc.setFill(wall);
        gc.fillPolygon(
                new double[] {left, left + width, left + width, left},
                new double[] {top + 8, top + 8, center.getY(), center.getY() + depth / 2.0},
                4
        );

        gc.setFill(roof);
        gc.fillPolygon(
                new double[] {left, left + width, left + width + depth / 2.0, left + depth / 2.0},
                new double[] {top + 8, top + 8, top + depth / 2.0 + 8, top + depth / 2.0 + 8},
                4
        );

        drawWindows(gc, left + 8, top + 18, width - 16, height - 18, ratio);
    }

    private void drawWindows(GraphicsContext gc, double x, double y, double width, double height, double ratio) {
        gc.setFill(Color.web("#f2e7a6", 0.22 + ratio * 0.18));

        for (double row = y; row < y + height - 10; row += 13) {
            for (double column = x; column < x + width - 4; column += 12) {
                gc.fillRect(column, row, 5, 6);
            }
        }
    }

    private void drawTree(GraphicsContext gc, Point2D center, double ratio) {
        gc.setFill(Color.web("#5f7042"));
        gc.fillRect(center.getX() - 2, center.getY() - 19, 4, 16);
        gc.setFill(Color.web("#4c9b54").interpolate(Color.web("#86c958"), ratio));
        gc.fillOval(center.getX() - 11, center.getY() - 31, 22, 18);
    }

    private void drawDistrictPerimeter(GraphicsContext gc, LevelDefinition level, int index, int currentLevelIndex) {
        double alpha = index == currentLevelIndex ? 0.92 : index < currentLevelIndex ? 0.55 : 0.28;

        for (GridPoint tile : level.shape().tiles()) {
            if (!level.shape().hasTile(tile.north().x(), tile.north().y())) {
                drawWall(gc, tile.x() + 0.5, tile.y() + 0.05, alpha);
            }
            if (!level.shape().hasTile(tile.south().x(), tile.south().y())) {
                drawWall(gc, tile.x() + 0.5, tile.y() + 0.95, alpha);
            }
            if (!level.shape().hasTile(tile.west().x(), tile.west().y())) {
                drawWall(gc, tile.x() + 0.05, tile.y() + 0.5, alpha);
            }
            if (!level.shape().hasTile(tile.east().x(), tile.east().y())) {
                drawWall(gc, tile.x() + 0.95, tile.y() + 0.5, alpha);
            }
        }
    }

    private void drawWall(GraphicsContext gc, double x, double y, double alpha) {
        Point2D screen = projection.toScreen(new IsoPoint(x, y));
        gc.setGlobalAlpha(alpha);
        gc.drawImage(boundaryWall, screen.getX() - 48, screen.getY() - 72);
        gc.setGlobalAlpha(1.0);
    }

    private void drawDistrictTitle(GraphicsContext gc, LevelDefinition level, int index, int currentLevelIndex) {
        Point2D screen = projection.toScreen(level.bounds().center());
        gc.setFill(index == currentLevelIndex ? Color.web("#f7f4dc", 0.82) : Color.web("#f7f4dc", 0.18));
        gc.fillText(level.title(), screen.getX() - 48, screen.getY() - 56);
    }

    private void drawDiamond(GraphicsContext gc, Point2D center, Color fill, Color stroke) {
        double halfWidth = GameConstants.TILE_WIDTH / 2.0;
        double halfHeight = GameConstants.TILE_HEIGHT / 2.0;

        double[] xPoints = {center.getX(), center.getX() + halfWidth, center.getX(), center.getX() - halfWidth};
        double[] yPoints = {center.getY() - halfHeight, center.getY(), center.getY() + halfHeight, center.getY()};

        gc.setFill(fill);
        gc.fillPolygon(xPoints, yPoints, 4);
        gc.setStroke(stroke);
        gc.strokePolygon(xPoints, yPoints, 4);
    }
}
