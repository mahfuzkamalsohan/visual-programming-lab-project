package pkg.restoration.ui;

import javafx.scene.Parent;
import pkg.restoration.ui.fxml.FxmlViewLoader;
import pkg.restoration.ui.fxml.LoadedFxml;
import pkg.restoration.ui.toast.ToastLayerController;

public final class ToastLayer {

    private final Parent root;
    private final ToastLayerController controller;

    public ToastLayer(FxmlViewLoader fxmlViewLoader, double appWidth) {
        LoadedFxml<ToastLayerController> loaded = fxmlViewLoader.load(
                "/fxml/restoration/toast-layer.fxml",
                ToastLayerController.class
        );
        root = loaded.root();
        controller = loaded.controller();
        controller.configure(appWidth);
    }

    public Parent root() {
        return root;
    }

    public void show(String text, double seconds) {
        controller.show(text, seconds);
    }
}
