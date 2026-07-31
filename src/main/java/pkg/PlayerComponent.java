package pkg;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import pkg.MovementApp.Direction;
import pkg.MovementApp.EntityType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PlayerComponent extends Component {

    private static final double SPEED = 150.0;
    private static final int FRAME_WIDTH = 16;
    private static final int FRAME_HEIGHT = 24;

    private static final double CHARACTER_SCALE = 1.5;

    private boolean up, down, left, right;

    private AnimatedTexture texture;
    private Map<Direction, AnimationChannel> walkAnimations;
    private Map<Direction, AnimationChannel> idleAnimations;

    private Direction currentDirection = Direction.NORTH;
    private int playerIndex = 1;

    public PlayerComponent() {
        this(1);
    }

    public PlayerComponent(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    @Override
    public void onAdded() {
        walkAnimations = new EnumMap<>(Direction.class);
        idleAnimations = new EnumMap<>(Direction.class);

        Image spriteSheet = FXGL.image("characters.png");

        int startRow = (playerIndex == 2) ? 5 : 9;

        for (Direction dir : Direction.values()) {
            int col = dir.index;

            // Crop the 3 vertical frames for this character row set
            Image stepA = cropFrame(spriteSheet, col, startRow, FRAME_WIDTH, FRAME_HEIGHT);
            Image idle  = cropFrame(spriteSheet, col, startRow + 1, FRAME_WIDTH, FRAME_HEIGHT);
            Image stepB = cropFrame(spriteSheet, col, startRow + 2, FRAME_WIDTH, FRAME_HEIGHT);

            // Walk animation cycles through: Step A -> Idle -> Step B -> Idle
            AnimationChannel walkChannel = new AnimationChannel(
                    List.of(stepA, idle, stepB, idle),
                    Duration.seconds(0.5));

            // Idle animation holds the center frame
            AnimationChannel idleChannel = new AnimationChannel(
                    List.of(idle),
                    Duration.seconds(1.0));

            walkAnimations.put(dir, walkChannel);
            idleAnimations.put(dir, idleChannel);
        }

        texture = new AnimatedTexture(idleAnimations.get(Direction.NORTH));
        texture.setScaleX(CHARACTER_SCALE);
        texture.setScaleY(CHARACTER_SCALE);
        texture.loop();
        entity.getViewComponent().addChild(texture);
    }

    private Image cropFrame(Image sheet, int col, int row, int width, int height) {
        PixelReader reader = sheet.getPixelReader();
        return new WritableImage(reader, col * width, row * height, width, height);
    }

    @Override
    public void onUpdate(double tpf) {
        double dx = 0, dy = 0;

        if (up)
            dy -= 1;
        if (down)
            dy += 1;
        if (left)
            dx -= 1;
        if (right)
            dx += 1;

        boolean moving = dx != 0 || dy != 0;

        if (moving) {
            double len = Math.hypot(dx, dy);
            dx /= len;
            dy /= len;

            double moveX = dx * SPEED * tpf;
            double moveY = dy * SPEED * tpf;

            if (moveX != 0) {
                entity.translateX(moveX);
                if (collidesWithWall()) {
                    entity.translateX(-moveX);
                    double sign = Math.signum(moveX);
                    double step = sign * 0.5;
                    double moved = 0;
                    while (Math.abs(moved + step) <= Math.abs(moveX)) {
                        entity.translateX(step);
                        if (collidesWithWall()) {
                            entity.translateX(-step);
                            break;
                        }
                        moved += step;
                    }
                }
            }

            if (moveY != 0) {
                entity.translateY(moveY);
                if (collidesWithWall()) {
                    entity.translateY(-moveY);
                    double sign = Math.signum(moveY);
                    double step = sign * 0.5;
                    double moved = 0;
                    while (Math.abs(moved + step) <= Math.abs(moveY)) {
                        entity.translateY(step);
                        if (collidesWithWall()) {
                            entity.translateY(-step);
                            break;
                        }
                        moved += step;
                    }
                }
            }

            currentDirection = determineDirection(dx, dy);
            AnimationChannel walk = walkAnimations.get(currentDirection);
            if (texture.getAnimationChannel() != walk) {
                texture.loopAnimationChannel(walk);
            }
        } else {
            AnimationChannel idle = idleAnimations.get(currentDirection);
            if (texture.getAnimationChannel() != idle) {
                texture.loopAnimationChannel(idle);
            }
        }
    }

    private boolean collidesWithWall() {
        List<Entity> walls = FXGL.getGameWorld().getEntitiesByType(EntityType.WALL);
        for (Entity wall : walls) {
            if (entity.isColliding(wall)) {
                return true;
            }
        }
        List<Entity> players = FXGL.getGameWorld().getEntitiesByType(EntityType.PLAYER);
        for (Entity otherPlayer : players) {
            if (otherPlayer != entity && entity.isColliding(otherPlayer)) {
                return true;
            }
        }
        return false;
    }

    private Direction determineDirection(double dx, double dy) {
        if (dx == 0 && dy < 0)
            return Direction.NORTH; // W
        if (dx > 0 && dy < 0)
            return Direction.NORTH_EAST; // W + D
        if (dx > 0 && dy == 0)
            return Direction.EAST; // D
        if (dx > 0 && dy > 0)
            return Direction.SOUTH_EAST; // S + D
        if (dx == 0 && dy > 0)
            return Direction.SOUTH; // S
        if (dx < 0 && dy > 0)
            return Direction.SOUTH_WEST; // S + A
        if (dx < 0 && dy == 0)
            return Direction.WEST; // A
        return Direction.NORTH_WEST; // W + A
    }

    public void setUp(boolean v) {
        this.up = v;
    }

    public void setDown(boolean v) {
        this.down = v;
    }

    public void setLeft(boolean v) {
        this.left = v;
    }

    public void setRight(boolean v) {
        this.right = v;
    }

    public Direction getCurrentDirection() {
        return currentDirection;
    }

    public void setCurrentDirection(Direction dir) {
        this.currentDirection = dir;
    }

    public boolean isMoving() {
        return up || down || left || right;
    }

    public void setRemoteState(Direction dir, boolean isMoving) {
        this.currentDirection = dir;
        if (texture != null) {
            AnimationChannel channel = isMoving ? walkAnimations.get(dir) : idleAnimations.get(dir);
            if (channel != null && texture.getAnimationChannel() != channel) {
                texture.loopAnimationChannel(channel);
            }
        }
    }
}
