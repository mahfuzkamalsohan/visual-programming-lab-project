package pkg;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

public class PlayerComponent extends Component {

    private static final double SPEED = 150.0;
    private static final int FRAME_WIDTH = 16;
    private static final int FRAME_HEIGHT = 24;

    private static final double CHARACTER_SCALE = 1.5;

    private boolean up, down, left, right;
    private boolean isRemote = false;
    private boolean remoteMoving = false;

    private AnimatedTexture texture;
    private Map<Direction, AnimationChannel> walkAnimations;
    private Map<Direction, AnimationChannel> idleAnimations;

    private Direction currentDirection = Direction.NORTH;
    private int playerIndex = 1;

    private double footstepTimer = 0.35;
    private static final double FOOTSTEP_INTERVAL = 0.35;

    public PlayerComponent() {
        this(1);
    }

    public PlayerComponent(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    public int getPlayerIndex() {
        return playerIndex;
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
        if (movementFrozen) {
            footstepTimer = FOOTSTEP_INTERVAL;
            if (texture != null && idleAnimations != null && currentDirection != null) {
                AnimationChannel idle = idleAnimations.get(currentDirection);
                if (idle != null && texture.getAnimationChannel() != idle) {
                    texture.loopAnimationChannel(idle);
                }
            }
            return;
        }

        if (isRemote) {
            if (remoteMoving) {
                footstepTimer += tpf;
                if (footstepTimer >= FOOTSTEP_INTERVAL) {
                    footstepTimer = 0.0;
                    pkg.audio.AudioManager.playFootstep();
                }
            } else {
                footstepTimer = FOOTSTEP_INTERVAL;
            }
            return;
        }

        // --- Depenetration: if already overlapping a wall, push out first ---
        if (collidesWithWallOnly()) {
            depenetrate();
        }

        // Track whether we were already overlapping another player at frame start
        // so we can allow walking away from the overlap
        overlappingPlayerAtFrameStart = isOverlappingOtherPlayer();

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

        double startX = entity.getX();
        double startY = entity.getY();

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

            boolean actuallyMoved = Math.hypot(entity.getX() - startX, entity.getY() - startY) > 0.05;
            if (actuallyMoved) {
                footstepTimer += tpf;
                if (footstepTimer >= FOOTSTEP_INTERVAL) {
                    footstepTimer = 0.0;
                    pkg.audio.AudioManager.playFootstep();
                }
            } else {
                footstepTimer = FOOTSTEP_INTERVAL;
            }
        } else {
            footstepTimer = FOOTSTEP_INTERVAL;
            AnimationChannel idle = idleAnimations.get(currentDirection);
            if (texture.getAnimationChannel() != idle) {
                texture.loopAnimationChannel(idle);
            }
        }
    }

    private boolean ignoreBoundaryWalls = false;
    private boolean overlappingPlayerAtFrameStart = false;

    public void setIgnoreBoundaryWalls(boolean ignore) {
        this.ignoreBoundaryWalls = ignore;
    }

    public boolean isIgnoreBoundaryWalls() {
        return ignoreBoundaryWalls;
    }

    /**
     * Checks collision with walls only (no player-on-player check).
     */
    private boolean collidesWithWallOnly() {
        List<Entity> walls = FXGL.getGameWorld().getEntitiesByType(EntityType.WALL);
        for (Entity wall : walls) {
            boolean isPermanent = wall.<Boolean>getPropertyOptional("isOuterBoundary").orElse(false);
            if ((!ignoreBoundaryWalls || isPermanent) && entity.isColliding(wall)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if this player is currently overlapping another player entity.
     */
    private boolean isOverlappingOtherPlayer() {
        List<Entity> players = FXGL.getGameWorld().getEntitiesByType(EntityType.PLAYER);
        for (Entity otherPlayer : players) {
            if (otherPlayer != entity && entity.isColliding(otherPlayer)) {
                return true;
            }
        }
        return false;
    }

    private boolean collidesWithWall() {
        if (collidesWithWallOnly()) {
            return true;
        }
        // If players were already overlapping at frame start, allow movement
        // (so they can walk away from each other instead of being deadlocked)
        if (overlappingPlayerAtFrameStart) {
            return false;
        }
        return isOverlappingOtherPlayer();
    }

    /**
     * Emergency depenetration: push the player out of any overlapping wall
     * by trying small steps in all 4 cardinal directions and picking the
     * shortest escape.
     */
    private void depenetrate() {
        double step = 1.0;
        double maxPush = 32.0; // don't push further than 2 tiles

        // Try each cardinal direction independently
        double[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        double bestDist = Double.MAX_VALUE;
        double bestDx = 0, bestDy = 0;

        for (double[] dir : directions) {
            double totalDx = 0, totalDy = 0;
            double origX = entity.getX();
            double origY = entity.getY();
            boolean escaped = false;

            for (double d = step; d <= maxPush; d += step) {
                totalDx = dir[0] * d;
                totalDy = dir[1] * d;
                entity.setX(origX + totalDx);
                entity.setY(origY + totalDy);
                if (!collidesWithWallOnly()) {
                    escaped = true;
                    break;
                }
            }

            entity.setX(origX);
            entity.setY(origY);

            if (escaped) {
                double dist = Math.hypot(totalDx, totalDy);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestDx = totalDx;
                    bestDy = totalDy;
                }
            }
        }

        if (bestDist < Double.MAX_VALUE) {
            entity.translateX(bestDx);
            entity.translateY(bestDy);
        }
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

    private boolean movementFrozen = false;

    public void setMovementFrozen(boolean frozen) {
        this.movementFrozen = frozen;
        if (frozen) {
            stopMovement();
        }
    }

    public boolean isMovementFrozen() {
        return movementFrozen;
    }

    public void stopMovement() {
        this.up = false;
        this.down = false;
        this.left = false;
        this.right = false;
        this.isRemote = false;
        if (texture != null && idleAnimations != null && currentDirection != null) {
            AnimationChannel idle = idleAnimations.get(currentDirection);
            if (idle != null && texture.getAnimationChannel() != idle) {
                texture.loopAnimationChannel(idle);
            }
        }
    }

    public void setUp(boolean v) {
        if (!movementFrozen) {
            this.up = v;
            this.isRemote = false;
        }
    }

    public void setDown(boolean v) {
        if (!movementFrozen) {
            this.down = v;
            this.isRemote = false;
        }
    }

    public void setLeft(boolean v) {
        if (!movementFrozen) {
            this.left = v;
            this.isRemote = false;
        }
    }

    public void setRight(boolean v) {
        if (!movementFrozen) {
            this.right = v;
            this.isRemote = false;
        }
    }

    public Direction getCurrentDirection() {
        return currentDirection;
    }

    public void setCurrentDirection(Direction dir) {
        this.currentDirection = dir;
    }

    public boolean isMoving() {
        return isRemote ? remoteMoving : (up || down || left || right);
    }

    public boolean isRemote() {
        return isRemote;
    }

    public void setRemote(boolean isRemote) {
        this.isRemote = isRemote;
    }

    public void setRemoteState(Direction dir, boolean isMoving) {
        this.isRemote = true;
        this.remoteMoving = isMoving;
        if (dir != null) {
            this.currentDirection = dir;
        }
        if (texture != null && walkAnimations != null && idleAnimations != null) {
            AnimationChannel channel = isMoving ? walkAnimations.get(currentDirection) : idleAnimations.get(currentDirection);
            if (channel != null && texture.getAnimationChannel() != channel) {
                texture.loopAnimationChannel(channel);
            }
        }
    }
}
