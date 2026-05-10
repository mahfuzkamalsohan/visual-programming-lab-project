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
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;
import static com.almasb.fxgl.dsl.FXGL.getInput;
import static com.almasb.fxgl.dsl.FXGL.getWorldProperties;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.ui.ProgressBar;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class FightPrototype extends GameApplication {

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

    private int currentLevel = 1;
    private final int MAX_LEVEL = 3;
    private boolean isLevelTransitioning = false;

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
                getDialogService().showMessageBox("Controls:\nA/D to Move\nW to Jump\nSPACE to Punch\nF to Dash\n\nGet near an enemy to trigger HACKING.\nUse Arrow Keys to connect the nodes and weaken them!");
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
        Canvas bgCanvas = new Canvas(40 * 30, 40 * 17);
        GraphicsContext gc = bgCanvas.getGraphicsContext2D();
        gc.setStroke(Color.web("#00E5FF", 0.05));
        gc.setLineWidth(1);
        for (int x = 0; x <= 40 * 30; x += 40) gc.strokeLine(x, 0, x, 40 * 17);
        for (int y = 0; y <= 40 * 17; y += 40) gc.strokeLine(0, y, 40 * 30, y);
        
        entityBuilder().zIndex(-100).view(bgCanvas).buildAndAttach();

        getGameWorld().addEntityFactory(new GameFactory());
        
        currentLevel = 1;
        loadLevel(currentLevel);
    }

    private void loadLevel(int level) {
        isLevelTransitioning = true;
        hideHackingUI();
        ongoingHacks.clear();
        targetEnemy = null;
        
        com.almasb.fxgl.dsl.FXGL.setLevelFromMap("level" + level + ".tmx");
        
        player = getGameWorld().getSingleton(EntityType.PLAYER);
        playerAnim = player.getComponent(PlayerComponent.class);

        var v = getGameScene().getViewport();
        v.setZoom(2.5);
        v.setBounds(0, 0, 40 * 30, 40 * 17); // Level bounding box based on 30x17 text grid
        v.setLazy(true); // Modern camera smooth tracking!
        v.bindToEntity(player, getAppWidth() / 2.0, getAppHeight() / 2.0);
        
        isLevelTransitioning = false;
    }

    private void nextLevel() {
        isLevelTransitioning = true;
        if (currentLevel >= MAX_LEVEL) {
            getDialogService().showMessageBox("SYSTEM COMPLETELY COMPROMISED.\nYOU WIN!", () -> {
                getGameController().gotoMainMenu();
            });
        } else {
            currentLevel++;
            getDialogService().showMessageBox("FIREWALL BREACHED. PROCEEDING TO LEVEL " + currentLevel, () -> {
                loadLevel(currentLevel);
            });
        }
    }

    private void playerDeath() {
        isLevelTransitioning = true;
        hideHackingUI();
        getDialogService().showMessageBox("CRITICAL SYSTEM FAILURE.\nYOU DIED.", () -> {
            getWorldProperties().setValue("playerHP", 100); // Reset HP
            loadLevel(currentLevel); // Restart the current map
        });
    }

    @Override
    protected void initUI() {
        // Wrap HUD in a high-tech container
        VBox hud = new VBox(10);
        hud.setTranslateX(30);
        hud.setTranslateY(30);
        hud.getStyleClass().add("game-container");

        Text hpLabel = new Text("SYS.INTEGRITY // HP");
        hpLabel.getStyleClass().add("hud-text");

        ProgressBar playerHPBar = new ProgressBar(false);
        playerHPBar.setMaxValue(100);
        playerHPBar.setWidth(250);
        playerHPBar.setFill(Color.web("#00E5FF")); // Neon Cyan
        
        playerHPBar.currentValueProperty().bind(getWorldProperties().intProperty("playerHP"));
        
        hud.getChildren().addAll(hpLabel, playerHPBar);
        addUINode(hud);
    }

    @Override
    protected void onUpdate(double tpf) {
        if (isLevelTransitioning) return;
        
        // Handle death by falling off the map (y > 680) or HP depletion
        if (getWorldProperties().getInt("playerHP") <= 0 || player.getY() > 40 * 17 + 50) {
            playerDeath();
            return;
        }

        if (getGameWorld().getEntitiesByType(EntityType.ENEMY).isEmpty()) {
            nextLevel();
            return;
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