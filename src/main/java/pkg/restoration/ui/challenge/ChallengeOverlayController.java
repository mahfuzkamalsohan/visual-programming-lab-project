package pkg.restoration.ui.challenge;

import java.util.function.IntConsumer;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import pkg.restoration.questions.QuestionChallenge;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public final class ChallengeOverlayController {

    private static final String CHOICE_BUTTON_STYLE = """
            -fx-background-color: #26372e;
            -fx-border-color: #d8e77f;
            -fx-border-width: 1;
            -fx-text-fill: #f7f4dc;
            -fx-font-family: Verdana;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-alignment: center-left;
            -fx-padding: 8 12 8 12;
            """;

    @FXML
    private StackPane root;

    @FXML
    private Text title;

    @FXML
    private Text prompt;

    @FXML
    private VBox choiceBox;

    private IntConsumer answerHandler;
    private QuestionChallenge activeChallenge;
    private boolean questionOpen;

    public void configure(double appWidth, double appHeight) {
        root.setPrefSize(appWidth, appHeight);
        hide();
    }

    public void showQuestion(QuestionChallenge challenge, IntConsumer answerHandler) {
        this.activeChallenge = challenge;
        this.answerHandler = answerHandler;
        this.questionOpen = true;
        root.setMouseTransparent(false);
        title.setText(challenge.difficulty() + " QUESTION // Choose with 1-3 or click");
        prompt.setText(challenge.prompt());
        choiceBox.getChildren().clear();

        for (int i = 0; i < challenge.choices().size(); i++) {
            int choiceIndex = i;
            Button button = new Button((i + 1) + ". " + challenge.choices().get(i));
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(38);
            button.setStyle(CHOICE_BUTTON_STYLE);
            button.setOnAction(event -> choose(choiceIndex));
            choiceBox.getChildren().add(button);
        }

        root.setVisible(true);
    }

    public void showDecisionBriefing(QuestionChallenge challenge) {
        this.activeChallenge = challenge;
        this.answerHandler = null;
        this.questionOpen = false;
        root.setMouseTransparent(true);
        title.setText(challenge.difficulty() + " DECISION // Walk through a door to commit");
        prompt.setText(challenge.prompt());
        choiceBox.getChildren().clear();

        for (int i = 0; i < challenge.choices().size(); i++) {
            Text choice = new Text((i + 1) + ". " + challenge.choices().get(i));
            choice.setFill(Color.web("#f8ffe8"));
            choice.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
            choice.setWrappingWidth(710);
            choiceBox.getChildren().add(choice);
        }

        root.setVisible(true);
    }

    public boolean isQuestionOpen() {
        return questionOpen && root.isVisible();
    }

    public void chooseByNumber(int oneBasedChoice) {
        if (!isQuestionOpen() || activeChallenge == null) {
            return;
        }

        int choiceIndex = oneBasedChoice - 1;
        if (choiceIndex >= 0 && choiceIndex < activeChallenge.choices().size()) {
            choose(choiceIndex);
        }
    }

    public void hide() {
        root.setVisible(false);
        activeChallenge = null;
        answerHandler = null;
        questionOpen = false;
    }

    private void choose(int choiceIndex) {
        if (answerHandler != null) {
            answerHandler.accept(choiceIndex);
        }
    }
}
