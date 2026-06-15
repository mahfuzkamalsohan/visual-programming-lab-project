package pkg.restoration.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class CityMap {

    private final Map<GridPoint, CityTileType> tiles;
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;

    CityMap(int minX, int minY, int width, int height, Map<GridPoint, CityTileType> tiles) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = minX + width;
        this.maxY = minY + height;
        this.tiles = new LinkedHashMap<>();
        tiles.forEach((point, type) -> put(point.x(), point.y(), type));
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public CityTileType tileAt(int x, int y) {
        return tiles.getOrDefault(new GridPoint(x, y), CityTileType.WATER);
    }

    public boolean isWalkable(int x, int y) {
        return tileAt(x, y).walkable();
    }

    public Set<GridPoint> tilePositions() {
        return Collections.unmodifiableSet(tiles.keySet());
    }

    boolean hasTile(int x, int y) {
        return tiles.containsKey(new GridPoint(x, y));
    }

    void put(int x, int y, CityTileType type) {
        tiles.put(new GridPoint(x, y), type);
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x + 1);
        maxY = Math.max(maxY, y + 1);
    }
}
