package pkg.restoration.components;

import static com.almasb.fxgl.dsl.FXGL.image;

import com.almasb.fxgl.entity.component.Component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import pkg.restoration.assets.AssetCatalog;
import pkg.restoration.world.IsoProjection;
import pkg.restoration.world.WallRun;

public final class WallRunComponent extends Component {

    private static final double WALL_HEIGHT = 42;
    private static final double CANVAS_PADDING = 8;
    private static final double END_OVERLAP = 5;
    private static final double TEXTURE_TILE_SIZE = 58;
    private static final int DEPTH_TIE_BREAKER = 6;

    private final WallRun run;
    private final IsoProjection projection;

    public WallRunComponent(WallRun run, IsoProjection projection) {
        this.run = run;
        this.projection = projection;
    }

    @Override
    public void onAdded() {
        RenderedWall rendered = renderWall();
        entity.getViewComponent().addChild(rendered.canvas());
        entity.setPosition(rendered.x(), rendered.y());
        entity.setZIndex(RenderDepth.at(rendered.depthY(), DEPTH_TIE_BREAKER));
    }

    private RenderedWall renderWall() {
        Point2D start = projection.toScreen(run.start());
        Point2D end = projection.toScreen(run.end());
        Point2D direction = end.subtract(start).normalize();
        start = start.subtract(direction.multiply(END_OVERLAP));
        end = end.add(direction.multiply(END_OVERLAP));

        double minX = Math.min(start.getX(), end.getX()) - CANVAS_PADDING;
        double maxX = Math.max(start.getX(), end.getX()) + CANVAS_PADDING;
        double minY = Math.min(start.getY(), end.getY()) - WALL_HEIGHT - CANVAS_PADDING;
        double maxY = Math.max(start.getY(), end.getY()) + CANVAS_PADDING;

        Canvas canvas = new Canvas(maxX - minX, maxY - minY);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        double x1 = start.getX() - minX;
        double y1 = start.getY() - minY;
        double x2 = end.getX() - minX;
        double y2 = end.getY() - minY;

        double[] faceX = {x1, x2, x2, x1};
        double[] faceY = {y1 - WALL_HEIGHT, y2 - WALL_HEIGHT, y2, y1};

        drawTexturedFace(gc, canvas, faceX, faceY);

        gc.setLineWidth(2.5);
        gc.setStroke(Color.web("#2d1c12", 0.65));
        gc.strokeLine(x1, y1, x2, y2);
        gc.setLineWidth(1.6);
        gc.setStroke(Color.web("#f0bd79", 0.58));
        gc.strokeLine(x1, y1 - WALL_HEIGHT, x2, y2 - WALL_HEIGHT);

        return new RenderedWall(canvas, minX, minY, Math.max(start.getY(), end.getY()));
    }

    private void drawTexturedFace(GraphicsContext gc, Canvas canvas, double[] faceX, double[] faceY) {
        Image texture = image(AssetCatalog.WALL_WOODEN);

        gc.save();
        gc.beginPath();
        gc.moveTo(faceX[0], faceY[0]);
        for (int i = 1; i < faceX.length; i++) {
            gc.lineTo(faceX[i], faceY[i]);
        }
        gc.closePath();
        gc.clip();

        for (double y = -TEXTURE_TILE_SIZE; y < canvas.getHeight() + TEXTURE_TILE_SIZE; y += TEXTURE_TILE_SIZE) {
            for (double x = -TEXTURE_TILE_SIZE; x < canvas.getWidth() + TEXTURE_TILE_SIZE; x += TEXTURE_TILE_SIZE) {
                gc.drawImage(texture, x, y, TEXTURE_TILE_SIZE, TEXTURE_TILE_SIZE);
            }
        }

        gc.restore();
    }

    private record RenderedWall(Canvas canvas, double x, double y, double depthY) {
    }
}
