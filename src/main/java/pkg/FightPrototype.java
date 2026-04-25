package pkg;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.ui.ProgressBar;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

public class FightPrototype extends GameApplication {

    private Entity player;
    private Entity enemy;
    private PhysicsComponent playerPhysics;
    private PlayerComponent playerAnim;

    private boolean left, right;
    private double currentTilt = 0;
    private final double MAX_TILT = 1.5;
    private final double PUNCH_TILT = 6.0;
    private final double TILT_SMOOTHNESS = 0.1;
    private boolean alternateRight = true;

    private ProgressBar enemyHPBar;
    private Group gameRoot;

    private boolean isQTEActive = false;
    private List<Entity> qteEntities = new ArrayList<>();
    private List<QTEComponent> qteComponents = new ArrayList<>();
    private List<Node> qteUINodes = new ArrayList<>();

    private double targetZoom = 2.5;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("Combat Prototype");
    }

    @Override
    protected void initInput() {
        onKeyDown(KeyCode.W, "Jump", () -> {
            if (!isQTEActive && Math.abs(playerPhysics.getVelocityY()) < 5) {
                playerPhysics.setVelocityY(-250);
            }
        });

        getInput().addAction(new UserAction("Left") {
            @Override protected void onActionBegin() { left = true; }
            @Override protected void onActionEnd()   { left = false; }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Right") {
            @Override protected void onActionBegin() { right = true; }
            @Override protected void onActionEnd()   { right = false; }
        }, KeyCode.D);

        onKeyDown(KeyCode.I, "QTE_I", () -> { if (isQTEActive) handleQTEInput(KeyCode.I); });
        onKeyDown(KeyCode.O, "QTE_O", () -> { if (isQTEActive) handleQTEInput(KeyCode.O); });
        onKeyDown(KeyCode.P, "QTE_P", () -> { if (isQTEActive) handleQTEInput(KeyCode.P); });

        getInput().addAction(new UserAction("Punch") {
            @Override protected void onActionBegin() {
                if (!isQTEActive) {
                    if (enemy != null && player.distance(enemy) < 85) {
                        startQTE();
                    } else if (playerAnim != null) {
                        playerAnim.attack();
                    }
                }
            }
        }, MouseButton.PRIMARY);
    }

    private void startQTE() {
        isQTEActive = true;
        targetZoom = 6.0;
        spawnQTE(KeyCode.I, 0);
        spawnQTE(KeyCode.O, 1);
        spawnQTE(KeyCode.P, 2);
    }

    private void handleQTEInput(KeyCode key) {
        for (QTEComponent comp : qteComponents) {
            if (comp.getTriggerKey() == key && !comp.isPressed()) {
                comp.press();
                break;
            }
        }

        if (qteComponents.stream().allMatch(QTEComponent::isPressed)) {
            boolean win = qteComponents.stream().allMatch(QTEComponent::isSuccess);
            getGameTimer().runOnceAfter(() -> endQTE(win), Duration.seconds(0.3));
        }
    }

    private void endQTE(boolean success) {
        isQTEActive = false;
        targetZoom = 2.5;

        qteEntities.forEach(Entity::removeFromWorld);
        qteEntities.clear();
        qteComponents.clear();

        qteUINodes.forEach(n -> getGameScene().removeUINode(n));
        qteUINodes.clear();

        if (success) {
            executePunch();
        } else {
            getNotificationService().pushNotification("MISS!");
        }
    }

    private void executePunch() {
        if (playerAnim != null) playerAnim.attack();
        if (enemy != null && enemy.isActive()) {
            currentTilt = alternateRight ? PUNCH_TILT : -PUNCH_TILT;
            alternateRight = !alternateRight;
            if (gameRoot != null) gameRoot.setRotate(currentTilt);
            enemyHPBar.setCurrentValue(enemyHPBar.getCurrentValue() - 10);
            var ep = enemy.getComponent(PhysicsComponent.class);
            ep.setVelocityX((player.getX() < enemy.getX() ? 1 : -1) * 150);
            getGameScene().getViewport().shake(4, 0.25);
        }
    }

    @Override
    protected void initGame() {
        entityBuilder()
                .at(-500, 500)
                .viewWithBBox(new Rectangle(3000, 500, Color.web("#2c3e50")))
                .with(new PhysicsComponent())
                .buildAndAttach();

        playerPhysics = new PhysicsComponent();
        playerPhysics.setBodyType(BodyType.DYNAMIC);
        playerAnim = new PlayerComponent();

        player = entityBuilder()
                .at(100, 300)
                .bbox(new HitBox("PLAYER_BODY", new Point2D(32, 48), BoundingShape.box(32, 32)))
                .with(playerPhysics)
                .with(playerAnim)
                .buildAndAttach();

        PhysicsComponent ep = new PhysicsComponent();
        ep.setBodyType(BodyType.DYNAMIC);
        enemy = entityBuilder()
                .at(400, 300)
                .viewWithBBox(new Rectangle(20, 30, Color.CRIMSON))
                .with(ep)
                .buildAndAttach();

        var v = getGameScene().getViewport();
        v.setZoom(2.5);
        v.bindToEntity(player, getAppWidth() / 2.0, getAppHeight() / 2.0);

        try {
            Field f = getGameScene().getClass().getDeclaredField("gameRoot");
            f.setAccessible(true);
            gameRoot = (Group) f.get(getGameScene());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void initUI() {
        enemyHPBar = new ProgressBar(false);
        enemyHPBar.setMaxValue(100);
        enemyHPBar.setCurrentValue(100);
        enemyHPBar.setFill(Color.RED);
        enemyHPBar.setTranslateX(getAppWidth() - 220);
        enemyHPBar.setTranslateY(35);
        addUINode(enemyHPBar);
    }

    @Override
    protected void onUpdate(double tpf) {
        var v = getGameScene().getViewport();
        
        if (isQTEActive) {
        	if (playerAnim != null) playerAnim.freeze();
            v.setZoom(v.getZoom() + (targetZoom - v.getZoom()) * 0.0003);
            playerPhysics.setVelocityX(0);
            playerPhysics.setVelocityY(0);
            return;
        }

        if (Math.abs(v.getZoom() - targetZoom) > 0.01) {
            v.setZoom(v.getZoom() + (targetZoom - v.getZoom()) * 0.1);
        }

        if (left) {
            playerPhysics.setVelocityX(-250);
            playerAnim.setFacing(-1);
            playerAnim.move();
        } else if (right) {
            playerPhysics.setVelocityX(250);
            playerAnim.setFacing(1);
            playerAnim.move();
        } else {
            playerPhysics.setVelocityX(playerPhysics.getVelocityX() * 0.9);
            playerAnim.stop();
        }

        double targetTilt = left ? MAX_TILT : (right ? -MAX_TILT : 0);
        currentTilt += (targetTilt - currentTilt) * TILT_SMOOTHNESS;
        if (gameRoot != null) gameRoot.setRotate(currentTilt);
    }

    private void spawnQTE(KeyCode key, int index) {
        QTEComponent comp = new QTEComponent(key, 0.6 + index * 0.2);
        Entity e = entityBuilder().at(0, 0).with(comp).buildAndAttach();

        Group view = comp.getView();
        view.setTranslateX(getAppWidth() / 2.0 - 80 + (index * 80));
        view.setTranslateY(getAppHeight() / 2.0 - 80);

        addUINode(view);
        qteEntities.add(e);
        qteComponents.add(comp);
        qteUINodes.add(view);
    }

    public static void main(String[] args) {
        launch(args);
    }
}