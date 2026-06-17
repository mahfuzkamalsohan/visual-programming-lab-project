package pkg.restoration.world;

import static com.almasb.fxgl.dsl.FXGL.image;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Image[] buildingSprites;
    private final Image[] treeSprites;
    private final Image[] plantSprites;
    private final Image signboardSprite;
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
        this.buildingSprites = new Image[] {
                image(AssetCatalog.BUILDING_1),
                image(AssetCatalog.BUILDING_2),
                image(AssetCatalog.BUILDING_3),
                image(AssetCatalog.BUILDING_4)
        };
        this.treeSprites = new Image[] {
                image(AssetCatalog.TREE),
                image(AssetCatalog.FRUIT_TREE),
                image(AssetCatalog.LONG_TREE)
        };
        this.plantSprites = new Image[] {
                image(AssetCatalog.BUSH),
                image(AssetCatalog.HERB)
        };
        this.signboardSprite = image(AssetCatalog.SIGNBOARD_POINTING_SE);
    }

    public Canvas canvas() {
        return canvas;
    }

    public void render(List<LevelDefinition> levels, int currentLevelIndex, double restorationRatio) {
        if (lastCurrentLevelIndex == currentLevelIndex && Math.abs(restorationRatio - lastRatio) < 0.006) {
            return;
        }

        lastCurrentLevelIndex = currentLevelIndex;
        lastRatio = restorationRatio;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawAtmosphere(gc, restorationRatio);
        drawCity(gc, restorationRatio, restorationPathTiles(levels, currentLevelIndex));

        for (int index = 0; index < levels.size(); index++) {
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

    private void drawCity(GraphicsContext gc, double ratio, Set<GridPoint> pathTiles) {
        List<GridPoint> tiles = new ArrayList<>(cityMap.tilePositions());
        tiles.sort(Comparator
                .comparingInt((GridPoint point) -> point.x() + point.y())
                .thenComparingInt(GridPoint::y)
                .thenComparingInt(GridPoint::x));

        for (GridPoint tile : tiles) {
            CityTileType type = cityMap.tileAt(tile.x(), tile.y());
            Point2D center = projection.toScreen(new IsoPoint(tile.x() + 0.5, tile.y() + 0.5));
            drawCityGround(gc, type, tile.x(), tile.y(), center, ratio, pathTiles.contains(tile));
        }

        for (GridPoint tile : tiles) {
            CityTileType type = cityMap.tileAt(tile.x(), tile.y());
            Point2D center = projection.toScreen(new IsoPoint(tile.x() + 0.5, tile.y() + 0.5));
            drawCityDetail(gc, type, tile.x(), tile.y(), center, ratio, pathTiles.contains(tile));
        }
    }

    private void drawCityGround(GraphicsContext gc, CityTileType type, int x, int y, Point2D center, double ratio, boolean routePath) {
        switch (type) {
            case WATER -> drawWater(gc, center, ratio);
            case PIER -> drawPier(gc, center);
            case ROAD -> drawRoadGround(gc, center, x, y, ratio, routePath);
            case PLAZA -> drawPlaza(gc, center, x, y, ratio, routePath);
            case PARK -> drawParkGround(gc, center, x, y, ratio, routePath);
            case BUILDING_LOW, BUILDING_HIGH -> drawBuildingGround(gc, center, x, y, ratio);
        }
    }

    private void drawCityDetail(GraphicsContext gc, CityTileType type, int x, int y, Point2D center, double ratio, boolean routePath) {
        switch (type) {
            case ROAD -> drawRoadDetail(gc, center, x, y, ratio, routePath);
            case PLAZA -> drawPlazaDetail(gc, center, x, y, ratio, routePath);
            case PARK -> drawParkDetail(gc, center, x, y, ratio, routePath);
            case BUILDING_LOW, BUILDING_HIGH -> drawBuildingStructure(gc, type, center, x, y, ratio);
            case WATER, PIER -> {
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
        drawIsoTile(gc, pathTile, center);
    }

    private void drawRoadMarking(GraphicsContext gc, Point2D center, int x, int y, boolean routePath) {
        if (routePath && Math.floorMod(x + y, 2) == 0) {
            gc.setStroke(Color.web("#f2f1dc", 0.55));
            gc.setLineWidth(2);
            gc.strokeLine(center.getX() - 18, center.getY(), center.getX() + 18, center.getY());
        }
    }

    private void drawRoadGround(GraphicsContext gc, Point2D center, int x, int y, double ratio, boolean routePath) {
        if (routePath) {
            drawPathTile(gc, center);
        } else {
            drawRestoringGround(gc, center, x, y, ratio, 0.04);
        }
    }

    private void drawRoadDetail(GraphicsContext gc, Point2D center, int x, int y, double ratio, boolean routePath) {
        drawRoadMarking(gc, center, x, y, routePath);
        if (!routePath && ratio > 0.35 && Math.floorMod(tileHash(x, y), 11) == 0) {
            drawPlantSprite(gc, plantSprites[Math.floorMod(tileHash(x + 3, y - 2), plantSprites.length)], center, 42, 0.72 + ratio * 0.24);
        }
    }

    private void drawPlaza(GraphicsContext gc, Point2D center, int x, int y, double ratio, boolean routePath) {
        if (routePath) {
            drawPathTile(gc, center);
        } else {
            drawRestoringGround(gc, center, x, y, ratio, 0.02);
        }
        if (routePath) {
            gc.setFill(Color.web("#e6d58d", 0.18 + ratio * 0.16));
            gc.fillPolygon(
                    new double[] {center.getX(), center.getX() + 24, center.getX(), center.getX() - 24},
                    new double[] {center.getY() - 12, center.getY(), center.getY() + 12, center.getY()},
                    4
            );
        }
    }

    private void drawPlazaDetail(GraphicsContext gc, Point2D center, int x, int y, double ratio, boolean routePath) {
        if (routePath) {
            return;
        }

        int hash = tileHash(x, y);
        if (ratio > 0.4 && Math.floorMod(hash, 17) == 0) {
            drawSprite(gc, signboardSprite, center.getX(), center.getY() + 13, 54, 54, 0.5, 0.82, 0.92);
        } else if (ratio > 0.35 && Math.floorMod(hash, 7) == 0) {
            drawPlantSprite(gc, plantSprites[Math.floorMod(hash, plantSprites.length)], center, 38, 0.72 + ratio * 0.24);
        }
    }

    private void drawParkGround(GraphicsContext gc, Point2D center, int x, int y, double ratio, boolean routePath) {
        if (routePath) {
            drawPathTile(gc, center);
        } else {
            drawRestoringGround(gc, center, x, y, ratio, 0.0);
        }
    }

    private void drawParkDetail(GraphicsContext gc, Point2D center, int x, int y, double ratio, boolean routePath) {
        if (routePath) {
            return;
        }

        int hash = tileHash(x, y);
        if (ratio < 0.22 && Math.floorMod(hash, 4) != 0) {
            return;
        }

        if (Math.floorMod(hash, 5) == 0) {
            drawPlantSprite(gc, plantSprites[Math.floorMod(hash, plantSprites.length)], center, 46, 0.64 + ratio * 0.28);
        } else if (Math.floorMod(hash, 3) != 0) {
            Image tree = treeSprites[Math.floorMod(hash, treeSprites.length)];
            double size = tree == treeSprites[2] ? 124 : 104;
            drawTreeSprite(gc, tree, center, size, 0.56 + ratio * 0.38);
        }
    }

    private void drawBuildingGround(GraphicsContext gc, Point2D center, int x, int y, double ratio) {
        drawRestoringGround(gc, center, x, y, ratio, 0.14);
    }

    private void drawBuildingStructure(GraphicsContext gc, CityTileType type, Point2D center, int x, int y, double ratio) {
        int hash = tileHash(x, y);
        Image building = buildingSprites[Math.floorMod(hash, buildingSprites.length)];
        double size = type == CityTileType.BUILDING_HIGH ? 178 + Math.floorMod(hash, 3) * 8 : 146 + Math.floorMod(hash, 2) * 8;
        drawSprite(gc, building, center.getX(), center.getY() + 30, size, size, 0.5, 0.84, 0.86 + ratio * 0.12);
    }

    private void drawRestoringGround(GraphicsContext gc, Point2D center, int x, int y, double ratio, double dustBias) {
        double adjusted = ratio + (tileNoise(x, y) - 0.5) * 0.12 - dustBias;
        if (adjusted < 0.48) {
            drawIsoTile(gc, dustTile, center);
        } else if (adjusted < 0.82) {
            drawIsoTile(gc, recoveringTile, center);
        } else {
            drawIsoTile(gc, greenTile, center);
        }
    }

    private void drawTreeSprite(GraphicsContext gc, Image tree, Point2D center, double size, double alpha) {
        drawSprite(gc, tree, center.getX(), center.getY() + 18, size, size, 0.5, 0.86, alpha);
    }

    private void drawPlantSprite(GraphicsContext gc, Image plant, Point2D center, double size, double alpha) {
        drawSprite(gc, plant, center.getX(), center.getY() + 15, size, size, 0.5, 0.84, alpha);
    }

    private void drawSprite(GraphicsContext gc, Image sprite, double anchorX, double anchorY, double width, double height,
                            double anchorRatioX, double anchorRatioY, double alpha) {
        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.drawImage(
                sprite,
                anchorX - width * anchorRatioX,
                anchorY - height * anchorRatioY,
                width,
                height
        );
        gc.restore();
    }

    private void drawIsoTile(GraphicsContext gc, Image tile, Point2D center) {
        gc.drawImage(
                tile,
                center.getX() - GameConstants.TILE_WIDTH / 2.0,
                center.getY() - GameConstants.TILE_WIDTH / 2.0,
                GameConstants.TILE_WIDTH,
                GameConstants.TILE_WIDTH
        );
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

    private Set<GridPoint> restorationPathTiles(List<LevelDefinition> levels, int currentLevelIndex) {
        Set<GridPoint> pathTiles = new HashSet<>();
        if (currentLevelIndex < 0 || currentLevelIndex >= levels.size()) {
            return pathTiles;
        }

        LevelDefinition level = levels.get(currentLevelIndex);
        for (GateDefinition gate : level.gates()) {
            pathTiles.addAll(pathBetween(level, tileAt(level.playerSpawn()), nearestShapeTile(level.shape(), gate.position())));
        }

        return pathTiles;
    }

    private List<GridPoint> pathBetween(LevelDefinition level, GridPoint start, GridPoint goal) {
        Set<GridPoint> walkable = level.shape().tiles();
        if (!walkable.contains(start) || !walkable.contains(goal)) {
            return List.of();
        }

        Deque<GridPoint> frontier = new ArrayDeque<>();
        Map<GridPoint, GridPoint> previous = new HashMap<>();
        frontier.add(start);
        previous.put(start, start);

        while (!frontier.isEmpty()) {
            GridPoint current = frontier.removeFirst();
            if (current.equals(goal)) {
                return reconstructPath(previous, start, goal);
            }

            for (GridPoint neighbor : pathNeighbors(current, goal)) {
                if (walkable.contains(neighbor) && !previous.containsKey(neighbor)) {
                    previous.put(neighbor, current);
                    frontier.addLast(neighbor);
                }
            }
        }

        return List.of();
    }

    private List<GridPoint> reconstructPath(Map<GridPoint, GridPoint> previous, GridPoint start, GridPoint goal) {
        List<GridPoint> path = new ArrayList<>();
        GridPoint current = goal;

        while (!current.equals(start)) {
            path.add(current);
            current = previous.get(current);
            if (current == null) {
                return List.of();
            }
        }

        path.add(start);
        return path;
    }

    private List<GridPoint> pathNeighbors(GridPoint current, GridPoint goal) {
        List<GridPoint> neighbors = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) {
                    neighbors.add(new GridPoint(current.x() + dx, current.y() + dy));
                }
            }
        }

        neighbors.sort(Comparator.comparingInt(point -> Math.abs(point.x() - goal.x()) + Math.abs(point.y() - goal.y())));
        return neighbors;
    }

    private GridPoint nearestShapeTile(LevelShape shape, IsoPoint point) {
        GridPoint target = tileAt(point);
        if (shape.tiles().contains(target)) {
            return target;
        }

        return shape.tiles().stream()
                .min(Comparator.comparingDouble(tile -> new IsoPoint(tile.x() + 0.5, tile.y() + 0.5).distance(point)))
                .orElse(target);
    }

    private static GridPoint tileAt(IsoPoint point) {
        return new GridPoint((int) Math.floor(point.x()), (int) Math.floor(point.y()));
    }

    private static int tileHash(int x, int y) {
        return Math.floorMod(x * 734287 + y * 912271, 10_000);
    }

    private static double tileNoise(int x, int y) {
        return tileHash(x, y) / 9_999.0;
    }
}
