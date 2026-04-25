package pkg;

import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.util.Duration;
import static com.almasb.fxgl.dsl.FXGL.*;

public class PlayerComponent extends Component {

    private AnimatedTexture texture;
    private AnimationChannel animIdle, animRun, animAttack;
    private AnimationChannel animFrozen;
    
    @Override
    public void onAdded() {
        // 1. Initialize Channels
        animIdle = new AnimationChannel(image("IDLE.png"), 10, 96, 96, Duration.seconds(1), 0, 9);
        animRun = new AnimationChannel(image("RUN.png"), 16, 96, 96, Duration.seconds(0.8), 0, 15);
        animAttack = new AnimationChannel(image("ATTACK_1.png"), 7, 96, 96, Duration.seconds(0.5), 3, 6);
        
        //attack freeze frame
        animFrozen = new AnimationChannel(image("ATTACK_1.png"), 7, 96, 96, Duration.seconds(9999), 3, 4);

        // 2. Setup Texture
        texture = new AnimatedTexture(animIdle);
        
        // --- THE FIX ---
        // This listener triggers whenever a 'play' animation (like attack) ends.
        texture.setOnCycleFinished(() -> {
            if (texture.getAnimationChannel() == animAttack) {
                texture.loopNoOverride(animIdle);
            }
        });
        
        // 3. Attach to entity
        entity.getViewComponent().addChild(texture);
        texture.loop();
    }

    @Override
    public void onUpdate(double tpf) {
        // We don't need the 'if' check here anymore! 
        // The listener in onAdded() handles the transition automatically.
    }

    public void move() {
        if (texture != null) texture.loopNoOverride(animRun);
    }

    public void stop() {
        // Only stop/idle if we aren't currently mid-attack
        if (texture != null && texture.getAnimationChannel() != animAttack) {
            texture.loopNoOverride(animIdle);
        }
    }

    public void attack() {
        if (texture != null) {
            // playAnimationChannel plays ONCE, then triggers setOnCycleFinished
            texture.playAnimationChannel(animAttack);
        }
    }
    public void setFacing(int direction) {
        // direction is 1 for right, -1 for left
        texture.setScaleX(direction);
    }
    
    public void freeze() {
        if (texture != null) texture.playAnimationChannel(animFrozen);
    }

    public void unfreeze() {
        if (texture != null && texture.getAnimationChannel() == animFrozen) {
            texture.loopNoOverride(animIdle);
        }
    }
}