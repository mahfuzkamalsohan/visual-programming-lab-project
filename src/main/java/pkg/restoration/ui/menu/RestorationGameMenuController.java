package pkg.restoration.ui.menu;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public final class RestorationGameMenuController {

    @FXML
    private StackPane root;

    @FXML
    private Rectangle veil;

    @FXML
    private Button resumeButton;

    @FXML
    private Button restartButton;

    @FXML
    private Button mainMenuButton;

    @FXML
    private Button exitButton;

    public void configure(double width, double height, RestorationMenuActions actions) {
        root.setPrefSize(width, height);
        veil.setWidth(width);
        veil.setHeight(height);

        resumeButton.setOnAction(event -> actions.resume());
        restartButton.setOnAction(event -> actions.newGame());
        mainMenuButton.setOnAction(event -> actions.exitToMainMenu());
        exitButton.setOnAction(event -> actions.exit());
    }
}
