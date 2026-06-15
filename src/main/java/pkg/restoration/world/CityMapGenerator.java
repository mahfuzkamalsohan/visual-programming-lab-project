package pkg.restoration.world;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CityMapGenerator {

    private static final int MIN_X = -6;
    private static final int MIN_Y = -16;
    private static final int WIDTH = 42;
    private static final int HEIGHT = 52;
    private static final int DISTRICT_PADDING = 9;
    private static final GridPoint[] ROUTE_ANCHORS = {
            new GridPoint(5, 31),
            new GridPoint(9, 24),
            new GridPoint(13, 16),
            new GridPoint(18, 7),
            new GridPoint(21, -2),
            new GridPoint(25, -11)
    };

    private CityMapGenerator() {
    }

    public static CityMap generateDefaultCity() {
        Map<GridPoint, CityTileType> tiles = new LinkedHashMap<>();

        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int x = MIN_X; x < MIN_X + WIDTH; x++) {
                tiles.put(new GridPoint(x, y), baseTile(x, y));
            }
        }

        addOrthogonalRoadGrid(tiles);
        addRouteBoulevard(tiles,
                new GridPoint(5, 31),
                new GridPoint(9, 24),
                new GridPoint(13, 16),
                new GridPoint(18, 7),
                new GridPoint(21, -2),
                new GridPoint(25, -11)
        );

        addPark(tiles, 0, 24, 6, 5);
        addPark(tiles, 11, 8, 6, 5);
        addPark(tiles, 22, -8, 7, 5);
        addPark(tiles, 7, 28, 5, 4);

        addDistrictForecourts(tiles);

        addPier(tiles, -5, 29, 9, 1);
        addPier(tiles, -4, 33, 8, 1);

        return new CityMap(MIN_X, MIN_Y, WIDTH, HEIGHT, tiles);
    }

    public static void ensureDistrict(CityMap cityMap, int districtIndex) {
        GridPoint entry = routeAnchor(districtIndex);
        GridPoint exit = routeAnchor(districtIndex + 1);

        int minX = Math.min(entry.x(), exit.x()) - DISTRICT_PADDING;
        int minY = Math.min(entry.y(), exit.y()) - DISTRICT_PADDING;
        int maxX = Math.max(entry.x(), exit.x()) + DISTRICT_PADDING;
        int maxY = Math.max(entry.y(), exit.y()) + DISTRICT_PADDING;

        paintCityWindow(cityMap, minX, minY, maxX - minX + 1, maxY - minY + 1);
        addOrthogonalRoadGrid(cityMap, minX, minY, maxX - minX + 1, maxY - minY + 1);
        carveLine(cityMap, entry, exit, 1);

        addPlaza(cityMap, entry.x(), entry.y(), 3);
        addPlaza(cityMap, exit.x(), exit.y(), 3);
        addPlaza(cityMap, entry.x() + 3, entry.y() - 2, 2);

        int parkOffset = Math.floorMod(districtIndex, 4);
        addPark(cityMap, minX + 2 + parkOffset, minY + 3 + parkOffset, 5 + parkOffset, 4);
    }

    public static GridPoint routeAnchor(int index) {
        if (index < ROUTE_ANCHORS.length) {
            return ROUTE_ANCHORS[index];
        }

        GridPoint lastStarter = ROUTE_ANCHORS[ROUTE_ANCHORS.length - 1];
        int extra = index - ROUTE_ANCHORS.length + 1;
        return new GridPoint(lastStarter.x() + extra * 2, lastStarter.y() - extra * 3);
    }

    private static CityTileType baseTile(int x, int y) {
        if (x < -2 || y > 33 || (x < 3 && y > 27)) {
            return CityTileType.WATER;
        }

        int hash = Math.floorMod(x * 37 + y * 53, 11);
        return hash <= 3 ? CityTileType.BUILDING_HIGH : CityTileType.BUILDING_LOW;
    }

    private static void addOrthogonalRoadGrid(Map<GridPoint, CityTileType> tiles) {
        for (GridPoint point : tiles.keySet()) {
            CityTileType current = tiles.get(point);
            if (current == CityTileType.WATER) {
                continue;
            }

            boolean avenue = Math.floorMod(point.x() + 1, 6) == 0;
            boolean street = Math.floorMod(point.y() + 2, 6) == 0;
            if (avenue || street) {
                tiles.put(point, CityTileType.ROAD);
            }
        }
    }

    private static void addRouteBoulevard(Map<GridPoint, CityTileType> tiles, GridPoint... points) {
        for (int i = 0; i < points.length - 1; i++) {
            carveLine(tiles, points[i], points[i + 1], 1);
        }
    }

    private static void carveLine(Map<GridPoint, CityTileType> tiles, GridPoint start, GridPoint end, int radius) {
        int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.y() - start.y())) * 3 + 1;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = (int) Math.round(start.x() + (end.x() - start.x()) * t);
            int y = (int) Math.round(start.y() + (end.y() - start.y()) * t);

            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    setIfLand(tiles, x + dx, y + dy, CityTileType.ROAD);
                }
            }
        }
    }

    private static void addPark(Map<GridPoint, CityTileType> tiles, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                setIfLand(tiles, x + column, y + row, CityTileType.PARK);
            }
        }
    }

    private static void addDistrictForecourts(Map<GridPoint, CityTileType> tiles) {
        addPlaza(tiles, 5, 31, 3);
        addPlaza(tiles, 9, 24, 3);
        addPlaza(tiles, 13, 16, 3);
        addPlaza(tiles, 18, 7, 3);
        addPlaza(tiles, 21, -2, 3);
        addPlaza(tiles, 25, -11, 3);

        addPlaza(tiles, 10, 13, 2);
        addPlaza(tiles, 16, -5, 2);

        addPlaza(tiles, 12, 18, 3);
        addPlaza(tiles, 19, 0, 3);
    }

    private static void addPlaza(Map<GridPoint, CityTileType> tiles, int centerX, int centerY, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= radius + 1) {
                    setIfLand(tiles, centerX + dx, centerY + dy, CityTileType.PLAZA);
                }
            }
        }
    }

    private static void addPier(Map<GridPoint, CityTileType> tiles, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                GridPoint point = new GridPoint(x + column, y + row);
                if (tiles.containsKey(point)) {
                    tiles.put(point, CityTileType.PIER);
                }
            }
        }
    }

    private static void setIfLand(Map<GridPoint, CityTileType> tiles, int x, int y, CityTileType type) {
        GridPoint point = new GridPoint(x, y);
        CityTileType current = tiles.get(point);
        if (current != null && current != CityTileType.WATER) {
            tiles.put(point, type);
        }
    }

    private static void paintCityWindow(CityMap cityMap, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int tileX = x + column;
                int tileY = y + row;
                if (!cityMap.hasTile(tileX, tileY)) {
                    cityMap.put(tileX, tileY, baseTile(tileX, tileY));
                }
            }
        }
    }

    private static void addOrthogonalRoadGrid(CityMap cityMap, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int tileX = x + column;
                int tileY = y + row;
                CityTileType current = cityMap.tileAt(tileX, tileY);
                if (current == CityTileType.WATER) {
                    continue;
                }

                boolean avenue = Math.floorMod(tileX + 1, 6) == 0;
                boolean street = Math.floorMod(tileY + 2, 6) == 0;
                if (avenue || street) {
                    cityMap.put(tileX, tileY, CityTileType.ROAD);
                }
            }
        }
    }

    private static void carveLine(CityMap cityMap, GridPoint start, GridPoint end, int radius) {
        int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.y() - start.y())) * 3 + 1;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = (int) Math.round(start.x() + (end.x() - start.x()) * t);
            int y = (int) Math.round(start.y() + (end.y() - start.y()) * t);

            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    setIfLand(cityMap, x + dx, y + dy, CityTileType.ROAD);
                }
            }
        }
    }

    private static void addPark(CityMap cityMap, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                setIfLand(cityMap, x + column, y + row, CityTileType.PARK);
            }
        }
    }

    private static void addPlaza(CityMap cityMap, int centerX, int centerY, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= radius + 1) {
                    setIfLand(cityMap, centerX + dx, centerY + dy, CityTileType.PLAZA);
                }
            }
        }
    }

    private static void setIfLand(CityMap cityMap, int x, int y, CityTileType type) {
        CityTileType current = cityMap.tileAt(x, y);
        if (cityMap.hasTile(x, y) && current != CityTileType.WATER) {
            cityMap.put(x, y, type);
        }
    }
}
