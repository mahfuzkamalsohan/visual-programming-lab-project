package pkg.restoration;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;

import pkg.restoration.ui.fxml.FxmlViewLoader;
import pkg.restoration.ui.fxml.LoadedFxml;
import pkg.restoration.ui.menu.RestorationMainMenuController;
import pkg.restoration.ui.menu.RestorationMenuActions;

public final class RestorationMainMenu extends FXGLMenu {

    public RestorationMainMenu(MenuType type, FxmlViewLoader fxmlViewLoader) {
        super(type);

        LoadedFxml<RestorationMainMenuController> view = fxmlViewLoader.load(
                "/fxml/restoration/main-menu.fxml",
                RestorationMainMenuController.class
        );
        view.controller().configure(FXGL.getAppWidth(), FXGL.getAppHeight(), new RestorationMenuActions() {
            @Override
            public void newGame() {
                fireNewGame();
            }

            @Override
            public void exit() {
                fireExit();
            }
        });

        getContentRoot().getChildren().add(view.root());
    }
}
