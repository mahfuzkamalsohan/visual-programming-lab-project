package pkg.restoration.components;

import java.util.function.Supplier;

import com.almasb.fxgl.entity.component.Component;

import javafx.geometry.Point2D;
import pkg.restoration.assets.AssetCatalog;
import pkg.restoration.world.IsoPoint;
import pkg.restoration.world.IsoProjection;
import pkg.restoration.world.LevelDefinition;

public final class PlayerIsoComponent extends Component {

    private static final double COLLISION_MARGIN = 0.5;
    private static final double WALL_CLEARANCE = 0.34;
    private static final double SPRITE_FOOT_OFFSET_Y = 122;
    private static final int DEPTH_TIE_BREAKER = 5;

    private final IsoProjection projection;
    private final Supplier<LevelDefinition> levelSupplier;
    private final double speedTiles;
    private final SpriteSheetAnimator animator = new SpriteSheetAnimator(
            AssetCatalog.PLAYER_FRAME_WIDTH,
            AssetCatalog.PLAYER_FRAME_HEIGHT,
            AssetCatalog.PLAYER_FRAMES
    );

    private IsoPoint isoPosition;
    private Direction facing = Direction.SE;
    private boolean movingUp;
    private boolean movingDown;
    private boolean movingLeft;
    private boolean movingRight;
    private boolean controlsLocked;

    public PlayerIsoComponent(IsoProjection projection, Supplier<LevelDefinition> levelSupplier, IsoPoint spawnPosition, double speedTiles) {
        this.projection = projection;
        this.levelSupplier = levelSupplier;
        this.isoPosition = spawnPosition;
        this.speedTiles = speedTiles;
    }

    @Override
    public void onAdded() {
        entity.getViewComponent().addChild(animator.view());
        syncEntityPosition();
    }

    @Override
    public void onUpdate(double tpf) {
        double x = controlsLocked ? 0 : movementAxis(movingRight, movingLeft);
        double y = controlsLocked ? 0 : movementAxis(movingDown, movingUp);
        boolean isMoving = Math.abs(x) > 0.001 || Math.abs(y) > 0.001;

        if (isMoving) {
            double length = Math.sqrt(x * x + y * y);
            double dx = x / length * speedTiles * tpf;
            double dy = y / length * speedTiles * tpf;
            isoPosition = resolveMovement(levelSupplier.get(), dx, dy);
            facing = Direction.fromVector(x, y, facing);
            syncEntityPosition();
        }

        animator.update(tpf, isMoving, facing);
    }

    public void teleport(IsoPoint position) {
        isoPosition = position;
        syncEntityPosition();
    }

    public IsoPoint isoPosition() {
        return isoPosition;
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

    private void syncEntityPosition() {
        if (entity == null) {
            return;
        }

        Point2D foot = projection.toScreen(isoPosition);
        entity.setPosition(foot.getX() - AssetCatalog.PLAYER_FRAME_WIDTH / 2.0, foot.getY() - SPRITE_FOOT_OFFSET_Y);
        entity.setZIndex(RenderDepth.at(foot.getY(), DEPTH_TIE_BREAKER));
    }

    private IsoPoint resolveMovement(LevelDefinition level, double dx, double dy) {
        IsoPoint target = isoPosition.add(dx, dy);
        if (level.containsPlayer(target, COLLISION_MARGIN, WALL_CLEARANCE)) {
            return target;
        }

        IsoPoint horizontal = isoPosition.add(dx, 0);
        if (level.containsPlayer(horizontal, COLLISION_MARGIN, WALL_CLEARANCE)) {
            return horizontal;
        }

        IsoPoint vertical = isoPosition.add(0, dy);
        if (level.containsPlayer(vertical, COLLISION_MARGIN, WALL_CLEARANCE)) {
            return vertical;
        }

        return isoPosition;
    }

    private static double movementAxis(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0;
        }
        return positive ? 1 : -1;
    }
}
