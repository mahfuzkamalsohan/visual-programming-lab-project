package pkg.restoration.assets;

import pkg.restoration.components.Direction;

public final class AssetCatalog {

    public static final int PLAYER_FRAME_WIDTH = 96;
    public static final int PLAYER_FRAME_HEIGHT = 128;
    public static final int PLAYER_FRAMES = 4;
    public static final int PLAYER_DIRECTIONS = 8;
    public static final String PLAYER_IDLE_ATLAS = "restoration/idle.png";
    public static final String PLAYER_WALKING_ATLAS = "restoration/walking.png";

    public static final String TILE_DUST = "restoration/tile_dust.png";
    public static final String TILE_RECOVERING = "restoration/tile_recovering.png";
    public static final String TILE_GREEN = "restoration/tile_green.png";
    public static final String TILE_PATH = "restoration/tile_path.png";
    public static final String WALL_BRICK = "restoration/wall_brick.png";
    public static final String WALL_STONE = "restoration/wall_stone.png";
    public static final String WALL_WOODEN = "restoration/wall_wooden.png";
    public static final String GATE_SEALED = "restoration/gate_sealed.png";
    public static final String GATE_OPEN = "restoration/gate_open.png";
    public static final String GATE_CLOSED = "restoration/gate_closed.png";
    public static final String GATE_DECISION = "restoration/gate_decision.png";
    public static final String NPC_KEEPER = "restoration/npc_keeper.png";
    public static final String NPC_BOY = "restoration/npc_boy.png";
    public static final String NPC_GIRL = "restoration/npc_girl.png";
    public static final String NPC_RESCUE_DOG = "restoration/npc_puppy.png";
    public static final String NPC_CANAL_DUCK = "restoration/npc_canal_duck.png";
    public static final String NPC_ORCHARD_DEER = "restoration/npc_dear.png";
    public static final String BUILDING_1 = "restoration/building_1.png";
    public static final String BUILDING_2 = "restoration/building_2.png";
    public static final String BUILDING_3 = "restoration/building_3.png";
    public static final String BUILDING_4 = "restoration/building_4.png";
    public static final String TREE = "restoration/tree.png";
    public static final String FRUIT_TREE = "restoration/fruit_tree.png";
    public static final String LONG_TREE = "restoration/long_tree.png";
    public static final String BUSH = "restoration/bush.png";
    public static final String HERB = "restoration/herb.png";
    public static final String SIGNBOARD_POINTING_SE = "restoration/signboard_pointing_se.png";

    private AssetCatalog() {
    }

    public static int playerAtlasRow(Direction direction, boolean walking) {
        return walking ? playerWalkingAtlasRow(direction) : playerIdleAtlasRow(direction);
    }

    private static int playerWalkingAtlasRow(Direction direction) {
        return switch (direction) {
            case W -> 0;
            case SW -> 1;
            case SE -> 2;
            case S -> 3;
            case NW -> 4;
            case NE -> 5;
            case N -> 6;
            case E -> 7;
        };
    }

    private static int playerIdleAtlasRow(Direction direction) {
        return switch (direction) {
            case W -> 0;
            case SW -> 1;
            case S -> 2;
            case N -> 3;
            case NW -> 4;
            case NE -> 5;
            case SE -> 6;
            case E -> 7;
        };
    }
}
