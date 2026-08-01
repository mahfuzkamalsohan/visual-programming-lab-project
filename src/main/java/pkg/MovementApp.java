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
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.entity.level.Level;
import com.almasb.fxgl.app.scene.Viewport;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import pkg.net.GameStatePacket;
import pkg.net.InputPacket;
import pkg.net.NetworkManager;
import pkg.restoration.systems.DynamicMapManager;
import pkg.restoration.systems.RestorationTimer;

import java.util.List;
import java.util.Optional;

public class MovementApp extends GameApplication {

    private static final double INITIAL_TIME = 120.0;
    private static final double MAX_TIME = 120.0;

    public static GameMode selectedGameMode = GameMode.SINGLE_PLAYER;
    public static String targetHostIp = "127.0.0.1";

    private Entity playerEntity;
    private Entity playerEntity2;
    private PlayerComponent playerComponent;
    private PlayerComponent playerComponent2;

    private RestorationTimer timer;
    private DynamicMapManager mapManager;

    private NetworkManager netManager;

    private Text timerText;
    private Text modeStatusText;

    private boolean clientUp, clientDown, clientLeft, clientRight;


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
        // Player 1 controls (WASD)
        bindKey("Move Up P1", KeyCode.W,
                () -> handleMovement(1, Direction.NORTH, true),
                () -> handleMovement(1, Direction.NORTH, false));
        bindKey("Move Down P1", KeyCode.S,
                () -> handleMovement(1, Direction.SOUTH, true),
                () -> handleMovement(1, Direction.SOUTH, false));
        bindKey("Move Left P1", KeyCode.A,
                () -> handleMovement(1, Direction.WEST, true),
                () -> handleMovement(1, Direction.WEST, false));
        bindKey("Move Right P1", KeyCode.D,
                () -> handleMovement(1, Direction.EAST, true),
                () -> handleMovement(1, Direction.EAST, false));

        // Player 2 / Alt controls (Arrow Keys)
        bindKey("Move Up P2", KeyCode.UP,
                () -> handleMovement(2, Direction.NORTH, true),
                () -> handleMovement(2, Direction.NORTH, false));
        bindKey("Move Down P2", KeyCode.DOWN,
                () -> handleMovement(2, Direction.SOUTH, true),
                () -> handleMovement(2, Direction.SOUTH, false));
        bindKey("Move Left P2", KeyCode.LEFT,
                () -> handleMovement(2, Direction.WEST, true),
                () -> handleMovement(2, Direction.WEST, false));
        bindKey("Move Right P2", KeyCode.RIGHT,
                () -> handleMovement(2, Direction.EAST, true),
                () -> handleMovement(2, Direction.EAST, false));

        bindKey("Add Time", KeyCode.E,
                () -> {
                    if (timer != null && selectedGameMode != GameMode.LAN_JOIN) {
                        timer.applyDelta(30.0);
                    }
                },
                () -> {
                });
    }

    private void handleMovement(int playerNum, Direction dir, boolean pressed) {
        if (selectedGameMode == GameMode.LAN_JOIN) {
            // Client inputs control Player 2 and send packet to Host
            switch (dir) {
                case NORTH -> clientUp = pressed;
                case SOUTH -> clientDown = pressed;
                case WEST -> clientLeft = pressed;
                case EAST -> clientRight = pressed;
            }
            sendClientInputPacket();
            return;
        }

        if (playerNum == 1) {
            if (playerComponent != null) {
                setComponentMovement(playerComponent, dir, pressed);
            }
        } else if (playerNum == 2) {
            if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN) {
                if (playerComponent2 != null) {
                    setComponentMovement(playerComponent2, dir, pressed);
                }
            } else if (selectedGameMode == GameMode.SINGLE_PLAYER || selectedGameMode == GameMode.LAN_HOST) {
                // In single player/host, Arrow keys can also move P1 as alternate keys
                if (playerComponent != null) {
                    setComponentMovement(playerComponent, dir, pressed);
                }
            }
        }
    }

    private void setComponentMovement(PlayerComponent comp, Direction dir, boolean pressed) {
        switch (dir) {
            case NORTH -> comp.setUp(pressed);
            case SOUTH -> comp.setDown(pressed);
            case WEST -> comp.setLeft(pressed);
            case EAST -> comp.setRight(pressed);
        }
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
        Level level = FXGL.setLevelFromMap("tmx/level_0.tmx");

        mapManager = new DynamicMapManager("tmx/level_0.tmx");
        if (level != null) {
            mapManager.setInitialTileEntities(level.getEntities());
        }

        List<Entity> players = FXGL.getGameWorld().getEntitiesByComponent(PlayerComponent.class);
        if (players.isEmpty()) {
            playerEntity = FXGL.spawn("restorationPlayer", 240, 160);
        } else {
            playerEntity = players.get(0);
        }
        playerComponent = playerEntity.getComponent(PlayerComponent.class);

        // Spawn Player 2 for Co-Op modes
        if (selectedGameMode != GameMode.SINGLE_PLAYER) {
            playerEntity2 = FXGL.entityBuilder()
                    .at(360, 160)
                    .type(EntityType.PLAYER)
                    .bbox(new HitBox(BoundingShape.box(16, 24)))
                    .with(new CollidableComponent(true))
                    .with(new PlayerComponent(2))
                    .buildAndAttach();
            playerComponent2 = playerEntity2.getComponent(PlayerComponent.class);
        }

        setupViewports();
        setupNetworking();
    }

    private void setupViewports() {
        Viewport vp = FXGL.getGameScene().getViewport();
        vp.setLazy(true);

        if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN && playerEntity != null && playerEntity2 != null) {
            // Shared Screen Local Co-Op: Fixed 1.8x crisp zoom tracking dual player
            // midpoint
            vp.setZoom(1.8);
            vp.unbind();
            updateCoopCamera();
        } else if (selectedGameMode == GameMode.LAN_JOIN && playerEntity2 != null) {
            // LAN Join (Client): Full screen following Player 2
            vp.setZoom(2.0);
            vp.bindToEntity(playerEntity2, FXGL.getAppWidth() / 2.0, FXGL.getAppHeight() / 2.0);
        } else {
            // Single Player or LAN Host: Full screen following Player 1
            vp.setZoom(2.0);
            vp.bindToEntity(playerEntity, FXGL.getAppWidth() / 2.0, FXGL.getAppHeight() / 2.0);
        }
    }

    private void updateCoopCamera() {
        if (playerEntity != null && playerEntity2 != null) {
            double midX = (playerEntity.getX() + playerEntity2.getX()) / 2.0 + 120.0;
            double midY = (playerEntity.getY() + playerEntity2.getY()) / 2.0 + 120.0;
            FXGL.getGameScene().getViewport().focusOn(new javafx.geometry.Point2D(midX, midY));
        }
    }

    private void setupNetworking() {
        if (selectedGameMode == GameMode.LAN_HOST) {
            netManager = new NetworkManager();
            netManager.startHost(NetworkManager.DEFAULT_PORT, input -> {
                FXGL.runOnce(() -> {
                    if (playerComponent2 != null) {
                        playerComponent2.setUp(input.up);
                        playerComponent2.setDown(input.down);
                        playerComponent2.setLeft(input.left);
                        playerComponent2.setRight(input.right);
                    }
                }, Duration.ZERO);
            });
        } else if (selectedGameMode == GameMode.LAN_JOIN) {
            netManager = new NetworkManager();
            netManager.startClient(targetHostIp, NetworkManager.DEFAULT_PORT, state -> {
                FXGL.runOnce(() -> applyRemoteGameState(state), Duration.ZERO);
            });
        }
    }

    private void sendClientInputPacket() {
        if (selectedGameMode == GameMode.LAN_JOIN && netManager != null) {
            netManager.sendInputPacket(new InputPacket(clientUp, clientDown, clientLeft, clientRight));
        }
    }

    private void applyRemoteGameState(GameStatePacket packet) {
        if (playerEntity != null) {
            playerEntity.setPosition(packet.p1X, packet.p1Y);
            playerComponent.setRemoteState(Direction.values()[packet.p1DirIndex], packet.p1Moving);
        }
        if (playerEntity2 != null) {
            playerEntity2.setPosition(packet.p2X, packet.p2Y);
            playerComponent2.setRemoteState(Direction.values()[packet.p2DirIndex], packet.p2Moving);
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

        modeStatusText = new Text();
        modeStatusText.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        modeStatusText.setFill(Color.web("#f7f4dc"));
        modeStatusText.setX(20);
        modeStatusText.setY(60);
        FXGL.addUINode(modeStatusText);

        if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN) {
            Text p1Label = new Text("P1: WASD");
            p1Label.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
            p1Label.setFill(Color.web("#eff7d4"));
            p1Label.setX(20);
            p1Label.setY(85);

            Text p2Label = new Text("P2: ARROW KEYS");
            p2Label.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
            p2Label.setFill(Color.web("#80d0ff"));
            p2Label.setX(20);
            p2Label.setY(105);

            FXGL.addUINode(p1Label);
            FXGL.addUINode(p2Label);
            modeStatusText.setText("Local Co-Op (2 Players 1 Keyboard)");
        } else if (selectedGameMode == GameMode.LAN_HOST) {
            modeStatusText.setText("LAN Co-Op: Hosting on Port " + NetworkManager.DEFAULT_PORT);
        } else if (selectedGameMode == GameMode.LAN_JOIN) {
            modeStatusText.setText("LAN Co-Op: Connected to " + targetHostIp);
        } else {
            modeStatusText.setText("Single Player Mode");
        }

        refreshTimerLabel();
    }

    @Override
    protected void onUpdate(double tpf) {
        if (timer == null)
            return;

        if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN) {
            updateCoopCamera();
        }

        if (selectedGameMode != GameMode.LAN_JOIN) {
            timer.tick(tpf);
        }

        if (mapManager != null) {
            mapManager.update(timer.restorationRatio());
        }

        if (selectedGameMode == GameMode.LAN_HOST && netManager != null && playerEntity != null
                && playerEntity2 != null) {
            GameStatePacket packet = new GameStatePacket(
                    playerEntity.getX(), playerEntity.getY(), playerComponent.getCurrentDirection().index,
                    playerComponent.isMoving(),
                    playerEntity2.getX(), playerEntity2.getY(), playerComponent2.getCurrentDirection().index,
                    playerComponent2.isMoving(),
                    timer.currentSeconds());
            netManager.sendGameState(packet);
        }

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

    public void onStop() {
        if (netManager != null) {
            netManager.stop();
        }
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
                    .with(new PlayerComponent(1))
                    .build();
        }

        @Spawns("")
        public Entity spawnEmpty(SpawnData data) {
            return FXGL.entityBuilder(data).build();
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
            title.setFont(Font.font("Georgia", FontWeight.BOLD, 68));
            title.setFill(Color.web("#eff7d4"));

            Text subtitle = new Text("Answer, decide, and keep the world alive.");
            subtitle.setFont(Font.font("Verdana", FontWeight.BOLD, 18));
            subtitle.setFill(Color.web("#d7e77f"));

            Button btnSingle = styledButton("Single Player");
            Button btnLocalCoop = styledButton("Local Co-Op (2 Players)");
            Button btnHostLan = styledButton("Host LAN Co-Op");
            Button btnJoinLan = styledButton("Join LAN Co-Op");
            Button btnExit = styledButton("Exit");

            btnSingle.setOnAction(e -> {
                selectedGameMode = GameMode.SINGLE_PLAYER;
                fireNewGame();
            });

            btnLocalCoop.setOnAction(e -> {
                selectedGameMode = GameMode.LOCAL_COOP_SPLITSCREEN;
                fireNewGame();
            });

            btnHostLan.setOnAction(e -> {
                selectedGameMode = GameMode.LAN_HOST;
                fireNewGame();
            });

            btnJoinLan.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog("127.0.0.1");
                dialog.setTitle("Join LAN Co-Op");
                dialog.setHeaderText("Enter Host IP Address:");
                dialog.setContentText("Host IP:");
                Optional<String> result = dialog.showAndWait();
                result.ifPresent(ip -> {
                    targetHostIp = ip.trim();
                    selectedGameMode = GameMode.LAN_JOIN;
                    fireNewGame();
                });
            });

            btnExit.setOnAction(e -> fireExit());

            VBox vbox = new VBox(14, title, subtitle, btnSingle, btnLocalCoop, btnHostLan, btnJoinLan, btnExit);
            vbox.setAlignment(Pos.CENTER_LEFT);
            vbox.setTranslateX(108);

            StackPane root = new StackPane(bg, vbox);
            root.setPrefSize(w, h);

            getContentRoot().getChildren().add(root);
        }

        private static Button styledButton(String label) {
            String base = "-fx-background-color:#24362d;-fx-border-color:#d8e77f;" +
                    "-fx-border-width:1.5;-fx-text-fill:#f7f4dc;" +
                    "-fx-font-family:Verdana;-fx-font-size:15px;-fx-font-weight:bold;";
            String hover = "-fx-background-color:#2e4a38;-fx-border-color:#d8e77f;" +
                    "-fx-border-width:1.5;-fx-text-fill:#ffffff;" +
                    "-fx-font-family:Verdana;-fx-font-size:15px;-fx-font-weight:bold;";
            Button btn = new Button(label);
            btn.setMinWidth(260);
            btn.setMinHeight(38);
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