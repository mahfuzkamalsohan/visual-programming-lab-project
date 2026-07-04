package pkg.restoration.world;

public enum CityDecor {
    NONE(false),
    ROAD_PLANT(false),
    PLAZA_SIGN(true),
    PLAZA_PLANT(false),
    PARK_PLANT(true),
    PARK_TREE(true),
    PARK_FRUIT_TREE(true),
    PARK_LONG_TREE(true);

    private final boolean blocksMovement;

    CityDecor(boolean blocksMovement) {
        this.blocksMovement = blocksMovement;
    }

    public boolean blocksMovement() {
        return blocksMovement;
    }

    public static CityDecor at(CityTileType type, int x, int y) {
        int hash = tileHash(x, y);
        return switch (type) {
            case ROAD -> Math.floorMod(hash, 11) == 0 ? ROAD_PLANT : NONE;
            case PLAZA -> plazaDecor(hash);
            case PARK -> parkDecor(hash);
            case WATER, PIER, BUILDING_LOW, BUILDING_HIGH -> NONE;
        };
    }

    public static int tileHash(int x, int y) {
        return Math.floorMod(x * 734287 + y * 912271, 10_000);
    }

    public static double tileNoise(int x, int y) {
        return tileHash(x, y) / 9_999.0;
    }

    private static CityDecor plazaDecor(int hash) {
        if (Math.floorMod(hash, 17) == 0) {
            return PLAZA_SIGN;
        }

        if (Math.floorMod(hash, 7) == 0) {
            return PLAZA_PLANT;
        }

        return NONE;
    }

    private static CityDecor parkDecor(int hash) {
        if (Math.floorMod(hash, 5) == 0) {
            return PARK_PLANT;
        }

        if (Math.floorMod(hash, 3) == 0) {
            return NONE;
        }

        return switch (Math.floorMod(hash / 3, 3)) {
            case 0 -> PARK_TREE;
            case 1 -> PARK_FRUIT_TREE;
            default -> PARK_LONG_TREE;
        };
    }
}
