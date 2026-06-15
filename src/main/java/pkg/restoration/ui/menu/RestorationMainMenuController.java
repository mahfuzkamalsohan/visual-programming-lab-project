package pkg.restoration.ui.menu;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public final class RestorationMainMenuController {

    @FXML
    private StackPane root;

    @FXML
    private Canvas backgroundCanvas;

    @FXML
    private Button beginButton;

    @FXML
    private Button exitButton;

    public void configure(double width, double height, RestorationMenuActions actions) {
        root.setPrefSize(width, height);
        backgroundCanvas.setWidth(width);
        backgroundCanvas.setHeight(height);
        drawBackground(width, height);

        beginButton.setOnAction(event -> actions.newGame());
        exitButton.setOnAction(event -> actions.exit());
    }

    private void drawBackground(double width, double height) {
        GraphicsContext gc = backgroundCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#17231e"));
        gc.fillRect(0, 0, width, height);

        gc.setFill(Color.web("#6fa85f", 0.42));
        for (int y = 0; y < height; y += 56) {
            for (int x = -80; x < width; x += 112) {
                double offset = (y / 56) % 2 == 0 ? 0 : 56;
                double cx = x + offset;
                gc.fillPolygon(
                        new double[] {cx, cx + 56, cx + 112, cx + 56},
                        new double[] {y + 28, y, y + 28, y + 56},
                        4
                );
            }
        }

        gc.setFill(Color.web("#0d1512", 0.42));
        gc.fillRect(0, 0, width, height);
        gc.setFill(Color.web("#f1d090", 0.18));
        gc.fillOval(width - 260, 80, 140, 140);
    }
}
