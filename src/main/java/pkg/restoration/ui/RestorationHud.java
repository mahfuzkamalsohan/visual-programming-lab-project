package pkg.restoration.ui;

import javafx.scene.Parent;
import pkg.restoration.ui.fxml.FxmlViewLoader;
import pkg.restoration.ui.fxml.LoadedFxml;
import pkg.restoration.ui.hud.RestorationHudController;

public final class RestorationHud {

    private final Parent root;
    private final RestorationHudController controller;

    public RestorationHud(FxmlViewLoader fxmlViewLoader, double appWidth) {
        LoadedFxml<RestorationHudController> loaded = fxmlViewLoader.load(
                "/fxml/restoration/hud.fxml",
                RestorationHudController.class
        );
        root = loaded.root();
        controller = loaded.controller();
        controller.configure(appWidth);
    }

    public Parent root() {
        return root;
    }

    public void setLevel(String title, int currentIndex) {
        controller.setLevel(title, currentIndex);
    }

    public void setObjective(String objective) {
        controller.setObjective(objective);
    }

    public void setHint(String hint) {
        controller.setHint(hint);
    }

    public void setTime(double currentSeconds, double maxSeconds, double ratio) {
        controller.setTime(currentSeconds, maxSeconds, ratio);
    }
}
