package pkg.restoration;

import static com.almasb.fxgl.dsl.FXGL.addUINode;
import static com.almasb.fxgl.dsl.FXGL.getAppHeight;
import static com.almasb.fxgl.dsl.FXGL.getAppWidth;
import static com.almasb.fxgl.dsl.FXGL.getDialogService;
import static com.almasb.fxgl.dsl.FXGL.getGameController;
import static com.almasb.fxgl.dsl.FXGL.getGameScene;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;
import static com.almasb.fxgl.dsl.FXGL.getInput;
import static com.almasb.fxgl.dsl.FXGL.runOnce;
import static com.almasb.fxgl.dsl.FXGL.spawn;
import static com.almasb.fxgl.dsl.FXGL.setLevelFromMap;

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

import javafx.geometry.Point2D;
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

public final class RestorationGameApp extends GameApplication {

    private static final double NPC_BLOCK_RADIUS = 30.0; 
    private static final double GATE_BLOCK_RADIUS = 28.0;
    private static final double CHOICE_DOOR_BLOCK_RADIUS = 20.0;

    private final List<Entity> gateEntities = new ArrayList<>();
    private final List<Entity> choiceDoorEntities = new ArrayList<>();

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
    private int currentLevelIndex;
    private Entity playerEntity;
    private PlayerIsoComponent playerControl;
    private RestorationHud hud;
    private ChallengeOverlay challengeOverlay;
    private ToastLayer toastLayer;
    private boolean transitionInProgress;
    private boolean gameEnded;

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
        currentLevelIndex = 0;
        gameEnded = false;
        transitionInProgress = false;

        loadTiledLevel(currentLevelIndex);
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
        if (gameEnded || timer == null) {
            return;
        }

        timer.tick(tpf);
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

    private void loadTiledLevel(int levelIndex) {
        transitionInProgress = true;
        gateEntities.clear();
        choiceDoorEntities.clear();

        if (challengeOverlay != null) {
            challengeOverlay.hide();
        }

        // FIX: Execute the void method directly without assigning it to a Level variable
        setLevelFromMap("tmx/level_" + levelIndex + ".tmx");

        // Cache gates loaded from Tiled layer objects via the factory
        gateEntities.addAll(getGameWorld().getEntitiesByType(RestorationEntityType.GATE));

        // Locate player singleton instantiated from Tiled Object Layer
        playerEntity = getGameWorld().getSingleton(RestorationEntityType.PLAYER);
        playerControl = playerEntity.getComponent(PlayerIsoComponent.class);
        
        playerControl.setMovementValidator(this::canPlayerOccupy);

        // FIX: Bind the viewport to follow the player without querying missing map dimensions
        var viewport = getGameScene().getViewport();
        viewport.setLazy(true);
        viewport.bindToEntity(playerEntity, getAppWidth() / 2.0, getAppHeight() / 2.0);

        currentLevelIndex = levelIndex;
        transitionInProgress = false;
        updateHud();

        if (toastLayer != null) {
            toastLayer.show("Entering Sector " + levelIndex, 3.2);
        }
    }
    private void interact() {
        if (gameEnded || transitionInProgress || challengeOverlay == null || playerControl == null) {
            return;
        }

        if (challengeOverlay.isQuestionOpen()) {
            return;
        }

        Optional<NpcComponent> npc = nearestNpc(60.0);
        if (npc.isPresent()) {
            toastLayer.show(npc.get().nextMessage(), 4.2);
            return;
        }

        nearestGate(70.0)
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
            toastLayer.show("Decision doors are live. Walk through the right path.", 3.8);
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
        Point2D gatePos = gate.getEntity().getPosition();

        int totalChoices = Math.min(gate.definition().choices(), challenge.choices().size());
        for (int i = 0; i < totalChoices; i++) {
            // Generates layout offsets relative to the Tiled Gate position coordinate
            Point2D doorPosition = gatePos.add((i - (totalChoices / 2.0)) * 80.0, 60.0);
            
            Entity door = spawn("restorationChoiceDoor", new SpawnData()
                    .put("challenge", challenge)
                    .put("choiceIndex", i)
                    .put("position", doorPosition));
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
            if (door.getEntity().getPosition().distance(playerEntity.getPosition()) < 40.0) {
                resolveDecisionDoor(doorEntity, door);
                break;
            }
        }
    }

    private void resolveDecisionDoor(Entity chosenDoorEntity, ChoiceDoorComponent door) {
        ChallengeResult result = door.challenge().evaluate(door.choiceIndex());
        timer.applyDelta(result.deltaSeconds());
        toastOutcome(result);

        Optional<GateComponent> awaitingGate = nearestGate(9999)
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
            loadTiledLevel(currentLevelIndex + 1);
        }, Duration.seconds(0.55));
    }

    private void checkOpenGateTraversal() {
        if (transitionInProgress || playerControl == null) {
            return;
        }

        nearestGate(45.0)
                .filter(gate -> gate.state() == GateState.OPEN)
                .ifPresent(this::transitionThroughGate);
    }

    private void transitionThroughGate(GateComponent gate) {
        transitionInProgress = true;
        playerControl.setControlsLocked(true);
        gate.closeBehind();
        toastLayer.show("Gate sealed. Progressing ahead...", 2.3);

        runOnce(() -> loadTiledLevel(currentLevelIndex + 1), Duration.seconds(0.65));
    }

    private void removeChoiceDoors() {
        getGameWorld().removeEntities(choiceDoorEntities);
        choiceDoorEntities.clear();
    }

    private boolean canPlayerOccupy(Point2D position) {
        // Checks static wall object tiles populated by Tiled map layer types
        boolean hitsWall = getGameWorld().getEntitiesAt(position).stream()
                .anyMatch(e -> e.isType(RestorationEntityType.WALL));
                
        if (hitsWall) return false;

        return !isBlockedByNpc(position)
                && !isBlockedByGate(position)
                && !isBlockedByChoiceDoor(position);
    }

    private boolean isBlockedByNpc(Point2D position) {
        return getGameWorld().getEntitiesByComponent(NpcComponent.class).stream()
                .map(entity -> entity.getComponent(NpcComponent.class))
                .anyMatch(npc -> npc.getEntity().getPosition().distance(position) < NPC_BLOCK_RADIUS);
    }

    private boolean isBlockedByGate(Point2D position) {
        return gateEntities.stream()
                .filter(Entity::isActive)
                .map(entity -> entity.getComponent(GateComponent.class))
                .filter(gate -> gate.state() != GateState.OPEN)
                .anyMatch(gate -> gate.getEntity().getPosition().distance(position) < GATE_BLOCK_RADIUS);
    }

    private boolean isBlockedByChoiceDoor(Point2D position) {
        return choiceDoorEntities.stream()
                .filter(Entity::isActive)
                .map(entity -> entity.getComponent(ChoiceDoorComponent.class))
                .anyMatch(door -> door.getEntity().getPosition().distance(position) < CHOICE_DOOR_BLOCK_RADIUS);
    }

    private Optional<GateComponent> nearestGate(double radius) {
        if (playerEntity == null) return Optional.empty();

        GateComponent nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Point2D playerPos = playerEntity.getPosition();

        for (Entity gateEntity : gateEntities) {
            if (!gateEntity.isActive()) continue;

            GateComponent gate = gateEntity.getComponent(GateComponent.class);
            double distance = gate.getEntity().getPosition().distance(playerPos);
            if (distance <= radius && distance < nearestDistance) {
                nearest = gate;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private Optional<NpcComponent> nearestNpc(double radius) {
        if (playerEntity == null) return Optional.empty();

        return getGameWorld().getEntitiesByComponent(NpcComponent.class).stream()
                .map(entity -> entity.getComponent(NpcComponent.class))
                .filter(npc -> npc.getEntity().getPosition().distance(playerEntity.getPosition()) <= radius)
                .findFirst();
    }

    private void updateContextHint() {
        if (hud == null || playerControl == null) return;

        if (challengeOverlay != null && challengeOverlay.isQuestionOpen()) {
            hud.setHint("Choose with 1, 2, or 3. The timer keeps draining.");
            return;
        }

        if (nearestNpc(60.0).isPresent()) {
            hud.setHint("Press E to talk.");
            return;
        }

        Optional<GateComponent> nearestGate = nearestGate(70.0);
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
        if (hud == null || timer == null) return;

        hud.setLevel("Sector " + currentLevelIndex, currentLevelIndex);
        hud.setObjective("Find the exit node.");
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
        if (gameEnded) return;

        gameEnded = true;
        transitionInProgress = true;
        if (playerControl != null) playerControl.setControlsLocked(true);
        if (challengeOverlay != null) challengeOverlay.hide();

        String message = victory
                ? "RESTORATION STABILIZED\nThe canopy holds, and the path is complete."
                : "RESTORATION FAILED\nThe time reserve is empty.";

        getDialogService().showMessageBox(message, () -> getGameController().gotoMainMenu());
    }

    private void withPlayer(java.util.function.Consumer<PlayerIsoComponent> action) {
        if (playerControl != null) {
            action.accept(playerControl);
        }
    }
}