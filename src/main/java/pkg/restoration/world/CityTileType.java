package pkg.restoration.world;

public enum CityTileType {
    WATER(false),
    PIER(true),
    ROAD(true),
    PLAZA(true),
    PARK(true),
    BUILDING_LOW(false),
    BUILDING_HIGH(false);

    private final boolean walkable;

    CityTileType(boolean walkable) {
        this.walkable = walkable;
    }

    public boolean walkable() {
        return walkable;
    }
}
