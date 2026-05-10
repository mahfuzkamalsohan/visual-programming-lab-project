package pkg;

import static com.almasb.fxgl.dsl.FXGL.image;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class PlayerComponent extends Component {

    private PhysicsComponent physics; // Auto-injected by FXGL
    private AnimatedTexture texture;
    private AnimationChannel animIdle, animRun, animAttack;
    private AnimationChannel animFrozen;
    
    private boolean flinching = false;
    private double flinchTimer = 0;
    
    // Modern Platformer variables
    private final double COYOTE_MAX = 0.15;
    private final double JUMP_BUFFER_MAX = 0.15;
    private final double MAX_SPEED = 250;
    private final double ACCELERATION = 2000;
    private final double FRICTION = 1500;
    private final double WALL_SLIDE_SPEED = 80; // Cap falling speed against walls
    private double coyoteTimer = 0;
    private double jumpBufferTimer = 0;
    private int moveDir = 0;
    private boolean isWallSliding = false;
    private double wallJumpControlLock = 0;
    
    private double dashTimer = 0;
    private double dashCooldown = 0;

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
        // Shift the 96x96 texture so it perfectly aligns over the 40x40 collision box
        texture.setTranslateX(-28);
        texture.setTranslateY(-56);
        entity.getViewComponent().addChild(texture);
        texture.loop();
    }

    @Override
    public void onUpdate(double tpf) {
        // 1. Handle Flinching (Invincibility Frames derived from old Player.java)
        if (flinching) {
            flinchTimer += tpf;
            // Blinking effect
            entity.getViewComponent().setOpacity((flinchTimer * 10) % 2 < 1 ? 0.5 : 1.0);
            if (flinchTimer > 1.5) { // 1.5 seconds of invincibility
                flinching = false;
                flinchTimer = 0;
                entity.getViewComponent().setOpacity(1.0);
            }
        }

        wallJumpControlLock -= tpf;
        if (dashCooldown > 0) dashCooldown -= tpf;

        // --- DASH MECHANIC ---
        if (dashTimer > 0) {
            dashTimer -= tpf;
            physics.setVelocityY(0); // Defy gravity while dashing
            physics.setVelocityX(Math.signum(texture.getScaleX()) * 800); // High speed
            
            // Spawn holographic afterimage trail
            if (com.almasb.fxgl.core.math.FXGLMath.randomBoolean(0.4)) {
                Rectangle shadow = new Rectangle(40, 40, Color.web("#00E5FF", 0.5));
                shadow.setArcWidth(10); shadow.setArcHeight(10);
                com.almasb.fxgl.entity.Entity trail = com.almasb.fxgl.dsl.FXGL.entityBuilder()
                    .at(entity.getPosition())
                    .view(shadow).zIndex(-1).buildAndAttach();
                com.almasb.fxgl.dsl.FXGL.animationBuilder()
                    .duration(Duration.seconds(0.3))
                    .onFinished(() -> trail.removeFromWorld())
                    .fadeOut(trail).buildAndPlay();
            }
            return; // Skip normal movement logic while dashing
        }

        // --- 1.5. SENSORS (Ground & Walls) ---
        Point2D center = entity.getCenter();
        var leftRay = com.almasb.fxgl.dsl.FXGL.getPhysicsWorld().raycast(center, center.add(-24, 0));
        var rightRay = com.almasb.fxgl.dsl.FXGL.getPhysicsWorld().raycast(center, center.add(24, 0));
        var downRay = com.almasb.fxgl.dsl.FXGL.getPhysicsWorld().raycast(center, center.add(0, 24)); // 24 reaches just past the feet
        
        boolean wallLeft = leftRay.getEntity().map(e -> e.isType(EntityType.PLATFORM)).orElse(false);
        boolean wallRight = rightRay.getEntity().map(e -> e.isType(EntityType.PLATFORM)).orElse(false);
        boolean isGrounded = downRay.getEntity().map(e -> e.isType(EntityType.PLATFORM)).orElse(false);

        // 2. Modern Movement Polish (Acceleration & Friction)
        double vx = physics.getVelocityX();
        if (wallJumpControlLock <= 0) {
            if (moveDir != 0) {
                vx += moveDir * ACCELERATION * tpf;
                // Cap the speed
                if (Math.abs(vx) > MAX_SPEED) vx = Math.signum(vx) * MAX_SPEED;
                setFacing(moveDir); // Visually face the correct way
            } else {
                // Apply Friction for a smooth stop
                if (vx > 0) {
                    vx -= FRICTION * tpf;
                    if (vx < 0) vx = 0;
                } else if (vx < 0) {
                    vx += FRICTION * tpf;
                    if (vx > 0) vx = 0;
                }
            }
            physics.setVelocityX(vx);
        }

        // 3. Coyote Time & Jump Buffering
        if (isGrounded) {
            coyoteTimer = COYOTE_MAX;
        } else {
            coyoteTimer -= tpf;
        }

        // --- WALL SLIDE / HANG ---
        // Only slide/hang if intentionally pushing into the wall
        boolean pushingLeftWall = wallLeft && moveDir < 0;
        boolean pushingRightWall = wallRight && moveDir > 0;
        
        isWallSliding = (pushingLeftWall || pushingRightWall) && !isGrounded && physics.getVelocityY() >= 0;

        if (isWallSliding) {
            // Cap falling speed to create a wall-slide friction effect
            // TIP: Set WALL_SLIDE_SPEED to 0 if you want a perfect static "Wall Hang"
            if (physics.getVelocityY() > WALL_SLIDE_SPEED) {
                physics.setVelocityY(WALL_SLIDE_SPEED);
            }
        }

        jumpBufferTimer -= tpf;
        if (jumpBufferTimer > 0) {
            if (coyoteTimer > 0) {
                // Normal Ground Jump
                physics.setVelocityY(-400);
                jumpBufferTimer = 0;
                coyoteTimer = 0;
            } else if (isWallSliding) {
                // Wall Jump Push-off!
                physics.setVelocityY(-400);
                physics.setVelocityX(wallLeft ? 350 : -350); // Push away from the wall
                wallJumpControlLock = 0.15; // Temporarily lock input so the player pushes off smoothly
                jumpBufferTimer = 0;
            }
        }

        // 4. Refined Animation State Machine
        // Let physics dictate the animation state automatically!
        if (texture.getAnimationChannel() != animAttack && texture.getAnimationChannel() != animFrozen) {
            if (Math.abs(physics.getVelocityY()) > 5) {
                // In the air (Jumping or Falling)
                // If you add JUMP/FALL animations later, uncomment the line below:
                // texture.loopNoOverride(physics.getVelocityY() < 0 ? animJump : animFall); 
                // If you want a Wall Slide animation:
                // if (isWallSliding) texture.loopNoOverride(animWallSlide);
            } else if (Math.abs(physics.getVelocityX()) > 5) {
                texture.loopNoOverride(animRun);
            } else {
                texture.loopNoOverride(animIdle);
            }
        }
    }

    public void takeDamage(int amount) {
        if (flinching) return; // Immune to damage while flinching!
        flinching = true;
        com.almasb.fxgl.dsl.FXGL.inc("playerHP", -amount);
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

    public void setMoveDir(int dir) {
        this.moveDir = dir;
    }

    public void jump() {
        jumpBufferTimer = JUMP_BUFFER_MAX;
    }

    public void stopJump() {
        if (physics.getVelocityY() < 0) physics.setVelocityY(physics.getVelocityY() * 0.4);
    }

    public void dash() {
        if (dashCooldown <= 0) {
            dashTimer = 0.15; // 150ms dash duration
            dashCooldown = 1.0; // 1 second cooldown
            flinching = true; // Invincibility during dash
            flinchTimer = 0; 
        }
    }
}