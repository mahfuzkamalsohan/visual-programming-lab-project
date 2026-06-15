package pkg.restoration;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;

import pkg.restoration.ui.fxml.FxmlViewLoader;
import pkg.restoration.ui.fxml.LoadedFxml;
import pkg.restoration.ui.menu.RestorationGameMenuController;
import pkg.restoration.ui.menu.RestorationMenuActions;

public final class RestorationGameMenu extends FXGLMenu {

    public RestorationGameMenu(MenuType type, FxmlViewLoader fxmlViewLoader) {
        super(type);

        LoadedFxml<RestorationGameMenuController> view = fxmlViewLoader.load(
                "/fxml/restoration/game-menu.fxml",
                RestorationGameMenuController.class
        );
        view.controller().configure(FXGL.getAppWidth(), FXGL.getAppHeight(), new RestorationMenuActions() {
            @Override
            public void newGame() {
                fireNewGame();
            }

            @Override
            public void resume() {
                fireResume();
            }

            @Override
            public void exitToMainMenu() {
                fireExitToMainMenu();
            }

            @Override
            public void exit() {
                fireExit();
            }
        });

        getContentRoot().getChildren().add(view.root());
    }
}
