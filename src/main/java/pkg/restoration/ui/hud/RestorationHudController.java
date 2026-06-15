package pkg.restoration.ui.hud;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public final class RestorationHudController {

    @FXML
    private Pane root;

    @FXML
    private Text levelText;

    @FXML
    private Text timeText;

    @FXML
    private Text objectiveText;

    @FXML
    private Text hintText;

    @FXML
    private ProgressBar timeBar;

    public void configure(double appWidth) {
        root.setPickOnBounds(false);
        root.setMouseTransparent(true);
        hintText.setTranslateX(appWidth / 2.0 - 260);
    }

    public void setLevel(String title, int currentIndex) {
        levelText.setText("RESTORATION // " + title + "  [RUN " + (currentIndex + 1) + "]");
    }

    public void setObjective(String objective) {
        objectiveText.setText(objective);
    }

    public void setHint(String hint) {
        hintText.setText(hint == null ? "" : hint);
    }

    public void setTime(double currentSeconds, double maxSeconds, double ratio) {
        double progress = maxSeconds <= 0 ? 0 : currentSeconds / maxSeconds;
        Color fill = Color.web("#e15f41").interpolate(Color.web("#76d66a"), ratio);

        timeBar.setProgress(progress);
        timeBar.setStyle("-fx-accent: " + toRgb(fill) + "; -fx-control-inner-background: #20231f;");
        timeText.setText(String.format("TIME RESERVE // %05.1fs", currentSeconds));
    }

    private static String toRgb(Color color) {
        int red = (int) Math.round(color.getRed() * 255);
        int green = (int) Math.round(color.getGreen() * 255);
        int blue = (int) Math.round(color.getBlue() * 255);
        return "rgb(" + red + "," + green + "," + blue + ")";
    }
}
