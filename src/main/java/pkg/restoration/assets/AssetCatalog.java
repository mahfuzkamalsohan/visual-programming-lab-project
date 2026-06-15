package pkg.restoration.assets;

import java.util.Locale;

import pkg.restoration.components.Direction;

public final class AssetCatalog {

    public static final int PLAYER_FRAME_WIDTH = 96;
    public static final int PLAYER_FRAME_HEIGHT = 128;
    public static final int PLAYER_FRAMES = 4;

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

    public static String playerSheet(boolean walking, Direction direction) {
        String state = walking ? "walk" : "idle";
        return "restoration/player_" + state + "_" + direction.assetSuffix().toLowerCase(Locale.ROOT) + ".png";
    }
}
