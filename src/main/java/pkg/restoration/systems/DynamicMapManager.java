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
import java.util.List;

public class DynamicMapManager {

    private final String mapResourcePath;
    private URL mapURL;
    private TiledMap tiledMap;
    private List<Long> originalTileGids;
    private Layer tileLayer;

    private int currentStage = 5;
    private Entity mapTileEntity;

    public DynamicMapManager(String mapResourcePath) {
        this.mapResourcePath = mapResourcePath;
        loadOriginalMap();
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
    }

    public void update(double restorationRatio) {
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

        List<Long> currentGids = new ArrayList<>(originalTileGids.size());

        for (Long originalGid : originalTileGids) {
            long gid = originalGid;

            // Stage Transition Rules based on timer percentage:
            // - Stage 5 (ID 35 or 36) -> Stage 4 (ID 20) when timer <= 75%
            // - Stage 4 (ID 20) -> Stage 2 (ID 18) when timer <= 50%
            // - Stage 3 (ID 19) -> Stage 2 (ID 18) when timer <= 25%
            // - Stage 2 (ID 18) -> Stage 1 (ID 17) when timer <= 10%

            if (originalGid == 35 || originalGid == 36) {
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
