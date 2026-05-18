package pkg;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import static com.almasb.fxgl.dsl.FXGL.addUINode;
import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getAppHeight;
import static com.almasb.fxgl.dsl.FXGL.getAppWidth;
import static com.almasb.fxgl.dsl.FXGL.getDialogService;
import static com.almasb.fxgl.dsl.FXGL.getGameController;
import static com.almasb.fxgl.dsl.FXGL.getGameScene;
import static com.almasb.fxgl.dsl.FXGL.getGameTimer;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;
import static com.almasb.fxgl.dsl.FXGL.getInput;
import static com.almasb.fxgl.dsl.FXGL.getWorldProperties;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.IrremovableComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.ui.ProgressBar;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class FightPrototype extends GameApplication {

    private static final int TILE_SIZE = 40;
    private static final int MAP_COLUMNS = 30;
    private static final int MAP_ROWS = 17;
    private static final int WORLD_WIDTH = TILE_SIZE * MAP_COLUMNS;
    private static final int WORLD_HEIGHT = TILE_SIZE * MAP_ROWS;
    private static final double GATE_WIDTH = 72;
    private static final double GATE_HEIGHT = 96;

    private Entity player;
    private PlayerComponent playerAnim;

    private boolean left, right;
    private double currentTilt = 0;
    private final double MAX_TILT = 1.5;
    private final double PUNCH_TILT = 6.0;
    private final double TILT_SMOOTHNESS = 0.1;
    private boolean alternateRight = true;

    private Entity targetEnemy = null;
    private HackingMinigameUI hackingUI = null;
    private double hackCooldown = 0.1;
    private final Map<Entity, HackingMinigameUI> ongoingHacks = new HashMap<>();

    private final String[] storyAreas = {
            "Neon Perimeter",
            "Spike Gallery",
            "Firewall Rift",
            "Memory Aqueduct",
            "Clockwork Terrace",
            "Cipher Barracks",
            "Glass Orchard",
            "Data Catacombs",
            "Mirror Foundry",
            "Storm Archive",
            "Pulse Citadel",
            "Core Spire"
    };
    private int currentArea = 0;
    private Entity gate;
    private boolean gateOpen = false;
    private double gateActivationDelay = 0;
    private boolean isAreaTransitioning = false;
    private Point2D respawnPoint = Point2D.ZERO;
    private double hazardCooldown = 0;
    private Text areaLabel;
    private Text objectiveLabel;

    @Override
    protected void initSettings(GameSettings settings) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        settings.setWidth((int) (screen.width * 0.9));
        settings.setHeight((int) (screen.height * 0.9));
        settings.setTitle("2D Platformer Hacker");
        settings.setVersion("1.0");
        settings.setMainMenuEnabled(true); // Enables standard Play / Options / Quit menu
        settings.setSceneFactory(new NeonSceneFactory()); // Inject our new Cyberpunk Menu!
    }

    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Jump") {
            @Override protected void onActionBegin() {
                playerAnim.jump();
            }
            @Override protected void onActionEnd() {
                playerAnim.stopJump();
            }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Left") {
            @Override protected void onActionBegin() { left = true; }
            @Override protected void onActionEnd()   { left = false; }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Right") {
            @Override protected void onActionBegin() { right = true; }
            @Override protected void onActionEnd()   { right = false; }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Punch") {
            @Override protected void onActionBegin() {
                executePunch();
            }
        }, KeyCode.SPACE);

        getInput().addAction(new UserAction("Dash") {
            @Override protected void onActionBegin() {
                playerAnim.dash();
            }
        }, KeyCode.F);

        getInput().addAction(new UserAction("Hack Up") {
            @Override protected void onActionBegin() { if (hackingUI != null) hackingUI.moveHacker(0, -1); }
        }, KeyCode.UP);

        getInput().addAction(new UserAction("Hack Down") {
            @Override protected void onActionBegin() { if (hackingUI != null) hackingUI.moveHacker(0, 1); }
        }, KeyCode.DOWN);

        getInput().addAction(new UserAction("Hack Left") {
            @Override protected void onActionBegin() { if (hackingUI != null) hackingUI.moveHacker(-1, 0); }
        }, KeyCode.LEFT);

        getInput().addAction(new UserAction("Hack Right") {
            @Override protected void onActionBegin() { if (hackingUI != null) hackingUI.moveHacker(1, 0); }
        }, KeyCode.RIGHT);

        getInput().addAction(new UserAction("Help") {
            @Override protected void onActionBegin() {
                getDialogService().showMessageBox("Controls:\nA/D to Move\nW to Jump\nSPACE to Punch\nF to Dash\n\nGet near an enemy to trigger HACKING.\nUse Arrow Keys to connect the nodes and weaken them.\n\nWatch for spikes, crumbling floors, pressure plates, key shards, dash crystals, and checkpoint flags.\nWhen a gate opens, cross it to continue the story.");
            }
        }, KeyCode.H);
    }

    private void executePunch() {
        playerAnim.attack();
        getGameWorld().getClosestEntity(player, e -> e.isType(EntityType.ENEMY))
            .filter(Entity::isActive)
            .filter(nearest -> player.distance(nearest) < 85)
            .ifPresent(nearest -> {
                currentTilt = alternateRight ? PUNCH_TILT : -PUNCH_TILT;
                alternateRight = !alternateRight;
                getGameScene().getViewport().shake(4, 0.25);
                
                nearest.getComponentOptional(EnemyComponent.class).ifPresent(ec -> ec.takeDamage(10, player));
            });
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("playerHP", 100);
    }

    @Override
    protected void initGame() {
        // Cyberspace dark background
        getGameScene().setBackgroundColor(Color.web("#050914"));

        // Draw a high-tech matrix grid background
        Canvas bgCanvas = new Canvas(WORLD_WIDTH, WORLD_HEIGHT);
        GraphicsContext gc = bgCanvas.getGraphicsContext2D();
        gc.setStroke(Color.web("#00E5FF", 0.05));
        gc.setLineWidth(1);
        for (int x = 0; x <= WORLD_WIDTH; x += TILE_SIZE) gc.strokeLine(x, 0, x, WORLD_HEIGHT);
        for (int y = 0; y <= WORLD_HEIGHT; y += TILE_SIZE) gc.strokeLine(0, y, WORLD_WIDTH, y);
        
        entityBuilder()
                .zIndex(-100)
                .view(bgCanvas)
                .with(new IrremovableComponent())
                .buildAndAttach();

        getGameWorld().addEntityFactory(new GameFactory());
        
        currentArea = 0;
        loadStoryArea(currentArea);
    }

    private void loadStoryArea(int area) {
        isAreaTransitioning = true;
        hideHackingUI();
        ongoingHacks.clear();
        targetEnemy = null;
        hackCooldown = 0.1;
        hazardCooldown = 0;
        gate = null;
        gateOpen = false;
        gateActivationDelay = 0;
        
        com.almasb.fxgl.dsl.FXGL.setLevelFromMap("level" + (area + 1) + ".tmx");
        
        player = getGameWorld().getSingleton(EntityType.PLAYER);
        playerAnim = player.getComponent(PlayerComponent.class);
        respawnPoint = player.getPosition();

        var v = getGameScene().getViewport();
        v.setZoom(2.5);
        v.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        v.setLazy(true); // Modern camera smooth tracking!
        v.bindToEntity(player, getAppWidth() / 2.0, getAppHeight() / 2.0);
        updateObjectiveHUD();
        
        isAreaTransitioning = false;
    }

    private void openGate() {
        if (gateOpen) return;

        gateOpen = true;
        gateActivationDelay = 0.5;

        double gateX = WORLD_WIDTH - TILE_SIZE - GATE_WIDTH - 8;
        double gateY = WORLD_HEIGHT - TILE_SIZE - GATE_HEIGHT;
        gate = getGameWorld().spawn("gate", gateX, gateY);

        if (currentArea == storyAreas.length - 1) {
            updateStoryHUD("Final gate open.");
        } else {
            updateStoryHUD("Gate open to " + storyAreas[currentArea + 1] + ".");
        }

        getGameScene().getViewport().shake(3, 0.2);
    }

    private boolean enterGate() {
        if (!gateOpen || gate == null || !gate.isActive() || !player.isColliding(gate)) return false;

        isAreaTransitioning = true;
        hideHackingUI();

        if (currentArea == storyAreas.length - 1) {
            updateStoryHUD("Core breached.");
            getDialogService().showMessageBox("SYSTEM COMPLETELY COMPROMISED.\nYOU WIN!", () -> {
                getGameController().gotoMainMenu();
            });
        } else {
            currentArea++;
            loadStoryArea(currentArea);
        }

        return true;
    }

    private void playerDeath() {
        respawnPlayer();
    }

    private void respawnPlayer() {
        Point2D checkpoint = respawnPoint;
        endHackingMode();
        getWorldProperties().setValue("playerHP", 100);
        loadStoryArea(currentArea);
        respawnPoint = checkpoint;
        player.setPosition(respawnPoint);
        player.getComponentOptional(PhysicsComponent.class).ifPresent(physics -> {
            physics.setVelocityX(0);
            physics.setVelocityY(0);
        });
        hazardCooldown = 1.0;
        getGameScene().getViewport().shake(5, 0.2);
        updateStoryHUD("Checkpoint restored.");
    }

    @Override
    protected void initUI() {
        // Wrap HUD in a high-tech container
        VBox hud = new VBox(10);
        hud.setTranslateX(30);
        hud.setTranslateY(30);
        hud.getStyleClass().add("game-container");

        areaLabel = new Text();
        areaLabel.getStyleClass().add("hud-text");

        objectiveLabel = new Text();
        objectiveLabel.getStyleClass().add("hud-text");

        Text hpLabel = new Text("SYS.INTEGRITY // HP");
        hpLabel.getStyleClass().add("hud-text");

        ProgressBar playerHPBar = new ProgressBar(false);
        playerHPBar.setMaxValue(100);
        playerHPBar.setWidth(250);
        playerHPBar.setFill(Color.web("#00E5FF")); // Neon Cyan
        
        playerHPBar.currentValueProperty().bind(getWorldProperties().intProperty("playerHP"));
        
        hud.getChildren().addAll(areaLabel, objectiveLabel, hpLabel, playerHPBar);
        addUINode(hud);
        updateStoryHUD("Clear the hostile code.");
    }

    private void updateStoryHUD(String objective) {
        if (areaLabel == null || objectiveLabel == null) return;

        areaLabel.setText("AREA // " + storyAreas[currentArea]);
        objectiveLabel.setText("OBJECTIVE // " + objective);
    }

    private void updateObjectiveHUD() {
        if (gateOpen) {
            updateStoryHUD(currentArea == storyAreas.length - 1
                    ? "Final gate open."
                    : "Gate open to " + storyAreas[currentArea + 1] + ".");
            return;
        }

        int locks = getGameWorld().getEntitiesByType(EntityType.LOCK).size();
        int keys = getGameWorld().getEntitiesByType(EntityType.KEY).size();
        int enemies = getGameWorld().getEntitiesByType(EntityType.ENEMY).size();

        if (locks > 0) {
            updateStoryHUD("Find a pressure plate.");
        } else if (keys > 0) {
            updateStoryHUD("Recover " + keys + " key shard" + (keys == 1 ? "." : "s."));
        } else if (enemies > 0) {
            updateStoryHUD("Defeat " + enemies + " sentry" + (enemies == 1 ? "." : "s."));
        } else {
            updateStoryHUD("Gate signal stabilizing.");
        }
    }

    private boolean isAreaCleared() {
        return getGameWorld().getEntitiesByType(EntityType.ENEMY).isEmpty()
                && getGameWorld().getEntitiesByType(EntityType.KEY).isEmpty()
                && getGameWorld().getEntitiesByType(EntityType.LOCK).isEmpty();
    }

    @Override
    protected void onUpdate(double tpf) {
        if (isAreaTransitioning) return;
        if (hazardCooldown > 0) hazardCooldown -= tpf;

        handleExplorationInteractions();
        
        // Handle death by falling off the map or HP depletion
        if (getWorldProperties().getInt("playerHP") <= 0 || player.getY() > WORLD_HEIGHT + 50) {
            playerDeath();
            return;
        }

        if (!gateOpen && isAreaCleared()) {
            openGate();
        }

        if (gateOpen) {
            if (gateActivationDelay > 0) {
                gateActivationDelay -= tpf;
            } else {
                if (enterGate()) return;
            }
        }

        if (hackCooldown > 0) {
            hackCooldown -= tpf;
        }
        
        // Clean up cached hacks for enemies that have died (e.g., from punches)
        ongoingHacks.keySet().removeIf(e -> !e.isActive());

        if (targetEnemy != null && !targetEnemy.isActive()) {
            endHackingMode();
        }

        // Let the smart component handle the smooth acceleration math
        if (left && !right) {
            playerAnim.setMoveDir(-1);
        } else if (right && !left) {
            playerAnim.setMoveDir(1);
        } else {
            playerAnim.setMoveDir(0);
        }

        double targetTilt = left ? MAX_TILT : (right ? -MAX_TILT : 0);
        currentTilt += (targetTilt - currentTilt) * TILT_SMOOTHNESS;

        // Trigger Hacking Mode based on Proximity
        Optional<Entity> closestOpt = getGameWorld().getClosestEntity(player, e -> e.isType(EntityType.ENEMY));
        if (closestOpt.isPresent()) {
            Entity closest = closestOpt.get();
            double dist = player.distance(closest);
            
            if (dist < 150) {
                // Trigger if it's a new enemy, OR if the UI is closed and the cooldown has refreshed
                if (targetEnemy != closest || (hackingUI == null && hackCooldown <= 0)) {
                    endHackingMode();
                    targetEnemy = closest;
                    startHackingMode(targetEnemy);
                }
            } else if (targetEnemy != null && player.distance(targetEnemy) > 200) {
                endHackingMode(); // Went too far away
            }
        } else if (targetEnemy != null) {
            endHackingMode(); // Target died
        }
    }

    private void handleExplorationInteractions() {
        activateCheckpoints();
        collectKeyShards();
        collectDashCrystals();
        pressPressurePlates();
        armCrumblingPlatforms();
        damageFromHazards();
    }

    private void activateCheckpoints() {
        for (Entity checkpoint : getGameWorld().getEntitiesByType(EntityType.CHECKPOINT)) {
            if (!checkpoint.getBoolean("activated") && player.isColliding(checkpoint)) {
                checkpoint.setProperty("activated", true);
                checkpoint.setOpacity(1.0);
                respawnPoint = checkpoint.getPosition().add(0, -8);
                updateStoryHUD("Checkpoint synced.");
            }
        }
    }

    private void collectKeyShards() {
        for (Entity key : new java.util.ArrayList<>(getGameWorld().getEntitiesByType(EntityType.KEY))) {
            if (key.isActive() && player.isColliding(key)) {
                key.removeFromWorld();
                getGameScene().getViewport().shake(2, 0.12);
                updateObjectiveHUD();
            }
        }
    }

    private void collectDashCrystals() {
        for (Entity crystal : new java.util.ArrayList<>(getGameWorld().getEntitiesByType(EntityType.DASH_CRYSTAL))) {
            if (crystal.isActive() && player.isColliding(crystal)) {
                crystal.removeFromWorld();
                playerAnim.refillDash();
                getGameScene().getViewport().shake(2, 0.12);
                updateStoryHUD("Dash restored.");
            }
        }
    }

    private void pressPressurePlates() {
        for (Entity plate : getGameWorld().getEntitiesByType(EntityType.PRESSURE_PLATE)) {
            if (!plate.getBoolean("activated") && player.isColliding(plate)) {
                plate.setProperty("activated", true);
                plate.setOpacity(0.45);

                for (Entity lock : new java.util.ArrayList<>(getGameWorld().getEntitiesByType(EntityType.LOCK))) {
                    if (lock.isActive()) lock.removeFromWorld();
                }

                getGameScene().getViewport().shake(4, 0.2);
                updateObjectiveHUD();
            }
        }
    }

    private void armCrumblingPlatforms() {
        for (Entity platform : getGameWorld().getEntitiesByType(EntityType.PLATFORM)) {
            if (!booleanProperty(platform, "crumbles") || platform.getBoolean("breaking") || !player.isColliding(platform)) {
                continue;
            }

            platform.setProperty("breaking", true);
            platform.setOpacity(0.55);

            getGameTimer().runOnceAfter(() -> {
                if (platform.isActive()) {
                    platform.removeFromWorld();
                }
            }, Duration.seconds(0.65));
        }
    }

    private void damageFromHazards() {
        if (hazardCooldown > 0) return;

        for (Entity spike : getGameWorld().getEntitiesByType(EntityType.SPIKE)) {
            if (spike.isActive() && player.isColliding(spike)) {
                playerAnim.takeDamage(25);
                player.getComponentOptional(PhysicsComponent.class).ifPresent(physics -> {
                    physics.setVelocityX(player.getX() < spike.getX() ? -220 : 220);
                    physics.setVelocityY(-260);
                });
                hazardCooldown = 0.85;
                getGameScene().getViewport().shake(5, 0.18);
                return;
            }
        }
    }

    private boolean booleanProperty(Entity entity, String name) {
        return entity.getPropertyOptional(name)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
    }

    private void startHackingMode(Entity enemy) {
        enemy.getComponentOptional(EnemyComponent.class).ifPresent(ec -> ec.setTargeted(true));
        
        // Check if this specific enemy already has an ongoing hack puzzle saved!
        if (ongoingHacks.containsKey(enemy)) {
            hackingUI = ongoingHacks.get(enemy);
            hackingUI.resume();
        } else {
            hackingUI = new HackingMinigameUI((collectedBonuses) -> {
                enemy.getComponentOptional(EnemyComponent.class).ifPresent(ec -> {
                    // If already hacked, reduce hacking damage efficiency by 50%
                    double damageMultiplier = ec.isHacked() ? 0.5 : 1.0;
                    ec.setHacked(true);
                    // Base damage is 10. Each bonus node adds 10 extra damage.
                    ec.takeDamage((10 + (collectedBonuses * 10)) * damageMultiplier, player); 
                });
                ongoingHacks.remove(enemy);
                hideHackingUI();
            }, () -> {
                // FAILURE PENALTY (Backlash)
                ongoingHacks.remove(enemy);
                hideHackingUI();
                player.getComponentOptional(PlayerComponent.class).ifPresent(p -> p.takeDamage(10));
                getGameScene().getViewport().shake(6, 0.5);
            });
            ongoingHacks.put(enemy, hackingUI);
        }
        
        hackingUI.setTranslateX(getAppWidth() - 400); // 350 canvas + 40 padding + borders
        hackingUI.setTranslateY(0); // Aligned with the top screen border
        getGameScene().addUINode(hackingUI);
    }

    private void endHackingMode() {
        hideHackingUI();
        if (targetEnemy != null) {
            targetEnemy.getComponentOptional(EnemyComponent.class).ifPresent(ec -> ec.setTargeted(false));
            // We removed the setHacked(false) reset here so the enemy stays permanently hacked!
            targetEnemy = null;
        }
    }

    private void hideHackingUI() {
        if (hackingUI != null) {
            hackingUI.stop();
            getGameScene().removeUINode(hackingUI);
            hackingUI = null;
            hackCooldown = 2.0; // 2 seconds before it can auto-trigger again on the same enemy
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
