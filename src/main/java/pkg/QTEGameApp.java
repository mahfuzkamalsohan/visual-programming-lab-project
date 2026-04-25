package pkg;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;

public class QTEGameApp extends GameApplication {

    // List to keep track of our button components for the win check
    private List<QTEComponent> qteComponents = new ArrayList<>();

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("Circular QTE Sequence");
        settings.setVersion("1.0");
    }

    @Override
    protected void initInput() {
        // FXGL requires a unique string name for every input action
        FXGL.onKeyDown(KeyCode.W, "Press_W", () -> handleInput(KeyCode.W));
        FXGL.onKeyDown(KeyCode.A, "Press_A", () -> handleInput(KeyCode.A));
        FXGL.onKeyDown(KeyCode.S, "Press_S", () -> handleInput(KeyCode.S));
    }

    @Override
    protected void initGame() {
        // Clear list in case of game restart
        qteComponents.clear();

        // Spawn buttons at specific positions with different speeds
        // W: Slow, A: Medium, S: Fast
        spawnQTEButton(KeyCode.W, 200, 300, 0.5);
        spawnQTEButton(KeyCode.A, 400, 300, 0.5);
        spawnQTEButton(KeyCode.S, 600, 300, 0.5);

        
    }

    private void spawnQTEButton(KeyCode key, double x, double y, double speed) {
        QTEComponent comp = new QTEComponent(key, speed);
        qteComponents.add(comp);

        FXGL.entityBuilder()
                .at(x, y)
                .with(comp)
                .buildAndAttach();
    }

    private void handleInput(KeyCode key) {
        // Find the component associated with this key and tell it it was pressed
        for (QTEComponent comp : qteComponents) {
            if (comp.getTriggerKey() == key) {
                comp.press();
            }
        }

        // Check if all buttons are currently in the 'Success' (Gold) state
        checkWinCondition();
    }

    private void checkWinCondition() {
        boolean allSuccess = true;
        for (QTEComponent comp : qteComponents) {
            if (!comp.isSuccess()) {
                allSuccess = false;
                break;
            }
        }

        if (allSuccess) {
            FXGL.getDialogService().showMessageBox("PERFECT SEQUENCE!", () -> {
                // This runs after the user clicks OK on the dialog
                initGame(); 
            });
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}