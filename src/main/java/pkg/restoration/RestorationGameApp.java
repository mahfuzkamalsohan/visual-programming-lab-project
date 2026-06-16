package pkg.restoration;

import static com.almasb.fxgl.dsl.FXGL.addUINode;
import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getAppHeight;
import static com.almasb.fxgl.dsl.FXGL.getAppWidth;
import static com.almasb.fxgl.dsl.FXGL.getDialogService;
import static com.almasb.fxgl.dsl.FXGL.getGameController;
import static com.almasb.fxgl.dsl.FXGL.getGameScene;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;
import static com.almasb.fxgl.dsl.FXGL.getInput;
import static com.almasb.fxgl.dsl.FXGL.runOnce;
import static com.almasb.fxgl.dsl.FXGL.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.input.UserAction;

import org.springframework.beans.factory.annotation.Autowired;

import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import pkg.restoration.components.ChoiceDoorComponent;
import pkg.restoration.components.GateComponent;
import pkg.restoration.components.NpcComponent;
import pkg.restoration.components.PlayerIsoComponent;
import pkg.restoration.questions.ChallengeResult;
import pkg.restoration.questions.ChallengeType;
import pkg.restoration.questions.QuestionBank;
import pkg.restoration.questions.QuestionChallenge;
import pkg.restoration.systems.DifficultyCurve;
import pkg.restoration.systems.RestorationTimer;
import pkg.restoration.spring.RestorationGameProperties;
import pkg.restoration.spring.RestorationSpringContext;
import pkg.restoration.ui.ChallengeOverlay;
import pkg.restoration.ui.RestorationHud;
import pkg.restoration.ui.ToastLayer;
import pkg.restoration.ui.fxml.FxmlViewLoader;
import pkg.restoration.world.GateDefinition;
import pkg.restoration.world.GateKind;
import pkg.restoration.world.GateState;
import pkg.restoration.world.IsoPoint;
import pkg.restoration.world.IsoProjection;
import pkg.restoration.world.LevelDefinition;
import pkg.restoration.world.LevelRepository;
import pkg.restoration.world.WorldRenderer;

public final class RestorationGameApp extends GameApplication {

    private final List<Entity> gateEntities = new ArrayList<>();
    private final List<Entity> choiceDoorEntities = new ArrayList<>();

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private DifficultyCurve difficultyCurve;

    @Autowired
    private QuestionBank questionBank;

    @Autowired
    private RestorationEntityFactory entityFactory;

    @Autowired
    private RestorationSceneFactory sceneFactory;

    @Autowired
    private RestorationGameProperties gameProperties;

    @Autowired
    private FxmlViewLoader fxmlViewLoader;

    private RestorationTimer timer;
    private IsoProjection projection;
    private WorldRenderer worldRenderer;
    private LevelDefinition currentLevel;
    private int currentLevelIndex;
    private Entity playerEntity;
    private PlayerIsoComponent playerControl;
    private RestorationHud hud;
    private ChallengeOverlay challengeOverlay;
    private ToastLayer toastLayer;
    private boolean transitionInProgress;
    private boolean gameEnded;
    private int spawnedLevelCount;

    public static void main(String[] args) {
        RestorationSpringContext.setLaunchArgs(args);
        launch(args);
    }

    @Override
    protected void initSettings(GameSettings settings) {
        RestorationSpringContext.start();
        RestorationSpringContext.autowire(this);

        settings.setWidth(gameProperties.width());
        settings.setHeight(gameProperties.height());
        settings.setTitle(gameProperties.title());
        settings.setVersion(gameProperties.version());
        settings.setMainMenuEnabled(true);
        settings.setGameMenuEnabled(true);
        settings.setFullScreenAllowed(true);
        settings.setFullScreenFromStart(true);
        settings.setMenuKey(KeyCode.ESCAPE);
        settings.setManualResizeEnabled(false);
        settings.setSceneFactory(sceneFactory);
    }

    @Override
    protected void initInput() {
        bindMovement("Move North", KeyCode.W, pressed -> withPlayer(player -> player.setMovingUp(pressed)));
        bindMovement("Move South", KeyCode.S, pressed -> withPlayer(player -> player.setMovingDown(pressed)));
        bindMovement("Move West", KeyCode.A, pressed -> withPlayer(player -> player.setMovingLeft(pressed)));
        bindMovement("Move East", KeyCode.D, pressed -> withPlayer(player -> player.setMovingRight(pressed)));

        bindMovement("Move North Alt", KeyCode.UP, pressed -> withPlayer(player -> player.setMovingUp(pressed)));
        bindMovement("Move South Alt", KeyCode.DOWN, pressed -> withPlayer(player -> player.setMovingDown(pressed)));
        bindMovement("Move West Alt", KeyCode.LEFT, pressed -> withPlayer(player -> player.setMovingLeft(pressed)));
        bindMovement("Move East Alt", KeyCode.RIGHT, pressed -> withPlayer(player -> player.setMovingRight(pressed)));

        getInput().addAction(new UserAction("Interact") {
            @Override
            protected void onActionBegin() {
                interact();
            }
        }, KeyCode.E);

        getInput().addAction(new UserAction("Choice 1") {
            @Override
            protected void onActionBegin() {
                chooseQuestionAnswer(1);
            }
        }, KeyCode.DIGIT1);

        getInput().addAction(new UserAction("Choice 2") {
            @Override
            protected void onActionBegin() {
                chooseQuestionAnswer(2);
            }
        }, KeyCode.DIGIT2);

        getInput().addAction(new UserAction("Choice 3") {
            @Override
            protected void onActionBegin() {
                chooseQuestionAnswer(3);
            }
        }, KeyCode.DIGIT3);
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("restorationTime", gameProperties.startTimeSeconds());
    }

    @Override
    protected void initGame() {
        getGameScene().setBackgroundColor(Color.web("#17231e"));
        getGameWorld().addEntityFactory(entityFactory);

        timer = new RestorationTimer(gameProperties.startTimeSeconds(), gameProperties.maxTimeSeconds());
        projection = new IsoProjection(
                GameConstants.WORLD_ORIGIN_X,
                GameConstants.WORLD_ORIGIN_Y,
                GameConstants.TILE_WIDTH,
                GameConstants.TILE_HEIGHT
        );
        currentLevelIndex = 0;
        gameEnded = false;
        transitionInProgress = false;

        buildWorld();
    }

    @Override
    protected void initUI() {
        hud = new RestorationHud(fxmlViewLoader, getAppWidth());
        challengeOverlay = new ChallengeOverlay(fxmlViewLoader, getAppWidth(), getAppHeight());
        toastLayer = new ToastLayer(fxmlViewLoader, getAppWidth());

        addUINode(hud.root());
        addUINode(challengeOverlay.root());
        addUINode(toastLayer.root());
        updateHud();
    }

    @Override
    protected void onUpdate(double tpf) {
        if (gameEnded || timer == null || currentLevel == null) {
            return;
        }

        timer.tick(tpf);
        if (worldRenderer != null) {
            worldRenderer.render(levelRepository.all(), currentLevelIndex, timer.restorationRatio());
        }
        updateHud();
        updateContextHint();
        checkDecisionDoorChoice();
        checkOpenGateTraversal();

        if (timer.isEmpty()) {
            endGame(false);
        }
    }

    private void bindMovement(String name, KeyCode keyCode, java.util.function.Consumer<Boolean> handler) {
        getInput().addAction(new UserAction(name) {
            @Override
            protected void onActionBegin() {
                handler.accept(true);
            }

            @Override
            protected void onActionEnd() {
                handler.accept(false);
            }
        }, keyCode);
    }

    private void buildWorld() {
        getGameWorld().removeEntities(getGameWorld().getEntitiesCopy());
        gateEntities.clear();
        choiceDoorEntities.clear();
        spawnedLevelCount = 0;

        levelRepository.ensureGeneratedThrough(currentLevelIndex + LevelRepository.GENERATED_AHEAD);
        currentLevel = levelRepository.get(currentLevelIndex);
        worldRenderer = new WorldRenderer(
                GameConstants.WORLD_CANVAS_WIDTH,
                GameConstants.WORLD_CANVAS_HEIGHT,
                projection,
                levelRepository.cityMap()
        );
        worldRenderer.render(levelRepository.all(), currentLevelIndex, timer.restorationRatio());
        entityBuilder()
                .type(RestorationEntityType.WORLD_VIEW)
                .view(worldRenderer.canvas())
                .zIndex(-10_000)
                .buildAndAttach();

        spawnLevelEntities(spawnedLevelCount, levelRepository.count());

        playerEntity = spawn("restorationPlayer", new SpawnData()
                .put("projection", projection)
                .put("levelSupplier", (java.util.function.Supplier<LevelDefinition>) () -> currentLevel)
                .put("spawn", currentLevel.playerSpawn()));
        playerControl = playerEntity.getComponent(PlayerIsoComponent.class);

        var viewport = getGameScene().getViewport();
        viewport.setBounds(0, 0, GameConstants.WORLD_CANVAS_WIDTH, GameConstants.WORLD_CANVAS_HEIGHT);
        viewport.setLazy(true);
        viewport.bindToEntity(playerEntity, getAppWidth() / 2.0, getAppHeight() / 2.0);

        enterLevel(0, true);
    }

    private void enterLevel(int levelIndex, boolean firstEntry) {
        enterLevel(levelIndex, firstEntry, null);
    }

    private void enterLevel(int levelIndex, boolean firstEntry, IsoPoint arrivalPosition) {
        currentLevelIndex = levelIndex;
        levelRepository.ensureGeneratedThrough(currentLevelIndex + LevelRepository.GENERATED_AHEAD);
        spawnLevelEntities(spawnedLevelCount, levelRepository.count());
        currentLevel = levelRepository.get(levelIndex);
        transitionInProgress = false;

        if (challengeOverlay != null) {
            challengeOverlay.hide();
        }

        if (!firstEntry && playerControl != null) {
            IsoPoint destination = arrivalPosition == null
                    ? currentLevel.playerSpawn()
                    : currentLevel.clamp(arrivalPosition, 0.55);
            playerControl.teleport(destination);
            playerControl.setControlsLocked(false);
        }

        if (worldRenderer != null) {
            worldRenderer.render(levelRepository.all(), currentLevelIndex, timer.restorationRatio());
        }

        updateHud();
        if (toastLayer != null) {
            toastLayer.show(currentLevel.title() + " // " + currentLevel.subtitle(), 3.2);
        }
    }

    private void interact() {
        if (gameEnded || transitionInProgress || challengeOverlay == null || playerControl == null) {
            return;
        }

        if (challengeOverlay != null && challengeOverlay.isQuestionOpen()) {
            return;
        }

        Optional<NpcComponent> npc = nearestNpc(1.15);
        if (npc.isPresent()) {
            toastLayer.show(npc.get().nextMessage(), 4.2);
            return;
        }

        nearestGate(1.25)
                .filter(gate -> gate.state() == GateState.SEALED)
                .ifPresent(this::startGateChallenge);
    }

    private void startGateChallenge(GateComponent gate) {
        ChallengeType type = gate.definition().kind() == GateKind.QUESTION
                ? ChallengeType.QUESTION
                : ChallengeType.DECISION;

        QuestionChallenge challenge = questionBank.next(
                difficultyCurve.forGate(currentLevelIndex, gate.definition(), timer.elapsedSeconds()),
                type
        );

        if (type == ChallengeType.QUESTION) {
            playerControl.setControlsLocked(true);
            challengeOverlay.showQuestion(challenge, selectedChoice -> resolveQuestionGate(gate, challenge, selectedChoice));
        } else {
            gate.awaitDecision();
            spawnDecisionDoors(gate, challenge);
            challengeOverlay.showDecisionBriefing(challenge);
            toastLayer.show("Decision doors are live. Walk through the door that matches your choice.", 3.8);
            runOnce(() -> {
                if (!challengeOverlay.isQuestionOpen()) {
                    challengeOverlay.hide();
                }
            }, Duration.seconds(4.8));
        }
    }

    private void resolveQuestionGate(GateComponent gate, QuestionChallenge challenge, int choiceIndex) {
        if (transitionInProgress) {
            return;
        }

        ChallengeResult result = challenge.evaluate(choiceIndex);
        timer.applyDelta(result.deltaSeconds());
        challengeOverlay.hide();
        playerControl.setControlsLocked(false);
        gate.open();
        toastOutcome(result);
        getGameScene().getViewport().shake(result.correct() ? 2 : 5, 0.18);
    }

    private void spawnDecisionDoors(GateComponent gate, QuestionChallenge challenge) {
        removeChoiceDoors();

        int totalChoices = Math.min(gate.definition().choices(), challenge.choices().size());
        List<IsoPoint> wallSlots = currentLevel.wallSlotsNear(gate.definition().position(), totalChoices, 1.85);

        for (int i = 0; i < totalChoices; i++) {
            IsoPoint doorPosition = i < wallSlots.size()
                    ? wallSlots.get(i)
                    : currentLevel.clamp(gate.definition().position().add(i + 1.0, 0), 0.75);
            Entity door = spawn("restorationChoiceDoor", new SpawnData()
                    .put("challenge", challenge)
                    .put("choiceIndex", i)
                    .put("position", doorPosition)
                    .put("projection", projection));
            choiceDoorEntities.add(door);
        }
    }

    private void checkDecisionDoorChoice() {
        if (choiceDoorEntities.isEmpty() || playerControl == null || transitionInProgress) {
            return;
        }

        for (Entity doorEntity : List.copyOf(choiceDoorEntities)) {
            if (!doorEntity.isActive()) {
                continue;
            }

            ChoiceDoorComponent door = doorEntity.getComponent(ChoiceDoorComponent.class);
            if (door.isNear(playerControl.isoPosition(), 0.62)) {
                resolveDecisionDoor(doorEntity, door);
                break;
            }
        }
    }

    private void resolveDecisionDoor(Entity chosenDoorEntity, ChoiceDoorComponent door) {
        ChallengeResult result = door.challenge().evaluate(door.choiceIndex());
        timer.applyDelta(result.deltaSeconds());
        toastOutcome(result);

        Optional<GateComponent> awaitingGate = nearestGate(99)
                .filter(gate -> gate.state() == GateState.AWAITING_DECISION);

        awaitingGate.ifPresent(gate -> {
            gate.open();
            runOnce(gate::closeBehind, Duration.seconds(0.35));
        });

        choiceDoorEntities.stream()
                .filter(entity -> entity != chosenDoorEntity)
                .forEach(Entity::removeFromWorld);
        choiceDoorEntities.clear();
        choiceDoorEntities.add(chosenDoorEntity);

        transitionInProgress = true;
        playerControl.setControlsLocked(true);
        runOnce(() -> {
            removeChoiceDoors();
            awaitingGate.ifPresentOrElse(
                    this::enterGateDestination,
                    () -> enterLevel(currentLevelIndex + 1, false)
            );
        }, Duration.seconds(0.55));
    }

    private void checkOpenGateTraversal() {
        if (transitionInProgress || playerControl == null) {
            return;
        }

        nearestGate(0.72)
                .filter(gate -> gate.state() == GateState.OPEN)
                .ifPresent(this::transitionThroughGate);
    }

    private void transitionThroughGate(GateComponent gate) {
        transitionInProgress = true;
        playerControl.setControlsLocked(true);
        gate.closeBehind();
        toastLayer.show("Gate sealed behind you. No return route remains.", 2.3);

        runOnce(() -> enterGateDestination(gate), Duration.seconds(0.65));
    }

    private void enterGateDestination(GateComponent gate) {
        GateDefinition definition = gate.definition();
        enterLevel(definition.destinationLevelIndex(), false, definition.destinationPosition());
    }

    private void spawnLevelEntities(int fromIndex, int toIndexExclusive) {
        for (int levelIndex = fromIndex; levelIndex < toIndexExclusive; levelIndex++) {
            LevelDefinition level = levelRepository.get(levelIndex);
            for (GateDefinition gate : level.gates()) {
                Entity gateEntity = spawn("restorationGate", new SpawnData()
                        .put("gate", gate)
                        .put("projection", projection)
                        .put("levelIndex", levelIndex));
                gateEntities.add(gateEntity);
            }

            int capturedLevelIndex = levelIndex;
            level.npcs().forEach(npc -> spawn("restorationNpc", new SpawnData()
                    .put("npc", npc)
                    .put("projection", projection)
                    .put("levelIndex", capturedLevelIndex)));
        }

        spawnedLevelCount = Math.max(spawnedLevelCount, toIndexExclusive);
    }

    private void removeChoiceDoors() {
        getGameWorld().removeEntities(choiceDoorEntities);
        choiceDoorEntities.clear();
    }

    private Optional<GateComponent> nearestGate(double radius) {
        if (playerControl == null) {
            return Optional.empty();
        }

        GateComponent nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        IsoPoint playerPosition = playerControl.isoPosition();

        for (Entity gateEntity : gateEntities) {
            if (!gateEntity.isActive()) {
                continue;
            }

            if (gateEntity.getInt("levelIndex") != currentLevelIndex) {
                continue;
            }

            GateComponent gate = gateEntity.getComponent(GateComponent.class);
            double distance = gate.definition().position().distance(playerPosition);
            if (distance <= radius && distance < nearestDistance) {
                nearest = gate;
                nearestDistance = distance;
            }
        }

        return Optional.ofNullable(nearest);
    }

    private Optional<NpcComponent> nearestNpc(double radius) {
        if (playerControl == null) {
            return Optional.empty();
        }

        return getGameWorld().getEntitiesByComponent(NpcComponent.class).stream()
                .filter(entity -> entity.getInt("levelIndex") == currentLevelIndex)
                .map(entity -> entity.getComponent(NpcComponent.class))
                .filter(npc -> npc.isNear(playerControl.isoPosition(), radius))
                .findFirst();
    }

    private void updateContextHint() {
        if (hud == null || playerControl == null) {
            return;
        }

        if (challengeOverlay != null && challengeOverlay.isQuestionOpen()) {
            hud.setHint("Choose with 1, 2, or 3. The timer keeps draining.");
            return;
        }

        if (nearestNpc(1.15).isPresent()) {
            hud.setHint("Press E to talk.");
            return;
        }

        Optional<GateComponent> nearestGate = nearestGate(1.25);
        if (nearestGate.isPresent()) {
            GateComponent gate = nearestGate.get();
            String hint = switch (gate.state()) {
                case SEALED -> "Press E to face the sealed gate.";
                case AWAITING_DECISION -> "Walk through one decision door to continue.";
                case OPEN -> "Move into the open gate.";
                case CLOSED_BEHIND -> "";
            };
            hud.setHint(hint);
            return;
        }

        hud.setHint("WASD or arrow keys move. E interacts. Esc pauses.");
    }

    private void updateHud() {
        if (hud == null || timer == null || currentLevel == null) {
            return;
        }

        hud.setLevel(currentLevel.title(), currentLevelIndex);
        hud.setObjective(currentLevel.subtitle());
        hud.setTime(timer.currentSeconds(), timer.maxSeconds(), timer.restorationRatio());
    }

    private void chooseQuestionAnswer(int oneBasedChoice) {
        if (challengeOverlay != null) {
            challengeOverlay.chooseByNumber(oneBasedChoice);
        }
    }

    private void toastOutcome(ChallengeResult result) {
        String sign = result.deltaSeconds() > 0 ? "+" : "";
        toastLayer.show(result.feedback() + "  [" + sign + result.deltaSeconds() + "s]", 3.5);
    }

    private void endGame(boolean victory) {
        if (gameEnded) {
            return;
        }

        gameEnded = true;
        transitionInProgress = true;
        if (playerControl != null) {
            playerControl.setControlsLocked(true);
        }
        if (challengeOverlay != null) {
            challengeOverlay.hide();
        }

        String message = victory
                ? "RESTORATION STABILIZED\nThe canopy holds, and the sealed path is complete."
                : "RESTORATION FAILED\nThe time reserve is empty.";

        getDialogService().showMessageBox(message, () -> getGameController().gotoMainMenu());
    }

    private void withPlayer(java.util.function.Consumer<PlayerIsoComponent> action) {
        if (playerControl != null) {
            action.accept(playerControl);
        }
    }
}
