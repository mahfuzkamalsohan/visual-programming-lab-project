package pkg.restoration.ui.fxml;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

@Component
public final class FxmlViewLoader {

    private final ApplicationContext applicationContext;

    public FxmlViewLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public <T> LoadedFxml<T> load(String resourcePath, Class<T> controllerType) {
        URL resource = Objects.requireNonNull(
                getClass().getResource(resourcePath),
                "FXML resource not found: " + resourcePath
        );

        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(applicationContext::getBean);

        try {
            Parent root = loader.load();
            return new LoadedFxml<>(root, controllerType.cast(loader.getController()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load FXML resource: " + resourcePath, exception);
        }
    }
}
