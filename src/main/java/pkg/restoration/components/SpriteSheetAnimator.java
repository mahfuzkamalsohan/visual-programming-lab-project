package pkg.restoration.components;

import static com.almasb.fxgl.dsl.FXGL.image;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import pkg.restoration.assets.AssetCatalog;

public final class SpriteSheetAnimator {

    private final ImageView view = new ImageView();
    private final int frameWidth;
    private final int frameHeight;
    private final int frameCount;

    private Direction direction = Direction.S;
    private boolean walking;
    private int frame;
    private double frameTimer;

    public SpriteSheetAnimator(int frameWidth, int frameHeight, int frameCount) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;
        view.setSmooth(false);
        view.setFitWidth(frameWidth);
        view.setFitHeight(frameHeight);
        loadCurrentSheet();
    }

    public ImageView view() {
        return view;
    }

    public void update(double tpf, boolean isWalking, Direction newDirection) {
        if (walking != isWalking || direction != newDirection) {
            walking = isWalking;
            direction = newDirection;
            frame = 0;
            frameTimer = 0;
            loadCurrentSheet();
        }

        frameTimer += tpf;
        double frameDuration = walking ? 0.12 : 0.32;
        if (frameTimer >= frameDuration) {
            frameTimer = 0;
            frame = (frame + 1) % frameCount;
            updateViewport();
        }
    }

    private void loadCurrentSheet() {
        Image sheet = image(walking
                ? AssetCatalog.PLAYER_WALKING_ATLAS
                : AssetCatalog.PLAYER_IDLE_ATLAS);
        view.setImage(sheet);
        updateViewport();
    }

    private void updateViewport() {
        Image sheet = view.getImage();
        double sourceFrameWidth = Math.floor(sheet.getWidth() / frameCount);
        double sourceFrameHeight = Math.floor(sheet.getHeight() / AssetCatalog.PLAYER_DIRECTIONS);
        int row = AssetCatalog.playerAtlasRow(direction, walking);
        view.setViewport(new Rectangle2D(
                frame * sourceFrameWidth,
                row * sourceFrameHeight,
                sourceFrameWidth,
                sourceFrameHeight
        ));
    }
}
