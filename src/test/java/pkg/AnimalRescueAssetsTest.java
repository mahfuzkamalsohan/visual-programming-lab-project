package pkg;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class AnimalRescueAssetsTest {

    @Test
    void allRabbitAndPuppyTexturesExist() {
        List<String> textures = List.of(
                "/assets/textures/injured_rabit.png",
                "/assets/textures/healed_rabit.png",
                "/assets/textures/injured_window.png",
                "/assets/textures/ointment_window.png",
                "/assets/textures/bandaid_window.png",
                "/assets/textures/ointment.png",
                "/assets/textures/bandaid.png",
                "/assets/textures/injured_puppy.png",
                "/assets/textures/healed_puppy.png",
                "/assets/textures/injured_puppy_window.png",
                "/assets/textures/pliers_puppy_window.png",
                "/assets/textures/bandage_window.png",
                "/assets/textures/pliers.png",
                "/assets/textures/bandage.png"
        );

        for (String texturePath : textures) {
            try (InputStream is = getClass().getResourceAsStream(texturePath)) {
                assertNotNull(is, "Asset texture missing from classpath: " + texturePath);
            } catch (Exception e) {
                throw new AssertionError("Failed to load texture: " + texturePath, e);
            }
        }
    }

    @Test
    void entityTypesContainRabbitAndPuppy() {
        assertNotNull(EntityType.valueOf("RABBIT"));
        assertNotNull(EntityType.valueOf("PUPPY"));
    }

    @Test
    void healingWindowsAreStackPanesNotStages() {
        // Must be in-game UI Nodes (StackPane), NOT separate desktop windows (Stage)
        org.junit.jupiter.api.Assertions.assertTrue(
                javafx.scene.layout.StackPane.class.isAssignableFrom(pkg.ui.RabbitHealingWindow.class),
                "RabbitHealingWindow must extend StackPane for in-game integration"
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                javafx.scene.layout.StackPane.class.isAssignableFrom(pkg.ui.PuppyHealingWindow.class),
                "PuppyHealingWindow must extend StackPane for in-game integration"
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                javafx.stage.Stage.class.isAssignableFrom(pkg.ui.RabbitHealingWindow.class),
                "RabbitHealingWindow must not extend Stage"
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                javafx.stage.Stage.class.isAssignableFrom(pkg.ui.PuppyHealingWindow.class),
                "PuppyHealingWindow must not extend Stage"
        );
    }

    @Test
    void playerComponentMovementFrozenBlocksMovement() {
        PlayerComponent playerComp = new PlayerComponent(1);
        playerComp.setUp(true);
        playerComp.setRight(true);
        org.junit.jupiter.api.Assertions.assertTrue(playerComp.isMoving(), "Player should be moving before freeze");

        playerComp.setMovementFrozen(true);
        org.junit.jupiter.api.Assertions.assertTrue(playerComp.isMovementFrozen(), "PlayerComponent should report frozen");
        org.junit.jupiter.api.Assertions.assertFalse(playerComp.isMoving(), "Frozen player should immediately stop moving");

        // Attempt to press keys while frozen
        playerComp.setUp(true);
        playerComp.setDown(true);
        playerComp.setLeft(true);
        playerComp.setRight(true);
        org.junit.jupiter.api.Assertions.assertFalse(playerComp.isMoving(), "Player must ignore direction presses while frozen");

        // Unfreeze
        playerComp.setMovementFrozen(false);
        org.junit.jupiter.api.Assertions.assertFalse(playerComp.isMovementFrozen(), "Player should no longer be frozen");
        playerComp.setUp(true);
        org.junit.jupiter.api.Assertions.assertTrue(playerComp.isMoving(), "Player should move again when unfrozen");
    }
}

