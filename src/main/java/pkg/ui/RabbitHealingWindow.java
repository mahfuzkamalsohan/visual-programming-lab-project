package pkg.ui;

/**
 * In-game healing overlay for the injured rabbit.
 * Integrated directly into the game window via FXGL UI layer.
 */
public class RabbitHealingWindow extends BaseAnimalHealingOverlay {

    public RabbitHealingWindow(Runnable onComplete) {
        this(onComplete, null);
    }

    public RabbitHealingWindow(Runnable onComplete, Runnable onClose) {
        super(
                "🐾 ANIMAL RESCUE — HEAL THE RABBIT",
                "/assets/textures/injured_window.png",
                "/assets/textures/ointment_window.png",
                "/assets/textures/bandaid_window.png",
                "/assets/textures/ointment.png",
                "Ointment",
                "ointment",
                "/assets/textures/bandaid.png",
                "Bandaid",
                "bandaid",
                "Apply Ointment to treat the injury (Drag or Click)",
                "Apply Bandaid over the treated wound (Drag or Click)",
                "Rabbit is fully healed! Sector restored!",
                onComplete,
                onClose
        );
    }
}
