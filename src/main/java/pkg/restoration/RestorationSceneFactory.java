package pkg.restoration;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;

import org.springframework.stereotype.Component;

import pkg.restoration.ui.fxml.FxmlViewLoader;

@Component
public final class RestorationSceneFactory extends SceneFactory {

    private final FxmlViewLoader fxmlViewLoader;

    public RestorationSceneFactory(FxmlViewLoader fxmlViewLoader) {
        this.fxmlViewLoader = fxmlViewLoader;
    }

    @Override
    public FXGLMenu newMainMenu() {
        return new RestorationMainMenu(MenuType.MAIN_MENU, fxmlViewLoader);
    }

    @Override
    public FXGLMenu newGameMenu() {
        return new RestorationGameMenu(MenuType.GAME_MENU, fxmlViewLoader);
    }
}
