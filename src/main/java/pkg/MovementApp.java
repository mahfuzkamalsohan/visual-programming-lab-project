package pkg;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.entity.level.Level;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;

import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;
import pkg.audio.AudioManager;
import pkg.ui.CutsceneOverlay;
import pkg.net.GameStatePacket;
import pkg.net.InputPacket;
import pkg.net.NetworkManager;
import pkg.restoration.questions.EnvironmentalQuestion;
import pkg.restoration.questions.QuestionLoader;
import pkg.restoration.questions.QuestionResult;
import pkg.restoration.questions.QuestionSelector;
import pkg.restoration.systems.DynamicMapManager;
import pkg.restoration.systems.InfiniteMapManager;
import pkg.restoration.systems.RestorationTimer;
import pkg.restoration.tasks.CollectionTask;
import pkg.restoration.tasks.SortingTask;
import pkg.restoration.tasks.TaskResult;
import pkg.restoration.tasks.TaskTimer;

public class MovementApp extends GameApplication {

    private static final double INITIAL_TIME = 120.0;
    private static final double MAX_TIME = 120.0;
    private static final int DEMO_QUESTION_COUNT = 6;

    public static GameMode selectedGameMode = GameMode.SINGLE_PLAYER;
    public static String targetHostIp = "127.0.0.1";

    public static boolean isInfiniteCoopMode() {
        return selectedGameMode == GameMode.MAP_GENERATOR || selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN;
    }

    public static boolean isInfiniteGameMode() {
        return selectedGameMode == GameMode.SINGLE_PLAYER || isInfiniteCoopMode();
    }

    private double currentCoopZoom = 2.0;
    private double currentCamX = 0;
    private double currentCamY = 160;
    public static final double MAX_TETHER_DISTANCE = 400.0;

    private Entity playerEntity;
    private Entity playerEntity2;
    private PlayerComponent playerComponent;
    private PlayerComponent playerComponent2;

    private RestorationTimer timer;
    private DynamicMapManager mapManager;
    private InfiniteMapManager infiniteMapManager;

    public enum GeneratorStage {
        TRASH_COLLECTION,
        QUESTION,
        SORTING
    }

    private GeneratorStage generatorStage = GeneratorStage.TRASH_COLLECTION;
    private boolean generatorStageCompleted = false;
    private int currentMapRadius = 0;
    private final java.util.Set<String> completedChunks = new java.util.HashSet<>();
    private final java.util.Map<String, GeneratorStage> assignedTasks = new java.util.HashMap<>();
    private double boundaryWallReenableCooldown = 0.0;

    private final List<Entity> generatorTrashEntities = new ArrayList<>();
    private int generatorTrashCollected = 0;
    private final int GENERATOR_TARGET_TRASH = 8;

    private final List<Entity> generatorQuestionEntities = new ArrayList<>();
    private int generatorQuestionsAnswered = 0;
    private final int GENERATOR_TARGET_QUESTIONS = 2;

    private final List<Entity> generatorSortingWasteEntities = new ArrayList<>();
    private int generatorSortedCount = 0;
    private final int GENERATOR_TARGET_SORTING = 6;

    private int currentDistrict = 1;
    private int ecoScore = 0;
    private Label carriedItemLabel;
    private Node currentNoticeNode;

    private NetworkManager netManager;

    private Rectangle timerBarFill;
    private Text modeStatusText;
    private Text scoreText;
    private Text trashCounterText;
    private Text levelNoticeText;
    private Text interactPromptText;

    private final int TOTAL_TRASH = 8;
    private int collectedTrash = 0;
    private int trashMask = (1 << TOTAL_TRASH) - 1;
    private final Map<Integer, Entity> trashEntities = new java.util.HashMap<>();

    private boolean clientUp, clientDown, clientLeft, clientRight, clientInteract;

    private Entity questionPoint;
    private VBox questionPanel;
    private Label questionLabel;
    private Label questionFeedbackLabel;
    private List<EnvironmentalQuestion> testQuestions = List.of();
    private final List<Entity> demoQuestionEntities = new ArrayList<>();
    private int testQuestionIndex;
    private boolean playerNearQuestionPoint;
    private boolean questionAnswerLocked;
    private Entity currentActiveQuestionEntity;
    private EnvironmentalQuestion currentActiveQuestion;
    private boolean gameEnded;
    private Node endGameOverlayNode;
    private boolean isCutsceneActive = false;
    private CutsceneOverlay cutsceneOverlayNode;
    private final List<Node> hudNodes = new ArrayList<>();

    private void addHudNode(Node node) {
        if (node == null) return;
        hudNodes.add(node);
        if (isCutsceneActive) {
            if (!node.visibleProperty().isBound()) {
                node.setVisible(false);
            }
        } else {
            FXGL.addUINode(node);
        }
    }

    private static final double SORT_ZONE_X = 500;
    private static final double SORT_ZONE_Y = 105;
    private static final double SORT_ZONE_WIDTH = 260;
    private static final double SORT_ZONE_HEIGHT = 225;
    // These are the visible red interaction boxes used by Sorting Test Mode.
    // Change them here while tuning interaction distances.
    private static final double PICKUP_BOX_WIDTH = 48;
    private static final double PICKUP_BOX_HEIGHT = 48;
    private static final double INTAKE_BOX_WIDTH = 90;
    private static final double INTAKE_BOX_HEIGHT = 120;
    private static final double BIN_BOX_WIDTH = 48;
    private static final double BIN_BOX_HEIGHT = 40;
    private static final double PLAYER_BOX_WIDTH = 16;
    private static final double PLAYER_BOX_HEIGHT = 24;
    private final Map<Entity, WasteItem> sortingWasteEntities = new LinkedHashMap<>();
    private final Map<Entity, String> sortingBins = new LinkedHashMap<>();
    private final Map<Entity, InteractionBox> sortingPickupBoxes = new LinkedHashMap<>();
    private final Map<Entity, InteractionBox> sortingBinBoxes = new LinkedHashMap<>();
    private final Deque<WasteItem> sortingIntake = new ArrayDeque<>();
    private SortingTask sortingTask;
    private WasteItem outsideCarriedWaste;
    private WasteItem insideCarriedWaste;
    private Entity sortingIntakePoint;
    private InteractionBox sortingIntakeBox;
    private ImageView collectorCarriedWasteView;
    private ImageView sorterCarriedWasteView;
    private ImageView intakeWasteView;
    private Text sortingStatusText;
    private String sortingFeedback = "P1: collect unknown waste and bring it to the intake";

    private DemoStage demoStage = DemoStage.COLLECTION;
    private final Map<Entity, String> demoCollectionItems = new LinkedHashMap<>();
    private final List<Entity> demoSortingObjects = new java.util.ArrayList<>();
    private CollectionTask demoCollectionTask;
    private int demoSortingItemCount;
    private Text demoStatusText;
    private boolean demoSorterLocked;
    private double activeSortZoneX = SORT_ZONE_X;
    private double activeSortZoneY = SORT_ZONE_Y;
    private double activeSortZoneWidth = SORT_ZONE_WIDTH;
    private double activeSortZoneHeight = SORT_ZONE_HEIGHT;

    @Override
    protected void onPreInit() {
        AudioManager.init();
        AudioManager.playMenuMusic();
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setFullScreenAllowed(true);
        settings.setFullScreenFromStart(false);
        settings.setManualResizeEnabled(true);
        settings.setPreserveResizeRatio(true);
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

            @Override
            public FXGLMenu newGameMenu() {
                return new PauseMenu(MenuType.GAME_MENU);
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

        bindKey("P1 Interact / Collect", KeyCode.E,
                this::tryCollectTrashP1,
                () -> {
                });

        bindKey("P1 Interact Space", KeyCode.SPACE,
                this::tryCollectTrashP1,
                () -> {
                });

        bindKey("P2 Interact / Collect", KeyCode.SLASH,
                this::tryCollectTrashP2,
                () -> {
                });

        bindKey("P2 Interact Enter", KeyCode.ENTER,
                this::tryCollectTrashP2,
                () -> {
                });

        bindKey("Toggle Fullscreen", KeyCode.F11,
                () -> FXGL.getPrimaryStage().setFullScreen(!FXGL.getPrimaryStage().isFullScreen()),
                () -> {
                });

        bindAnswerKey("Answer 1", KeyCode.DIGIT1, 0);
        bindAnswerKey("Answer 2", KeyCode.DIGIT2, 1);
        bindAnswerKey("Answer 3", KeyCode.DIGIT3, 2);
        bindAnswerKey("Answer Numpad 1", KeyCode.NUMPAD1, 0);
        bindAnswerKey("Answer Numpad 2", KeyCode.NUMPAD2, 1);
        bindAnswerKey("Answer Numpad 3", KeyCode.NUMPAD3, 2);
    }

    private void bindAnswerKey(String name, KeyCode code, int choiceIndex) {
        bindKey(name, code, () -> answerTestQuestion(choiceIndex), () -> {
        });
    }

    private void handleMovement(int playerNum, Direction dir, boolean pressed) {
        if (isCutsceneActive) {
            return;
        }

        if (selectedGameMode == GameMode.LAN_JOIN) {
            // Client inputs control Player 2 and send packet to Host
            switch (dir) {
                case NORTH -> clientUp = pressed;
                case SOUTH -> clientDown = pressed;
                case WEST -> clientLeft = pressed;
                case EAST -> clientRight = pressed;
                case NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST -> {}
            }
            sendClientInputPacket();
            return;
        }

        if (playerNum == 1) {
            if (playerComponent != null) {
                setComponentMovement(playerComponent, dir, pressed);
            }
        } else if (playerNum == 2) {
            if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN
                    || selectedGameMode == GameMode.SORTING_TEST
                    || selectedGameMode == GameMode.SEQUENTIAL_DEMO
                    || selectedGameMode == GameMode.MAP_GENERATOR) {
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
            case NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST -> {}
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
        // A new game clears FXGL's world, but Java fields still reference entities from
        // the previous run. Reset them before resolving players from the new level.
        playerEntity = null;
        playerEntity2 = null;
        playerComponent = null;
        playerComponent2 = null;

        AudioManager.playGameMusic();
        FXGL.getGameScene().setBackgroundColor(Color.web("#17231e"));
        timer = new RestorationTimer(INITIAL_TIME, MAX_TIME);
        gameEnded = false;
        if (endGameOverlayNode != null) {
            FXGL.removeUINode(endGameOverlayNode);
            endGameOverlayNode = null;
        }

        if (cutsceneOverlayNode != null) {
            FXGL.removeUINode(cutsceneOverlayNode);
            cutsceneOverlayNode = null;
        }

        hudNodes.clear();
        isCutsceneActive = true;
        cutsceneOverlayNode = new CutsceneOverlay(FXGL.getAppWidth(), FXGL.getAppHeight(), () -> {
            if (cutsceneOverlayNode != null) {
                FXGL.removeUINode(cutsceneOverlayNode);
                cutsceneOverlayNode = null;
            }
            isCutsceneActive = false;
            for (Node hudNode : hudNodes) {
                if (hudNode != null) {
                    if (!hudNode.visibleProperty().isBound()) {
                        hudNode.setVisible(true);
                    }
                    if (hudNode.getParent() == null) {
                        FXGL.addUINode(hudNode);
                    }
                }
            }
        });
        FXGL.addUINode(cutsceneOverlayNode);

        if (infiniteMapManager != null) {
            infiniteMapManager.clearAll();
            infiniteMapManager = null;
        }

        FXGL.getGameWorld().addEntityFactory(new GameEntityFactory());

        if (isInfiniteGameMode()) {
            mapManager = null;
            infiniteMapManager = new InfiniteMapManager(System.currentTimeMillis());
            playerEntity = FXGL.spawn("restorationPlayer", 0, 160);
            playerEntity.setZIndex(100);
            playerComponent = playerEntity.getComponent(PlayerComponent.class);

            if (isInfiniteCoopMode()) {
                // Spawn Player 2 for Local Co-Op in Infinite Mode
                playerEntity2 = FXGL.entityBuilder()
                        .at(40, 160)
                        .type(EntityType.PLAYER)
                        .bbox(new HitBox(BoundingShape.box(16, 24)))
                        .with(new CollidableComponent(true))
                        .with(new PlayerComponent(2))
                        .zIndex(100)
                        .buildAndAttach();
                playerComponent2 = playerEntity2.getComponent(PlayerComponent.class);

                attachPlayerBadge(playerEntity, "P1", Color.web("#39ff14"));
                attachPlayerBadge(playerEntity2, "P2", Color.web("#d7e77f"));
            } else {
                playerEntity2 = null;
                playerComponent2 = null;
            }

            infiniteMapManager.updatePlayerPosition(playerEntity.getX(), playerEntity.getY());
            collectedTrash = 0;
            currentDistrict = 1;
            ecoScore = 0;
            generatorStage = GeneratorStage.TRASH_COLLECTION;
            generatorStageCompleted = false;
            completedChunks.clear();
            currentMapRadius = 0;
            assignedTasks.clear();
            assignedTasks.put("0,0", GeneratorStage.TRASH_COLLECTION);
            infiniteMapManager.unlockLayer(0);
            setupGeneratorStage1(0, 0);
        } else {
            String mapPath = selectedGameMode == GameMode.SEQUENTIAL_DEMO
                    ? "tmx/level_demo.tmx"
                    : "tmx/level_0.tmx";
            Level level = FXGL.setLevelFromMap(mapPath);

            if (selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
                mapManager = null;
            } else {
                mapManager = new DynamicMapManager(mapPath);
                mapManager.setInitialTileEntities(level.getEntities());
            }

            List<Entity> players = FXGL.getGameWorld().getEntitiesByComponent(PlayerComponent.class);
            playerEntity = findPlayer(players, 1);
            if (playerEntity == null) {
                playerEntity = FXGL.spawn("restorationPlayer", 640, 320);
            }
            playerEntity.setZIndex(100);
            playerComponent = playerEntity.getComponent(PlayerComponent.class);

            if (selectedGameMode == GameMode.LAN_HOST
                    || selectedGameMode == GameMode.LAN_JOIN
                    || selectedGameMode == GameMode.SORTING_TEST
                    || selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
                playerEntity2 = findPlayer(players, 2);
                if (playerEntity2 == null) {
                    playerEntity2 = FXGL.entityBuilder()
                            .at(670, 320)
                            .type(EntityType.PLAYER)
                            .bbox(new HitBox(BoundingShape.box(16, 24)))
                            .with(new CollidableComponent(true))
                            .with(new PlayerComponent(2))
                            .zIndex(100)
                            .buildAndAttach();
                } else {
                    playerEntity2.setZIndex(100);
                }
                playerComponent2 = playerEntity2.getComponent(PlayerComponent.class);
            }

            switch (selectedGameMode) {
                case QUESTION_TEST -> setupQuestionTest();
                case SORTING_TEST -> setupSortingTest();
                case SEQUENTIAL_DEMO -> setupSequentialDemo(level);
                default -> spawnRandomTrash();
            }
        }
        setupViewports();
        setupNetworking();
    }

    private Entity findPlayer(List<Entity> players, int index) {
        return players.stream()
                .filter(entity -> entity.getComponent(PlayerComponent.class).getPlayerIndex() == index)
                .findFirst()
                .orElse(null);
    }

    private void setupQuestionTest() {
        try {
            testQuestions = new QuestionLoader().loadResource("assets/questions/environment.dat");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load question test data", exception);
        }
        if (testQuestions.isEmpty()) {
            throw new IllegalStateException("Question test data is empty");
        }

        testQuestionIndex = 0;
        questionAnswerLocked = false;
        currentActiveQuestionEntity = null;
        currentActiveQuestion = null;

        // The standalone test intentionally reuses one reachable point for the
        // complete question bank. The three-stage demo uses one point per question.
        questionPoint = FXGL.entityBuilder()
                .at(420, 220)
                .type(EntityType.QUESTION_POINT)
                .view(safeQuestionTexture())
                .buildAndAttach();
        questionPoint.setProperty("question", testQuestions.getFirst());
        createQuestionPanel();
    }

    private void createQuestionPanel() {
        if (questionPanel != null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/ui/fxml/question_overlay.fxml"));
            questionPanel = loader.load();
            questionLabel = (Label) questionPanel.lookup("#questionPromptLabel");
            questionFeedbackLabel = (Label) questionPanel.lookup("#feedbackLabel");
        } catch (IOException | RuntimeException e) {
            questionLabel = new Label();
            questionLabel.setWrapText(true);
            questionLabel.setMaxWidth(360);
            questionLabel.getStyleClass().add("pixel-question-text");
            questionFeedbackLabel = new Label("Press 1, 2, or 3 to answer");
            questionFeedbackLabel.getStyleClass().add("pixel-label-yellow");

            questionPanel = new VBox(7, questionLabel, questionFeedbackLabel);
            questionPanel.getStyleClass().add("pixel-panel");
        }
        try {
            questionPanel.getStylesheets()
                    .add(getClass().getResource("/assets/ui/css/pixel_style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        questionPanel.setTranslateX(-135);
        questionPanel.setTranslateY(-165);
        questionPanel.setScaleX(0.72);
        questionPanel.setScaleY(0.72);
        questionPanel.setVisible(false);
    }

    private void attachQuestionPanelToEntity(Entity targetEntity) {
        createQuestionPanel();
        switch (questionPanel.getParent()) {
            case javafx.scene.Group group -> group.getChildren().remove(questionPanel);
            case javafx.scene.layout.Pane pane -> pane.getChildren().remove(questionPanel);
            case null, default -> {}
        }
        targetEntity.getViewComponent().addChild(questionPanel);
    }

    private void setupSortingTest() {
        resetSortingTestState();
        playerEntity2.setPosition(625, 215);

        Rectangle zone = new Rectangle(SORT_ZONE_WIDTH, SORT_ZONE_HEIGHT, Color.web("#d7e77f", 0.10));
        zone.setStroke(Color.web("#d7e77f"));
        zone.setStrokeWidth(4);
        FXGL.entityBuilder().at(SORT_ZONE_X, SORT_ZONE_Y)
                .type(EntityType.QUESTION_POINT).view(zone).buildAndAttach();

        Node intakeView = safeTexture("intake.png", 48, 48, "#f1d090");
        Label intakeLabel = worldLabel("INTAKE");
        intakeLabel.setTranslateX(-8);
        intakeLabel.setTranslateY(-18);
        sortingIntakePoint = FXGL.entityBuilder().at(SORT_ZONE_X, 215)
                .type(EntityType.QUESTION_POINT).view(intakeView).buildAndAttach();
        sortingIntakePoint.getViewComponent().addChild(intakeLabel);
        sortingIntakeBox = new InteractionBox(
                SORT_ZONE_X - INTAKE_BOX_WIDTH / 2,
                215 - INTAKE_BOX_HEIGHT / 2,
                INTAKE_BOX_WIDTH,
                INTAKE_BOX_HEIGHT);

        createSortingBin("black", 545, 145, "#272727");
        createSortingBin("blue", 675, 145, "#397ac7");
        createSortingBin("green", 545, 275, "#3e914c");
        createSortingBin("red", 675, 275, "#bd4545");

        List<WasteItem> waste = List.of(
                new WasteItem("wrapper", "Greasy snack wrapper", "black"),
                new WasteItem("foam", "Used foam food box", "black"),
                new WasteItem("newspaper", "Clean newspaper", "blue"),
                new WasteItem("can", "Clean aluminium can", "blue"),
                new WasteItem("peel", "Banana peel", "green"),
                new WasteItem("leaves", "Dry yard leaves", "green"),
                new WasteItem("battery", "Used battery", "red"),
                new WasteItem("phone", "Broken mobile phone", "red"));
        Map<String, String> expectedBins = new LinkedHashMap<>();
        double[][] positions = { { 180, 150 }, { 280, 255 }, { 390, 160 }, { 205, 280 },
                { 350, 265 }, { 440, 75 }, { 155, 75 }, { 410, 305 } };
        for (int i = 0; i < waste.size(); i++) {
            WasteItem item = waste.get(i);
            expectedBins.put(item.id(), item.binId());
            Entity entity = FXGL.entityBuilder().at(positions[i][0], positions[i][1])
                    .type(EntityType.TRASH)
                    .viewWithBBox("trashbag.png")
                    .with(new CollidableComponent(true))
                    .buildAndAttach();
            sortingWasteEntities.put(entity, item);
            InteractionBox pickupBox = new InteractionBox(
                    positions[i][0] - 8,
                    positions[i][1] - 8,
                    PICKUP_BOX_WIDTH,
                    PICKUP_BOX_HEIGHT);
            sortingPickupBoxes.put(entity, pickupBox);
        }
        sortingTask = new SortingTask(expectedBins, 15, 7, 8);

        attachSortingCarryViews();
    }

    private void attachSortingCarryViews() {
        if (playerEntity != null) {
            if (collectorCarriedWasteView == null) {
                collectorCarriedWasteView = new ImageView(FXGL.image("trashbag.png"));
                collectorCarriedWasteView.setTranslateX(-4);
                collectorCarriedWasteView.setTranslateY(-16);
                collectorCarriedWasteView.setMouseTransparent(true);
                playerEntity.getViewComponent().addChild(collectorCarriedWasteView);
            }
            collectorCarriedWasteView.setVisible(outsideCarriedWaste != null);

            if (carriedItemLabel == null) {
                carriedItemLabel = new Label();
                carriedItemLabel.setStyle(
                        "-fx-text-fill:#ffd700;-fx-font-family:Monospaced;-fx-font-size:9px;-fx-font-weight:bold;-fx-background-color:rgba(0,0,0,0.75);-fx-padding:1px 4px;-fx-border-color:#ffd700;-fx-border-width:1px;");
                carriedItemLabel.setTranslateX(-30);
                carriedItemLabel.setTranslateY(-28);
                carriedItemLabel.setMouseTransparent(true);
                playerEntity.getViewComponent().addChild(carriedItemLabel);
            }
            carriedItemLabel.setVisible(outsideCarriedWaste != null);
            if (outsideCarriedWaste != null) {
                carriedItemLabel.setText(outsideCarriedWaste.name());
            }
        }

        if (playerEntity2 != null) {
            if (sorterCarriedWasteView == null) {
                sorterCarriedWasteView = new ImageView(FXGL.image("trashbag.png"));
                sorterCarriedWasteView.setTranslateX(-4);
                sorterCarriedWasteView.setTranslateY(-16);
                sorterCarriedWasteView.setMouseTransparent(true);
                playerEntity2.getViewComponent().addChild(sorterCarriedWasteView);
            }
            sorterCarriedWasteView.setVisible(insideCarriedWaste != null);
        }

        if (sortingIntakePoint != null) {
            if (intakeWasteView == null) {
                intakeWasteView = new ImageView(FXGL.image("trashbag.png"));
                intakeWasteView.setTranslateX(-8);
                intakeWasteView.setTranslateY(-12);
                intakeWasteView.setMouseTransparent(true);
                sortingIntakePoint.getViewComponent().addChild(intakeWasteView);
            }
            intakeWasteView.setVisible(!sortingIntake.isEmpty());
        }
    }

    private void setupSequentialDemo(Level level) {
        resetSortingTestState();
        demoCollectionItems.clear();
        demoSortingObjects.clear();
        demoQuestionEntities.clear();
        demoStage = DemoStage.COLLECTION;
        demoSorterLocked = false;
        testQuestionIndex = 0;
        questionAnswerLocked = false;

        for (Entity entity : FXGL.getGameWorld().getEntitiesByType(EntityType.DEMO_COLLECTION_ITEM)) {
            demoCollectionItems.put(entity, entity.getProperties().getString("itemId"));
        }
        if (demoCollectionItems.isEmpty()) {
            throw new IllegalStateException("level_demo.tmx has no demoCollectable objects");
        }
        double collectionReward = level.getProperties().getDouble("collectionReward");
        double sortingItemReward = level.getProperties().getDouble("sortingItemReward");
        double sortingWrongPenalty = level.getProperties().getDouble("sortingWrongPenalty");
        double sortingCompletionReward = level.getProperties().getDouble("sortingCompletionReward");
        demoCollectionTask = new CollectionTask(demoCollectionItems.size(), collectionReward);

        List<Entity> questionPoints = FXGL.getGameWorld().getEntitiesByType(EntityType.DEMO_QUESTION_POINT);
        if (questionPoints.size() != 1) {
            throw new IllegalStateException("level_demo.tmx must contain exactly one demoQuestionPoint");
        }
        questionPoint = questionPoints.getFirst();
        String questionResource = questionPoint.getProperties().getString("questionResource");
        try {
            List<EnvironmentalQuestion> allQuestions = new QuestionLoader().loadResource(questionResource);
            testQuestions = QuestionSelector.randomUnique(allQuestions, DEMO_QUESTION_COUNT);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load demo questions: " + questionResource, exception);
        }

        double baseX = questionPoint.getX();
        double baseY = questionPoint.getY();
        double[][] demoOffsets = {
                { 0, 0 }, { -190, 0 }, { 190, 0 },
                { -95, -180 }, { 95, -180 }, { 0, 190 }
        };
        for (int i = 0; i < testQuestions.size(); i++) {
            EnvironmentalQuestion q = testQuestions.get(i);
            Entity qEntity;
            if (i == 0) {
                qEntity = questionPoint;
            } else {
                double[] offset = demoOffsets[i];
                qEntity = FXGL.entityBuilder()
                        .at(baseX + offset[0], baseY + offset[1])
                        .type(EntityType.DEMO_QUESTION_POINT)
                        .view(safeQuestionTexture())
                        .buildAndAttach();
            }
            qEntity.setProperty("question", q);
            qEntity.setVisible(false);
            demoQuestionEntities.add(qEntity);
        }
        createQuestionPanel();

        List<Entity> zones = FXGL.getGameWorld().getEntitiesByType(EntityType.DEMO_SORTING_ZONE);
        if (zones.size() != 1) {
            throw new IllegalStateException("level_demo.tmx must contain exactly one demoSortingZone");
        }
        Entity sortingZone = zones.getFirst();
        activeSortZoneX = sortingZone.getX();
        activeSortZoneY = sortingZone.getY();
        activeSortZoneWidth = sortingZone.getDouble("zoneWidth");
        activeSortZoneHeight = sortingZone.getDouble("zoneHeight");
        demoSortingObjects.add(sortingZone);

        List<Entity> intakes = FXGL.getGameWorld().getEntitiesByType(EntityType.DEMO_INTAKE);
        if (intakes.size() != 1) {
            throw new IllegalStateException("level_demo.tmx must contain exactly one demoIntake");
        }
        sortingIntakePoint = intakes.getFirst();
        sortingIntakeBox = new InteractionBox(
                sortingIntakePoint.getX() - INTAKE_BOX_WIDTH / 2,
                sortingIntakePoint.getY() - INTAKE_BOX_HEIGHT / 2,
                INTAKE_BOX_WIDTH,
                INTAKE_BOX_HEIGHT);
        demoSortingObjects.add(sortingIntakePoint);

        for (Entity bin : FXGL.getGameWorld().getEntitiesByType(EntityType.DEMO_BIN)) {
            sortingBins.put(bin, bin.getProperties().getString("binId"));
            double width = bin.getDouble("boxWidth");
            double height = bin.getDouble("boxHeight");
            double offsetX = (72 - width) / 2.0;
            double offsetY = (58 - height) / 2.0;
            sortingBinBoxes.put(bin, new InteractionBox(bin.getX() + offsetX, bin.getY() + offsetY, width, height));
            demoSortingObjects.add(bin);
        }

        Map<String, String> expectedBins = new LinkedHashMap<>();
        for (Entity entity : FXGL.getGameWorld().getEntitiesByType(EntityType.DEMO_SORTING_WASTE)) {
            WasteItem item = new WasteItem(
                    entity.getProperties().getString("itemId"),
                    entity.getProperties().getString("itemName"),
                    entity.getProperties().getString("binId"));
            expectedBins.put(item.id(), item.binId());
            sortingWasteEntities.put(entity, item);
            sortingPickupBoxes.put(entity, new InteractionBox(
                    entity.getX() - 8, entity.getY() - 8,
                    PICKUP_BOX_WIDTH, PICKUP_BOX_HEIGHT));
            demoSortingObjects.add(entity);
        }
        if (sortingBins.size() != 4 || expectedBins.isEmpty()) {
            throw new IllegalStateException("level_demo.tmx needs four bins and at least one demoSortingWaste");
        }
        demoSortingItemCount = expectedBins.size();
        sortingTask = new SortingTask(expectedBins, sortingCompletionReward,
                sortingItemReward, sortingWrongPenalty);
        attachSortingCarryViews();

        demoSortingObjects.forEach(entity -> entity.setVisible(false));
        sortingFeedback = "Collect all scattered garbage";
    }

    private void activateDemoQuestionStage() {
        demoStage = DemoStage.QUESTION;
        demoQuestionEntities.stream()
                .filter(Entity::isActive)
                .forEach(entity -> entity.setVisible(true));
        sortingFeedback = "Walk up to the question points to answer";
    }

    private void activateDemoSortingStage() {
        if (demoStage != DemoStage.QUESTION) {
            return;
        }
        demoStage = DemoStage.SORTING;
        questionPanel.setVisible(false);
        demoQuestionEntities.stream()
                .filter(Entity::isActive)
                .forEach(entity -> entity.setVisible(false));
        demoSortingObjects.forEach(entity -> entity.setVisible(true));
        sortingFeedback = "Travel east — P2 enters the sorting zone";
    }

    private void resetSortingTestState() {
        sortingWasteEntities.clear();
        sortingBins.clear();
        sortingPickupBoxes.clear();
        sortingBinBoxes.clear();
        sortingIntake.clear();
        outsideCarriedWaste = null;
        insideCarriedWaste = null;
        sortingIntakePoint = null;
        sortingIntakeBox = null;
        collectorCarriedWasteView = null;
        sorterCarriedWasteView = null;
        intakeWasteView = null;
        sortingFeedback = "Collect every scattered garbage and sort it";
        activeSortZoneX = SORT_ZONE_X;
        activeSortZoneY = SORT_ZONE_Y;
        activeSortZoneWidth = SORT_ZONE_WIDTH;
        activeSortZoneHeight = SORT_ZONE_HEIGHT;
    }

    private void setupGeneratorStage1(int chunkX, int chunkY) {
        clearGeneratorStageEntities();
        if (infiniteMapManager != null) {
            infiniteMapManager.setFragmentedMode(false);
            infiniteMapManager.lockCurrentRegion(chunkX, chunkY);
        }
        generatorTrashCollected = 0;

        double originX = (chunkX - chunkY) * 320.0;
        double originY = (chunkX + chunkY) * 160.0;

        int[][] tilePositions = {
                { 5, 5 }, { 9, 5 }, { 14, 5 },
                { 6, 9 }, { 10, 9 }, { 14, 9 },
                { 5, 14 }, { 9, 14 }
        };

        for (int i = 0; i < GENERATOR_TARGET_TRASH && i < tilePositions.length; i++) {
            int[] pos = tilePositions[i];
            int lx = pos[0];
            int ly = pos[1];
            double isoX = originX + (lx - ly) * 16.0 + 8;
            double isoY = originY + (lx + ly) * 8.0 + 4;
            String textureName = (i % 2 == 0) ? "bottle.png" : "trashbag.png";
            Entity trash = FXGL.entityBuilder()
                    .at(isoX, isoY)
                    .type(EntityType.TRASH)
                    .viewWithBBox(textureName)
                    .with(new CollidableComponent(true))
                    .buildAndAttach();
            trash.setRotation(FXGL.random(0, 360));
            generatorTrashEntities.add(trash);
        }
        generatorStageCompleted = false;
        updateTrashCounter();
        if (selectedGameMode == GameMode.SINGLE_PLAYER) {
            showTemporaryNotice("🌱 DISTRICT " + currentDistrict
                    + " — PHASE 1: ECO-CLEANUP\nCollect scattered trash items to heal the soil! [E / Space]");
        }
    }

    private void setupGeneratorStage2(int chunkX, int chunkY) {
        clearGeneratorStageEntities();
        if (infiniteMapManager != null) {
            infiniteMapManager.setFragmentedMode(false);
            infiniteMapManager.lockCurrentRegion(chunkX, chunkY);
        }
        generatorQuestionsAnswered = 0;

        double originX = (chunkX - chunkY) * 320.0;
        double originY = (chunkX + chunkY) * 160.0;

        try {
            List<EnvironmentalQuestion> allQuestions = new QuestionLoader()
                    .loadResource("assets/questions/environment.dat");
            testQuestions = QuestionSelector.randomUnique(allQuestions, GENERATOR_TARGET_QUESTIONS);
        } catch (IOException ignored) {
        }

        int[][] tilePositions = { { 7, 7 }, { 13, 11 } };
        for (int i = 0; i < GENERATOR_TARGET_QUESTIONS && i < testQuestions.size(); i++) {
            int lx = tilePositions[i][0];
            int ly = tilePositions[i][1];
            double isoX = originX + (lx - ly) * 16.0;
            double isoY = originY + (lx + ly) * 8.0;

            Entity qPoint = FXGL.entityBuilder()
                    .at(isoX, isoY)
                    .type(EntityType.QUESTION_POINT)
                    .view(safeQuestionTexture())
                    .buildAndAttach();
            qPoint.setProperty("question", testQuestions.get(i));
            generatorQuestionEntities.add(qPoint);
        }
        generatorStageCompleted = false;
        createQuestionPanel();
        updateTrashCounter();
        if (selectedGameMode == GameMode.SINGLE_PLAYER) {
            showTemporaryNotice("⚡ DISTRICT " + currentDistrict
                    + " — PHASE 2: ECO-GRID\nApproach interactive terminals to resolve environmental queries! [1, 2, 3]");
        }
    }

    private void setupGeneratorStage3(int chunkX, int chunkY) {
        clearGeneratorStageEntities();
        if (infiniteMapManager != null) {
            infiniteMapManager.setFragmentedMode(false);
            infiniteMapManager.lockCurrentRegion(chunkX, chunkY);
        }
        generatorSortedCount = 0;

        double originX = (chunkX - chunkY) * 320.0;
        double originY = (chunkX + chunkY) * 160.0;

        if (selectedGameMode != GameMode.SINGLE_PLAYER) {
            double intakeX = originX + (10 - 10) * 16.0;
            double intakeY = originY + (10 + 10) * 8.0;
            Node intakeView = safeTexture("intake.png", 48, 48, "#f1d090");
            Label intakeLabel = worldLabel("INTAKE");
            intakeLabel.setTranslateX(-8);
            intakeLabel.setTranslateY(-18);

            sortingIntakePoint = FXGL.entityBuilder()
                    .at(intakeX, intakeY)
                    .type(EntityType.QUESTION_POINT)
                    .view(intakeView)
                    .buildAndAttach();
            sortingIntakePoint.getViewComponent().addChild(intakeLabel);
            sortingIntakeBox = new InteractionBox(intakeX - 25, intakeY - 25, INTAKE_BOX_WIDTH, INTAKE_BOX_HEIGHT);
        }

        createSortingBin("black", originX + (10 - 6) * 16.0, originY + (10 + 6) * 8.0, "#272727");
        createSortingBin("blue", originX + (14 - 10) * 16.0, originY + (14 + 10) * 8.0, "#397ac7");
        createSortingBin("green", originX + (6 - 10) * 16.0, originY + (6 + 10) * 8.0, "#3e914c");
        createSortingBin("red", originX + (10 - 14) * 16.0, originY + (10 + 14) * 8.0, "#bd4545");

        List<WasteItem> waste = List.of(
                new WasteItem("wrapper", "Greasy snack wrapper", "black"),
                new WasteItem("foam", "Used foam food box", "black"),
                new WasteItem("newspaper", "Clean newspaper", "blue"),
                new WasteItem("can", "Clean aluminium can", "blue"),
                new WasteItem("peel", "Banana peel", "green"),
                new WasteItem("battery", "Used battery", "red"));

        Map<String, String> expectedBins = new LinkedHashMap<>();
        for (WasteItem item : waste) {
            expectedBins.put(item.id(), item.binId());
        }
        sortingTask = new SortingTask(expectedBins, 15.0, 7.0, 8.0);

        int[][] wasteTilePositions = { { 4, 4 }, { 16, 4 }, { 4, 16 }, { 16, 16 }, { 5, 12 }, { 15, 8 } };
        for (int i = 0; i < waste.size(); i++) {
            WasteItem item = waste.get(i);
            int lx = wasteTilePositions[i][0];
            int ly = wasteTilePositions[i][1];
            double wX = originX + (lx - ly) * 16.0;
            double wY = originY + (lx + ly) * 8.0;

            Entity entity = FXGL.entityBuilder()
                    .at(wX, wY)
                    .type(EntityType.TRASH)
                    .viewWithBBox("trashbag.png")
                    .with(new CollidableComponent(true))
                    .buildAndAttach();
            generatorSortingWasteEntities.add(entity);
            sortingWasteEntities.put(entity, item);
            InteractionBox pickupBox = new InteractionBox(wX - 8, wY - 8, PICKUP_BOX_WIDTH, PICKUP_BOX_HEIGHT);
            sortingPickupBoxes.put(entity, pickupBox);
        }
        attachSortingCarryViews();
        if (selectedGameMode == GameMode.SINGLE_PLAYER) {
            sortingFeedback = "Pick up waste with [E] / [Space] & deposit directly into matching bin";
        } else {
            sortingFeedback = "P1: Collect waste to INTAKE | P2: Identify & sort into bins";
        }
        generatorStageCompleted = false;
        updateTrashCounter();
        if (selectedGameMode == GameMode.SINGLE_PLAYER) {
            showTemporaryNotice("♻ DISTRICT " + currentDistrict
                    + " — PHASE 3: ECO-SORTING\nPick up waste items & examine bin plaques to sort! [E / Space]");
        }
    }

    private void clearGeneratorStageEntities() {
        for (Entity e : generatorTrashEntities) {
            if (e != null && e.isActive())
                e.removeFromWorld();
        }
        generatorTrashEntities.clear();

        for (Entity e : generatorQuestionEntities) {
            if (e != null && e.isActive())
                e.removeFromWorld();
        }
        generatorQuestionEntities.clear();

        for (Entity e : generatorSortingWasteEntities) {
            if (e != null && e.isActive())
                e.removeFromWorld();
        }
        generatorSortingWasteEntities.clear();

        if (collectorCarriedWasteView != null) {
            collectorCarriedWasteView.setVisible(false);
        }
        if (carriedItemLabel != null) {
            carriedItemLabel.setVisible(false);
        }
        if (sorterCarriedWasteView != null) {
            sorterCarriedWasteView.setVisible(false);
        }
        if (intakeWasteView != null) {
            intakeWasteView.setVisible(false);
        }

        resetSortingTestState();
    }

    private void showTemporaryNotice(String msg) {
        if (isCutsceneActive) {
            return;
        }
        String minimalMessage;
        if (msg.contains("ECO-GRID ONLINE")) {
            minimalMessage = "NEXT AREA OPEN";
        } else if (msg.contains("WORLD RESTORED")) {
            minimalMessage = "NEW AREA OPEN";
        } else if (msg.contains("CLEARED") || msg.contains("RESTORED")) {
            minimalMessage = "AREA CLEARED";
        } else {
            return;
        }
        if (currentNoticeNode != null) {
            FXGL.removeUINode(currentNoticeNode);
            currentNoticeNode = null;
        }

        Label label = new Label(minimalMessage);
        label.setFont(Font.font("Monospaced", FontWeight.BOLD, 15));
        label.setTextFill(Color.web("#ffd700"));
        label.setStyle("-fx-alignment:center;-fx-text-alignment:center;");

        StackPane banner = new StackPane(label);
        banner.setStyle(
                "-fx-background-color:rgba(11,23,14,0.92);-fx-border-color:#39ff14;-fx-border-width:3px;-fx-padding:10px 24px;-fx-effect:dropshadow(three-pass-box, rgba(0,0,0,0.85), 8, 0, 3, 3);");
        banner.setMaxWidth(680);
        banner.setLayoutX(FXGL.getAppWidth() / 2.0 - 340);
        banner.setLayoutY(75);
        banner.setMouseTransparent(true);

        currentNoticeNode = banner;
        FXGL.addUINode(banner);

        FXGL.animationBuilder()
                .duration(Duration.seconds(0.3))
                .fadeIn(banner)
                .buildAndPlay();

        FXGL.runOnce(() -> {
            if (banner == currentNoticeNode) {
                FXGL.animationBuilder()
                        .duration(Duration.seconds(0.4))
                        .fadeOut(banner)
                        .buildAndPlay();
                FXGL.runOnce(() -> {
                    if (banner == currentNoticeNode) {
                        FXGL.removeUINode(banner);
                        currentNoticeNode = null;
                    }
                }, Duration.seconds(0.45));
            }
        }, Duration.seconds(3.5));
    }

    private void spawnFloatingText(double x, double y, String text, Color color) {
        Text popup = new Text(text);
        popup.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        popup.setFill(color);
        popup.setStroke(Color.BLACK);
        popup.setStrokeWidth(1.2);
        popup.setMouseTransparent(true);

        Entity entity = FXGL.entityBuilder()
                .at(x - 20, y)
                .view(popup)
                .zIndex(1000)
                .buildAndAttach();

        FXGL.animationBuilder()
                .duration(Duration.seconds(1.2))
                .translate(entity)
                .from(new Point2D(x - 20, y))
                .to(new Point2D(x - 20, y - 40))
                .buildAndPlay();

        FXGL.animationBuilder()
                .duration(Duration.seconds(1.2))
                .fadeOut(entity)
                .buildAndPlay();

        FXGL.runOnce(() -> {
            if (entity.isActive()) {
                entity.removeFromWorld();
            }
        }, Duration.seconds(1.25));
    }

    private Label worldLabel(String text) {
        Label label = new Label(text);
        label.setStyle(
                "-fx-background-color: rgba(9, 17, 24, 0.90);"
                + "-fx-border-color: #f1d090;"
                + "-fx-border-width: 1.2px;"
                + "-fx-border-radius: 3px;"
                + "-fx-background-radius: 3px;"
                + "-fx-padding: 2px 6px;"
                + "-fx-text-fill: #f1d090;"
                + "-fx-font-family: 'Monospaced';"
                + "-fx-font-size: 9.5px;"
                + "-fx-font-weight: bold;"
                + "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.8), 3, 0, 0, 2);");
        label.setMouseTransparent(true);
        return label;
    }

    private static Node safeTexture(String assetName, double fallbackW, double fallbackH, String fallbackColor) {
        try {
            return FXGL.texture(assetName);
        } catch (Throwable e) {
            Rectangle rect = new Rectangle(fallbackW, fallbackH, Color.web(fallbackColor));
            rect.setStroke(Color.WHITE);
            rect.setStrokeWidth(2);
            return rect;
        }
    }

    private static Node safeQuestionTexture() {
        try {
            return FXGL.texture("question.png");
        } catch (Throwable e) {
            Circle marker = new Circle(18, Color.web("#f1d090"));
            marker.setStroke(Color.web("#fff5bd"));
            marker.setStrokeWidth(3);
            return marker;
        }
    }

    public static class BinInfo {
        public final String title;
        public final String category;
        public final String accepts;
        public final String colorHex;

        public BinInfo(String title, String category, String accepts, String colorHex) {
            this.title = title;
            this.category = category;
            this.accepts = accepts;
            this.colorHex = colorHex;
        }
    }

    public static BinInfo getBinInfo(String binId) {
        String lower = binId == null ? "" : binId.toLowerCase();
        return switch (lower) {
            case "blue" -> new BinInfo("BLUE BIN", "RECYCLABLES", "Paper, Cans, Clean Metals", "#5bc0be");
            case "green" -> new BinInfo("GREEN BIN", "ORGANIC", "Food, Peels, Leaves, Compost", "#39ff14");
            case "red" -> new BinInfo("RED BIN", "HAZARDOUS", "Batteries, E-Waste, Chemicals", "#ff6b6b");
            case "black" -> new BinInfo("BLACK BIN", "GENERAL WASTE", "Wrappers, Foam, Non-Recyclable", "#e0e0e0");
            default -> new BinInfo("BIN", "DISPOSAL", "Miscellaneous Items", "#ffffff");
        };
    }

    static VBox createBinPlaque(String binId) {
        BinInfo info = getBinInfo(binId);

        String lower = binId == null ? "" : binId.toLowerCase().trim();
        boolean isUnderneath = lower.equals("red") || lower.equals("blue")
                || lower.contains("hazard") || lower.contains("recyc");

        VBox plaque = new VBox(2);
        plaque.setAlignment(Pos.CENTER);
        plaque.setStyle(
                "-fx-background-color: rgba(9, 17, 24, 0.92);"
                + "-fx-border-color: " + info.colorHex + ";"
                + "-fx-border-width: 1.8px;"
                + "-fx-border-radius: 4px;"
                + "-fx-background-radius: 4px;"
                + "-fx-padding: 3px 8px;"
                + "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.85), 4, 0, 0, 2);");

        String arrow = isUnderneath ? "▲ " : "▼ ";
        Label catLabel = new Label(arrow + info.category + " " + arrow);
        catLabel.setStyle(
                "-fx-text-fill: " + info.colorHex + ";"
                + "-fx-font-family: 'Monospaced';"
                + "-fx-font-size: 9px;"
                + "-fx-font-weight: bold;"
                + "-fx-alignment: center;");

        plaque.getChildren().add(catLabel);
        plaque.setMouseTransparent(true);
        plaque.setMinWidth(100);
        plaque.setMaxWidth(114);

        // Center plaque horizontally relative to 72px can: (72 - 104) / 2 ~= -16
        plaque.setTranslateX(-16);
        // Hazardous & Recyclable underneath (Y=62), Organic & General Waste above (Y=-32)
        plaque.setTranslateY(isUnderneath ? 62 : -32);
        return plaque;
    }

    private void createSortingBin(String binId, double x, double y, String color) {
        Node binView;
        String lowerId = binId == null ? "" : binId.toLowerCase();
        switch (lowerId) {
            case "blue" -> binView = safeTexture("trashcan_blue.png", 72, 58, color);
            case "green" -> binView = safeTexture("trashcan_green.png", 72, 58, color);
            case "red" -> binView = safeTexture("trashcan_red.png", 72, 58, color);
            case "black" -> binView = safeTexture("trashcan_black.png", 72, 58, color);
            default -> {
                Rectangle bin = new Rectangle(72, 58, Color.web(color));
                bin.setStroke(Color.WHITE);
                bin.setStrokeWidth(2);
                binView = bin;
            }
        }
        Entity entity = FXGL.entityBuilder().at(x, y).type(EntityType.QUESTION_POINT).view(binView).buildAndAttach();
        VBox plaque = createBinPlaque(binId);
        entity.getViewComponent().addChild(plaque);
        double boxOffsetX = (72 - BIN_BOX_WIDTH) / 2.0;
        double boxOffsetY = (58 - BIN_BOX_HEIGHT) / 2.0;
        sortingBins.put(entity, binId);
        sortingBinBoxes.put(entity, new InteractionBox(x + boxOffsetX, y + boxOffsetY, BIN_BOX_WIDTH, BIN_BOX_HEIGHT));
    }

    private void tryCollectTrashP1() {
        if (isCutsceneActive) {
            return;
        }

        if (isInfiniteGameMode()) {
            if (generatorStage == GeneratorStage.TRASH_COLLECTION) {
                for (Entity trash : List.copyOf(generatorTrashEntities)) {
                    if (trash != null && trash.isActive() && playerEntity != null
                            && playerEntity.distance(trash) < 48.0) {
                        trash.removeFromWorld();
                        generatorTrashEntities.remove(trash);
                        generatorTrashCollected++;
                        collectedTrash++;
                        ecoScore += 50;
                        AudioManager.playTrashPickup();
                        if (infiniteMapManager != null) {
                            infiniteMapManager.onBottleCollected(generatorTrashCollected, GENERATOR_TARGET_TRASH);
                        }
                        if (timer != null)
                            timer.applyDelta(10.0);
                        spawnFloatingText(playerEntity.getX(), playerEntity.getY() - 16, "+50",
                                Color.web("#39ff14"));
                        updateTrashCounter();
                        if (generatorTrashCollected >= GENERATOR_TARGET_TRASH) {
                            completeCurrentChunkTask();
                            AudioManager.playCorrectAnswer();
                            showTemporaryNotice("STAGE 1 CLEARED!\nSpreading world restoration wave...");
                            if (infiniteMapManager != null) {
                                infiniteMapManager.startSpreadingRestoration(() -> {
                                    showTemporaryNotice(
                                            "WORLD RESTORED! Render distance expanded.\nWalk into next sector for Eco-Grid Challenge.");
                                });
                            }
                        }
                        break;
                    }
                }
            } else if (generatorStage == GeneratorStage.SORTING) {
                if (selectedGameMode == GameMode.SINGLE_PLAYER) {
                    interactWithSoloSorting();
                } else {
                    interactWithSortingP1();
                }
            }
            return;
        }
        if (selectedGameMode == GameMode.LAN_JOIN) {
            clientInteract = true;
            sendClientInputPacket();
            clientInteract = false;
            return;
        }
        if (selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
            if (demoStage == DemoStage.COLLECTION) {
                interactWithDemoCollection(playerEntity);
            } else if (demoStage == DemoStage.SORTING) {
                interactWithSortingP1();
            }
            return;
        }
        if (selectedGameMode == GameMode.SORTING_TEST) {
            interactWithSortingP1();
            return;
        }
        collectNearbyTrashForPlayer(playerEntity);
    }

    private void tryCollectTrashP2() {
        if (isCutsceneActive) {
            return;
        }

        if (isInfiniteCoopMode()) {
            if (generatorStage == GeneratorStage.TRASH_COLLECTION) {
                for (Entity trash : List.copyOf(generatorTrashEntities)) {
                    if (trash != null && trash.isActive() && playerEntity2 != null
                            && playerEntity2.distance(trash) < 48.0) {
                        trash.removeFromWorld();
                        generatorTrashEntities.remove(trash);
                        generatorTrashCollected++;
                        collectedTrash++;
                        ecoScore += 50;
                        AudioManager.playTrashPickup();
                        if (infiniteMapManager != null) {
                            infiniteMapManager.onBottleCollected(generatorTrashCollected, GENERATOR_TARGET_TRASH);
                        }
                        if (timer != null)
                            timer.applyDelta(10.0);
                        spawnFloatingText(playerEntity2.getX(), playerEntity2.getY() - 16, "+50",
                                Color.web("#d7e77f"));
                        updateTrashCounter();
                        if (generatorTrashCollected >= GENERATOR_TARGET_TRASH) {
                            completeCurrentChunkTask();
                            AudioManager.playCorrectAnswer();
                            showTemporaryNotice("STAGE 1 CLEARED!\nSpreading world restoration wave...");
                            if (infiniteMapManager != null) {
                                infiniteMapManager.startSpreadingRestoration(() -> {
                                    showTemporaryNotice(
                                            "WORLD RESTORED! Render distance expanded.\nWalk into next sector for Eco-Grid Challenge.");
                                });
                            }
                        }
                        break;
                    }
                }
            } else if (generatorStage == GeneratorStage.SORTING) {
                interactWithSortingP2();
                if (sortingTask != null && sortingTask.isComplete()) {
                    generatorSortedCount = GENERATOR_TARGET_SORTING;
                    infiniteMapManager.unlockCurrentRegion();
                    completeCurrentChunkTask();
                    ecoScore += 500;
                    if (timer != null)
                        timer.applyDelta(30.0);
                    showTemporaryNotice("🎉 DISTRICT " + currentDistrict
                            + " RESTORED! (+30s Bonus, +500 pts)\nWalk through gateway into District "
                            + (currentDistrict + 1) + "!");
                }
            }
            return;
        }
        if (selectedGameMode == GameMode.LAN_JOIN) {
            clientInteract = true;
            sendClientInputPacket();
            clientInteract = false;
            return;
        }
        if (selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
            if (demoStage == DemoStage.COLLECTION) {
                interactWithDemoCollection(playerEntity2 != null ? playerEntity2 : playerEntity);
            } else if (demoStage == DemoStage.SORTING) {
                interactWithSortingP2();
            }
            return;
        }
        if (selectedGameMode == GameMode.SORTING_TEST) {
            interactWithSortingP2();
            return;
        }
        collectNearbyTrashForPlayer(playerEntity2 != null ? playerEntity2 : playerEntity);
    }

    private void interactWithDemoCollection(Entity player) {
        if (player == null || demoStage != DemoStage.COLLECTION)
            return;
        for (Map.Entry<Entity, String> entry : List.copyOf(demoCollectionItems.entrySet())) {
            InteractionBox pickup = new InteractionBox(
                    entry.getKey().getX() - 16, entry.getKey().getY() - 16,
                    PICKUP_BOX_WIDTH + 32, PICKUP_BOX_HEIGHT + 32);
            if (pickup.intersectsPlayer(player) || safelyCollides(player, entry.getKey())) {
                TaskResult result = demoCollectionTask.collect(entry.getValue());
                TaskTimer.apply(timer, result);
                entry.getKey().removeFromWorld();
                demoCollectionItems.remove(entry.getKey());
                AudioManager.playTrashPickup();
                if (result.completedNow()) {
                    activateDemoQuestionStage();
                }
                return;
            }
        }
    }

    private void collectNearbyTrashForPlayer(Entity player) {
        if (player == null)
            return;
        for (Map.Entry<Integer, Entity> entry : List.copyOf(trashEntities.entrySet())) {
            int idx = entry.getKey();
            Entity trash = entry.getValue();
            if (trash != null && trash.isActive() && safelyCollides(player, trash)) {
                trash.removeFromWorld();
                trashMask &= ~(1 << idx);
                collectedTrash++;
                AudioManager.playTrashPickup();
                if (timer != null) {
                    timer.applyDelta(10.0);
                }
                updateTrashCounter();
                checkLevelCompletion();
                break;
            }
        }
    }

    private void interactWithSoloSorting() {
        if (playerEntity == null)
            return;

        // 1. If carrying waste: deposit into matching bin
        if (outsideCarriedWaste != null) {
            for (Map.Entry<Entity, String> bin : sortingBins.entrySet()) {
                InteractionBox binBox = sortingBinBoxes.get(bin.getKey());
                if ((binBox != null && binBox.intersectsPlayer(playerEntity))
                        || playerEntity.distance(bin.getKey()) < 56.0) {
                    String binId = bin.getValue();
                    if (binId.equalsIgnoreCase(outsideCarriedWaste.binId())) {
                        TaskResult result = sortingTask.sort(outsideCarriedWaste.id(), binId);
                        TaskTimer.apply(timer, result);
                        ecoScore += 100;
                        generatorSortedCount++;
                        updateTrashCounter();
                        AudioManager.playCorrectAnswer();
                        spawnFloatingText(playerEntity.getX(), playerEntity.getY() - 20,
                                "+100", Color.web("#39ff14"));
                        BinInfo info = getBinInfo(binId);
                        sortingFeedback = "Correct: " + outsideCarriedWaste.name() + " -> " + info.category
                                + " (+8s)!";
                        showTemporaryNotice("CORRECT! " + outsideCarriedWaste.name() + "\nSorted into "
                                + info.category + " (+8s)");
                        outsideCarriedWaste = null;
                        if (collectorCarriedWasteView != null) {
                            collectorCarriedWasteView.setVisible(false);
                        }
                        if (carriedItemLabel != null) {
                            carriedItemLabel.setVisible(false);
                        }

                        if (sortingTask.isComplete()) {
                            generatorSortedCount = GENERATOR_TARGET_SORTING;
                            infiniteMapManager.unlockCurrentRegion();
                            completeCurrentChunkTask();
                            ecoScore += 500;
                            if (timer != null)
                                timer.applyDelta(30.0);
                            AudioManager.playCorrectAnswer();
                            showTemporaryNotice("🎉 DISTRICT " + currentDistrict
                                    + " RESTORED! (+30s Bonus, +500 pts)\nWalk through gateway into District "
                                    + (currentDistrict + 1) + "!");
                        }
                    } else {
                        if (timer != null)
                            timer.applyDelta(-6.0);
                        AudioManager.playWrongAnswer();
                        spawnFloatingText(playerEntity.getX(), playerEntity.getY() - 20, "-6s",
                                Color.web("#ff3860"));
                        showTemporaryNotice("WRONG BIN! (-6s)\n" + outsideCarriedWaste.name() + " does not belong here!\nRead bin accepted items.");
                        sortingFeedback = "Wrong bin! " + outsideCarriedWaste.name() + " rejected (-6s)";
                    }
                    return;
                }
            }
            showTemporaryNotice("Carrying: " + outsideCarriedWaste.name() + "\nWalk to the matching waste bin and press [E / Space]");
            return;
        }

        // 2. Empty-handed: pick up nearby waste
        for (Map.Entry<Entity, WasteItem> entry : List.copyOf(sortingWasteEntities.entrySet())) {
            InteractionBox pickupBox = sortingPickupBoxes.get(entry.getKey());
            if ((pickupBox != null && pickupBox.intersectsPlayer(playerEntity))
                    || playerEntity.distance(entry.getKey()) < 48.0) {
                outsideCarriedWaste = entry.getValue();
                entry.getKey().removeFromWorld();
                sortingWasteEntities.remove(entry.getKey());
                sortingPickupBoxes.remove(entry.getKey());
                AudioManager.playTrashPickup();
                if (collectorCarriedWasteView != null) {
                    collectorCarriedWasteView.setVisible(true);
                }
                if (carriedItemLabel != null) {
                    carriedItemLabel.setText(outsideCarriedWaste.name());
                    carriedItemLabel.setVisible(true);
                }
                sortingFeedback = "Carrying: " + outsideCarriedWaste.name() + " — Check bin labels to sort";
                showTemporaryNotice("PICKED UP: " + outsideCarriedWaste.name() + "\nExamine the bins and deposit into the matching category!");
                return;
            }
        }
    }

    private void interactWithSortingP1() {
        if (playerEntity == null)
            return;
        if (outsideCarriedWaste != null) {
            // Player 1 is carrying waste: must be physically at the intake point to deliver
            if (sortingIntakeBox.intersectsPlayer(playerEntity)) {
                sortingIntake.addLast(outsideCarriedWaste);
                outsideCarriedWaste = null;
                if (collectorCarriedWasteView != null) {
                    collectorCarriedWasteView.setVisible(false);
                }
                if (intakeWasteView != null) {
                    intakeWasteView.setVisible(true);
                }
                AudioManager.playTrashPickup();
                sortingFeedback = "Waste delivered. P2: press / at the intake to identify it";
                showTemporaryNotice("Delivered to INTAKE!\nP2: press [/] at INTAKE to retrieve & identify");
            } else {
                sortingFeedback = "Carry waste to INTAKE at center before pressing [E]";
                showTemporaryNotice("Carry waste to INTAKE at center before pressing [E]");
            }
            return;
        }
        // Player 1 is empty-handed: physically pick up nearby waste bag
        for (Map.Entry<Entity, WasteItem> entry : List.copyOf(sortingWasteEntities.entrySet())) {
            if (sortingPickupBoxes.get(entry.getKey()).intersectsPlayer(playerEntity)) {
                outsideCarriedWaste = entry.getValue();
                entry.getKey().removeFromWorld();
                sortingWasteEntities.remove(entry.getKey());
                sortingPickupBoxes.remove(entry.getKey());
                if (collectorCarriedWasteView != null) {
                    collectorCarriedWasteView.setVisible(true);
                }
                AudioManager.playTrashPickup();
                sortingFeedback = "P1 picked up: " + outsideCarriedWaste.name() + " -> Carry to INTAKE";
                showTemporaryNotice(
                        "P1 picked up: " + outsideCarriedWaste.name() + "\nPhysically carry to INTAKE at center");
                return;
            }
        }
    }

    private void interactWithSortingP2() {
        if (playerEntity2 == null)
            return;
        if (insideCarriedWaste != null) {
            for (Map.Entry<Entity, String> bin : sortingBins.entrySet()) {
                if (sortingBinBoxes.get(bin.getKey()).intersectsPlayer(playerEntity2)) {
                    TaskResult result = sortingTask.sort(insideCarriedWaste.id(), bin.getValue());
                    TaskTimer.apply(timer, result);
                    boolean retryableWrongBin = result.status() == pkg.restoration.tasks.TaskStatus.REJECTED
                            && "Wrong bin".equals(result.message());
                    if (!retryableWrongBin) {
                        AudioManager.playCorrectAnswer();
                        spawnFloatingText(playerEntity2.getX(), playerEntity2.getY() - 20,
                                "+100", Color.web("#39ff14"));
                        BinInfo info = getBinInfo(bin.getValue());
                        showTemporaryNotice("CORRECT! " + insideCarriedWaste.name() + "\nSorted into " + info.category + " (+8s)");
                        sortingFeedback = "Correct: " + insideCarriedWaste.name() + " -> " + info.category + " (+8s)!";
                        clearInsideCarriedWaste();
                    } else {
                        AudioManager.playWrongAnswer();
                        spawnFloatingText(playerEntity2.getX(), playerEntity2.getY() - 20, "-6s", Color.web("#ff3860"));
                        showTemporaryNotice("WRONG BIN! (-6s)\n" + insideCarriedWaste.name() + " does not belong here!\nRead bin accepted items.");
                        sortingFeedback = "Wrong bin! " + insideCarriedWaste.name() + " rejected (-6s)";
                    }
                    if (isInfiniteCoopMode() && sortingTask.isComplete()) {
                        generatorSortedCount = GENERATOR_TARGET_SORTING;
                        infiniteMapManager.unlockCurrentRegion();
                        completeCurrentChunkTask();
                        ecoScore += 500;
                        if (timer != null)
                            timer.applyDelta(30.0);
                        AudioManager.playCorrectAnswer();
                        showTemporaryNotice("🎉 DISTRICT " + currentDistrict
                                + " RESTORED! (+30s Bonus, +500 pts)\nWalk through gateway into District "
                                + (currentDistrict + 1) + "!");
                    }
                    if (selectedGameMode == GameMode.SEQUENTIAL_DEMO && sortingTask.isComplete() && !gameEnded) {
                        gameEnded = true;
                        demoStage = DemoStage.COMPLETE;
                        sortingFeedback = "Demo complete — all three stages passed";
                        if (timer != null) {
                            timer.applyDelta(120.0);
                        }
                        showEndGameOverlay("WORLD RESTORED",
                                "All 3 stages completed successfully!\nAdded +120 seconds bonus time.", true);
                    }
                    return;
                }
            }
        } else if (!sortingIntake.isEmpty() && sortingIntakeBox.intersectsPlayer(playerEntity2)) {
            insideCarriedWaste = sortingIntake.removeFirst();
            if (intakeWasteView != null) {
                intakeWasteView.setVisible(!sortingIntake.isEmpty());
            }
            if (sorterCarriedWasteView != null) {
                sorterCarriedWasteView.setVisible(true);
            }
            AudioManager.playTrashPickup();
            sortingFeedback = "P2 retrieved: " + insideCarriedWaste.name() + " — Check bin labels to sort";
            showTemporaryNotice("RETRIEVED: " + insideCarriedWaste.name() + "\nExamine the bins and sort into the matching category!");
        }
    }

    private void clearInsideCarriedWaste() {
        insideCarriedWaste = null;
        if (sorterCarriedWasteView != null) {
            sorterCarriedWasteView.setVisible(false);
        }
    }

    private List<Entity> getActiveQuestionEntities() {
        List<Entity> list = new java.util.ArrayList<>();
        for (Entity e : FXGL.getGameWorld().getEntitiesByType(EntityType.QUESTION_POINT)) {
            if (e.isActive() && e.getProperties().exists("question")) {
                list.add(e);
            }
        }
        for (Entity e : FXGL.getGameWorld().getEntitiesByType(EntityType.DEMO_QUESTION_POINT)) {
            if (e.isActive() && e.getProperties().exists("question")) {
                list.add(e);
            }
        }
        return list;
    }

    private Entity findNearestQuestionEntity() {
        List<Entity> allQ = getActiveQuestionEntities();
        Entity nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (Entity qEntity : allQ) {
            if (!qEntity.isVisible()) {
                continue;
            }
            double dist1 = playerEntity != null ? distanceBetween(playerEntity, qEntity) : Double.MAX_VALUE;
            double dist2 = playerEntity2 != null ? distanceBetween(playerEntity2, qEntity) : Double.MAX_VALUE;
            double minDist = Math.min(dist1, dist2);
            if (minDist <= 85 && minDist < minDistance) {
                minDistance = minDist;
                nearest = qEntity;
            }
        }
        return nearest;
    }

    private double distanceBetween(Entity a, Entity b) {
        return Math.hypot(a.getCenter().getX() - b.getCenter().getX(),
                a.getCenter().getY() - b.getCenter().getY());
    }

    private void refreshQuestionPanelForCurrent() {
        if (questionLabel == null || currentActiveQuestion == null) {
            return;
        }
        StringBuilder text = new StringBuilder(currentActiveQuestion.prompt());
        for (int i = 0; i < Math.min(3, currentActiveQuestion.choices().size()); i++) {
            text.append("\n").append(i + 1).append(". ").append(currentActiveQuestion.choices().get(i));
        }
        questionLabel.setText(text.toString());
        questionFeedbackLabel.setText("Press 1, 2, or 3 to answer");
    }

    private void answerTestQuestion(int choiceIndex) {
        if (isInfiniteGameMode() && generatorStage == GeneratorStage.QUESTION) {
            if (currentActiveQuestionEntity == null || currentActiveQuestion == null || questionAnswerLocked)
                return;
            if (choiceIndex >= currentActiveQuestion.choices().size())
                return;
            QuestionResult result = currentActiveQuestion.answer(choiceIndex);
            questionAnswerLocked = true;
            if (result.quality() == pkg.restoration.questions.AnswerQuality.WRONG) {
                AudioManager.playWrongAnswer();
            } else {
                AudioManager.playCorrectAnswer();
            }
            double appliedDelta = TaskTimer.apply(timer, result.asTaskResult());
            generatorQuestionsAnswered++;
            ecoScore += (result.quality() == pkg.restoration.questions.AnswerQuality.BEST) ? 100
                    : ((result.quality() == pkg.restoration.questions.AnswerQuality.SECOND_BEST) ? 30 : 0);
            updateTrashCounter();

            Color fbColor = (result.quality() == pkg.restoration.questions.AnswerQuality.BEST) ? Color.web("#39ff14")
                    : ((result.quality() == pkg.restoration.questions.AnswerQuality.SECOND_BEST) ? Color.web("#ffd700")
                            : Color.web("#ff3860"));
            if (playerEntity != null) {
                int points = result.quality() == pkg.restoration.questions.AnswerQuality.BEST ? 100
                        : result.quality() == pkg.restoration.questions.AnswerQuality.SECOND_BEST ? 30 : 0;
                spawnFloatingText(playerEntity.getX(), playerEntity.getY() - 20,
                        points > 0 ? "+" + points : String.format("%+.0fs", appliedDelta), fbColor);
            }

            questionFeedbackLabel.setText(
                    result.quality() + ": " + result.feedback() + String.format(" (%+.0f seconds)", appliedDelta));
            Entity targetEntity = currentActiveQuestionEntity;
            currentActiveQuestionEntity = null;
            currentActiveQuestion = null;

            FXGL.runOnce(() -> {
                if (targetEntity != null && targetEntity.isActive()) {
                    targetEntity.removeFromWorld();
                }
                if (questionPanel != null)
                    questionPanel.setVisible(false);
                questionAnswerLocked = false;

                if (generatorQuestionsAnswered >= GENERATOR_TARGET_QUESTIONS) {
                    infiniteMapManager.unlockCurrentRegion();
                    completeCurrentChunkTask();
                    showTemporaryNotice("💡 ECO-GRID ONLINE!\nProceed into next sector for Eco-Sorting.");
                }
            }, Duration.seconds(1.2));
            return;
        }

        boolean questionModeActive = selectedGameMode == GameMode.QUESTION_TEST
                || (selectedGameMode == GameMode.SEQUENTIAL_DEMO && demoStage == DemoStage.QUESTION);
        if (!questionModeActive || currentActiveQuestionEntity == null || currentActiveQuestion == null
                || questionAnswerLocked) {
            return;
        }
        if (choiceIndex >= currentActiveQuestion.choices().size()) {
            return;
        }
        QuestionResult result = currentActiveQuestion.answer(choiceIndex);
        questionAnswerLocked = true;
        if (result.quality() == pkg.restoration.questions.AnswerQuality.WRONG) {
            AudioManager.playWrongAnswer();
        } else {
            AudioManager.playCorrectAnswer();
        }
        double appliedDelta = TaskTimer.apply(timer, result.asTaskResult());
        testQuestionIndex++;
        questionFeedbackLabel.setText(result.quality() + ": " + result.feedback()
                + String.format(" (%+.0f seconds)", appliedDelta));

        Entity targetEntity = currentActiveQuestionEntity;
        currentActiveQuestionEntity = null;
        currentActiveQuestion = null;

        FXGL.runOnce(() -> {
            if (targetEntity != null && targetEntity.isActive()) {
                if (selectedGameMode == GameMode.QUESTION_TEST
                        && testQuestionIndex < testQuestions.size()) {
                    targetEntity.setProperty("question", testQuestions.get(testQuestionIndex));
                } else {
                    targetEntity.removeFromWorld();
                }
            }
            if (questionPanel != null) {
                questionPanel.setVisible(false);
            }
            questionAnswerLocked = false;

            if (selectedGameMode == GameMode.SEQUENTIAL_DEMO
                    && testQuestionIndex >= testQuestions.size()) {
                activateDemoSortingStage();
            }
        }, Duration.seconds(1.2));
    }

    private void spawnRandomTrash() {
        collectedTrash = 0;
        trashMask = (1 << TOTAL_TRASH) - 1;
        trashEntities.clear();
        double[][] spawnPositions = {
                { 200, 180 },
                { 320, 240 },
                { 450, 180 },
                { 150, 300 },
                { 520, 280 },
                { 280, 350 },
                { 400, 320 },
                { 600, 220 }
        };

        for (int i = 0; i < TOTAL_TRASH; i++) {
            double x = (i < spawnPositions.length) ? spawnPositions[i][0] : 150 + Math.random() * 450;
            double y = (i < spawnPositions.length) ? spawnPositions[i][1] : 150 + Math.random() * 200;
            Entity trash = FXGL.spawn("trash", new SpawnData(x, y));
            trash.setRotation(FXGL.random(0, 360));
            trash.setProperty("trashIndex", i);
            trashEntities.put(i, trash);
        }
    }

    @Override
    protected void initPhysics() {
        // Trash is collected actively by pressing E
    }

    private void setupViewports() {
        Viewport vp = FXGL.getGameScene().getViewport();
        vp.setLazy(true);

        if ((selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN
                || selectedGameMode == GameMode.SORTING_TEST
                || selectedGameMode == GameMode.SEQUENTIAL_DEMO
                || selectedGameMode == GameMode.MAP_GENERATOR) && playerEntity != null && playerEntity2 != null) {
            // Shared Screen Local Co-Op: Dynamic zoom and smooth dual-player midpoint tracking
            unbindEntityTracking(vp);
            currentCoopZoom = 2.0;
            currentCamX = (playerEntity.getX() + playerEntity2.getX()) / 2.0 + 8.0;
            currentCamY = (playerEntity.getY() + playerEntity2.getY()) / 2.0 + 12.0;
            vp.setZoom(currentCoopZoom);
            double halfW = FXGL.getAppWidth() / (2.0 * currentCoopZoom);
            double halfH = FXGL.getAppHeight() / (2.0 * currentCoopZoom);
            vp.setX(currentCamX - halfW);
            vp.setY(currentCamY - halfH);
            updateCoopCamera(0.016);
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

    private void unbindEntityTracking(Viewport vp) {
        vp.unbind();
        try {
            java.lang.reflect.Field fx = Viewport.class.getDeclaredField("boundX");
            fx.setAccessible(true);
            fx.set(vp, null);
            java.lang.reflect.Field fy = Viewport.class.getDeclaredField("boundY");
            fy.setAccessible(true);
            fy.set(vp, null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void attachPlayerBadge(Entity player, String text, Color color) {
        if (player == null) return;
        Text badge = new Text(text);
        badge.setFont(Font.font("Monospaced", FontWeight.BOLD, 11));
        badge.setFill(color);
        badge.setStroke(Color.BLACK);
        badge.setStrokeWidth(1.0);
        badge.setTranslateX(text.length() > 1 ? 0 : 4);
        badge.setTranslateY(-6);
        badge.setMouseTransparent(true);
        player.getViewComponent().addChild(badge);
    }

    private void updateCoopCamera(double tpf) {
        if (playerEntity != null && playerEntity2 != null) {
            double p1X = playerEntity.getX() + 8.0;
            double p1Y = playerEntity.getY() + 12.0;
            double p2X = playerEntity2.getX() + 8.0;
            double p2Y = playerEntity2.getY() + 12.0;

            double midX = (p1X + p2X) / 2.0;
            double midY = (p1Y + p2Y) / 2.0;

            double deltaX = Math.abs(p1X - p2X);
            double deltaY = Math.abs(p1Y - p2Y);

            // Dynamic zoom framing both players within screen bounds with comfortable padding
            double zoomX = 1280.0 / Math.max(340.0, deltaX + 220.0);
            double zoomY = 720.0 / Math.max(240.0, deltaY + 180.0);
            double targetZoom = Math.max(1.35, Math.min(2.35, Math.min(zoomX, zoomY)));

            // Smooth interpolation
            double zoomLerp = Math.min(1.0, tpf * 5.0);
            currentCoopZoom += (targetZoom - currentCoopZoom) * zoomLerp;

            double camLerp = Math.min(1.0, tpf * 6.0);
            currentCamX += (midX - currentCamX) * camLerp;
            currentCamY += (midY - currentCamY) * camLerp;

            Viewport viewport = FXGL.getGameScene().getViewport();
            viewport.setZoom(currentCoopZoom);
            double halfW = FXGL.getAppWidth() / (2.0 * currentCoopZoom);
            double halfH = FXGL.getAppHeight() / (2.0 * currentCoopZoom);
            viewport.setX(currentCamX - halfW);
            viewport.setY(currentCamY - halfH);
        }
    }

    private void enforcePlayerTether() {
        if (playerEntity == null || playerEntity2 == null) return;
        double dist = playerEntity.distance(playerEntity2);
        if (dist > MAX_TETHER_DISTANCE) {
            double excess = dist - MAX_TETHER_DISTANCE;
            double angle = Math.atan2(playerEntity.getY() - playerEntity2.getY(), playerEntity.getX() - playerEntity2.getX());
            boolean p1Moving = playerComponent != null && playerComponent.isMoving();
            boolean p2Moving = playerComponent2 != null && playerComponent2.isMoving();

            if (p1Moving && !p2Moving) {
                playerEntity.translateX(-Math.cos(angle) * excess);
                playerEntity.translateY(-Math.sin(angle) * excess);
            } else if (p2Moving && !p1Moving) {
                playerEntity2.translateX(Math.cos(angle) * excess);
                playerEntity2.translateY(Math.sin(angle) * excess);
            } else {
                double halfCorrX = Math.cos(angle) * (excess / 2.0);
                double halfCorrY = Math.sin(angle) * (excess / 2.0);
                playerEntity.translateX(-halfCorrX);
                playerEntity.translateY(-halfCorrY);
                playerEntity2.translateX(halfCorrX);
                playerEntity2.translateY(halfCorrY);
            }
        }
    }

    private void setupNetworking() {
        if (netManager != null) {
            netManager.stop();
            netManager = null;
        }
        if (selectedGameMode == GameMode.LAN_HOST) {
            netManager = new NetworkManager();
            netManager.startHost(NetworkManager.DEFAULT_PORT, input -> {
                javafx.application.Platform.runLater(() -> {
                    if (playerComponent2 != null) {
                        playerComponent2.setUp(input.up);
                        playerComponent2.setDown(input.down);
                        playerComponent2.setLeft(input.left);
                        playerComponent2.setRight(input.right);
                    }
                    if (input.interact) {
                        tryCollectTrashP2();
                    }
                });
            });
        } else if (selectedGameMode == GameMode.LAN_JOIN) {
            netManager = new NetworkManager();
            netManager.startClient(targetHostIp, NetworkManager.DEFAULT_PORT, state -> {
                javafx.application.Platform.runLater(() -> applyRemoteGameState(state));
            });
        }
    }

    private void sendClientInputPacket() {
        if (selectedGameMode == GameMode.LAN_JOIN && netManager != null) {
            netManager.sendInputPacket(new InputPacket(clientUp, clientDown, clientLeft, clientRight, clientInteract));
        }
    }

    private void applyRemoteGameState(GameStatePacket packet) {
        if (playerEntity != null) {
            playerEntity.setPosition(packet.p1X, packet.p1Y);
            if (playerComponent != null) {
                playerComponent.setRemoteState(
                        Direction.values()[Math.min(packet.p1DirIndex, Direction.values().length - 1)],
                        packet.p1Moving);
            }
        }
        if (playerEntity2 != null) {
            playerEntity2.setPosition(packet.p2X, packet.p2Y);
            if (playerComponent2 != null) {
                playerComponent2.setRemoteState(
                        Direction.values()[Math.min(packet.p2DirIndex, Direction.values().length - 1)],
                        packet.p2Moving);
            }
        }
        if (timer != null) {
            timer.setCurrentSeconds(packet.remainingTime);
        }
        this.collectedTrash = packet.collectedTrash;
        updateTrashCounter();
        checkLevelCompletion();

        for (Map.Entry<Integer, Entity> entry : trashEntities.entrySet()) {
            int idx = entry.getKey();
            boolean isActiveOnHost = (packet.trashMask & (1 << idx)) != 0;
            if (!isActiveOnHost && entry.getValue() != null && entry.getValue().isActive()) {
                entry.getValue().removeFromWorld();
            }
        }
    }

    @Override
    protected void initUI() {
        final double timerBarWidth = 220;
        Rectangle timerBarTrack = new Rectangle(timerBarWidth, 12, Color.web("#15251b"));
        timerBarTrack.setStroke(Color.web("#39ff14"));
        timerBarTrack.setStrokeWidth(1.5);
        timerBarFill = new Rectangle(timerBarWidth - 4, 8, Color.web("#39ff14"));
        StackPane timerBar = new StackPane(timerBarTrack, timerBarFill);
        timerBar.setAlignment(Pos.CENTER_LEFT);
        timerBar.setTranslateX(16);
        timerBar.setTranslateY(18);
        timerBar.setMouseTransparent(true);
        addHudNode(timerBar);

        // Retained for mode logic, but intentionally not displayed in the minimal HUD.
        modeStatusText = new Text();
        boolean hasScore = selectedGameMode == GameMode.SINGLE_PLAYER
                || selectedGameMode == GameMode.MAP_GENERATOR
                || selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN;

        switch (selectedGameMode) {
            case QUESTION_TEST ->
                modeStatusText.setText("Question Test — Answered: 0 / " + testQuestions.size());
            case SORTING_TEST ->
                modeStatusText.setText("Sorting Test — P1: WASD + E, P2: Arrows + /");
            case SEQUENTIAL_DEMO ->
                modeStatusText.setText("Sequential Demo — P1: WASD, P2: arrows, E: interact");
            case MAP_GENERATOR, LOCAL_COOP_SPLITSCREEN -> {
                modeStatusText.setText("District 1: Phase 1 — Co-op Cleanup");
            }
            case LAN_HOST ->
                modeStatusText.setText("LAN Co-Op: Hosting on Port " + NetworkManager.DEFAULT_PORT);
            case LAN_JOIN ->
                modeStatusText.setText("LAN Co-Op: Connected to " + targetHostIp);
            case SINGLE_PLAYER -> {
                modeStatusText.setText("District 1: Phase 1 — Eco-Cleanup");
            }
            default ->
                modeStatusText.setText("Single Player Mode");
        }

        if (hasScore) {
            scoreText = new Text("★ 0");
            scoreText.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
            scoreText.setFill(Color.web("#5bc0be"));

            HBox topBarRightCard = new HBox(scoreText);
            topBarRightCard.setAlignment(Pos.CENTER_RIGHT);
            topBarRightCard.setStyle(
                    "-fx-background-color: rgba(9, 17, 24, 0.88);"
                    + "-fx-border-color: #00f0ff;"
                    + "-fx-border-width: 2px;"
                    + "-fx-border-radius: 5px;"
                    + "-fx-background-radius: 5px;"
                    + "-fx-padding: 8px 14px;"
                    + "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.85), 6, 0, 0, 3);");
            topBarRightCard.setTranslateX(FXGL.getAppWidth() - 112);
            topBarRightCard.setTranslateY(14);
            topBarRightCard.setMouseTransparent(true);
            addHudNode(topBarRightCard);
        }

        if (selectedGameMode != GameMode.SORTING_TEST
                && selectedGameMode != GameMode.SEQUENTIAL_DEMO) {

            Pane iconOverlayPane = new Pane();
            iconOverlayPane.setPrefSize(44, 32);

            ImageView bottleIv = safeImageView("/assets/textures/bottle.png", 28, 28);
            if (bottleIv == null) {
                bottleIv = safeImageView("/assets/ui/menu/bottle.png", 28, 28);
            }
            if (bottleIv != null) {
                bottleIv.setLayoutX(0);
                bottleIv.setLayoutY(2);
                bottleIv.setRotate(-12.0);
                iconOverlayPane.getChildren().add(bottleIv);
            }

            ImageView trashIv = safeImageView("/assets/textures/trash.png", 28, 28);
            if (trashIv == null) {
                trashIv = safeImageView("/assets/textures/trashbag.png", 28, 28);
            }
            if (trashIv != null) {
                trashIv.setLayoutX(14);
                trashIv.setLayoutY(4);
                trashIv.setRotate(12.0);
                iconOverlayPane.getChildren().add(trashIv);
            }

            trashCounterText = new Text("0/10");
            trashCounterText.setFont(Font.font("Monospaced", FontWeight.BOLD, 22));
            trashCounterText.setFill(Color.web("#39ff14"));

            HBox trashHUDBox = new HBox(8, iconOverlayPane, trashCounterText);
            trashHUDBox.setAlignment(Pos.CENTER_LEFT);
            trashHUDBox.setStyle(
                    "-fx-background-color: rgba(9, 17, 24, 0.88);"
                    + "-fx-border-color: #39ff14;"
                    + "-fx-border-width: 2px;"
                    + "-fx-border-radius: 5px;"
                    + "-fx-background-radius: 5px;"
                    + "-fx-padding: 6px 14px;"
                    + "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.85), 6, 0, 0, 3);");
            trashHUDBox.setTranslateX(16);
            trashHUDBox.setTranslateY(FXGL.getAppHeight() - 62);
            trashHUDBox.setMouseTransparent(true);

            addHudNode(trashHUDBox);
            updateTrashCounter();
        }

        // Keep the backing text for interaction logic, without rendering a bottom instruction banner.
        interactPromptText = new Text();
        interactPromptText.setVisible(false);

        refreshTimerLabel();
    }

    private static ImageView safeImageView(String path, double width, double height) {
        try {
            java.io.InputStream stream = MovementApp.class.getResourceAsStream(path);
            if (stream != null) {
                Image img = new Image(stream);
                ImageView iv = new ImageView(img);
                if (width > 0) {
                    iv.setFitWidth(width);
                    iv.setFitHeight(height);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                }
                return iv;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void completeCurrentChunkTask() {
        if (infiniteMapManager != null) {
            infiniteMapManager.unlockCurrentRegion();
            String chunkKey = infiniteMapManager.getCurrentChunkX() + "," + infiniteMapManager.getCurrentChunkY();
            completedChunks.add(chunkKey);
        }
        generatorStageCompleted = true;
        checkAndExpandLayer();
    }

    private void checkAndExpandLayer() {
        if (infiniteMapManager == null) return;
        boolean layerComplete = true;
        for (int dx = -currentMapRadius; dx <= currentMapRadius; dx++) {
            for (int dy = -currentMapRadius; dy <= currentMapRadius; dy++) {
                if (Math.abs(dx) == currentMapRadius || Math.abs(dy) == currentMapRadius) {
                    if (!completedChunks.contains(dx + "," + dy)) {
                        layerComplete = false;
                        break;
                    }
                }
            }
            if (!layerComplete) break;
        }

        if (layerComplete) {
            currentMapRadius++;
            infiniteMapManager.unlockLayer(currentMapRadius);
            
            GeneratorStage[] stages = {GeneratorStage.TRASH_COLLECTION, GeneratorStage.QUESTION, GeneratorStage.SORTING};
            java.util.Random rand = new java.util.Random();
            
            for (int dx = -currentMapRadius; dx <= currentMapRadius; dx++) {
                for (int dy = -currentMapRadius; dy <= currentMapRadius; dy++) {
                    if (Math.abs(dx) == currentMapRadius || Math.abs(dy) == currentMapRadius) {
                        assignedTasks.put(dx + "," + dy, stages[rand.nextInt(stages.length)]);
                    }
                }
            }
        }
    }

    private void updateTrashCounter() {
        if (trashCounterText != null) {
            switch (selectedGameMode) {
                case SINGLE_PLAYER -> {
                    switch (generatorStage) {
                        case TRASH_COLLECTION -> trashCounterText.setText(
                                String.format("%d/%d", generatorTrashCollected, GENERATOR_TARGET_TRASH));
                        case QUESTION -> trashCounterText.setText(String.format("%d/%d", generatorQuestionsAnswered,
                                GENERATOR_TARGET_QUESTIONS));
                        case SORTING -> trashCounterText.setText(
                                String.format("%d/%d", generatorSortedCount, GENERATOR_TARGET_SORTING));
                        default -> {}
                    }
                }
                case MAP_GENERATOR -> trashCounterText.setText(String.format("%d", collectedTrash));
                default -> trashCounterText.setText(String.format("%d/%d", collectedTrash, TOTAL_TRASH));
            }
        }
    }

    private void checkLevelCompletion() {
        if (selectedGameMode == GameMode.MAP_GENERATOR || selectedGameMode == GameMode.SINGLE_PLAYER)
            return;
        if (collectedTrash >= TOTAL_TRASH) {
            if (levelNoticeText == null) {
                levelNoticeText = new Text("LEVEL 1 CLEARED! AREA RESTORED");
                levelNoticeText.setFont(Font.font("Monospaced", FontWeight.BOLD, 28));
                levelNoticeText.setFill(Color.web("#ffd700"));
                levelNoticeText.setStroke(Color.BLACK);
                levelNoticeText.setStrokeWidth(2.0);
                levelNoticeText.setX(FXGL.getAppWidth() / 2.0 - 270);
                levelNoticeText.setY(100);
                FXGL.addUINode(levelNoticeText);
            }
            if (trashCounterText != null) {
                trashCounterText
                        .setText(String.format("%d/%d", collectedTrash, TOTAL_TRASH));
                trashCounterText.setFill(Color.web("#ffd700"));
            }
        }
    }

    @Override
    protected void onUpdate(double tpf) {
        if (isCutsceneActive || timer == null)
            return;

        if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN
                || selectedGameMode == GameMode.SORTING_TEST
                || selectedGameMode == GameMode.SEQUENTIAL_DEMO
                || selectedGameMode == GameMode.MAP_GENERATOR) {
            if (isInfiniteCoopMode() && playerEntity != null && playerEntity2 != null) {
                enforcePlayerTether();
            }
            updateCoopCamera(tpf);
        }

        if (isInfiniteGameMode()) {
            if (infiniteMapManager != null && playerEntity != null) {
                double avgX = playerEntity2 != null ? (playerEntity.getX() + playerEntity2.getX()) / 2.0
                        : playerEntity.getX();
                double avgY = playerEntity2 != null ? (playerEntity.getY() + playerEntity2.getY()) / 2.0
                        : playerEntity.getY();
                infiniteMapManager.updatePlayerPosition(avgX, avgY);

                double p1TileX = (playerEntity.getX() / 32.0) + (playerEntity.getY() / 16.0);
                double p1TileY = (playerEntity.getY() / 16.0) - (playerEntity.getX() / 32.0);
                int p1CurX = (int) Math.floor(p1TileX / InfiniteMapManager.CHUNK_SIZE);
                int p1CurY = (int) Math.floor(p1TileY / InfiniteMapManager.CHUNK_SIZE);
                String p1Key = p1CurX + "," + p1CurY;
                int p1LocalX = (int) Math.floor(p1TileX) - p1CurX * InfiniteMapManager.CHUNK_SIZE;
                int p1LocalY = (int) Math.floor(p1TileY) - p1CurY * InfiniteMapManager.CHUNK_SIZE;

                double p2TileX = playerEntity2 != null ? (playerEntity2.getX() / 32.0) + (playerEntity2.getY() / 16.0)
                        : p1TileX;
                double p2TileY = playerEntity2 != null ? (playerEntity2.getY() / 16.0) - (playerEntity2.getX() / 32.0)
                        : p1TileY;
                int p2CurX = (int) Math.floor(p2TileX / InfiniteMapManager.CHUNK_SIZE);
                int p2CurY = (int) Math.floor(p2TileY / InfiniteMapManager.CHUNK_SIZE);
                String p2Key = p2CurX + "," + p2CurY;
                int p2LocalX = (int) Math.floor(p2TileX) - p2CurX * InfiniteMapManager.CHUNK_SIZE;
                int p2LocalY = (int) Math.floor(p2TileY) - p2CurY * InfiniteMapManager.CHUNK_SIZE;

                boolean p1Entered = !completedChunks.contains(p1Key)
                        && p1LocalX >= 3 && p1LocalX <= (InfiniteMapManager.CHUNK_SIZE - 4)
                        && p1LocalY >= 3 && p1LocalY <= (InfiniteMapManager.CHUNK_SIZE - 4);

                boolean p2Entered = (playerEntity2 != null) && !completedChunks.contains(p2Key)
                        && p2LocalX >= 3 && p2LocalX <= (InfiniteMapManager.CHUNK_SIZE - 4)
                        && p2LocalY >= 3 && p2LocalY <= (InfiniteMapManager.CHUNK_SIZE - 4);

                if (generatorStageCompleted && (p1Entered || p2Entered)) {
                    int targetChunkX = p1Entered ? p1CurX : p2CurX;
                    int targetChunkY = p1Entered ? p1CurY : p2CurY;

                    // Disable boundary wall collisions ONLY for the player outside the new area
                    boolean p1Inside = (p1CurX == targetChunkX && p1CurY == targetChunkY);
                    boolean p2Inside = (p2CurX == targetChunkX && p2CurY == targetChunkY);

                    if (playerComponent != null) {
                        playerComponent.setIgnoreBoundaryWalls(!p1Inside);
                    }
                    if (playerComponent2 != null) {
                        playerComponent2.setIgnoreBoundaryWalls(!p2Inside);
                    }
                    boundaryWallReenableCooldown = 3.0; // 3-second grace period

                    generatorStage = assignedTasks.getOrDefault(targetChunkX + "," + targetChunkY, GeneratorStage.TRASH_COLLECTION);
                    switch (generatorStage) {
                        case TRASH_COLLECTION -> setupGeneratorStage1(targetChunkX, targetChunkY);
                        case QUESTION -> setupGeneratorStage2(targetChunkX, targetChunkY);
                        case SORTING -> setupGeneratorStage3(targetChunkX, targetChunkY);
                    }
                }

                if (boundaryWallReenableCooldown > 0.0) {
                    boundaryWallReenableCooldown -= tpf;
                }

                // Only enable walls once the player is at a significant distance inside the
                // zone and cooldown has expired
                if (boundaryWallReenableCooldown <= 0.0 && !generatorStageCompleted && infiniteMapManager != null
                        && infiniteMapManager.isRegionLocked()) {
                    int lockedX = infiniteMapManager.getLockedChunkX();
                    int lockedY = infiniteMapManager.getLockedChunkY();

                    if (playerComponent != null && playerComponent.isIgnoreBoundaryWalls()) {
                        if (p1CurX == lockedX && p1CurY == lockedY
                                && p1LocalX >= 5 && p1LocalX <= (InfiniteMapManager.CHUNK_SIZE - 6)
                                && p1LocalY >= 5 && p1LocalY <= (InfiniteMapManager.CHUNK_SIZE - 6)) {
                            playerComponent.setIgnoreBoundaryWalls(false);
                        }
                    }

                    if (playerComponent2 != null && playerComponent2.isIgnoreBoundaryWalls()) {
                        if (p2CurX == lockedX && p2CurY == lockedY
                                && p2LocalX >= 5 && p2LocalX <= (InfiniteMapManager.CHUNK_SIZE - 6)
                                && p2LocalY >= 5 && p2LocalY <= (InfiniteMapManager.CHUNK_SIZE - 6)) {
                            playerComponent2.setIgnoreBoundaryWalls(false);
                        }
                    }
                }
            }
            if (modeStatusText != null) {
                if (selectedGameMode == GameMode.SINGLE_PLAYER) {
                    switch (generatorStage) {
                        case TRASH_COLLECTION -> modeStatusText.setText(String.format("District %d: Phase 1 — Eco-Cleanup (%d / %d)",
                                currentDistrict, generatorTrashCollected, GENERATOR_TARGET_TRASH));
                        case QUESTION -> modeStatusText.setText(String.format("District %d: Phase 2 — Eco-Grid Terminals (%d / %d)",
                                currentDistrict, generatorQuestionsAnswered, GENERATOR_TARGET_QUESTIONS));
                        case SORTING -> modeStatusText.setText(String.format("District %d: Phase 3 — Recycling Station (%d / %d)",
                                currentDistrict, generatorSortedCount, GENERATOR_TARGET_SORTING));
                        default -> {}
                    }
                } else {
                    switch (generatorStage) {
                        case TRASH_COLLECTION -> modeStatusText.setText(String.format(
                                "District %d: Phase 1 — Co-op Cleanup (%d / %d)",
                                currentDistrict, generatorTrashCollected, GENERATOR_TARGET_TRASH));
                        case QUESTION -> modeStatusText.setText(String.format(
                                "District %d: Phase 2 — Co-op Eco-Grid (%d / %d)",
                                currentDistrict, generatorQuestionsAnswered, GENERATOR_TARGET_QUESTIONS));
                        case SORTING -> modeStatusText.setText(
                                String.format("District %d: Phase 3 — Co-op Sorting (%s)", currentDistrict, sortingFeedback));
                        default -> {}
                    }
                }
            }
            if (scoreText != null) {
                scoreText.setText(String.format("★ %d", ecoScore));
            }
        } else if (selectedGameMode == GameMode.SORTING_TEST) {
            enforceSortingRoles();
            updateSortingStatus();
        } else if (selectedGameMode == GameMode.QUESTION_TEST && modeStatusText != null) {
            modeStatusText.setText(String.format("Question Test — Answered: %d / %d",
                    testQuestionIndex, testQuestions.size()));
        } else if (selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
            if (demoStage == DemoStage.SORTING || demoStage == DemoStage.COMPLETE) {
                if (!demoSorterLocked && playerIsInsideSortingZone(playerEntity2)) {
                    demoSorterLocked = true;
                    sortingFeedback = "P2 locked in zone — collect, deliver, and sort";
                }
                if (demoSorterLocked) {
                    enforceSortingRoles();
                } else {
                    keepCollectorOutsideSortingZone();
                }
            }
            updateDemoStatus();
        }

        if (!gameEnded && selectedGameMode != GameMode.LAN_JOIN) {
            timer.tick(tpf);
            if (timer.isExpired()) {
                gameEnded = true;
                showEndGameOverlay("TIME EXPIRED", "The world could not be restored in time.", false);
            }
        }

        if (mapManager != null) {
            mapManager.update(timer.restorationRatio());
        }
        if (infiniteMapManager != null && timer != null) {
            infiniteMapManager.update(timer.restorationRatio());
        }

        boolean activeQuestionStage = (selectedGameMode == GameMode.QUESTION_TEST
                || (selectedGameMode == GameMode.SEQUENTIAL_DEMO && demoStage == DemoStage.QUESTION)
                || (isInfiniteGameMode() && generatorStage == GeneratorStage.QUESTION));
        if (activeQuestionStage) {
            Entity nearEntity = findNearestQuestionEntity();
            if (nearEntity != null) {
                if (currentActiveQuestionEntity != nearEntity && !questionAnswerLocked) {
                    currentActiveQuestionEntity = nearEntity;
                    currentActiveQuestion = (EnvironmentalQuestion) nearEntity.getProperties().getObject("question");
                    attachQuestionPanelToEntity(nearEntity);
                    refreshQuestionPanelForCurrent();
                }
                if (questionPanel != null) {
                    questionPanel.setVisible(true);
                }
                playerNearQuestionPoint = true;
            } else if (!questionAnswerLocked) {
                if (questionPanel != null) {
                    questionPanel.setVisible(false);
                }
                currentActiveQuestionEntity = null;
                currentActiveQuestion = null;
                playerNearQuestionPoint = false;
            }
        } else if (questionPanel != null) {
            questionPanel.setVisible(false);
            currentActiveQuestionEntity = null;
            currentActiveQuestion = null;
            playerNearQuestionPoint = false;
        }

        if (selectedGameMode == GameMode.LAN_HOST && netManager != null && playerEntity != null
                && playerEntity2 != null) {
            GameStatePacket packet = new GameStatePacket(
                    playerEntity.getX(), playerEntity.getY(), playerComponent.getCurrentDirection().index,
                    playerComponent.isMoving(),
                    playerEntity2.getX(), playerEntity2.getY(), playerComponent2.getCurrentDirection().index,
                    playerComponent2.isMoving(),
                    timer.currentSeconds(),
                    trashMask,
                    collectedTrash);
            netManager.sendGameState(packet);
        }

        if (selectedGameMode == GameMode.SINGLE_PLAYER) {
            if (generatorStageCompleted) {
                interactPromptText.setText("District Sector Cleared! Walk through gateway into next zone ->");
                interactPromptText.setVisible(true);
            } else if (generatorStage == GeneratorStage.TRASH_COLLECTION) {
                boolean nearTrash = false;
                for (Entity trash : generatorTrashEntities) {
                    if (trash != null && trash.isActive() && playerEntity != null
                            && (playerEntity.distance(trash) < 48.0 || safelyCollides(playerEntity, trash))) {
                        nearTrash = true;
                        break;
                    }
                }
                interactPromptText.setText("Press [E / Space] to Clean Waste (+10s, +50 pts)");
                interactPromptText.setVisible(nearTrash);
            } else if (generatorStage == GeneratorStage.QUESTION) {
                interactPromptText.setText("Press [1, 2, or 3] to Answer Terminal Question");
                interactPromptText.setVisible(playerNearQuestionPoint);
            } else if (generatorStage == GeneratorStage.SORTING) {
                if (outsideCarriedWaste != null) {
                    boolean nearBin = false;
                    for (Map.Entry<Entity, String> bin : sortingBins.entrySet()) {
                        InteractionBox binBox = sortingBinBoxes.get(bin.getKey());
                        if ((binBox != null && binBox.intersectsPlayer(playerEntity))
                                || (playerEntity != null && playerEntity.distance(bin.getKey()) < 56.0)) {
                            BinInfo info = getBinInfo(bin.getValue());
                            interactPromptText.setText(
                                    "Press [E / Space] to Deposit in " + info.category + " Bin");
                            nearBin = true;
                            break;
                        }
                    }
                    if (!nearBin) {
                        interactPromptText.setText("Carrying: " + outsideCarriedWaste.name() + " — Find the matching bin");
                    }
                    interactPromptText.setVisible(true);
                } else {
                    boolean nearWaste = false;
                    for (Map.Entry<Entity, WasteItem> entry : sortingWasteEntities.entrySet()) {
                        InteractionBox pickupBox = sortingPickupBoxes.get(entry.getKey());
                        if ((pickupBox != null && pickupBox.intersectsPlayer(playerEntity))
                                || (playerEntity != null && playerEntity.distance(entry.getKey()) < 48.0)) {
                            interactPromptText.setText("Press [E / Space] to Pick Up " + entry.getValue().name());
                            nearWaste = true;
                            break;
                        }
                    }
                    interactPromptText.setVisible(nearWaste);
                }
            }
        } else {
            boolean showStandardTrashPrompt = selectedGameMode != GameMode.SORTING_TEST
                    && selectedGameMode != GameMode.SEQUENTIAL_DEMO
                    && selectedGameMode != GameMode.QUESTION_TEST;
            boolean nearTrash = false;
            if (showStandardTrashPrompt) {
                List<Entity> trashes = FXGL.getGameWorld().getEntitiesByType(EntityType.TRASH);
                for (Entity trash : trashes) {
                    if (safelyCollides(playerEntity, trash) || safelyCollides(playerEntity2, trash)) {
                        nearTrash = true;
                        break;
                    }
                }
            }
            if (interactPromptText != null && showStandardTrashPrompt) {
                interactPromptText.setVisible(nearTrash);
            } else if (interactPromptText != null) {
                interactPromptText.setVisible(false);
            }
        }

        refreshTimerLabel();
    }

    private boolean safelyCollides(Entity first, Entity second) {
        return hasLiveBoundingBox(first)
                && hasLiveBoundingBox(second)
                && first.isColliding(second);
    }

    private boolean hasLiveBoundingBox(Entity entity) {
        return entity != null
                && entity.isActive()
                && entity.getBoundingBoxComponent().getEntity() != null;
    }

    private void enforceSortingRoles() {
        if (playerEntity2 != null) {
            playerEntity2.setX(Math.max(activeSortZoneX + 8,
                    Math.min(activeSortZoneX + activeSortZoneWidth - 24, playerEntity2.getX())));
            playerEntity2.setY(Math.max(activeSortZoneY + 8,
                    Math.min(activeSortZoneY + activeSortZoneHeight - 28, playerEntity2.getY())));
        }
        keepCollectorOutsideSortingZone();
    }

    private void keepCollectorOutsideSortingZone() {
        if (playerEntity != null
                && playerEntity.getX() >= activeSortZoneX - 5
                && playerEntity.getX() <= activeSortZoneX + activeSortZoneWidth
                && playerEntity.getY() >= activeSortZoneY - 5
                && playerEntity.getY() <= activeSortZoneY + activeSortZoneHeight) {
            playerEntity.setX(activeSortZoneX - 28);
        }
    }

    private boolean playerIsInsideSortingZone(Entity player) {
        if (player == null) {
            return false;
        }
        double centerX = player.getCenter().getX();
        double centerY = player.getCenter().getY();
        return centerX >= activeSortZoneX
                && centerX <= activeSortZoneX + activeSortZoneWidth
                && centerY >= activeSortZoneY
                && centerY <= activeSortZoneY + activeSortZoneHeight;
    }

    private void updateSortingStatus() {
        if (sortingStatusText == null || sortingTask == null) {
            return;
        }
        sortingStatusText.setText(String.format("Sorted: %d / 8%n%s",
                sortingTask.sortedItems(), sortingFeedback));
    }

    private void updateDemoStatus() {
        if (demoStatusText == null) {
            return;
        }
        String status = switch (demoStage) {
            case COLLECTION -> String.format("Stage 1/3 — Collect garbage: %d / %d",
                    demoCollectionTask.collectedItems(),
                    demoCollectionTask.collectedItems() + demoCollectionItems.size());
            case QUESTION -> String.format("Stage 2/3 — Questions: %d / %d",
                    testQuestionIndex, testQuestions.size());
            case SORTING -> String.format("Stage 3/3 — Sort garbage: %d / %d%n%s",
                    sortingTask.sortedItems(), demoSortingItemCount, sortingFeedback);
            case COMPLETE -> "Demo complete — collection, questions, and sorting passed";
        };
        demoStatusText.setText(status);
    }

    private void refreshTimerLabel() {
        if (timerBarFill == null || timer == null)
            return;
        double ratio = Math.max(0.0, Math.min(1.0, timer.restorationRatio()));
        timerBarFill.setWidth(216 * ratio);
        timerBarFill.setFill(ratio < 0.17 ? Color.web("#ff6b6b")
                : ratio < 0.4 ? Color.web("#ffb703") : Color.web("#39ff14"));
    }

    private void stopNetworking() {
        if (netManager != null) {
            netManager.stop();
            netManager = null;
        }
    }

    public void onStop() {
        stopNetworking();
        AudioManager.stopAll();
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
                    .zIndex(100)
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

        @Spawns("trash")
        public Entity spawnTrash(SpawnData data) {
            return FXGL.entityBuilder(data)
                    .type(EntityType.TRASH)
                    .viewWithBBox("bottle.png")
                    .with(new CollidableComponent(true))
                    .build();
        }

        @Spawns("demoPlayer1")
        public Entity spawnDemoPlayer1(SpawnData data) {
            return buildDemoPlayer(data, 1);
        }

        @Spawns("demoPlayer2")
        public Entity spawnDemoPlayer2(SpawnData data) {
            return buildDemoPlayer(data, 2);
        }

        private Entity buildDemoPlayer(SpawnData data, int playerIndex) {
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.PLAYER)
                    .bbox(new HitBox(BoundingShape.box(16, 24)))
                    .with(new CollidableComponent(true))
                    .with(new PlayerComponent(playerIndex))
                    .build();
            copy(data, entity, "logicalId");
            return entity;
        }

        @Spawns("demoCollectable")
        public Entity spawnDemoCollectable(SpawnData data) {
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.DEMO_COLLECTION_ITEM)
                    .viewWithBBox("bottle.png")
                    .build();
            copy(data, entity, "logicalId", "itemId");
            return entity;
        }

        @Spawns("demoQuestionPoint")
        public Entity spawnDemoQuestionPoint(SpawnData data) {
            Node marker = safeQuestionTexture();
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.DEMO_QUESTION_POINT)
                    .view(marker)
                    .build();
            copy(data, entity, "logicalId", "questionResource");
            return entity;
        }

        @Spawns("demoSortingZone")
        public Entity spawnDemoSortingZone(SpawnData data) {
            double width = number(data, "width", SORT_ZONE_WIDTH);
            double height = number(data, "height", SORT_ZONE_HEIGHT);
            Rectangle zone = new Rectangle(width, height, Color.web("#d7e77f", 0.08));
            zone.setStroke(Color.web("#d7e77f"));
            zone.setStrokeWidth(3);
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.DEMO_SORTING_ZONE)
                    .view(zone)
                    .build();
            entity.setProperty("zoneWidth", width);
            entity.setProperty("zoneHeight", height);
            copy(data, entity, "logicalId");
            return entity;
        }

        @Spawns("demoIntake")
        public Entity spawnDemoIntake(SpawnData data) {
            Circle marker = new Circle(16, Color.web("#f1d090"));
            marker.setStroke(Color.WHITE);
            marker.setStrokeWidth(2);
            Text label = new Text("INTAKE");
            label.setFill(Color.WHITE);
            label.setFont(Font.font("Verdana", FontWeight.BOLD, 9));
            StackPane view = new StackPane(marker, label);
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.DEMO_INTAKE)
                    .view(view)
                    .build();
            copy(data, entity, "logicalId");
            return entity;
        }

        @Spawns("demoBin")
        public Entity spawnDemoBin(SpawnData data) {
            double width = number(data, "width", 72);
            double height = number(data, "height", 58);
            String color = text(data, "color", "#555555");
            String binId = text(data, "binId", "");
            Node bgNode;
            String lowerId = binId == null ? "" : binId.toLowerCase();
            switch (lowerId) {
                case "blue" -> bgNode = safeTexture("trashcan_blue.png", width, height, color);
                case "green" -> bgNode = safeTexture("trashcan_green.png", width, height, color);
                case "red" -> bgNode = safeTexture("trashcan_red.png", width, height, color);
                case "black" -> bgNode = safeTexture("trashcan_black.png", width, height, color);
                default -> {
                    Rectangle background = new Rectangle(width, height, Color.web(color));
                    background.setStroke(Color.WHITE);
                    background.setStrokeWidth(2);
                    bgNode = background;
                }
            }
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.DEMO_BIN)
                    .view(bgNode)
                    .build();
            try {
                VBox plaque = createBinPlaque(binId);
                entity.getViewComponent().addChild(plaque);
            } catch (Throwable ignored) {
            }
            double boxW = number(data, "boxWidth", BIN_BOX_WIDTH);
            double boxH = number(data, "boxHeight", BIN_BOX_HEIGHT);
            entity.setProperty("boxWidth", boxW);
            entity.setProperty("boxHeight", boxH);
            copy(data, entity, "logicalId", "binId");
            return entity;
        }

        @Spawns("demoSortingWaste")
        public Entity spawnDemoSortingWaste(SpawnData data) {
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.DEMO_SORTING_WASTE)
                    .viewWithBBox("trashbag.png")
                    .build();
            copy(data, entity, "logicalId", "itemId", "itemName", "binId");
            return entity;
        }

        private static double number(SpawnData data, String key, double fallback) {
            return data.hasKey(key) ? ((Number) data.get(key)).doubleValue() : fallback;
        }

        private static String text(SpawnData data, String key, String fallback) {
            if (!data.hasKey(key)) {
                return fallback;
            }
            Object value = data.get(key);
            return value == null ? fallback : value.toString();
        }

        private static void copy(SpawnData data, Entity entity, String... keys) {
            for (String key : keys) {
                if (data.hasKey(key)) {
                    entity.setProperty(key, data.get(key));
                }
            }
        }
    }

    private static Node findFxmlNode(Node rootNode, FXMLLoader loader, String id) {
        if (rootNode != null) {
            Node n = rootNode.lookup("#" + id);
            if (n != null) {
                return n;
            }
        }
        if (loader != null && loader.getNamespace() != null) {
            Object obj = loader.getNamespace().get(id);
            if (obj instanceof Node n) {
                return n;
            }
        }
        return null;
    }

    public static final class MainMenu extends FXGLMenu {

        // =========================================================================
        // MAIN MENU TABLET LAYOUT & PIXEL DISPLACEMENT CONFIGURATION
        // Adjust these pixel displacement values (X, Y offsets) and tilt angles!
        // =========================================================================

        // Base starting coordinates for menu tablets (positioned slightly to the left)
        private static final double MENU_BASE_X = 250.0;
        private static final double MENU_BASE_Y = 350.0;
        private static final double MENU_ITEM_SPACING = 70.0;
        private static final double TABLET_WIDTH = 270.0; // Display width (maintains 505x110 aspect ratio)

        // Individual pixel displacement offsets (X and Y) for manual fine-tuning:
        private static final double SINGLEPLAYER_OFFSET_X = 0.0;
        private static final double SINGLEPLAYER_OFFSET_Y = 0.0;

        private static final double MULTIPLAYER_OFFSET_X = 12.0;
        private static final double MULTIPLAYER_OFFSET_Y = 0.0;

        private static final double SETTINGS_OFFSET_X = -8.0;
        private static final double SETTINGS_OFFSET_Y = 0.0;

        private static final double ABOUT_OFFSET_X = 8.0;
        private static final double ABOUT_OFFSET_Y = 0.0;

        private static final double EXIT_OFFSET_X = 0.0;
        private static final double EXIT_OFFSET_Y = 0.0;

        // Base rotation angles in degrees (negative = anticlockwise tilt, positive =
        // clockwise tilt)
        private static final double SINGLEPLAYER_BASE_TILT = -4.0; // Anticlockwise
        private static final double MULTIPLAYER_BASE_TILT = 4.0; // Clockwise
        private static final double SETTINGS_BASE_TILT = -3.0; // Anticlockwise
        private static final double ABOUT_BASE_TILT = 3.0; // Clockwise
        private static final double EXIT_BASE_TILT = -4.0; // Anticlockwise

        // Additional tilt magnitude added on mouse hover (degrees)
        private static final double HOVER_TILT_DELTA = 5.0;

        // =========================================================================
        // MULTIPLAYER SUB-MENU TABLET LAYOUT CONFIGURATION (CENTERED & LESS TILT)
        // =========================================================================
        private static final double MP_BASE_Y = 225.0;
        private static final double MP_ITEM_SPACING = 72.0;

        private static final double HOST_OFFSET_X = 0.0;
        private static final double HOST_OFFSET_Y = 0.0;

        private static final double JOIN_OFFSET_X = 4.0;
        private static final double JOIN_OFFSET_Y = 0.0;

        private static final double SHARED_OFFSET_X = -3.0;
        private static final double SHARED_OFFSET_Y = 0.0;

        private static final double BACK_OFFSET_X = 2.0;
        private static final double BACK_OFFSET_Y = 0.0;

        // Less tilt for multiplayer sub-menu tablets
        private static final double HOST_BASE_TILT = -1.5;
        private static final double JOIN_BASE_TILT = 1.5;
        private static final double SHARED_BASE_TILT = -1.0;
        private static final double BACK_BASE_TILT = 1.0;

        private static final double MP_HOVER_TILT_DELTA = 2.5;

        public MainMenu(MenuType type) {
            super(type);
            AudioManager.playMenuMusic();

            double w = FXGL.getAppWidth();
            double h = FXGL.getAppHeight();

            // Background images
            Image mainBgImg = safeLoadImage("/assets/ui/menu/mainmenu_bg.png");
            Image menuBgImg = safeLoadImage("/assets/ui/menu/menu.png");
            if (menuBgImg == null) {
                menuBgImg = safeLoadImage("/assets/ui/menu/menu_bg.png");
            }

            ImageView bgIv;
            Node bgNode;
            if (mainBgImg != null) {
                bgIv = new ImageView(mainBgImg);
                bgIv.setFitWidth(w);
                bgIv.setFitHeight(h);
                bgIv.setPreserveRatio(false);
                bgIv.setMouseTransparent(true);
                bgNode = bgIv;
            } else {
                bgIv = null;
                Canvas bgCanvas = new Canvas(w, h);
                drawBackground(bgCanvas.getGraphicsContext2D(), w, h);
                bgCanvas.setMouseTransparent(true);
                bgNode = bgCanvas;
            }

            Node menuRoot;
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/ui/fxml/main_menu.fxml"));
                menuRoot = loader.load();

                Node mainMenuBoxNode = findFxmlNode(menuRoot, loader, "mainMenuBox");
                Node multiplayerMenuBoxNode = findFxmlNode(menuRoot, loader, "multiplayerMenuBox");
                Node joinCoopBox = findFxmlNode(menuRoot, loader, "joinCoopBox");
                Node settingsBox = findFxmlNode(menuRoot, loader, "settingsBox");
                Node aboutBox = findFxmlNode(menuRoot, loader, "aboutBox");

                Pane mainMenuBox = (mainMenuBoxNode instanceof Pane p) ? p : new Pane();
                Pane multiplayerMenuBox = (multiplayerMenuBoxNode instanceof Pane p) ? p : new Pane();

                if (menuRoot instanceof Pane pRoot) {
                    if (!pRoot.getChildren().contains(mainMenuBox)) {
                        pRoot.getChildren().add(0, mainMenuBox);
                    }
                    if (!pRoot.getChildren().contains(multiplayerMenuBox)) {
                        pRoot.getChildren().add(1, multiplayerMenuBox);
                    }
                }

                mainMenuBox.getChildren().clear();
                multiplayerMenuBox.getChildren().clear();

                // Navigation runnables with background switching
                final Image finalMainBgImg = mainBgImg;
                final Image finalMenuBgImg = menuBgImg;
                final ImageView finalBgIv = bgIv;

                Runnable showMainCard = () -> {
                    if (finalBgIv != null && finalMainBgImg != null) {
                        finalBgIv.setImage(finalMainBgImg);
                    }
                    showCard(mainMenuBox, multiplayerMenuBox, joinCoopBox, settingsBox, aboutBox);
                };

                Runnable showMultiCard = () -> {
                    if (finalBgIv != null && finalMenuBgImg != null) {
                        finalBgIv.setImage(finalMenuBgImg);
                    }
                    showCard(multiplayerMenuBox, mainMenuBox, joinCoopBox, settingsBox, aboutBox);
                };

                Runnable showJoinCard = () -> {
                    if (finalBgIv != null && finalMenuBgImg != null) {
                        finalBgIv.setImage(finalMenuBgImg);
                    }
                    showCard(joinCoopBox, multiplayerMenuBox, mainMenuBox, settingsBox, aboutBox);
                    if (joinCoopBox != null) {
                        TextField txtHostIp = (TextField) findFxmlNode(joinCoopBox, loader, "txtHostIp");
                        if (txtHostIp != null) {
                            txtHostIp.requestFocus();
                            txtHostIp.selectAll();
                        }
                    }
                };

                Runnable showSettingsCard = () -> {
                    if (finalBgIv != null && finalMainBgImg != null) {
                        finalBgIv.setImage(finalMainBgImg);
                    }
                    showCard(settingsBox, mainMenuBox, multiplayerMenuBox, joinCoopBox, aboutBox);
                };

                Runnable showAboutCard = () -> {
                    if (finalBgIv != null && finalMainBgImg != null) {
                        finalBgIv.setImage(finalMainBgImg);
                    }
                    showCard(aboutBox, mainMenuBox, multiplayerMenuBox, joinCoopBox, settingsBox);
                };

                // 1. Create Main Menu tablet buttons
                Node btnSingleTablet = createTabletItem("/assets/ui/menu/singleplayer.png", "Single Player",
                        MENU_BASE_X + SINGLEPLAYER_OFFSET_X,
                        MENU_BASE_Y + 0 * MENU_ITEM_SPACING + SINGLEPLAYER_OFFSET_Y,
                        SINGLEPLAYER_BASE_TILT,
                        () -> {
                            selectedGameMode = GameMode.SINGLE_PLAYER;
                            fireNewGame();
                        });

                Node btnMultiTablet = createTabletItem("/assets/ui/menu/multiplayer.png", "Multiplayer",
                        MENU_BASE_X + MULTIPLAYER_OFFSET_X,
                        MENU_BASE_Y + 1 * MENU_ITEM_SPACING + MULTIPLAYER_OFFSET_Y,
                        MULTIPLAYER_BASE_TILT,
                        showMultiCard);

                Node btnSettingsTablet = createTabletItem("/assets/ui/menu/settings.png", "Settings",
                        MENU_BASE_X + SETTINGS_OFFSET_X,
                        MENU_BASE_Y + 2 * MENU_ITEM_SPACING + SETTINGS_OFFSET_Y,
                        SETTINGS_BASE_TILT,
                        showSettingsCard);

                Node btnAboutTablet = createTabletItem("/assets/ui/menu/about.png", "About",
                        MENU_BASE_X + ABOUT_OFFSET_X,
                        MENU_BASE_Y + 3 * MENU_ITEM_SPACING + ABOUT_OFFSET_Y,
                        ABOUT_BASE_TILT,
                        showAboutCard);

                Node btnExitTablet = createTabletItem("/assets/ui/menu/exit.png", "Exit",
                        MENU_BASE_X + EXIT_OFFSET_X,
                        MENU_BASE_Y + 4 * MENU_ITEM_SPACING + EXIT_OFFSET_Y,
                        EXIT_BASE_TILT,
                        () -> showPixelExitConfirmation());

                mainMenuBox.getChildren().addAll(
                        btnSingleTablet,
                        btnMultiTablet,
                        btnSettingsTablet,
                        btnAboutTablet,
                        btnExitTablet);

                // 2. Create Multiplayer Sub-Menu tablet buttons (Centered & Less Tilt)
                double mpCenterX = (w - TABLET_WIDTH) / 2.0;

                Node btnHostTablet = createTabletItem("/assets/ui/menu/host.png", "Host Co-op",
                        mpCenterX + HOST_OFFSET_X,
                        MP_BASE_Y + 0 * MP_ITEM_SPACING + HOST_OFFSET_Y,
                        HOST_BASE_TILT,
                        MP_HOVER_TILT_DELTA,
                        () -> {
                            selectedGameMode = GameMode.LAN_HOST;
                            fireNewGame();
                        });

                Node btnJoinTablet = createTabletItem("/assets/ui/menu/join.png", "Join Co-op",
                        mpCenterX + JOIN_OFFSET_X,
                        MP_BASE_Y + 1 * MP_ITEM_SPACING + JOIN_OFFSET_Y,
                        JOIN_BASE_TILT,
                        MP_HOVER_TILT_DELTA,
                        showJoinCard);

                Node btnSharedTablet = createTabletItem("/assets/ui/menu/shared.png", "Shared-Screen Co-op",
                        mpCenterX + SHARED_OFFSET_X,
                        MP_BASE_Y + 2 * MP_ITEM_SPACING + SHARED_OFFSET_Y,
                        SHARED_BASE_TILT,
                        MP_HOVER_TILT_DELTA,
                        () -> {
                            selectedGameMode = GameMode.LOCAL_COOP_SPLITSCREEN;
                            fireNewGame();
                        });

                Node btnBackTablet = createTabletItem("/assets/ui/menu/back.png", "Back",
                        mpCenterX + BACK_OFFSET_X,
                        MP_BASE_Y + 3 * MP_ITEM_SPACING + BACK_OFFSET_Y,
                        BACK_BASE_TILT,
                        MP_HOVER_TILT_DELTA,
                        showMainCard);

                multiplayerMenuBox.getChildren().addAll(
                        btnHostTablet,
                        btnJoinTablet,
                        btnSharedTablet,
                        btnBackTablet);

                // Join Co-op card handlers
                Button btnJoinConnect = (Button) findFxmlNode(menuRoot, loader, "btnJoinConnect");
                Button btnJoinCancel = (Button) findFxmlNode(menuRoot, loader, "btnJoinCancel");
                TextField txtHostIp = (TextField) findFxmlNode(menuRoot, loader, "txtHostIp");

                if (btnJoinConnect != null) {
                    btnJoinConnect.setOnAction(e -> {
                        AudioManager.playButtonClick();
                        String ip = (txtHostIp != null && txtHostIp.getText() != null) ? txtHostIp.getText().trim()
                                : "";
                        if (!ip.isEmpty()) {
                            targetHostIp = ip;
                            selectedGameMode = GameMode.LAN_JOIN;
                            fireNewGame();
                        }
                    });
                }
                if (txtHostIp != null && btnJoinConnect != null) {
                    txtHostIp.setOnAction(e -> btnJoinConnect.fire());
                }
                if (btnJoinCancel != null) {
                    btnJoinCancel.setOnAction(e -> {
                        AudioManager.playButtonClick();
                        showMultiCard.run();
                    });
                }

                // Settings button and slider handlers
                Button btnToggleFullscreen = (Button) findFxmlNode(menuRoot, loader, "btnToggleFullscreen");
                Button btnSettingsBack = (Button) findFxmlNode(menuRoot, loader, "btnSettingsBack");
                Button btnAboutBack = (Button) findFxmlNode(menuRoot, loader, "btnAboutBack");
                Slider sliderMusicVolume = (Slider) findFxmlNode(menuRoot, loader, "sliderMusicVolume");
                Label lblMusicVolume = (Label) findFxmlNode(menuRoot, loader, "lblMusicVolume");

                if (sliderMusicVolume != null) {
                    double initVol = AudioManager.getMusicVolume() * 100.0;
                    sliderMusicVolume.setValue(initVol);
                    if (lblMusicVolume != null) {
                        lblMusicVolume.setText(String.format("MUSIC VOLUME: %d%%", Math.round(initVol)));
                    }
                    sliderMusicVolume.valueProperty().addListener((obs, oldVal, newVal) -> {
                        double volPercent = newVal.doubleValue();
                        AudioManager.setMusicVolume(volPercent / 100.0);
                        if (lblMusicVolume != null) {
                            lblMusicVolume.setText(String.format("MUSIC VOLUME: %d%%", Math.round(volPercent)));
                        }
                    });
                }

                if (btnToggleFullscreen != null) {
                    btnToggleFullscreen.setOnAction(e -> {
                        AudioManager.playButtonClick();
                        FXGL.getPrimaryStage().setFullScreen(!FXGL.getPrimaryStage().isFullScreen());
                    });
                }
                if (btnSettingsBack != null) {
                    btnSettingsBack.setOnAction(e -> {
                        AudioManager.playButtonClick();
                        showMainCard.run();
                    });
                }

                // About button handlers
                if (btnAboutBack != null) {
                    btnAboutBack.setOnAction(e -> {
                        AudioManager.playButtonClick();
                        showMainCard.run();
                    });
                }

            } catch (IOException | RuntimeException ex) {
                System.err.println("[MainMenu] FXML load failed: " + ex.getMessage());
                menuRoot = createFallbackMenu();
            }

            StackPane root = new StackPane(bgNode, menuRoot);
            root.setPrefSize(w, h);
            root.setPickOnBounds(false);
            if (menuRoot instanceof Region r) {
                r.setPickOnBounds(false);
            }

            getContentRoot().getChildren().add(root);
        }

        private static Image safeLoadImage(String path) {
            try {
                java.io.InputStream stream = MainMenu.class.getResourceAsStream(path);
                if (stream != null) {
                    return new Image(stream);
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        private static Node createTabletItem(String imagePath, String fallbackLabel, double posX, double posY,
                double baseTilt, Runnable onClick) {
            return createTabletItem(imagePath, fallbackLabel, posX, posY, baseTilt, HOVER_TILT_DELTA, onClick);
        }

        private static Node createTabletItem(String imagePath, String fallbackLabel, double posX, double posY,
                double baseTilt, double hoverTiltDelta, Runnable onClick) {
            ImageView iv = null;
            try {
                java.io.InputStream stream = MainMenu.class.getResourceAsStream(imagePath);
                if (stream == null && imagePath.contains("main_menu.png")) {
                    stream = MainMenu.class.getResourceAsStream("/assets/ui/menu/mainmenu.png");
                }
                if (stream != null) {
                    Image img = new Image(stream);
                    iv = new ImageView(img);
                    if (TABLET_WIDTH > 0) {
                        iv.setFitWidth(TABLET_WIDTH);
                        iv.setPreserveRatio(true);
                        iv.setSmooth(true);
                    }
                    iv.setMouseTransparent(true);
                }
            } catch (Exception ignored) {
            }

            double tabletHeight = TABLET_WIDTH * 110.0 / 505.0;

            StackPane container = new StackPane();
            container.setPickOnBounds(true);
            container.setPrefSize(TABLET_WIDTH, tabletHeight);
            container.setMinSize(TABLET_WIDTH, tabletHeight);
            container.setMaxSize(TABLET_WIDTH, tabletHeight);

            Button fallbackBtn = null;
            if (iv != null) {
                container.getChildren().add(iv);
            } else {
                fallbackBtn = styledButton(fallbackLabel);
                fallbackBtn.setPrefSize(TABLET_WIDTH, tabletHeight);
                fallbackBtn.setMinSize(TABLET_WIDTH, tabletHeight);
                container.getChildren().add(fallbackBtn);
            }

            container.setLayoutX(posX);
            container.setLayoutY(posY);
            container.setRotate(baseTilt);
            container.setCursor(javafx.scene.Cursor.HAND);

            double hoverTilt = baseTilt < 0 ? (baseTilt - hoverTiltDelta) : (baseTilt + hoverTiltDelta);

            RotateTransition rotateIn = new RotateTransition(Duration.millis(120), container);
            rotateIn.setToAngle(hoverTilt);
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(120), container);
            scaleIn.setToX(1.06);
            scaleIn.setToY(1.06);

            RotateTransition rotateOut = new RotateTransition(Duration.millis(120), container);
            rotateOut.setToAngle(baseTilt);
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(120), container);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);

            container.setOnMouseEntered(e -> {
                rotateOut.stop();
                scaleOut.stop();
                rotateIn.playFromStart();
                scaleIn.playFromStart();
            });

            container.setOnMouseExited(e -> {
                rotateIn.stop();
                scaleIn.stop();
                rotateOut.playFromStart();
                scaleOut.playFromStart();
            });

            final long[] lastClickTime = new long[1];
            final Runnable safeClick = () -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime[0] > 250) {
                    lastClickTime[0] = now;
                    AudioManager.playButtonClick();
                    if (onClick != null) {
                        onClick.run();
                    }
                }
            };

            container.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    container.setScaleX(1.02);
                    container.setScaleY(1.02);
                    safeClick.run();
                }
            });

            container.setOnMouseReleased(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    container.setScaleX(1.06);
                    container.setScaleY(1.06);
                }
            });

            container.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    safeClick.run();
                }
            });

            if (fallbackBtn != null) {
                final Button fb = fallbackBtn;
                fb.setOnAction(e -> safeClick.run());
                fb.setOnMousePressed(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        safeClick.run();
                    }
                });
            }

            return container;
        }

        private static void showCard(Node activeCard, Node... cardsToHide) {
            if (activeCard != null) {
                activeCard.setVisible(true);
                activeCard.setManaged(true);
                activeCard.toFront();
            }
            for (Node c : cardsToHide) {
                if (c != null) {
                    c.setVisible(false);
                    c.setManaged(false);
                }
            }
        }

        private VBox createFallbackMenu() {
            Text title = new Text("RESTORATION");
            title.setFont(Font.font("Monospaced", FontWeight.BOLD, 42));
            title.setFill(Color.web("#39ff14"));

            Text subtitle = new Text("★ 8-BIT RETRO RESTORATION ADVENTURE ★");
            subtitle.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
            subtitle.setFill(Color.web("#d7e77f"));

            Button btnSingle = styledButton("Single Player");
            Button btnMultiplayer = styledButton("Multiplayer");
            Button btnSettings = styledButton("Settings");
            Button btnAbout = styledButton("About");
            Button btnExit = styledButton("Exit");

            Button btnHostCoop = styledButton("Host Co-op");
            Button btnJoinCoop = styledButton("Join Co-op");
            Button btnSharedScreenCoop = styledButton("Shared-Screen Co-op");
            Button btnMultiBack = styledButton("Back");

            Button btnToggleFullscreen = styledButton("Toggle Fullscreen");
            Button btnSettingsBack = styledButton("Back");

            Text aboutInfo = new Text(
                    "Objective: Clean & Restore ecosystems by collecting trash & answering questions.\nP1: WASD + Space | P2: Arrows + Enter\nVersion: 1.0.0");
            aboutInfo.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
            aboutInfo.setFill(Color.web("#6c5139"));
            Button btnAboutBack = styledButton("Back");

            VBox mainBox = new VBox(10, title, subtitle, btnSingle, btnMultiplayer, btnSettings, btnAbout, btnExit);
            mainBox.setAlignment(Pos.CENTER_LEFT);

            Text mpTitle = new Text("MULTIPLAYER");
            mpTitle.setFont(Font.font("Monospaced", FontWeight.BOLD, 32));
            mpTitle.setFill(Color.web("#39ff14"));
            VBox mpBox = new VBox(10, mpTitle, btnHostCoop, btnJoinCoop, btnSharedScreenCoop, btnMultiBack);
            mpBox.setAlignment(Pos.CENTER_LEFT);
            mpBox.setVisible(false);
            mpBox.setManaged(false);

            Text stTitle = new Text("SETTINGS");
            stTitle.setFont(Font.font("Monospaced", FontWeight.BOLD, 32));
            stTitle.setFill(Color.web("#6c5139"));
            
            Text lblFallbackVol = new Text(String.format("MUSIC VOLUME: %d%%", Math.round(AudioManager.getMusicVolume() * 100.0)));
            lblFallbackVol.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
            lblFallbackVol.setFill(Color.web("#6c5139"));
            
            Slider sliderFallbackVol = new Slider(0.0, 100.0, AudioManager.getMusicVolume() * 100.0);
            sliderFallbackVol.setMaxWidth(300);
            sliderFallbackVol.valueProperty().addListener((obs, oldVal, newVal) -> {
                double v = newVal.doubleValue();
                AudioManager.setMusicVolume(v / 100.0);
                lblFallbackVol.setText(String.format("MUSIC VOLUME: %d%%", Math.round(v)));
            });

            VBox stBox = new VBox(10, stTitle, lblFallbackVol, sliderFallbackVol, btnToggleFullscreen, btnSettingsBack);
            stBox.setAlignment(Pos.CENTER_LEFT);
            stBox.setVisible(false);
            stBox.setManaged(false);

            Text abTitle = new Text("ABOUT");
            abTitle.setFont(Font.font("Monospaced", FontWeight.BOLD, 32));
            abTitle.setFill(Color.web("#6c5139"));
            VBox abBox = new VBox(10, abTitle, aboutInfo, btnAboutBack);
            abBox.setAlignment(Pos.CENTER_LEFT);
            abBox.setVisible(false);
            abBox.setManaged(false);

            btnSingle.setOnAction(e -> {
                selectedGameMode = GameMode.SINGLE_PLAYER;
                fireNewGame();
            });
            btnMultiplayer.setOnAction(e -> showCard(mpBox, mainBox, stBox, abBox));
            btnSettings.setOnAction(e -> showCard(stBox, mainBox, mpBox, abBox));
            btnAbout.setOnAction(e -> showCard(abBox, mainBox, mpBox, stBox));
            btnExit.setOnAction(e -> showPixelExitConfirmation());

            btnHostCoop.setOnAction(e -> {
                selectedGameMode = GameMode.LAN_HOST;
                fireNewGame();
            });
            btnJoinCoop.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog("127.0.0.1");
                dialog.setTitle("Join LAN Co-Op");
                dialog.setHeaderText("Enter Host IP Address:");
                dialog.setContentText("Host IP:");
                try {
                    dialog.getDialogPane().getStylesheets()
                            .add(getClass().getResource("/assets/ui/css/pixel_style.css").toExternalForm());
                } catch (Exception ignored) {
                }
                Optional<String> result = dialog.showAndWait();
                result.ifPresent(ip -> {
                    targetHostIp = ip.trim();
                    selectedGameMode = GameMode.LAN_JOIN;
                    fireNewGame();
                });
            });
            btnSharedScreenCoop.setOnAction(e -> {
                selectedGameMode = GameMode.LOCAL_COOP_SPLITSCREEN;
                fireNewGame();
            });
            btnMultiBack.setOnAction(e -> showCard(mainBox, mpBox, stBox, abBox));

            btnToggleFullscreen
                    .setOnAction(e -> FXGL.getPrimaryStage().setFullScreen(!FXGL.getPrimaryStage().isFullScreen()));
            btnSettingsBack.setOnAction(e -> showCard(mainBox, mpBox, stBox, abBox));
            btnAboutBack.setOnAction(e -> showCard(mainBox, mpBox, stBox, abBox));

            VBox container = new VBox(mainBox, mpBox, stBox, abBox);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setTranslateX(108);
            return container;
        }

        private static Button styledButton(String label) {
            String base = "-fx-background-color:#142918;-fx-border-color:#39ff14 #0c4d18 #0c4d18 #39ff14;" +
                    "-fx-border-width:3;-fx-text-fill:#f8f9fa;" +
                    "-fx-font-family:Monospaced;-fx-font-size:13px;-fx-font-weight:bold;";
            String hover = "-fx-background-color:#214227;-fx-border-color:#d7e77f #687522 #687522 #d7e77f;" +
                    "-fx-border-width:3;-fx-text-fill:#39ff14;" +
                    "-fx-font-family:Monospaced;-fx-font-size:13px;-fx-font-weight:bold;";
            Button btn = new Button(label);
            btn.setMinWidth(280);
            btn.setMinHeight(38);
            btn.setStyle(base);
            btn.setOnMouseEntered(e -> btn.setStyle(hover));
            btn.setOnMouseExited(e -> btn.setStyle(base));
            btn.addEventHandler(javafx.event.ActionEvent.ACTION, e -> AudioManager.playButtonClick());
            return btn;
        }

        private static void drawBackground(GraphicsContext gc, double w, double h) {
            gc.setFill(Color.web("#0b170e"));
            gc.fillRect(0, 0, w, h);
            gc.setStroke(Color.web("#17331b", 0.6));
            gc.setLineWidth(1);
            for (int x = 0; x < w; x += 32) {
                gc.strokeLine(x, 0, x, h);
            }
            for (int y = 0; y < h; y += 32) {
                gc.strokeLine(0, y, w, y);
            }
            gc.setFill(Color.web("#39ff14", 0.04));
            for (int y = 0; y < h; y += 4) {
                gc.fillRect(0, y, w, 2);
            }
        }
    }

    public static final class PauseMenu extends FXGLMenu {

        private static final double TABLET_WIDTH = 270.0;
        private static final double PAUSE_BASE_Y = 265.0;
        private static final double PAUSE_ITEM_SPACING = 75.0;

        private static final double RESUME_BASE_TILT = -1.5;
        private static final double MAINMENU_BASE_TILT = 1.5;
        private static final double EXIT_BASE_TILT = -1.0;
        private static final double HOVER_TILT_DELTA = 2.5;

        public PauseMenu(MenuType type) {
            super(type);

            double w = FXGL.getAppWidth();
            double h = FXGL.getAppHeight();

            // 1. Dark backdrop to darken the live gameplay behind the menu
            Rectangle darkOverlay = new Rectangle(w, h, Color.rgb(0, 0, 0, 0.65));
            darkOverlay.setMouseTransparent(true);

            // 2. stick.png overlay background
            ImageView stickIv = null;
            try {
                java.io.InputStream stream = PauseMenu.class.getResourceAsStream("/assets/ui/menu/stick.png");
                if (stream != null) {
                    Image stickImg = new Image(stream);
                    stickIv = new ImageView(stickImg);
                    stickIv.setFitWidth(w);
                    stickIv.setFitHeight(h);
                    stickIv.setPreserveRatio(false);
                    stickIv.setMouseTransparent(true);
                }
            } catch (Exception ignored) {
            }

            Group bgGroup = new Group();
            bgGroup.setMouseTransparent(true);
            bgGroup.getChildren().add(darkOverlay);
            if (stickIv != null) {
                bgGroup.getChildren().add(stickIv);
            }

            Node menuRoot;
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/ui/fxml/pause_menu.fxml"));
                menuRoot = loader.load();

                Node pauseMenuBoxNode = findFxmlNode(menuRoot, loader, "pauseMenuBox");
                Pane pauseMenuBox = (pauseMenuBoxNode instanceof Pane p) ? p : new Pane();
                if (menuRoot instanceof Pane pRoot && !pRoot.getChildren().contains(pauseMenuBox)) {
                    pRoot.getChildren().add(pauseMenuBox);
                }
                pauseMenuBox.getChildren().clear();

                double centerX = (w - TABLET_WIDTH) / 2.0;

                Node btnResumeTablet = createTabletItem("/assets/ui/menu/resume.png", "Resume Game",
                        centerX,
                        PAUSE_BASE_Y + 0 * PAUSE_ITEM_SPACING,
                        RESUME_BASE_TILT,
                        HOVER_TILT_DELTA,
                        () -> fireResume());

                Node btnMainMenuTablet = createTabletItem("/assets/ui/menu/main_menu.png", "Main Menu",
                        centerX,
                        PAUSE_BASE_Y + 1 * PAUSE_ITEM_SPACING,
                        MAINMENU_BASE_TILT,
                        HOVER_TILT_DELTA,
                        () -> {
                            AudioManager.playMenuMusic();
                            fireExitToMainMenu();
                        });

                Node btnExitTablet = createTabletItem("/assets/ui/menu/exit.png", "Exit Game",
                        centerX,
                        PAUSE_BASE_Y + 2 * PAUSE_ITEM_SPACING,
                        EXIT_BASE_TILT,
                        HOVER_TILT_DELTA,
                        () -> showPixelExitConfirmation());

                pauseMenuBox.getChildren().addAll(btnResumeTablet, btnMainMenuTablet, btnExitTablet);
            } catch (IOException | RuntimeException ex) {
                menuRoot = createFallbackPauseMenu();
            }

            StackPane root = new StackPane(bgGroup, menuRoot);
            root.setPrefSize(w, h);
            root.setPickOnBounds(false);
            if (menuRoot instanceof Region r) {
                r.setPickOnBounds(false);
            }
            getContentRoot().getChildren().add(root);
        }

        private static Node createTabletItem(String imagePath, String fallbackLabel, double posX, double posY,
                double baseTilt, double hoverTiltDelta, Runnable onClick) {
            ImageView iv = null;
            try {
                java.io.InputStream stream = PauseMenu.class.getResourceAsStream(imagePath);
                if (stream == null && imagePath.contains("main_menu.png")) {
                    stream = PauseMenu.class.getResourceAsStream("/assets/ui/menu/mainmenu.png");
                }
                if (stream != null) {
                    Image img = new Image(stream);
                    iv = new ImageView(img);
                    if (TABLET_WIDTH > 0) {
                        iv.setFitWidth(TABLET_WIDTH);
                        iv.setPreserveRatio(true);
                        iv.setSmooth(true);
                    }
                    iv.setMouseTransparent(true);
                }
            } catch (Exception ignored) {
            }

            double tabletHeight = TABLET_WIDTH * 110.0 / 505.0;

            StackPane container = new StackPane();
            container.setPickOnBounds(true);
            container.setPrefSize(TABLET_WIDTH, tabletHeight);
            container.setMinSize(TABLET_WIDTH, tabletHeight);
            container.setMaxSize(TABLET_WIDTH, tabletHeight);

            Button fallbackBtn = null;
            if (iv != null) {
                container.getChildren().add(iv);
            } else {
                fallbackBtn = styledButton(fallbackLabel);
                fallbackBtn.setPrefSize(TABLET_WIDTH, tabletHeight);
                fallbackBtn.setMinSize(TABLET_WIDTH, tabletHeight);
                container.getChildren().add(fallbackBtn);
            }

            container.setLayoutX(posX);
            container.setLayoutY(posY);
            container.setRotate(baseTilt);
            container.setCursor(javafx.scene.Cursor.HAND);

            double hoverTilt = baseTilt < 0 ? (baseTilt - hoverTiltDelta) : (baseTilt + hoverTiltDelta);

            RotateTransition rotateIn = new RotateTransition(Duration.millis(120), container);
            rotateIn.setToAngle(hoverTilt);
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(120), container);
            scaleIn.setToX(1.06);
            scaleIn.setToY(1.06);

            RotateTransition rotateOut = new RotateTransition(Duration.millis(120), container);
            rotateOut.setToAngle(baseTilt);
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(120), container);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);

            container.setOnMouseEntered(e -> {
                rotateOut.stop();
                scaleOut.stop();
                rotateIn.playFromStart();
                scaleIn.playFromStart();
            });

            container.setOnMouseExited(e -> {
                rotateIn.stop();
                scaleIn.stop();
                rotateOut.playFromStart();
                scaleOut.playFromStart();
            });

            final long[] lastClickTime = new long[1];
            final Runnable safeClick = () -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime[0] > 250) {
                    lastClickTime[0] = now;
                    AudioManager.playButtonClick();
                    if (onClick != null) {
                        onClick.run();
                    }
                }
            };

            container.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    container.setScaleX(1.02);
                    container.setScaleY(1.02);
                    safeClick.run();
                }
            });

            container.setOnMouseReleased(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    container.setScaleX(1.06);
                    container.setScaleY(1.06);
                }
            });

            container.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    safeClick.run();
                }
            });

            if (fallbackBtn != null) {
                final Button fb = fallbackBtn;
                fb.setOnAction(e -> safeClick.run());
                fb.setOnMousePressed(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        safeClick.run();
                    }
                });
            }

            return container;
        }

        private VBox createFallbackPauseMenu() {
            Text title = new Text("GAME PAUSED");
            title.setFont(Font.font("Monospaced", FontWeight.BOLD, 28));
            title.setFill(Color.web("#39ff14"));

            Button btnResume = styledButton("Resume Game");
            Button btnMainMenu = styledButton("Main Menu");
            Button btnExit = styledButton("Exit Game");

            btnResume.setOnAction(e -> {
                AudioManager.playButtonClick();
                fireResume();
            });
            btnMainMenu.setOnAction(e -> {
                AudioManager.playButtonClick();
                AudioManager.playMenuMusic();
                fireExitToMainMenu();
            });
            btnExit.setOnAction(e -> {
                AudioManager.playButtonClick();
                showPixelExitConfirmation();
            });

            VBox vbox = new VBox(10, title, btnResume, btnMainMenu, btnExit);
            vbox.setAlignment(Pos.CENTER);
            vbox.setStyle(
                    "-fx-background-color:rgba(11,23,14,0.95);-fx-border-color:#39ff14;-fx-border-width:4px;-fx-padding:20px;");
            return vbox;
        }

        private static Button styledButton(String label) {
            String base = "-fx-background-color:#142918;-fx-border-color:#39ff14 #0c4d18 #0c4d18 #39ff14;" +
                    "-fx-border-width:3;-fx-text-fill:#f8f9fa;" +
                    "-fx-font-family:Monospaced;-fx-font-size:13px;-fx-font-weight:bold;";
            String hover = "-fx-background-color:#214227;-fx-border-color:#d7e77f #687522 #687522 #d7e77f;" +
                    "-fx-border-width:3;-fx-text-fill:#39ff14;" +
                    "-fx-font-family:Monospaced;-fx-font-size:13px;-fx-font-weight:bold;";
            Button btn = new Button(label);
            btn.setMinWidth(260);
            btn.setMinHeight(38);
            btn.setStyle(base);
            btn.setOnMouseEntered(e -> btn.setStyle(hover));
            btn.setOnMouseExited(e -> btn.setStyle(base));
            btn.addEventHandler(javafx.event.ActionEvent.ACTION, e -> AudioManager.playButtonClick());
            return btn;
        }
    }

    private static void showPixelExitConfirmation() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        try {
            alert.initOwner(FXGL.getPrimaryStage());
        } catch (Exception ignored) {
        }
        alert.setTitle("Exit Game");
        alert.setHeaderText("EXIT RESTORATION?");
        alert.setContentText("Are you sure you want to quit the game?");
        try {
            alert.getDialogPane().getStylesheets()
                    .add(MovementApp.class.getResource("/assets/ui/css/pixel_style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            FXGL.getGameController().exit();
        }
    }

    private void showEndGameOverlay(String titleText, String subtitleText, boolean isVictory) {
        if (endGameOverlayNode != null) {
            FXGL.removeUINode(endGameOverlayNode);
            endGameOverlayNode = null;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/ui/fxml/game_end_overlay.fxml"));
            endGameOverlayNode = loader.load();
            Label titleLabel = (Label) endGameOverlayNode.lookup("#endTitleLabel");
            Label subtitleLabel = (Label) endGameOverlayNode.lookup("#endSubtitleLabel");
            Label scoreLabel = (Label) endGameOverlayNode.lookup("#endScoreLabel");
            Button btnRetry = (Button) endGameOverlayNode.lookup("#btnRetry");
            Button btnMainMenu = (Button) endGameOverlayNode.lookup("#btnMainMenu");

            if (titleLabel != null) {
                titleLabel.setText(titleText);
                if (!isVictory) {
                    titleLabel.setStyle("-fx-text-fill:#ff0055; -fx-font-size:28px;");
                    AudioManager.playWrongAnswer();
                } else {
                    titleLabel.setStyle("-fx-text-fill:#39ff14; -fx-font-size:28px;");
                    AudioManager.playCorrectAnswer();
                }
            }
            if (subtitleLabel != null) {
                subtitleLabel.setText(subtitleText);
            }
            if (scoreLabel != null) {
                scoreLabel.setText("⭐ Earned Eco-Score: " + ecoScore);
            }
            if (btnRetry != null) {
                btnRetry.setOnAction(e -> {
                    AudioManager.playButtonClick();
                    if (endGameOverlayNode != null) {
                        FXGL.removeUINode(endGameOverlayNode);
                        endGameOverlayNode = null;
                    }
                    gameEnded = false;
                    FXGL.getGameController().startNewGame();
                });
            }
            if (btnMainMenu != null) {
                btnMainMenu.setOnAction(e -> {
                    AudioManager.playButtonClick();
                    if (endGameOverlayNode != null) {
                        FXGL.removeUINode(endGameOverlayNode);
                        endGameOverlayNode = null;
                    }
                    gameEnded = false;
                    AudioManager.playMenuMusic();
                    FXGL.getGameController().gotoMainMenu();
                });
            }
            FXGL.addUINode(endGameOverlayNode);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private record WasteItem(String id, String name, String binId) {
    }

    private enum DemoStage {
        COLLECTION,
        QUESTION,
        SORTING,
        COMPLETE
    }

    private record InteractionBox(double x, double y, double width, double height) {
        private boolean intersectsPlayer(Entity entity) {
            if (entity == null)
                return false;
            double playerX = entity.getX();
            double playerY = entity.getY();
            boolean bboxOverlap = playerX + PLAYER_BOX_WIDTH >= x && playerX <= x + width
                    && playerY + PLAYER_BOX_HEIGHT >= y && playerY <= y + height;
            double centerX = x + width / 2.0;
            double centerY = y + height / 2.0;
            double dist = Math.hypot(entity.getCenter().getX() - centerX, entity.getCenter().getY() - centerY);
            return bboxOverlap || dist < 36.0;
        }
    }
}
