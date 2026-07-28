package pkg;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import pkg.restoration.systems.RestorationTimer;

public class MovementApp extends GameApplication {

    private static final double INITIAL_TIME = 120.0;
    private static final double MAX_TIME = 120.0;

    private Entity playerEntity;
    private PlayerComponent playerComponent;
    private RestorationTimer timer;

    private Text timerText;

    public enum EntityType {
        PLAYER, WALL
    }

    public enum Direction {
        NORTH(0), // Col 0: Up
        NORTH_EAST(1), // Col 1: Up-Right
        EAST(2), // Col 2: Right
        SOUTH_EAST(3), // Col 3: Down-Right
        SOUTH(4), // Col 4: Down
        SOUTH_WEST(5), // Col 5: Down-Left
        WEST(6), // Col 6: Left
        NORTH_WEST(7); // Col 7: Up-Left

        public final int index;

        Direction(int index) {
            this.index = index;
        }
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setTitle("Restoration");
        settings.setVersion("0.1.0");
        settings.setMainMenuEnabled(true);
        settings.setGameMenuEnabled(true);
        settings.setMenuKey(KeyCode.ESCAPE);
        settings.setSceneFactory(new SceneFactory() {
            @Override
            public FXGLMenu newMainMenu() {
                return new MainMenu(MenuType.MAIN_MENU);
            }
        });
    }

    @Override
    protected void initInput() {
        bindKey("Move Up", KeyCode.W,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setUp(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setUp(false);
                });
        bindKey("Move Down", KeyCode.S,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setDown(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setDown(false);
                });
        bindKey("Move Left", KeyCode.A,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setLeft(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setLeft(false);
                });
        bindKey("Move Right", KeyCode.D,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setRight(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setRight(false);
                });

        bindKey("Move Up Alt", KeyCode.UP,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setUp(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setUp(false);
                });
        bindKey("Move Down Alt", KeyCode.DOWN,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setDown(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setDown(false);
                });
        bindKey("Move Left Alt", KeyCode.LEFT,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setLeft(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setLeft(false);
                });
        bindKey("Move Right Alt", KeyCode.RIGHT,
                () -> {
                    if (playerComponent != null)
                        playerComponent.setRight(true);
                },
                () -> {
                    if (playerComponent != null)
                        playerComponent.setRight(false);
                });
    }

    private void bindKey(String name, KeyCode code, Runnable onPress, Runnable onRelease) {
        FXGL.getInput().addAction(new UserAction(name) {
            @Override
            protected void onActionBegin() {
                onPress.run();
            }

            @Override
            protected void onActionEnd() {
                onRelease.run();
            }
        }, code);
    }

    @Override
    protected void initGame() {
        FXGL.getGameScene().setBackgroundColor(Color.web("#17231e"));
        timer = new RestorationTimer(INITIAL_TIME, MAX_TIME);

        FXGL.getGameWorld().addEntityFactory(new GameEntityFactory());
        FXGL.setLevelFromMap("tmx/level_0.tmx");

        List<Entity> players = FXGL.getGameWorld().getEntitiesByComponent(PlayerComponent.class);
        if (!players.isEmpty()) {
            playerEntity = players.get(0);
            playerComponent = playerEntity.getComponent(PlayerComponent.class);

            var viewport = FXGL.getGameScene().getViewport();
            viewport.setLazy(true);
            viewport.setZoom(2.0);
            viewport.bindToEntity(playerEntity,
                    FXGL.getAppWidth() / 2.0,
                    FXGL.getAppHeight() / 2.0);
        }
    }

    @Override
    protected void initUI() {
        timerText = new Text();
        timerText.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        timerText.setFill(Color.web("#d7e77f"));
        timerText.setX(20);
        timerText.setY(36);
        FXGL.addUINode(timerText);
        refreshTimerLabel();
    }

    @Override
    protected void onUpdate(double tpf) {
        if (timer == null)
            return;
        timer.tick(tpf);
        refreshTimerLabel();
    }

    private void refreshTimerLabel() {
        if (timerText == null || timer == null)
            return;
        int secs = (int) Math.ceil(timer.currentSeconds());
        timerText.setText(String.format("Time: %d s", secs));
        timerText.setFill(timer.restorationRatio() < 0.17
                ? Color.web("#ff6b6b")
                : Color.web("#d7e77f"));
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class GameEntityFactory implements EntityFactory {

        @Spawns("restorationPlayer")
        public Entity spawnPlayer(SpawnData data) {
            return FXGL.entityBuilder(data)
                    .type(EntityType.PLAYER)
                    .bbox(new HitBox(BoundingShape.box(16, 24)))
                    .with(new CollidableComponent(true))
                    .with(new PlayerComponent())
                    .build();
        }

        @Spawns("wall")
        public Entity spawnWall(SpawnData data) {
            double w = data.hasKey("width") ? ((Number) data.get("width")).doubleValue() : 32.0;
            double h = data.hasKey("height") ? ((Number) data.get("height")).doubleValue() : 32.0;
            Rectangle vis = new Rectangle(w, h, Color.TRANSPARENT);
            return FXGL.entityBuilder(data)
                    .type(EntityType.WALL)
                    .view(vis)
                    .bbox(new HitBox(BoundingShape.box(w, h)))
                    .with(new CollidableComponent(true))
                    .build();
        }
    }


    public static final class MainMenu extends FXGLMenu {

        public MainMenu(MenuType type) {
            super(type);

            double w = FXGL.getAppWidth();
            double h = FXGL.getAppHeight();

            Canvas bg = new Canvas(w, h);
            drawBackground(bg.getGraphicsContext2D(), w, h);

            Text title = new Text("RESTORATION");
            title.setFont(Font.font("Georgia", FontWeight.BOLD, 74));
            title.setFill(Color.web("#eff7d4"));

            Text subtitle = new Text("Answer, decide, and keep the world alive.");
            subtitle.setFont(Font.font("Verdana", FontWeight.BOLD, 18));
            subtitle.setFill(Color.web("#d7e77f"));

            Button btnStart = styledButton("Begin");
            Button btnExit = styledButton("Exit");

            btnStart.setOnAction(e -> fireNewGame());
            btnExit.setOnAction(e -> fireExit());

            VBox vbox = new VBox(18, title, subtitle, btnStart, btnExit);
            vbox.setAlignment(Pos.CENTER_LEFT);
            vbox.setTranslateX(108);

            StackPane root = new StackPane(bg, vbox);
            root.setPrefSize(w, h);

            getContentRoot().getChildren().add(root);
        }

        private static Button styledButton(String label) {
            String base = "-fx-background-color:#24362d;-fx-border-color:#d8e77f;" +
                    "-fx-border-width:1.5;-fx-text-fill:#f7f4dc;" +
                    "-fx-font-family:Verdana;-fx-font-size:16px;-fx-font-weight:bold;";
            String hover = "-fx-background-color:#2e4a38;-fx-border-color:#d8e77f;" +
                    "-fx-border-width:1.5;-fx-text-fill:#ffffff;" +
                    "-fx-font-family:Verdana;-fx-font-size:16px;-fx-font-weight:bold;";
            Button btn = new Button(label);
            btn.setMinWidth(220);
            btn.setMinHeight(42);
            btn.setStyle(base);
            btn.setOnMouseEntered(e -> btn.setStyle(hover));
            btn.setOnMouseExited(e -> btn.setStyle(base));
            return btn;
        }

        private static void drawBackground(GraphicsContext gc, double w, double h) {
            gc.setFill(Color.web("#17231e"));
            gc.fillRect(0, 0, w, h);
            gc.setFill(Color.web("#6fa85f", 0.42));
            for (int y = 0; y < h; y += 56) {
                for (int x = -80; x < w; x += 112) {
                    double offset = (y / 56) % 2 == 0 ? 0 : 56;
                    double cx = x + offset;
                    gc.fillPolygon(
                            new double[] { cx, cx + 56, cx + 112, cx + 56 },
                            new double[] { y + 28, y, y + 28, y + 56 },
                            4);
                }
            }
            gc.setFill(Color.web("#0d1512", 0.42));
            gc.fillRect(0, 0, w, h);
            gc.setFill(Color.web("#f1d090", 0.18));
            gc.fillOval(w - 260, 80, 140, 140);
        }
    }
}