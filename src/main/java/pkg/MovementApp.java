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

import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;
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
    private String lastCompletedChunkKey = "";
    private double boundaryWallReenableCooldown = 0.0;

    private final List<Entity> generatorTrashEntities = new ArrayList<>();
    private int generatorTrashCollected = 0;
    private final int GENERATOR_TARGET_TRASH = 10;

    private final List<Entity> generatorQuestionEntities = new ArrayList<>();
    private int generatorQuestionsAnswered = 0;
    private final int GENERATOR_TARGET_QUESTIONS = 3;

    private final List<Entity> generatorSortingWasteEntities = new ArrayList<>();
    private int generatorSortedCount = 0;
    private final int GENERATOR_TARGET_SORTING = 6;

    private NetworkManager netManager;

    private Text timerText;
    private Text modeStatusText;
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

        bindKey("P2 Interact / Collect", KeyCode.SLASH,
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

        FXGL.getGameScene().setBackgroundColor(Color.web("#17231e"));
        timer = new RestorationTimer(INITIAL_TIME, MAX_TIME);
        gameEnded = false;
        if (endGameOverlayNode != null) {
            FXGL.removeUINode(endGameOverlayNode);
            endGameOverlayNode = null;
        }

        if (infiniteMapManager != null) {
            infiniteMapManager.clearAll();
            infiniteMapManager = null;
        }

        FXGL.getGameWorld().addEntityFactory(new GameEntityFactory());

        if (selectedGameMode == GameMode.MAP_GENERATOR) {
            mapManager = null;
            infiniteMapManager = new InfiniteMapManager(System.currentTimeMillis());
            playerEntity = FXGL.spawn("restorationPlayer", -20, 160);
            playerComponent = playerEntity.getComponent(PlayerComponent.class);

            // Spawn Player 2 for Local Co-Op in Map Generator mode
            playerEntity2 = FXGL.entityBuilder()
                    .at(20, 160)
                    .type(EntityType.PLAYER)
                    .bbox(new HitBox(BoundingShape.box(16, 24)))
                    .with(new CollidableComponent(true))
                    .with(new PlayerComponent(2))
                    .buildAndAttach();
            playerComponent2 = playerEntity2.getComponent(PlayerComponent.class);

            infiniteMapManager.updatePlayerPosition(playerEntity.getX(), playerEntity.getY());
            collectedTrash = 0;
            generatorStage = GeneratorStage.TRASH_COLLECTION;
            generatorStageCompleted = false;
            lastCompletedChunkKey = "";
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
                if (level != null) {
                    mapManager.setInitialTileEntities(level.getEntities());
                }
            }

            List<Entity> players = FXGL.getGameWorld().getEntitiesByComponent(PlayerComponent.class);
            playerEntity = findPlayer(players, 1);
            if (playerEntity == null) {
                playerEntity = FXGL.spawn("restorationPlayer", 640, 320);
            }
            playerComponent = playerEntity.getComponent(PlayerComponent.class);

            if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN
                    || selectedGameMode == GameMode.LAN_HOST
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
                            .buildAndAttach();
                }
                playerComponent2 = playerEntity2.getComponent(PlayerComponent.class);
            }

            if (selectedGameMode == GameMode.QUESTION_TEST) {
                setupQuestionTest();
            } else if (selectedGameMode == GameMode.SORTING_TEST) {
                setupSortingTest();
            } else if (selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
                setupSequentialDemo(level);
            } else {
                spawnRandomTrash();
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
        } catch (Exception e) {
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
        if (questionPanel.getParent() instanceof javafx.scene.Group group) {
            group.getChildren().remove(questionPanel);
        } else if (questionPanel.getParent() instanceof javafx.scene.layout.Pane pane) {
            pane.getChildren().remove(questionPanel);
        }
        targetEntity.getViewComponent().addChild(questionPanel);
    }

    private void setupSortingTest() {
        resetSortingTestState();
        playerEntity2.setPosition(625, 215);
        playerEntity.getViewComponent().addChild(debugRectangle(PLAYER_BOX_WIDTH, PLAYER_BOX_HEIGHT));
        playerEntity2.getViewComponent().addChild(debugRectangle(PLAYER_BOX_WIDTH, PLAYER_BOX_HEIGHT));

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
        Rectangle intakeHitBox = debugRectangle(INTAKE_BOX_WIDTH, INTAKE_BOX_HEIGHT);
        intakeHitBox.setTranslateX(-INTAKE_BOX_WIDTH / 2);
        intakeHitBox.setTranslateY(-INTAKE_BOX_HEIGHT / 2);
        sortingIntakePoint.getViewComponent().addChild(intakeHitBox);

        createSortingBin("black", "BLACK\nGeneral", 545, 145, "#272727");
        createSortingBin("blue", "BLUE\nRecyclables", 675, 145, "#397ac7");
        createSortingBin("green", "GREEN\nOrganic", 545, 275, "#3e914c");
        createSortingBin("red", "RED\nHazardous", 675, 275, "#bd4545");

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
            Rectangle pickupHitBox = debugRectangle(PICKUP_BOX_WIDTH, PICKUP_BOX_HEIGHT);
            pickupHitBox.setTranslateX(-8);
            pickupHitBox.setTranslateY(-8);
            entity.getViewComponent().addChild(pickupHitBox);
        }
        sortingTask = new SortingTask(expectedBins, 15, 7, 8);

        attachSortingCarryViews();
    }

    private void attachSortingCarryViews() {
        if (playerEntity != null) {
            collectorCarriedWasteView = new ImageView(FXGL.image("trashbag.png"));
            collectorCarriedWasteView.setTranslateX(-4);
            collectorCarriedWasteView.setTranslateY(-16);
            collectorCarriedWasteView.setMouseTransparent(true);
            collectorCarriedWasteView.setVisible(outsideCarriedWaste != null);
            playerEntity.getViewComponent().addChild(collectorCarriedWasteView);
        }

        if (playerEntity2 != null) {
            sorterCarriedWasteView = new ImageView(FXGL.image("trashbag.png"));
            sorterCarriedWasteView.setTranslateX(-4);
            sorterCarriedWasteView.setTranslateY(-16);
            sorterCarriedWasteView.setMouseTransparent(true);
            sorterCarriedWasteView.setVisible(insideCarriedWaste != null);
            playerEntity2.getViewComponent().addChild(sorterCarriedWasteView);
        }

        if (sortingIntakePoint != null) {
            intakeWasteView = new ImageView(FXGL.image("trashbag.png"));
            intakeWasteView.setTranslateX(-8);
            intakeWasteView.setTranslateY(-12);
            intakeWasteView.setMouseTransparent(true);
            intakeWasteView.setVisible(!sortingIntake.isEmpty());
            sortingIntakePoint.getViewComponent().addChild(intakeWasteView);
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

    private void interactWithSequentialDemo() {
        if (demoStage == DemoStage.COLLECTION) {
            for (Map.Entry<Entity, String> entry : List.copyOf(demoCollectionItems.entrySet())) {
                InteractionBox pickup = new InteractionBox(
                        entry.getKey().getX() - 8, entry.getKey().getY() - 8,
                        PICKUP_BOX_WIDTH, PICKUP_BOX_HEIGHT);
                if (pickup.intersectsPlayer(playerEntity)
                        || (playerEntity2 != null && pickup.intersectsPlayer(playerEntity2))) {
                    TaskResult result = demoCollectionTask.collect(entry.getValue());
                    TaskTimer.apply(timer, result);
                    entry.getKey().removeFromWorld();
                    demoCollectionItems.remove(entry.getKey());
                    if (result.completedNow()) {
                        activateDemoQuestionStage();
                    }
                    return;
                }
            }
        } else if (demoStage == DemoStage.SORTING) {
            interactWithSortingP1();
            interactWithSortingP2();
        }
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
            infiniteMapManager.setFragmentedMode(true);
        }
        infiniteMapManager.lockCurrentRegion(chunkX, chunkY);
        generatorTrashCollected = 0;

        double originX = (chunkX - chunkY) * 320.0;
        double originY = (chunkX + chunkY) * 160.0;

        int[][] tilePositions = {
            {5, 5}, {9, 5}, {14, 5},
            {6, 9}, {10, 9}, {14, 9},
            {5, 14}, {9, 14}, {14, 14},
            {10, 12}
        };

        for (int[] pos : tilePositions) {
            int lx = pos[0];
            int ly = pos[1];
            double isoX = originX + (lx - ly) * 16.0 + 8;
            double isoY = originY + (lx + ly) * 8.0 + 4;
            Entity bottle = FXGL.entityBuilder()
                    .at(isoX, isoY)
                    .type(EntityType.TRASH)
                    .viewWithBBox("bottle.png")
                    .with(new CollidableComponent(true))
                    .buildAndAttach();
            bottle.setRotation(FXGL.random(0, 360));
            generatorTrashEntities.add(bottle);
        }
        generatorStageCompleted = false;
        updateTrashCounter();
    }

    private void setupGeneratorStage2(int chunkX, int chunkY) {
        clearGeneratorStageEntities();
        if (infiniteMapManager != null) {
            infiniteMapManager.setFragmentedMode(false);
        }
        infiniteMapManager.lockCurrentRegion(chunkX, chunkY);
        generatorQuestionsAnswered = 0;

        double originX = (chunkX - chunkY) * 320.0;
        double originY = (chunkX + chunkY) * 160.0;

        try {
            testQuestions = new QuestionLoader().loadResource("assets/questions/environment.dat");
        } catch (IOException ignored) {}

        int[][] tilePositions = { {6, 6}, {13, 6}, {10, 13} };
        for (int i = 0; i < 3 && i < testQuestions.size(); i++) {
            int lx = tilePositions[i][0];
            int ly = tilePositions[i][1];
            double isoX = originX + (lx - ly) * 16.0;
            double isoY = originY + (lx + ly) * 8.0;

            Entity qPoint = FXGL.entityBuilder()
                    .at(isoX, isoY)
                    .type(EntityType.QUESTION_POINT)
                    .view(safeQuestionTexture())
                    .buildAndAttach();
            qPoint.setProperty("question", testQuestions.get(i % testQuestions.size()));
            generatorQuestionEntities.add(qPoint);
        }
        generatorStageCompleted = false;
        createQuestionPanel();
    }

    private void setupGeneratorStage3(int chunkX, int chunkY) {
        clearGeneratorStageEntities();
        if (infiniteMapManager != null) {
            infiniteMapManager.setFragmentedMode(false);
        }
        infiniteMapManager.lockCurrentRegion(chunkX, chunkY);
        generatorSortedCount = 0;

        double originX = (chunkX - chunkY) * 320.0;
        double originY = (chunkX + chunkY) * 160.0;

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

        createSortingBin("black", "BLACK\nGeneral", originX + (10 - 6) * 16.0, originY + (10 + 6) * 8.0, "#272727");
        createSortingBin("blue", "BLUE\nRecyclables", originX + (14 - 10) * 16.0, originY + (14 + 10) * 8.0, "#397ac7");
        createSortingBin("green", "GREEN\nOrganic", originX + (6 - 10) * 16.0, originY + (6 + 10) * 8.0, "#3e914c");
        createSortingBin("red", "RED\nHazardous", originX + (10 - 14) * 16.0, originY + (10 + 14) * 8.0, "#bd4545");

        List<WasteItem> waste = List.of(
                new WasteItem("wrapper", "Greasy snack wrapper", "black"),
                new WasteItem("foam", "Used foam food box", "black"),
                new WasteItem("newspaper", "Clean newspaper", "blue"),
                new WasteItem("can", "Clean aluminium can", "blue"),
                new WasteItem("peel", "Banana peel", "green"),
                new WasteItem("battery", "Used battery", "red")
        );

        Map<String, String> expectedBins = new LinkedHashMap<>();
        for (WasteItem item : waste) {
            expectedBins.put(item.id(), item.binId());
        }
        sortingTask = new SortingTask(expectedBins, 15.0, 7.0, 8.0);

        int[][] wasteTilePositions = { {4, 4}, {16, 4}, {4, 16}, {16, 16}, {5, 12}, {15, 8} };
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
        sortingFeedback = "P1: Collect waste to INTAKE | P2: Identify & sort into bins";
        generatorStageCompleted = false;
    }

    private void clearGeneratorStageEntities() {
        for (Entity e : generatorTrashEntities) {
            if (e != null && e.isActive()) e.removeFromWorld();
        }
        generatorTrashEntities.clear();

        for (Entity e : generatorQuestionEntities) {
            if (e != null && e.isActive()) e.removeFromWorld();
        }
        generatorQuestionEntities.clear();

        for (Entity e : generatorSortingWasteEntities) {
            if (e != null && e.isActive()) e.removeFromWorld();
        }
        generatorSortingWasteEntities.clear();

        if (collectorCarriedWasteView != null) {
            collectorCarriedWasteView.setVisible(false);
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
        if (levelNoticeText != null) {
            FXGL.removeUINode(levelNoticeText);
        }
        levelNoticeText = new Text(msg);
        levelNoticeText.setFont(Font.font("Monospaced", FontWeight.BOLD, 18));
        levelNoticeText.setFill(Color.web("#ffd700"));
        levelNoticeText.setX(FXGL.getAppWidth() / 2.0 - 280);
        levelNoticeText.setY(100);
        FXGL.addUINode(levelNoticeText);

        FXGL.runOnce(() -> {
            if (levelNoticeText != null) {
                FXGL.removeUINode(levelNoticeText);
                levelNoticeText = null;
            }
        }, Duration.seconds(4.0));
    }

    private Rectangle debugRectangle(double width, double height) {
        Rectangle rectangle = new Rectangle(width, height, Color.web("#ff2020", 0.08));
        rectangle.setStroke(Color.web("#ff2020"));
        rectangle.setStrokeWidth(2);
        rectangle.setMouseTransparent(true);
        return rectangle;
    }

    private Label worldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill:white;-fx-font-family:Verdana;-fx-font-size:10px;-fx-font-weight:bold;");
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

    private void createSortingBin(String binId, String labelText, double x, double y, String color) {
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
        Label label = new Label(labelText);
        label.setStyle(
                "-fx-text-fill:white;-fx-font-family:Verdana;-fx-font-size:8px;-fx-font-weight:bold;-fx-alignment:center;");
        label.setWrapText(true);
        label.setMaxWidth(76);
        label.setTranslateX(-2);
        label.setTranslateY(-20);
        entity.getViewComponent().addChild(label);
        double boxOffsetX = (72 - BIN_BOX_WIDTH) / 2.0;
        double boxOffsetY = (58 - BIN_BOX_HEIGHT) / 2.0;
        Rectangle binHitBox = debugRectangle(BIN_BOX_WIDTH, BIN_BOX_HEIGHT);
        binHitBox.setTranslateX(boxOffsetX);
        binHitBox.setTranslateY(boxOffsetY);
        entity.getViewComponent().addChild(binHitBox);
        sortingBins.put(entity, binId);
        sortingBinBoxes.put(entity, new InteractionBox(x + boxOffsetX, y + boxOffsetY, BIN_BOX_WIDTH, BIN_BOX_HEIGHT));
    }

    private void tryCollectTrashP1() {
        if (selectedGameMode == GameMode.MAP_GENERATOR) {
            if (generatorStage == GeneratorStage.TRASH_COLLECTION) {
                for (Entity trash : List.copyOf(generatorTrashEntities)) {
                    if (trash != null && trash.isActive() && playerEntity != null && playerEntity.distance(trash) < 48.0) {
                        trash.removeFromWorld();
                        generatorTrashEntities.remove(trash);
                        generatorTrashCollected++;
                        collectedTrash++;
                        if (infiniteMapManager != null) {
                            infiniteMapManager.onBottleCollected(generatorTrashCollected, GENERATOR_TARGET_TRASH);
                        }
                        if (timer != null) timer.applyDelta(10.0);
                        updateTrashCounter();
                        if (generatorTrashCollected >= GENERATOR_TARGET_TRASH) {
                            generatorStageCompleted = true;
                            lastCompletedChunkKey = infiniteMapManager.getCurrentChunkX() + "," + infiniteMapManager.getCurrentChunkY();
                            showTemporaryNotice("STAGE 1 CLEARED!\nSpreading world restoration wave...");
                            if (infiniteMapManager != null) {
                                infiniteMapManager.startSpreadingRestoration(() -> {
                                    showTemporaryNotice("WORLD RESTORED! Render distance expanded.\nWalk to next region for Questions.");
                                });
                            }
                        }
                        break;
                    }
                }
            } else if (generatorStage == GeneratorStage.SORTING) {
                interactWithSortingP1();
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
        if (selectedGameMode == GameMode.MAP_GENERATOR) {
            if (generatorStage == GeneratorStage.TRASH_COLLECTION) {
                for (Entity trash : List.copyOf(generatorTrashEntities)) {
                    if (trash != null && trash.isActive() && playerEntity2 != null && playerEntity2.distance(trash) < 48.0) {
                        trash.removeFromWorld();
                        generatorTrashEntities.remove(trash);
                        generatorTrashCollected++;
                        collectedTrash++;
                        if (infiniteMapManager != null) {
                            infiniteMapManager.onBottleCollected(generatorTrashCollected, GENERATOR_TARGET_TRASH);
                        }
                        if (timer != null) timer.applyDelta(10.0);
                        updateTrashCounter();
                        if (generatorTrashCollected >= GENERATOR_TARGET_TRASH) {
                            generatorStageCompleted = true;
                            lastCompletedChunkKey = infiniteMapManager.getCurrentChunkX() + "," + infiniteMapManager.getCurrentChunkY();
                            showTemporaryNotice("STAGE 1 CLEARED!\nSpreading world restoration wave...");
                            if (infiniteMapManager != null) {
                                infiniteMapManager.startSpreadingRestoration(() -> {
                                    showTemporaryNotice("WORLD RESTORED! Render distance expanded.\nWalk to next region for Questions.");
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
                    generatorStageCompleted = true;
                    lastCompletedChunkKey = infiniteMapManager.getCurrentChunkX() + "," + infiniteMapManager.getCurrentChunkY();
                    showTemporaryNotice("STAGE 3 (SORTING) CLEARED! Walk into a new region to restart cycle.");
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
        if (player == null || demoStage != DemoStage.COLLECTION) return;
        for (Map.Entry<Entity, String> entry : List.copyOf(demoCollectionItems.entrySet())) {
            InteractionBox pickup = new InteractionBox(
                    entry.getKey().getX() - 16, entry.getKey().getY() - 16,
                    PICKUP_BOX_WIDTH + 32, PICKUP_BOX_HEIGHT + 32);
            if (pickup.intersectsPlayer(player) || safelyCollides(player, entry.getKey())) {
                TaskResult result = demoCollectionTask.collect(entry.getValue());
                TaskTimer.apply(timer, result);
                entry.getKey().removeFromWorld();
                demoCollectionItems.remove(entry.getKey());
                if (result.completedNow()) {
                    activateDemoQuestionStage();
                }
                return;
            }
        }
    }

    private void collectNearbyTrashForPlayer(Entity player) {
        if (player == null) return;
        for (Map.Entry<Integer, Entity> entry : List.copyOf(trashEntities.entrySet())) {
            int idx = entry.getKey();
            Entity trash = entry.getValue();
            if (trash != null && trash.isActive() && safelyCollides(player, trash)) {
                trash.removeFromWorld();
                trashMask &= ~(1 << idx);
                collectedTrash++;
                if (timer != null) {
                    timer.applyDelta(10.0);
                }
                updateTrashCounter();
                checkLevelCompletion();
                break;
            }
        }
    }

    private void interactWithSortingP1() {
        if (playerEntity == null) return;
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
                sortingFeedback = "P1 picked up: " + outsideCarriedWaste.name() + " -> Carry to INTAKE";
                showTemporaryNotice("P1 picked up: " + outsideCarriedWaste.name() + "\nPhysically carry to INTAKE at center");
                return;
            }
        }
    }

    private void interactWithSortingP2() {
        if (playerEntity2 == null) return;
        if (insideCarriedWaste != null) {
            for (Map.Entry<Entity, String> bin : sortingBins.entrySet()) {
                if (sortingBinBoxes.get(bin.getKey()).intersectsPlayer(playerEntity2)) {
                    TaskResult result = sortingTask.sort(insideCarriedWaste.id(), bin.getValue());
                    double applied = TaskTimer.apply(timer, result);
                    sortingFeedback = result.message() + String.format(" (%+.0f seconds)", applied);
                    boolean retryableWrongBin = result.status() == pkg.restoration.tasks.TaskStatus.REJECTED
                            && "Wrong bin".equals(result.message());
                    if (!retryableWrongBin) {
                        clearInsideCarriedWaste();
                    }
                    if (selectedGameMode == GameMode.MAP_GENERATOR && sortingTask.isComplete()) {
                        generatorSortedCount = GENERATOR_TARGET_SORTING;
                        infiniteMapManager.unlockCurrentRegion();
                        generatorStageCompleted = true;
                        lastCompletedChunkKey = infiniteMapManager.getCurrentChunkX() + "," + infiniteMapManager.getCurrentChunkY();
                        showTemporaryNotice("STAGE 3 (SORTING) CLEARED! Walk into a new region to restart cycle.");
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
            sortingFeedback = "P2 identified: " + insideCarriedWaste.name() + " -> Sort into " + insideCarriedWaste.binId().toUpperCase() + " bin";
            showTemporaryNotice("IDENTIFIED: " + insideCarriedWaste.name() + "\nSort into " + insideCarriedWaste.binId().toUpperCase() + " BIN");
            return;
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
        if (selectedGameMode == GameMode.MAP_GENERATOR && generatorStage == GeneratorStage.QUESTION) {
            if (currentActiveQuestionEntity == null || currentActiveQuestion == null || questionAnswerLocked) return;
            if (choiceIndex >= currentActiveQuestion.choices().size()) return;
            QuestionResult result = currentActiveQuestion.answer(choiceIndex);
            questionAnswerLocked = true;
            double appliedDelta = TaskTimer.apply(timer, result.asTaskResult());
            generatorQuestionsAnswered++;
            questionFeedbackLabel.setText(result.quality() + ": " + result.feedback() + String.format(" (%+.0f seconds)", appliedDelta));
            Entity targetEntity = currentActiveQuestionEntity;
            currentActiveQuestionEntity = null;
            currentActiveQuestion = null;

            FXGL.runOnce(() -> {
                if (targetEntity != null && targetEntity.isActive()) {
                    targetEntity.removeFromWorld();
                }
                if (questionPanel != null) questionPanel.setVisible(false);
                questionAnswerLocked = false;

                if (generatorQuestionsAnswered >= GENERATOR_TARGET_QUESTIONS) {
                    infiniteMapManager.unlockCurrentRegion();
                    generatorStageCompleted = true;
                    lastCompletedChunkKey = infiniteMapManager.getCurrentChunkX() + "," + infiniteMapManager.getCurrentChunkY();
                    showTemporaryNotice("QUESTIONS CLEARED! Walk into a new region for Sorting Stage.");
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

    private void tryCollectTrash() {
        tryCollectTrashP1();
    }

    private void setupViewports() {
        Viewport vp = FXGL.getGameScene().getViewport();
        vp.setLazy(true);

        if ((selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN
                || selectedGameMode == GameMode.SORTING_TEST
                || selectedGameMode == GameMode.SEQUENTIAL_DEMO
                || selectedGameMode == GameMode.MAP_GENERATOR) && playerEntity != null && playerEntity2 != null) {
            // Shared Screen Local Co-Op: Fixed 1.8x crisp zoom tracking dual player midpoint
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
            if (selectedGameMode != GameMode.SEQUENTIAL_DEMO) {
                double midX = (playerEntity.getX() + playerEntity2.getX()) / 2.0 + 120.0;
                double midY = (playerEntity.getY() + playerEntity2.getY()) / 2.0 + 120.0;
                FXGL.getGameScene().getViewport().focusOn(new javafx.geometry.Point2D(midX, midY));
                return;
            }

            double midX = (playerEntity.getCenter().getX() + playerEntity2.getCenter().getX()) / 2.0;
            double midY = (playerEntity.getCenter().getY() + playerEntity2.getCenter().getY()) / 2.0;
            Viewport viewport = FXGL.getGameScene().getViewport();
            viewport.setX(midX - viewport.getWidth() / (2.0 * viewport.getZoom()));
            viewport.setY(midY - viewport.getHeight() / (2.0 * viewport.getZoom()));
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
                        Direction.values()[Math.min(packet.p1DirIndex, Direction.values().length - 1)], packet.p1Moving);
            }
        }
        if (playerEntity2 != null) {
            playerEntity2.setPosition(packet.p2X, packet.p2Y);
            if (playerComponent2 != null) {
                playerComponent2.setRemoteState(
                        Direction.values()[Math.min(packet.p2DirIndex, Direction.values().length - 1)], packet.p2Moving);
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
        timerText = new Text();
        timerText.setFont(Font.font("Monospaced", FontWeight.BOLD, 20));
        timerText.setFill(Color.web("#ffb703"));
        timerText.setX(20);
        timerText.setY(36);
        FXGL.addUINode(timerText);

        modeStatusText = new Text();
        modeStatusText.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
        modeStatusText.setFill(Color.web("#d7e77f"));
        modeStatusText.setX(20);
        modeStatusText.setY(60);
        FXGL.addUINode(modeStatusText);

        if (selectedGameMode == GameMode.QUESTION_TEST) {
            modeStatusText.setText("Question Test — Answered: 0 / " + testQuestions.size());
        } else if (selectedGameMode == GameMode.SORTING_TEST) {
            modeStatusText.setText("Sorting Test — P1: WASD + E, P2: Arrows + /");
        } else if (selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
            modeStatusText.setText("Sequential Demo — P1: WASD, P2: arrows, E: interact");
        } else if (selectedGameMode == GameMode.MAP_GENERATOR) {
            Text p1Label = new Text("P1: WASD + [E]");
            p1Label.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
            p1Label.setFill(Color.web("#39ff14"));
            p1Label.setX(20);
            p1Label.setY(85);

            Text p2Label = new Text("P2: ARROWS + [/]");
            p2Label.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
            p2Label.setFill(Color.web("#d7e77f"));
            p2Label.setX(20);
            p2Label.setY(105);

            FXGL.addUINode(p1Label);
            FXGL.addUINode(p2Label);
            modeStatusText.setText("Map Generator (2P Local Co-Op)");
        } else if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN) {
            Text p1Label = new Text("P1: WASD");
            p1Label.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
            p1Label.setFill(Color.web("#39ff14"));
            p1Label.setX(20);
            p1Label.setY(85);

            Text p2Label = new Text("P2: ARROW KEYS");
            p2Label.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
            p2Label.setFill(Color.web("#d7e77f"));
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

        if (selectedGameMode != GameMode.SORTING_TEST
                && selectedGameMode != GameMode.SEQUENTIAL_DEMO) {
            trashCounterText = new Text();
            trashCounterText.setFont(Font.font("Monospaced", FontWeight.BOLD, 16));
            trashCounterText.setFill(Color.web("#39ff14"));
            trashCounterText.setX(20);
            trashCounterText.setY(FXGL.getAppHeight() - 30);
            FXGL.addUINode(trashCounterText);
            updateTrashCounter();
        }

        interactPromptText = new Text("Press [E] to Collect Trash (+10s)");
        interactPromptText.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        interactPromptText.setFill(Color.web("#ffb703"));
        interactPromptText.setX(FXGL.getAppWidth() / 2.0 - 140);
        interactPromptText.setY(FXGL.getAppHeight() - 40);
        interactPromptText.setVisible(false);
        FXGL.addUINode(interactPromptText);

        if (selectedGameMode == GameMode.SORTING_TEST) {
            sortingStatusText = new Text();
            sortingStatusText.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
            sortingStatusText.setFill(Color.web("#f7f4dc"));
            sortingStatusText.setX(20);
            sortingStatusText.setY(88);
            FXGL.addUINode(sortingStatusText);
        } else if (selectedGameMode == GameMode.SEQUENTIAL_DEMO) {
            demoStatusText = new Text();
            demoStatusText.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
            demoStatusText.setFill(Color.web("#f7f4dc"));
            demoStatusText.setX(20);
            demoStatusText.setY(88);
            FXGL.addUINode(demoStatusText);
        }

        refreshTimerLabel();
    }

    private void updateTrashCounter() {
        if (trashCounterText != null) {
            if (selectedGameMode == GameMode.MAP_GENERATOR) {
                trashCounterText.setText(String.format("Trash Collected: %d (Infinite Mode)", collectedTrash));
            } else {
                trashCounterText.setText(String.format("Trash Collected: %d / %d", collectedTrash, TOTAL_TRASH));
            }
        }
    }

    private void checkLevelCompletion() {
        if (selectedGameMode == GameMode.MAP_GENERATOR) return;
        if (collectedTrash >= TOTAL_TRASH) {
            if (levelNoticeText == null) {
                levelNoticeText = new Text("LEVEL 1 CLEARED! AREA RESTORED");
                levelNoticeText.setFont(Font.font("Georgia", FontWeight.BOLD, 32));
                levelNoticeText.setFill(Color.web("#ffd700"));
                levelNoticeText.setX(FXGL.getAppWidth() / 2.0 - 270);
                levelNoticeText.setY(100);
                FXGL.addUINode(levelNoticeText);
            }
            if (trashCounterText != null) {
                trashCounterText
                        .setText(String.format("Trash Collected: %d / %d (COMPLETE!)", collectedTrash, TOTAL_TRASH));
                trashCounterText.setFill(Color.web("#ffd700"));
            }
        }
    }

    @Override
    protected void onUpdate(double tpf) {
        if (timer == null)
            return;

        if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN
                || selectedGameMode == GameMode.SORTING_TEST
                || selectedGameMode == GameMode.SEQUENTIAL_DEMO
                || selectedGameMode == GameMode.MAP_GENERATOR) {
            updateCoopCamera();
        }

        if (selectedGameMode == GameMode.MAP_GENERATOR) {
            if (infiniteMapManager != null && playerEntity != null) {
                double avgX = playerEntity2 != null ? (playerEntity.getX() + playerEntity2.getX()) / 2.0 : playerEntity.getX();
                double avgY = playerEntity2 != null ? (playerEntity.getY() + playerEntity2.getY()) / 2.0 : playerEntity.getY();
                infiniteMapManager.updatePlayerPosition(avgX, avgY);

                double p1TileX = (playerEntity.getX() / 32.0) + (playerEntity.getY() / 16.0);
                double p1TileY = (playerEntity.getY() / 16.0) - (playerEntity.getX() / 32.0);
                int p1CurX = (int) Math.floor(p1TileX / InfiniteMapManager.CHUNK_SIZE);
                int p1CurY = (int) Math.floor(p1TileY / InfiniteMapManager.CHUNK_SIZE);
                String p1Key = p1CurX + "," + p1CurY;
                int p1LocalX = (int) Math.floor(p1TileX) - p1CurX * InfiniteMapManager.CHUNK_SIZE;
                int p1LocalY = (int) Math.floor(p1TileY) - p1CurY * InfiniteMapManager.CHUNK_SIZE;

                double p2TileX = playerEntity2 != null ? (playerEntity2.getX() / 32.0) + (playerEntity2.getY() / 16.0) : p1TileX;
                double p2TileY = playerEntity2 != null ? (playerEntity2.getY() / 16.0) - (playerEntity2.getX() / 32.0) : p1TileY;
                int p2CurX = (int) Math.floor(p2TileX / InfiniteMapManager.CHUNK_SIZE);
                int p2CurY = (int) Math.floor(p2TileY / InfiniteMapManager.CHUNK_SIZE);
                String p2Key = p2CurX + "," + p2CurY;
                int p2LocalX = (int) Math.floor(p2TileX) - p2CurX * InfiniteMapManager.CHUNK_SIZE;
                int p2LocalY = (int) Math.floor(p2TileY) - p2CurY * InfiniteMapManager.CHUNK_SIZE;

                boolean p1Entered = !p1Key.equals(lastCompletedChunkKey)
                        && p1LocalX >= 3 && p1LocalX <= (InfiniteMapManager.CHUNK_SIZE - 4)
                        && p1LocalY >= 3 && p1LocalY <= (InfiniteMapManager.CHUNK_SIZE - 4);

                boolean p2Entered = !p2Key.equals(lastCompletedChunkKey)
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

                    if (generatorStage == GeneratorStage.TRASH_COLLECTION) {
                        generatorStage = GeneratorStage.QUESTION;
                        setupGeneratorStage2(targetChunkX, targetChunkY);
                    } else if (generatorStage == GeneratorStage.QUESTION) {
                        generatorStage = GeneratorStage.SORTING;
                        setupGeneratorStage3(targetChunkX, targetChunkY);
                    } else {
                        generatorStage = GeneratorStage.TRASH_COLLECTION;
                        setupGeneratorStage1(targetChunkX, targetChunkY);
                    }
                }

                if (boundaryWallReenableCooldown > 0.0) {
                    boundaryWallReenableCooldown -= tpf;
                }

                // Only enable walls once the player is at a significant distance inside the zone and cooldown has expired
                if (boundaryWallReenableCooldown <= 0.0 && !generatorStageCompleted && infiniteMapManager != null && infiniteMapManager.isRegionLocked()) {
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
                if (generatorStage == GeneratorStage.TRASH_COLLECTION) {
                    modeStatusText.setText(String.format("MAP GENERATOR (2P CO-OP) — STAGE 1: TRASH COLLECTION (%d / 10)", generatorTrashCollected));
                } else if (generatorStage == GeneratorStage.QUESTION) {
                    modeStatusText.setText(String.format("MAP GENERATOR (2P CO-OP) — STAGE 2: QUESTIONS ANSWERED (%d / 3)", generatorQuestionsAnswered));
                } else if (generatorStage == GeneratorStage.SORTING) {
                    modeStatusText.setText(String.format("MAP GENERATOR (2P CO-OP) — STAGE 3: %s", sortingFeedback));
                }
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

        boolean activeQuestionStage = (selectedGameMode == GameMode.QUESTION_TEST
                || (selectedGameMode == GameMode.SEQUENTIAL_DEMO && demoStage == DemoStage.QUESTION)
                || (selectedGameMode == GameMode.MAP_GENERATOR && generatorStage == GeneratorStage.QUESTION));
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

    private boolean playerIsNearQuestionPoint(Entity player) {
        return Math.hypot(
                questionPoint.getX() - player.getCenter().getX(),
                questionPoint.getY() - player.getCenter().getY()) <= 85;
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
            Text label = new Text(text(data, "label", "BIN"));
            label.setFill(Color.WHITE);
            label.setFont(Font.font("Verdana", FontWeight.BOLD, 8));
            label.setTranslateY(-16);
            StackPane view = new StackPane(bgNode, label);
            StackPane.setAlignment(label, Pos.TOP_CENTER);
            Entity entity = FXGL.entityBuilder(data)
                    .type(EntityType.DEMO_BIN)
                    .view(view)
                    .build();
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

    private static void bindWindowControls(Node root) {
        if (root == null) return;
        Button btnMin = (Button) root.lookup("#btnMinimize");
        Button btnFS = (Button) root.lookup("#btnFullscreen");
        Button btnClose = (Button) root.lookup("#btnClose");

        if (btnMin != null) {
            btnMin.setOnAction(e -> {
                try {
                    if (FXGL.getPrimaryStage() != null) {
                        FXGL.getPrimaryStage().setIconified(true);
                    }
                } catch (Exception ignored) {}
            });
        }
        if (btnFS != null) {
            btnFS.setOnAction(e -> toggleFullscreen());
        }
        if (btnClose != null) {
            btnClose.setOnAction(e -> showPixelExitConfirmation());
        }
    }

    private static void toggleFullscreen() {
        try {
            javafx.stage.Stage stage = FXGL.getPrimaryStage();
            if (stage != null) {
                boolean current = stage.isFullScreen();
                stage.setFullScreen(!current);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void stopNetworking() {
        if (netManager != null) {
            netManager.stop();
            netManager = null;
        }
    }

    public static final class MainMenu extends FXGLMenu {

        public MainMenu(MenuType type) {
            super(type);

            double w = FXGL.getAppWidth();
            double h = FXGL.getAppHeight();

            Canvas bg = new Canvas(w, h);
            drawBackground(bg.getGraphicsContext2D(), w, h);

            Node menuRoot;
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/ui/fxml/main_menu.fxml"));
                menuRoot = loader.load();

                Button btnSingle = (Button) menuRoot.lookup("#btnSingle");
                Button btnQuestionTest = (Button) menuRoot.lookup("#btnQuestionTest");
                Button btnSortingTest = (Button) menuRoot.lookup("#btnSortingTest");
                Button btnSequentialDemo = (Button) menuRoot.lookup("#btnSequentialDemo");
                Button btnLocalCoop = (Button) menuRoot.lookup("#btnLocalCoop");
                Button btnHostLan = (Button) menuRoot.lookup("#btnHostLan");
                Button btnJoinLan = (Button) menuRoot.lookup("#btnJoinLan");
                Button btnMapGenerator = (Button) menuRoot.lookup("#btnMapGenerator");
                Button btnFullscreen = (Button) menuRoot.lookup("#btnFullscreen");
                Button btnExit = (Button) menuRoot.lookup("#btnExit");

                if (btnSingle != null)
                    btnSingle.setOnAction(e -> {
                        selectedGameMode = GameMode.SINGLE_PLAYER;
                        fireNewGame();
                    });
                if (btnQuestionTest != null)
                    btnQuestionTest.setOnAction(e -> {
                        selectedGameMode = GameMode.QUESTION_TEST;
                        fireNewGame();
                    });
                if (btnSortingTest != null)
                    btnSortingTest.setOnAction(e -> {
                        selectedGameMode = GameMode.SORTING_TEST;
                        fireNewGame();
                    });
                if (btnSequentialDemo != null)
                    btnSequentialDemo.setOnAction(e -> {
                        selectedGameMode = GameMode.SEQUENTIAL_DEMO;
                        fireNewGame();
                    });
                if (btnLocalCoop != null)
                    btnLocalCoop.setOnAction(e -> {
                        selectedGameMode = GameMode.LOCAL_COOP_SPLITSCREEN;
                        fireNewGame();
                    });
                if (btnHostLan != null)
                    btnHostLan.setOnAction(e -> {
                        selectedGameMode = GameMode.LAN_HOST;
                        fireNewGame();
                    });
                if (btnJoinLan != null)
                    btnJoinLan.setOnAction(e -> {
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
                if (btnMapGenerator != null)
                    btnMapGenerator.setOnAction(e -> {
                        selectedGameMode = GameMode.MAP_GENERATOR;
                        fireNewGame();
                    });
                if (btnExit != null)
                    btnExit.setOnAction(e -> showPixelExitConfirmation());

            } catch (Exception ex) {
                menuRoot = createFallbackMenu();
            }

            StackPane root = new StackPane(bg, menuRoot);
            root.setPrefSize(w, h);

            getContentRoot().getChildren().add(root);
        }

        private VBox createFallbackMenu() {
            Text title = new Text("RESTORATION");
            title.setFont(Font.font("Monospaced", FontWeight.BOLD, 42));
            title.setFill(Color.web("#39ff14"));

            Text subtitle = new Text("★ 8-BIT RETRO RESTORATION ADVENTURE ★");
            subtitle.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
            subtitle.setFill(Color.web("#d7e77f"));

            Button btnSingle = styledButton("Single Player");
            Button btnQuestionTest = styledButton("Question Test Mode");
            Button btnSortingTest = styledButton("Sorting Test Mode");
            Button btnSequentialDemo = styledButton("3-Stage Demo Map");
            Button btnLocalCoop = styledButton("Local Co-Op (2 Players)");
            Button btnHostLan = styledButton("Host LAN Co-Op");
            Button btnJoinLan = styledButton("Join LAN Co-Op");
            Button btnMapGenerator = styledButton("Map Generator (Infinite)");
            Button btnExit = styledButton("Exit");

            btnSingle.setOnAction(e -> {
                selectedGameMode = GameMode.SINGLE_PLAYER;
                fireNewGame();
            });
            btnMapGenerator.setOnAction(e -> {
                selectedGameMode = GameMode.MAP_GENERATOR;
                fireNewGame();
            });
            btnExit.setOnAction(e -> fireExit());

            VBox vbox = new VBox(8, title, subtitle, btnSingle, btnQuestionTest, btnSortingTest,
                    btnSequentialDemo, btnLocalCoop, btnHostLan, btnJoinLan, btnMapGenerator, btnExit);
            vbox.setAlignment(Pos.CENTER_LEFT);
            vbox.setTranslateX(108);
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
            btn.setMinWidth(280);
            btn.setMinHeight(38);
            btn.setStyle(base);
            btn.setOnMouseEntered(e -> btn.setStyle(hover));
            btn.setOnMouseExited(e -> btn.setStyle(base));
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

        public PauseMenu(MenuType type) {
            super(type);

            double w = FXGL.getAppWidth();
            double h = FXGL.getAppHeight();

            Canvas bg = new Canvas(w, h);
            GraphicsContext gc = bg.getGraphicsContext2D();
            gc.setFill(Color.web("#0b170e", 0.85));
            gc.fillRect(0, 0, w, h);

            Node menuRoot;
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/ui/fxml/pause_menu.fxml"));
                menuRoot = loader.load();

                Button btnResume = (Button) menuRoot.lookup("#btnResume");
                Button btnMainMenu = (Button) menuRoot.lookup("#btnMainMenu");
                Button btnExit = (Button) menuRoot.lookup("#btnExit");

                if (btnResume != null)
                    btnResume.setOnAction(e -> fireResume());
                if (btnMainMenu != null)
                    btnMainMenu.setOnAction(e -> fireExitToMainMenu());
                if (btnExit != null)
                    btnExit.setOnAction(e -> showPixelExitConfirmation());
            } catch (Exception ex) {
                menuRoot = createFallbackPauseMenu();
            }

            StackPane root = new StackPane(bg, menuRoot);
            root.setPrefSize(w, h);
            getContentRoot().getChildren().add(root);
        }

        private VBox createFallbackPauseMenu() {
            Text title = new Text("GAME PAUSED");
            title.setFont(Font.font("Monospaced", FontWeight.BOLD, 28));
            title.setFill(Color.web("#39ff14"));

            Button btnResume = styledButton("Resume Game");
            Button btnMainMenu = styledButton("Main Menu");
            Button btnExit = styledButton("Exit Game");

            btnResume.setOnAction(e -> fireResume());
            btnMainMenu.setOnAction(e -> fireExitToMainMenu());
            btnExit.setOnAction(e -> showPixelExitConfirmation());

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
            return btn;
        }
    }

    private static void showPixelExitConfirmation() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
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
            Button btnRetry = (Button) endGameOverlayNode.lookup("#btnRetry");
            Button btnMainMenu = (Button) endGameOverlayNode.lookup("#btnMainMenu");

            if (titleLabel != null) {
                titleLabel.setText(titleText);
                if (!isVictory) {
                    titleLabel.setStyle("-fx-text-fill:#ff0055; -fx-font-size:28px;");
                } else {
                    titleLabel.setStyle("-fx-text-fill:#39ff14; -fx-font-size:28px;");
                }
            }
            if (subtitleLabel != null) {
                subtitleLabel.setText(subtitleText);
            }
            if (btnRetry != null) {
                btnRetry.setOnAction(e -> {
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
                    if (endGameOverlayNode != null) {
                        FXGL.removeUINode(endGameOverlayNode);
                        endGameOverlayNode = null;
                    }
                    gameEnded = false;
                    FXGL.getGameController().gotoMainMenu();
                });
            }
            FXGL.addUINode(endGameOverlayNode);
        } catch (Exception ex) {
            ex.printStackTrace();
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
            if (entity == null) return false;
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
