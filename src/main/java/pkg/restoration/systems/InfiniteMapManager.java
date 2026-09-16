package pkg.restoration.systems;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.entity.level.tiled.Layer;
import com.almasb.fxgl.entity.level.tiled.TMXLevelLoader;
import com.almasb.fxgl.entity.level.tiled.TiledMap;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.util.Duration;
import pkg.EntityType;

public class InfiniteMapManager {

    public static final int CHUNK_SIZE = 20; // 20x20 tiles per region (spacious 640x320 area)
    public static final int ACTIVE_RADIUS = 1; // 3x3 active grid (center chunk + 8 surrounding neighbors = 9 chunks total)

    // Isometric tile dimensions matching project TMX maps
    public static final double TILE_HALF_WIDTH = 16.0;
    public static final double TILE_HALF_HEIGHT = 8.0;

    private final long worldSeed;
    private final List<ChunkTemplate> templates = new ArrayList<>();
    private final Map<String, ChunkState> chunkStateCache = new HashMap<>();
    private final Map<String, LoadedChunk> loadedActiveChunks = new HashMap<>();

    private Image spritesheetImage;
    private int currentChunkX = Integer.MIN_VALUE;
    private int currentChunkY = Integer.MIN_VALUE;
    private int totalTrashCollectedInWorld = 0;

    private final List<Entity> currentBoundaryWalls = new ArrayList<>();
    private boolean regionLocked = false;
    private int lockedChunkX = Integer.MIN_VALUE;
    private int lockedChunkY = Integer.MIN_VALUE;
    private boolean fragmentedMode = false;
    private double rippleRadius = 52.0;
    private static final double MAX_RIPPLE_RADIUS = 52.0;

    private double currentRestorationRatio = 1.0;
    private int currentStage = 5;

    public void update(double restorationRatio) {
        this.currentRestorationRatio = restorationRatio;
        int targetStage = calculateStage(restorationRatio);
        if (targetStage != currentStage) {
            currentStage = targetStage;
            updateAllChunkViews();
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

    private long transformGidByRatio(long originalGid, double ratio) {
        // Fully restored: map barren/dead tiles upward to lush grass
        if (ratio <= 0.0) {
            if (originalGid == 17 || originalGid == 18) return 35;
            if (originalGid == 19) return 36;
            if (originalGid == 20) return 37;
            return originalGid;
        }
        long gid = originalGid;
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
        return gid;
    }

    public void setFragmentedMode(boolean fragmented) {
        this.fragmentedMode = fragmented;
        updateAllChunkViews();
    }

    public void onBottleCollected(int count, int total) {
        // Pre-generate active surrounding chunks in background memory per bottle
        int activeRadius = ACTIVE_RADIUS;
        int chunkIdx = 0;
        int targetChunks = (int) Math.ceil(((double) count / Math.max(1, total)) * 9);
        int baseCX = currentChunkX == Integer.MIN_VALUE ? 0 : currentChunkX;
        int baseCY = currentChunkY == Integer.MIN_VALUE ? 0 : currentChunkY;
        for (int dy = -activeRadius; dy <= activeRadius; dy++) {
            for (int dx = -activeRadius; dx <= activeRadius; dx++) {
                int cx = baseCX + dx;
                int cy = baseCY + dy;
                if (chunkIdx++ < targetChunks) {
                    getOrCreateChunkState(cx, cy);
                }
            }
        }
    }

    public void startSpreadingRestoration(Runnable onComplete) {
        // Mark the center chunk and all 8 neighbors as permanently restored
        for (int dy = -ACTIVE_RADIUS; dy <= ACTIVE_RADIUS; dy++) {
            for (int dx = -ACTIVE_RADIUS; dx <= ACTIVE_RADIUS; dx++) {
                String neighborKey = (currentChunkX + dx) + "," + (currentChunkY + dy);
                ChunkState neighborState = chunkStateCache.get(neighborKey);
                if (neighborState == null) {
                    neighborState = getOrCreateChunkState(currentChunkX + dx, currentChunkY + dy);
                }
                neighborState.isRestored = true;
                neighborState.restorationRatio = 0.0;
            }
        }

        int steps = 10;
        double interval = 0.05;

        for (int s = 1; s <= steps; s++) {
            final int stepIndex = s;
            FXGL.runOnce(() -> {
                updateAllChunkViews();
                if (stepIndex == steps) {
                    unlockCurrentRegion();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }, Duration.seconds(s * interval));
        }
    }

    public boolean isFragmentedMode() {
        return fragmentedMode;
    }

    public void updateAllChunkViews() {
        for (LoadedChunk lc : loadedActiveChunks.values()) {
            if (lc != null && lc.canvas != null && lc.state != null) {
                drawChunkToCanvas(lc.state, lc.canvas);
            }
        }
    }

    public void reloadAllActiveChunks() {
        List<String> keys = new ArrayList<>(loadedActiveChunks.keySet());
        for (String key : keys) {
            unloadChunk(key);
        }
        refreshActiveChunks();
    }

    public void lockCurrentRegion(int chunkX, int chunkY) {
        unlockCurrentRegion();
        lockedChunkX = chunkX;
        lockedChunkY = chunkY;
        double chunkOriginX = (chunkX - chunkY) * (CHUNK_SIZE * TILE_HALF_WIDTH);
        double chunkOriginY = (chunkX + chunkY) * (CHUNK_SIZE * TILE_HALF_HEIGHT);

        for (int i = 0; i < CHUNK_SIZE; i++) {
            if (i >= 8 && i <= 11) continue; // Leave central gateway archways open for smooth transit
            spawnBoundaryTile(chunkOriginX, chunkOriginY, 0, i);
            spawnBoundaryTile(chunkOriginX, chunkOriginY, CHUNK_SIZE - 1, i);
            spawnBoundaryTile(chunkOriginX, chunkOriginY, i, 0);
            spawnBoundaryTile(chunkOriginX, chunkOriginY, i, CHUNK_SIZE - 1);
        }
        regionLocked = true;
    }

    private void spawnBoundaryTile(double chunkOriginX, double chunkOriginY, int lx, int ly) {
        double isoX = chunkOriginX + (lx - ly) * TILE_HALF_WIDTH;
        double isoY = chunkOriginY + (lx + ly) * TILE_HALF_HEIGHT;

        Entity wall = FXGL.entityBuilder()
                .at(isoX + 8, isoY + 4)
                .type(EntityType.WALL)
                .bbox(new HitBox(BoundingShape.box(16, 8)))
                .with(new CollidableComponent(true))
                .buildAndAttach();
        currentBoundaryWalls.add(wall);
    }

    public void unlockCurrentRegion() {
        for (Entity wall : currentBoundaryWalls) {
            if (wall != null && wall.isActive()) {
                wall.removeFromWorld();
            }
        }
        currentBoundaryWalls.clear();
        regionLocked = false;
    }

    public boolean isRegionLocked() {
        return regionLocked;
    }

    public int getLockedChunkX() {
        return lockedChunkX;
    }

    public int getLockedChunkY() {
        return lockedChunkY;
    }

    public int getCurrentChunkX() {
        return currentChunkX;
    }

    public int getCurrentChunkY() {
        return currentChunkY;
    }

    public static class ChunkTemplate {
        public final long[] gids;
        public final boolean[] walls;
        public final List<Point2D> trashCandidates;

        public ChunkTemplate(long[] gids, boolean[] walls, List<Point2D> trashCandidates) {
            this.gids = gids;
            this.walls = walls;
            this.trashCandidates = trashCandidates;
        }
    }

    public static class TrashItemState {
        public final int id;
        public final double localX;
        public final double localY;
        public boolean collected;
        public Entity entity;

        public TrashItemState(int id, double localX, double localY) {
            this.id = id;
            this.localX = localX;
            this.localY = localY;
            this.collected = false;
        }
    }

    public static class ChunkState {
        final int chunkX;
        final int chunkY;
        final ChunkTemplate template;
        final List<TrashItemState> trashItems = new ArrayList<>();
        public boolean isRestored = false;
        public double restorationRatio = 1.0;

        ChunkState(int chunkX, int chunkY, ChunkTemplate template) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.template = template;
        }
    }

    private static class LoadedChunk {
        final Entity mapEntity;
        final Canvas canvas;
        final ChunkState state;

        LoadedChunk(Entity mapEntity, Canvas canvas, ChunkState state) {
            this.mapEntity = mapEntity;
            this.canvas = canvas;
            this.state = state;
        }
    }

    public InfiniteMapManager(long worldSeed) {
        this.worldSeed = worldSeed;
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("assets/levels/tmx/map_spritesheet.png");
            if (is != null) {
                this.spritesheetImage = new Image(is);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        loadMapTemplates();
    }

    private void loadMapTemplates() {
        String[] mapFiles = { "tmx/level_0.tmx", "tmx/level_demo.tmx" };
        for (String relativePath : mapFiles) {
            try {
                URL resourceURL = getClass().getClassLoader().getResource("assets/levels/" + relativePath);
                if (resourceURL == null) continue;
                TiledMap map;
                try (InputStream is = resourceURL.openStream()) {
                    map = new TMXLevelLoader().parse(is);
                }
                if (map == null || map.getLayers().isEmpty()) continue;

                Layer tileLayer = null;
                for (Layer l : map.getLayers()) {
                    if ("tilelayer".equalsIgnoreCase(l.getType()) || !l.getData().isEmpty()) {
                        tileLayer = l;
                        break;
                    }
                }
                if (tileLayer == null) continue;

                int mw = map.getWidth();
                int mh = map.getHeight();
                List<Long> data = tileLayer.getData();

                int chunksX = mw / CHUNK_SIZE;
                int chunksY = mh / CHUNK_SIZE;

                for (int cy = 0; cy < chunksY; cy++) {
                    for (int cx = 0; cx < chunksX; cx++) {
                        long[] gids = new long[CHUNK_SIZE * CHUNK_SIZE];
                        boolean[] walls = new boolean[CHUNK_SIZE * CHUNK_SIZE];
                        List<Point2D> trashCandidates = new ArrayList<>();

                        for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                            for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                                int gx = cx * CHUNK_SIZE + lx;
                                int gy = cy * CHUNK_SIZE + ly;
                                int srcIdx = gy * mw + gx;
                                int dstIdx = ly * CHUNK_SIZE + lx;

                                long gid = (srcIdx >= 0 && srcIdx < data.size()) ? data.get(srcIdx) : 36L;
                                if (gid <= 0) {
                                    gid = 36L;
                                }
                                gids[dstIdx] = gid;

                                // Impassable water or deep hole obstacle tiles
                                if (gid == 111 || gid == 112 || gid == 113 || gid == 118) {
                                    walls[dstIdx] = true;
                                }
                                // Ensure central roaming and spawn area is completely clear
                                if (lx >= 6 && lx <= 14 && ly >= 6 && ly <= 14) {
                                    walls[dstIdx] = false;
                                }

                                // Candidate positions for trash spawn in open walking areas
                                if ((gid == 35 || gid == 36 || gid == 37 || gid == 38 || gid == 25 || gid == 15)
                                        && (lx == 3 || lx == 7) && (ly == 3 || ly == 7)) {
                                    trashCandidates.add(new Point2D(lx, ly));
                                }
                            }
                        }
                        templates.add(new ChunkTemplate(gids, walls, trashCandidates));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Fallback synthetic template if no maps could be parsed
        if (templates.isEmpty()) {
            long[] gids = new long[CHUNK_SIZE * CHUNK_SIZE];
            boolean[] walls = new boolean[CHUNK_SIZE * CHUNK_SIZE];
            Arrays.fill(gids, 36L);
            List<Point2D> candidates = List.of(new Point2D(4, 4));
            templates.add(new ChunkTemplate(gids, walls, candidates));
        }
    }

    public void updatePlayerPosition(double playerX, double playerY) {
        // Convert isometric screen coordinates back to tile coordinates
        double tileX = (playerX / 32.0) + (playerY / 16.0);
        double tileY = (playerY / 16.0) - (playerX / 32.0);

        int chunkX = (int) Math.floor(tileX / CHUNK_SIZE);
        int chunkY = (int) Math.floor(tileY / CHUNK_SIZE);

        if (chunkX != currentChunkX || chunkY != currentChunkY) {
            currentChunkX = chunkX;
            currentChunkY = chunkY;
            refreshActiveChunks();
        }
    }

    private void refreshActiveChunks() {
        Set<String> activeKeysNeeded = new HashSet<>();

        for (int dy = -ACTIVE_RADIUS; dy <= ACTIVE_RADIUS; dy++) {
            for (int dx = -ACTIVE_RADIUS; dx <= ACTIVE_RADIUS; dx++) {
                int cx = currentChunkX + dx;
                int cy = currentChunkY + dy;
                activeKeysNeeded.add(cx + "," + cy);
            }
        }

        // Unload chunks outside active radius (virtualization)
        List<String> toUnload = new ArrayList<>();
        for (Map.Entry<String, LoadedChunk> entry : loadedActiveChunks.entrySet()) {
            if (!activeKeysNeeded.contains(entry.getKey())) {
                toUnload.add(entry.getKey());
            }
        }
        for (String key : toUnload) {
            unloadChunk(key);
        }

        // Load newly active chunks
        for (String key : activeKeysNeeded) {
            if (!loadedActiveChunks.containsKey(key)) {
                String[] parts = key.split(",");
                int cx = Integer.parseInt(parts[0]);
                int cy = Integer.parseInt(parts[1]);
                loadChunk(cx, cy);
            }
        }
    }

    private ChunkState getOrCreateChunkState(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        if (chunkStateCache.containsKey(key)) {
            return chunkStateCache.get(key);
        }

        // Deterministic pseudo-random seed per chunk coordinate
        long chunkSeed = worldSeed ^ (chunkX * 73856093L ^ chunkY * 19349663L);
        Random rand = new Random(chunkSeed);
        int templateIdx = Math.abs(rand.nextInt(templates.size()));
        ChunkTemplate template = templates.get(templateIdx);

        ChunkState state = new ChunkState(chunkX, chunkY, template);

        // Populate trash items based on candidates
        int trashId = 0;
        for (Point2D candidate : template.trashCandidates) {
            if (rand.nextDouble() < 0.6) { // 60% chance per candidate spot
                state.trashItems.add(new TrashItemState(trashId++, candidate.getX(), candidate.getY()));
            }
        }

        chunkStateCache.put(key, state);
        return state;
    }

    private void loadChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        ChunkState state = getOrCreateChunkState(chunkX, chunkY);

        // Continuous global chunk origin with 0 pixel gap
        double originX = (chunkX - chunkY) * (CHUNK_SIZE * TILE_HALF_WIDTH) - (CHUNK_SIZE * TILE_HALF_WIDTH);
        double originY = (chunkX + chunkY) * (CHUNK_SIZE * TILE_HALF_HEIGHT);

        Canvas canvas = new Canvas(CHUNK_SIZE * TILE_HALF_WIDTH * 2, CHUNK_SIZE * TILE_HALF_HEIGHT * 2 + 32);
        drawChunkToCanvas(state, canvas);

        // Depth-sorting zIndex to ensure lower chunks render in front and prevent occlusion
        int zIndex = -1000 + (chunkX + chunkY) * 10;

        Entity mapEntity = FXGL.entityBuilder()
                .at(originX, originY)
                .view(canvas)
                .zIndex(zIndex)
                .buildAndAttach();

        LoadedChunk loadedChunk = new LoadedChunk(mapEntity, canvas, state);
        loadedActiveChunks.put(key, loadedChunk);
    }

    private void drawChunkToCanvas(ChunkState state, Canvas canvas) {
        if (spritesheetImage == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double ratio = state.isRestored ? 0.0 : currentRestorationRatio;

        // Draw in isometric depth order (lx + ly) to guarantee proper painter's algorithm
        for (int depth = 0; depth <= (CHUNK_SIZE - 1) * 2; depth++) {
            for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                int ly = depth - lx;
                if (ly < 0 || ly >= CHUNK_SIZE) continue;

                int i = ly * CHUNK_SIZE + lx;
                long originalGid = state.template.gids[i];
                if (originalGid <= 0) {
                    originalGid = 36L;
                }
                long gid = transformGidByRatio(originalGid, ratio);

                int tileIdx = (int) (gid - 1);
                int sx = (tileIdx % 11) * 32;
                int sy = (tileIdx / 11) * 32;
                double dx = (lx - ly) * TILE_HALF_WIDTH + (CHUNK_SIZE * TILE_HALF_WIDTH) - 16.0;
                double dy = (lx + ly) * TILE_HALF_HEIGHT;

                gc.drawImage(spritesheetImage, sx, sy, 32, 32, dx, dy, 32, 32);
            }
        }

        // Overlay trees/bushes on restored chunks for a lush forest look
        if (state.isRestored) {
            long treeSeed = worldSeed ^ (state.chunkX * 48271L ^ state.chunkY * 31337L);
            Random treeRng = new Random(treeSeed);
            // Vegetation decoration GIDs: bush (39), flowering bush (40), dense hedge (44)
            long[] treeGids = {39, 40, 44};

            for (int depth = 0; depth <= (CHUNK_SIZE - 1) * 2; depth++) {
                for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                    int ly = depth - lx;
                    if (ly < 0 || ly >= CHUNK_SIZE) continue;

                    // Skip central walkway area and edges to keep paths clear
                    if (lx >= 6 && lx <= 14 && ly >= 6 && ly <= 14) continue;
                    if (lx <= 1 || lx >= CHUNK_SIZE - 2 || ly <= 1 || ly >= CHUNK_SIZE - 2) continue;

                    // Skip wall tiles
                    int i = ly * CHUNK_SIZE + lx;
                    if (state.template.walls[i]) continue;

                    // ~18% chance to place a tree decoration
                    if (treeRng.nextDouble() < 0.18) {
                        long treeGid = treeGids[treeRng.nextInt(treeGids.length)];
                        int treeTileIdx = (int) (treeGid - 1);
                        int tsx = (treeTileIdx % 11) * 32;
                        int tsy = (treeTileIdx / 11) * 32;
                        double tdx = (lx - ly) * TILE_HALF_WIDTH + (CHUNK_SIZE * TILE_HALF_WIDTH) - 16.0;
                        double tdy = (lx + ly) * TILE_HALF_HEIGHT;

                        gc.drawImage(spritesheetImage, tsx, tsy, 32, 32, tdx, tdy, 32, 32);
                    }
                }
            }
        }
    }

    private void unloadChunk(String key) {
        LoadedChunk loaded = loadedActiveChunks.remove(key);
        if (loaded != null) {
            if (loaded.mapEntity != null && loaded.mapEntity.isActive()) {
                loaded.mapEntity.removeFromWorld();
            }
            for (TrashItemState trash : loaded.state.trashItems) {
                if (trash.entity != null && trash.entity.isActive()) {
                    trash.entity.removeFromWorld();
                    trash.entity = null;
                }
            }
        }
    }

    public boolean tryCollectTrashNearPlayer(Entity player) {
        if (player == null) return false;

        for (LoadedChunk loaded : loadedActiveChunks.values()) {
            for (TrashItemState trashState : loaded.state.trashItems) {
                if (!trashState.collected && trashState.entity != null && trashState.entity.isActive()) {
                    double dist = player.distance(trashState.entity);
                    if (dist < 48.0) { // Collection range
                        trashState.collected = true;
                        trashState.entity.removeFromWorld();
                        trashState.entity = null;
                        totalTrashCollectedInWorld++;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getTotalTrashCollectedInWorld() {
        return totalTrashCollectedInWorld;
    }

    public void clearAll() {
        unlockCurrentRegion();
        List<String> keys = new ArrayList<>(loadedActiveChunks.keySet());
        for (String k : keys) {
            unloadChunk(k);
        }
        chunkStateCache.clear();
        loadedActiveChunks.clear();
        totalTrashCollectedInWorld = 0;
        currentChunkX = Integer.MIN_VALUE;
        currentChunkY = Integer.MIN_VALUE;
    }
}
