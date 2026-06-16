package pkg.restoration.assets;

import java.util.Locale;

import pkg.restoration.components.Direction;

public final class AssetCatalog {

    public static final int PLAYER_FRAME_WIDTH = 96;
    public static final int PLAYER_FRAME_HEIGHT = 128;
    public static final int PLAYER_FRAMES = 4;
    public static final int PLAYER_DIRECTIONS = 8;
    public static final String PLAYER_WALKING_ATLAS = "restoration/walking.png";

    public static final String TILE_DUST = "restoration/tile_dust.png";
    public static final String TILE_RECOVERING = "restoration/tile_recovering.png";
    public static final String TILE_GREEN = "restoration/tile_green.png";
    public static final String TILE_PATH = "restoration/tile_path.png";
    public static final String BOUNDARY_WALL = "restoration/boundary_wall.png";
    public static final String GATE_SEALED = "restoration/gate_sealed.png";
    public static final String GATE_OPEN = "restoration/gate_open.png";
    public static final String GATE_CLOSED = "restoration/gate_closed.png";
    public static final String GATE_DECISION = "restoration/gate_decision.png";
    public static final String NPC_KEEPER = "restoration/npc_keeper.png";
    public static final String NPC_RESCUE_DOG = "restoration/npc_rescue_dog.png";
    public static final String NPC_CANAL_DUCK = "restoration/npc_canal_duck.png";
    public static final String NPC_ORCHARD_DEER = "restoration/npc_orchard_deer.png";

    private AssetCatalog() {
    }

    public static String playerIdleSheet(Direction direction) {
        return "restoration/player_idle_" + direction.assetSuffix().toLowerCase(Locale.ROOT) + ".png";
    }

    public static int walkingAtlasRow(Direction direction) {
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
}
