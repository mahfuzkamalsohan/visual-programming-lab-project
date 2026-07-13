package pkg.restoration.components;

import java.util.function.Predicate;

import com.almasb.fxgl.entity.component.Component;

import javafx.geometry.Point2D;
import pkg.restoration.assets.AssetCatalog;

public final class PlayerIsoComponent extends Component {

    private static final double COLLISION_MARGIN = 16.0; // Converted from metric tile units to pixel bounds
    private static final double SPRITE_FOOT_OFFSET_Y = 122;
    private static final int DEPTH_TIE_BREAKER = 5;

    private Predicate<Point2D> movementValidator;
    private final double speedPixels; // Renamed from speedTiles
    private final SpriteSheetAnimator animator = new SpriteSheetAnimator(
            AssetCatalog.PLAYER_FRAME_WIDTH,
            AssetCatalog.PLAYER_FRAME_HEIGHT,
            AssetCatalog.PLAYER_FRAMES
    );

    private Direction facing = Direction.SE;
    private boolean movingUp;
    private boolean movingDown;
    private boolean movingLeft;
    private boolean movingRight;
    private boolean controlsLocked;

    // Default constructor for automatic Tiled factory instantiation
    public PlayerIsoComponent() {
        this.speedPixels = 220.0; // Standard baseline pixel units per second
    }

    public PlayerIsoComponent(double speedPixels) {
        this.speedPixels = speedPixels;
    }

    public void setMovementValidator(Predicate<Point2D> validator) {
        this.movementValidator = validator;
    }

    @Override
    public void onAdded() {
        entity.getViewComponent().addChild(animator.view());
        syncEntityZIndex();
    }

    @Override
    public void onUpdate(double tpf) {
        double x = controlsLocked ? 0 : movementAxis(movingRight, movingLeft);
        double y = controlsLocked ? 0 : movementAxis(movingDown, movingUp);
        boolean isMoving = Math.abs(x) > 0.001 || Math.abs(y) > 0.001;

        if (isMoving) {
            double length = Math.sqrt(x * x + y * y);
            double dx = x / length * speedPixels * tpf;
            double dy = y / length * speedPixels * tpf;
            
            resolveAndApplyMovement(dx, dy);
            facing = Direction.fromVector(x, y, facing);
        }

        animator.update(tpf, isMoving, facing);
        syncEntityZIndex();
    }

    public void teleport(Point2D position) {
        if (entity != null) {
            entity.setPosition(position.getX() - AssetCatalog.PLAYER_FRAME_WIDTH / 2.0, position.getY() - SPRITE_FOOT_OFFSET_Y);
        }
    }

    public Point2D isoPosition() {
        if (entity == null) {
            return Point2D.ZERO;
        }
        // Returns the visual logical center anchor point at the character's feet
        return entity.getPosition().add(AssetCatalog.PLAYER_FRAME_WIDTH / 2.0, SPRITE_FOOT_OFFSET_Y);
    }

    public void setMovingUp(boolean movingUp) {
        this.movingUp = movingUp;
    }

    public void setMovingDown(boolean movingDown) {
        this.movingDown = movingDown;
    }

    public void setMovingLeft(boolean movingLeft) {
        this.movingLeft = movingLeft;
    }

    public void setMovingRight(boolean movingRight) {
        this.movingRight = movingRight;
    }

    public void setControlsLocked(boolean controlsLocked) {
        this.controlsLocked = controlsLocked;
    }

    public boolean controlsLocked() {
        return controlsLocked;
    }

    private void syncEntityZIndex() {
        if (entity == null) {
            return;
        }
        double footY = entity.getY() + SPRITE_FOOT_OFFSET_Y;
        entity.setZIndex(RenderDepth.at(footY, DEPTH_TIE_BREAKER));
    }

    private void resolveAndApplyMovement(double dx, double dy) {
        Point2D currentPos = entity.getPosition();

        // 1. Evaluate full target step diagonal vector
        Point2D target = currentPos.add(dx, dy);
        if (canOccupy(target.add(AssetCatalog.PLAYER_FRAME_WIDTH / 2.0, SPRITE_FOOT_OFFSET_Y))) {
            entity.setPosition(target);
            return;
        }

        // 2. Sliding physics fallback: Check purely horizontal movement vector slice
        Point2D horizontal = currentPos.add(dx, 0);
        if (canOccupy(horizontal.add(AssetCatalog.PLAYER_FRAME_WIDTH / 2.0, SPRITE_FOOT_OFFSET_Y))) {
            entity.setPosition(horizontal);
            return;
        }

        // 3. Sliding physics fallback: Check purely vertical movement vector slice
        Point2D vertical = currentPos.add(0, dy);
        if (canOccupy(vertical.add(AssetCatalog.PLAYER_FRAME_WIDTH / 2.0, SPRITE_FOOT_OFFSET_Y))) {
            entity.setPosition(vertical);
        }
    }

    private boolean canOccupy(Point2D position) {
        return movementValidator == null || movementValidator.test(position);
    }

    public static double collisionMargin() {
        return COLLISION_MARGIN;
    }

    private static double movementAxis(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0;
        }
        return positive ? 1 : -1;
    }
}