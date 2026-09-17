package pkg;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;
import javafx.util.Duration;

public class BirdComponent extends Component {

    private AnimatedTexture texture;
    private AnimationChannel animFly;
    private double speed = 150.0; // pixels per second

    public BirdComponent() {
        // Image is 128x48: 8 frames horizontally, 3 frames vertically. Frame size is 16x16.
        // Middle row frames are index 8 through 15.
        animFly = new AnimationChannel(FXGL.image("BirdSprite.png"), 8, 16, 16, Duration.seconds(0.8), 8, 15);
        texture = new AnimatedTexture(animFly);
        texture.loop();
    }

    private double startY;
    private double timeAlive = 0;

    @Override
    public void onAdded() {
        entity.getViewComponent().addChild(texture);
        // Changed to 2.0 to fix the backwards flying
        entity.setScaleX(2.0);
        entity.setScaleY(2.0);
        startY = entity.getY();
    }

    @Override
    public void onUpdate(double tpf) {
        timeAlive += tpf;
        // Move to the left
        entity.translateX(-speed * tpf);
        
        // Add a sine wave to its Y coordinate to make it fly up and down
        double newY = startY + Math.sin(timeAlive * 3.0) * 35.0;
        entity.setY(newY);

        // Remove if off screen to the left
        if (entity.getX() < FXGL.getGameScene().getViewport().getX() - 100) {
            entity.removeFromWorld();
        }
    }
}
