package pkg.restoration.components;

import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import pkg.restoration.questions.QuestionChallenge;

public final class ChoiceDoorComponent extends Component {

    private final QuestionChallenge challenge;
    private final int choiceIndex;
    private final Point2D position; // Changed from IsoPoint to Point2D

    // Ensure the constructor signature matches this exactly:
    public ChoiceDoorComponent(QuestionChallenge challenge, int choiceIndex, Point2D position) {
        this.challenge = challenge;
        this.choiceIndex = choiceIndex;
        this.position = position;
    }

    @Override
    public void onAdded() {
        // Set the entity's position directly using pixel coordinates
        entity.setPosition(position);
        
        // Setup your choice door rendering views here if needed (e.g., textures/text labels)
    }

    public QuestionChallenge challenge() {
        return challenge;
    }

    public int choiceIndex() {
        return choiceIndex;
    }
}