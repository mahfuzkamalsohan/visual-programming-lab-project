package pkg.restoration.ui;

import java.util.function.IntConsumer;

import javafx.scene.Parent;
import pkg.restoration.questions.QuestionChallenge;
import pkg.restoration.ui.challenge.ChallengeOverlayController;
import pkg.restoration.ui.fxml.FxmlViewLoader;
import pkg.restoration.ui.fxml.LoadedFxml;

public final class ChallengeOverlay {

    private final Parent root;
    private final ChallengeOverlayController controller;

    public ChallengeOverlay(FxmlViewLoader fxmlViewLoader, double appWidth, double appHeight) {
        LoadedFxml<ChallengeOverlayController> loaded = fxmlViewLoader.load(
                "/fxml/restoration/challenge-overlay.fxml",
                ChallengeOverlayController.class
        );
        root = loaded.root();
        controller = loaded.controller();
        controller.configure(appWidth, appHeight);
    }

    public Parent root() {
        return root;
    }

    public void showQuestion(QuestionChallenge challenge, IntConsumer answerHandler) {
        controller.showQuestion(challenge, answerHandler);
    }

    public void showDecisionBriefing(QuestionChallenge challenge) {
        controller.showDecisionBriefing(challenge);
    }

    public boolean isQuestionOpen() {
        return controller.isQuestionOpen();
    }

    public void chooseByNumber(int oneBasedChoice) {
        controller.chooseByNumber(oneBasedChoice);
    }

    public void hide() {
        controller.hide();
    }
}
