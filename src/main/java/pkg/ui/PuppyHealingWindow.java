package pkg.ui;

/**
 * In-game healing overlay for the injured puppy.
 * Integrated directly into the game window via FXGL UI layer.
 */
public class PuppyHealingWindow extends BaseAnimalHealingOverlay {

    public PuppyHealingWindow(Runnable onComplete) {
        this(onComplete, null);
    }

    public PuppyHealingWindow(Runnable onComplete, Runnable onClose) {
        super(
                "🐾 ANIMAL RESCUE — HEAL THE PUPPY",
                "/assets/textures/injured_puppy_window.png",
                "/assets/textures/pliers_puppy_window.png",
                "/assets/textures/bandage_window.png",
                "/assets/textures/pliers.png",
                "Pliers",
                "pliers",
                "/assets/textures/bandage.png",
                "Bandage",
                "bandage",
                "Use Pliers to pluck the thorn from paw (Drag or Click)",
                "Apply Bandage to wrap the injured paw (Drag or Click)",
                "Puppy is fully healed! Sector restored!",
                onComplete,
                onClose
        );
    }
}
