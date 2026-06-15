package pkg.restoration.ui.toast;

import static com.almasb.fxgl.dsl.FXGL.runOnce;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.util.Duration;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public final class ToastLayerController {

    @FXML
    private StackPane root;

    @FXML
    private Text message;

    private int version;

    public void configure(double appWidth) {
        root.setPrefWidth(appWidth);
        root.setMouseTransparent(true);
        root.setVisible(false);
    }

    public void show(String text, double seconds) {
        int currentVersion = ++version;
        message.setText(text);
        root.setVisible(true);

        runOnce(() -> {
            if (version == currentVersion) {
                root.setVisible(false);
            }
        }, Duration.seconds(seconds));
    }
}
