package pkg;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;

public class NeonSceneFactory extends SceneFactory {
    @Override
    public FXGLMenu newMainMenu() {
        return new NeonMainMenu(MenuType.MAIN_MENU);
    }
}