package pkg.restoration.systems;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.level.tiled.Layer;
import com.almasb.fxgl.entity.level.tiled.TMXLevelLoader;
import com.almasb.fxgl.entity.level.tiled.TiledMap;
import com.almasb.fxgl.entity.level.tiled.TilesetLoader;

import javafx.scene.Node;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicMapManager {

    // MAP DIMENSIONS (40x40 Tiles)
    private static final int MAP_WIDTH_TILES = 40;
    private static final int MAP_HEIGHT_TILES = 40;

    // CHUNK CONFIGURATION (10x10 Tiles per Chunk -> 4x4 Chunks total)
    private static final int CHUNK_SIZE = 10;
    private static final int CHUNKS_X = MAP_WIDTH_TILES / CHUNK_SIZE; // 4
    private static final int CHUNKS_Y = MAP_HEIGHT_TILES / CHUNK_SIZE; // 4

    // [y][x] array representing the 4x4 grid of 10x10 chunks.
    // Set true = Explored (Visible/Restored), false = Unexplored (Hidden/polluted)
    private final boolean[][] exploredChunks = new boolean[CHUNKS_Y][CHUNKS_X];
    // Cache of map entities by their tile coordinates
    private final Map<String, Entity> tileEntities = new HashMap<>();

    private final String mapResourcePath;
    private URL mapURL;
    private TiledMap tiledMap;
    private List<Long> originalTileGids;
    private Layer tileLayer;

    private int currentStage = 5;
    private double currentRestorationRatio = 1.0;
    private Entity mapTileEntity;

    public DynamicMapManager(String mapResourcePath) {

        exploredChunks[0][0] = true; // Top-Left Chunk
        exploredChunks[0][1] = true; // Top-Left Chunk
        exploredChunks[0][2] = true;
        exploredChunks[0][3] = true; // Center-Right Chunk]
        exploredChunks[1][0] = true; // Top-Left Chunk
        exploredChunks[1][1] = true; // Top-Left Chunk
        exploredChunks[1][2] = true;
        exploredChunks[1][3] = true;
        exploredChunks[2][0] = true; // Top-Left Chunk
        exploredChunks[2][1] = true; // Top-Left Chunk
        exploredChunks[2][2] = true;
        exploredChunks[2][3] = true;
        exploredChunks[3][0] = true; // Bottom-Right Chunk
        exploredChunks[3][1] = true;
        exploredChunks[3][2] = true; // Center-Right Chunk
        exploredChunks[3][3] = true; // Bottom-Right Chunk
        this.mapResourcePath = mapResourcePath;
        loadOriginalMap();
    }

    public void registerTileEntity(int tileX, int tileY, Entity entity) {
        String key = tileX + "," + tileY;
        tileEntities.put(key, entity);

        // Apply initial visibility based on chunk state
        updateSingleTileVisibility(tileX, tileY, entity);
    }

    private void updateSingleTileVisibility(int tileX, int tileY, Entity entity) {
        int chunkX = tileX / CHUNK_SIZE;
        int chunkY = tileY / CHUNK_SIZE;

        // Check if current tile belongs to an explored chunk
        boolean isExplored = isChunkExplored(chunkX, chunkY);

        // Adjust visibility or effects
        entity.setVisible(isExplored);

        // Optional: Disable entity components/collisions if unexplored
        if (!isExplored) {
            // e.g., entity.setProperty("collidable", false);
        }
    }

    public boolean isChunkExplored(int chunkX, int chunkY) {
        if (chunkX < 0 || chunkX >= CHUNKS_X || chunkY < 0 || chunkY >= CHUNKS_Y) {
            return false;
        }
        return exploredChunks[chunkY][chunkX];
    }

    public void setChunkExplored(int chunkX, int chunkY, boolean explored) {
        if (chunkX >= 0 && chunkX < CHUNKS_X && chunkY >= 0 && chunkY < CHUNKS_Y) {
            if (exploredChunks[chunkY][chunkX] != explored) {
                exploredChunks[chunkY][chunkX] = explored;
                refreshMapVisibility();
            }
        }
    }

    public void refreshMapVisibility() {
        applyStageTransitions(currentRestorationRatio);
        tileEntities.forEach((key, entity) -> {
            String[] coords = key.split(",");
            int tileX = Integer.parseInt(coords[0]);
            int tileY = Integer.parseInt(coords[1]);
            updateSingleTileVisibility(tileX, tileY, entity);
        });
    }

    public void unlockChunkAtWorldPos(double worldX, double worldY) {
        // Isometric coordinate conversion matching 32x16 isometric tiles
        int tileX = (int) (worldX / 32);
        int tileY = (int) (worldY / 16);

        int chunkX = tileX / CHUNK_SIZE;
        int chunkY = tileY / CHUNK_SIZE;

        setChunkExplored(chunkX, chunkY, true);
    }

    private void loadOriginalMap() {
        try {
            mapURL = getClass().getClassLoader().getResource("assets/levels/" + mapResourcePath);
            if (mapURL != null) {
                try (InputStream is = mapURL.openStream()) {
                    tiledMap = new TMXLevelLoader().parse(is);
                    if (tiledMap != null && !tiledMap.getLayers().isEmpty()) {
                        for (Layer layer : tiledMap.getLayers()) {
                            if ("tilelayer".equalsIgnoreCase(layer.getType()) || !layer.getData().isEmpty()) {
                                tileLayer = layer;
                                originalTileGids = new ArrayList<>(layer.getData());
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setInitialTileEntities(List<Entity> levelEntities) {
        if (levelEntities == null)
            return;
        for (Entity entity : levelEntities) {
            if (!entity.getProperties().exists("type") ||
                    (!"wall".equalsIgnoreCase(entity.getProperties().getString("type")) &&
                            !"PLAYER".equalsIgnoreCase(entity.getProperties().getString("type")))) {
                this.mapTileEntity = entity;
                break;
            }
        }
        refreshMapVisibility();
    }

    public void update(double restorationRatio) {
        this.currentRestorationRatio = restorationRatio;
        int targetStage = calculateStage(restorationRatio);
        if (targetStage != currentStage) {
            currentStage = targetStage;
            applyStageTransitions(restorationRatio);
        }
    }

    private int calculateStage(double ratio) {
        if (ratio > 0.75) {
            return 5;
        } else if (ratio > 0.50) {
            return 4;
        } else if (ratio > 0.25) {
            return 3;
        } else if (ratio > 0.10) {
            return 2;
        } else {
            return 1;
        }
    }

    private void applyStageTransitions(double ratio) {
        if (tileLayer == null || originalTileGids == null)
            return;

        int mapWidth = tiledMap != null ? tiledMap.getWidth() : MAP_WIDTH_TILES;
        List<Long> currentGids = new ArrayList<>(originalTileGids.size());

        for (int i = 0; i < originalTileGids.size(); i++) {

            long originalGid = originalTileGids.get(i);

            int tileX = i % mapWidth;
            int tileY = i / mapWidth;
            int chunkX = tileX / CHUNK_SIZE;
            int chunkY = tileY / CHUNK_SIZE;

            if (!isChunkExplored(chunkX, chunkY)) {
                currentGids.add(0L);
                continue;
            }
            long gid = originalGid;

            // Stage Transition Rules based on timer percentage:
            // - Stage 5 (ID 35 or 36) -> Stage 4 (ID 20) when timer <= 75%
            // - Stage 4 (ID 20) -> Stage 2 (ID 18) when timer <= 50%
            // - Stage 3 (ID 19) -> Stage 2 (ID 18) when timer <= 25%
            // - Stage 2 (ID 18) -> Stage 1 (ID 17) when timer <= 10%

            if (originalGid == 35 || originalGid == 36 || originalGid == 37 || originalGid == 38) {
                if (ratio <= 0.10) {
                    gid = 17;
                } else if (ratio <= 0.50) {
                    gid = 18;
                } else if (ratio <= 0.75) {
                    gid = 20;
                }
            } else if (originalGid == 20) {
                if (ratio <= 0.10) {
                    gid = 17;
                } else if (ratio <= 0.50) {
                    gid = 18;
                }
            } else if (originalGid == 19) {
                if (ratio <= 0.10) {
                    gid = 17;
                } else if (ratio <= 0.25) {
                    gid = 18;
                }
            } else if (originalGid == 18) {
                if (ratio <= 0.10) {
                    gid = 17;
                }
            }

            currentGids.add(gid);
        }

        tileLayer.setData(currentGids);
        renderCurrentMap();
    }

    private void renderCurrentMap() {
        if (tiledMap == null || mapURL == null || tileLayer == null)
            return;

        try {
            TilesetLoader loader = new TilesetLoader(tiledMap, mapURL);
            Node mapNode = "isometric".equalsIgnoreCase(tiledMap.getOrientation())
                    ? loader.loadViewIsometric(tileLayer.getName())
                    : loader.loadView(tileLayer.getName());

            if (mapNode != null) {
                if (mapTileEntity == null || !mapTileEntity.isActive()) {
                    mapTileEntity = FXGL.entityBuilder()
                            .at(0, 0)
                            .view(mapNode)
                            .zIndex(-100)
                            .buildAndAttach();
                } else {
                    mapTileEntity.getViewComponent().clearChildren();
                    mapTileEntity.getViewComponent().addChild(mapNode);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getCurrentStage() {
        return currentStage;
    }
}
