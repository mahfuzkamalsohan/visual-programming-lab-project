package pkg;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

public class GameFactory implements EntityFactory {

    private static double number(SpawnData data, String key, double fallback) {
        return data.hasKey(key) ? data.<Number>get(key).doubleValue() : fallback;
    }

    @Spawns("platform")
    public Entity newPlatform(SpawnData data) {
        Rectangle rect = new Rectangle(data.<Number>get("width").doubleValue(), data.<Number>get("height").doubleValue(), Color.web("#0B0F24"));
        rect.setStroke(Color.web("#D500F9")); // Magenta neon border
        rect.setStrokeWidth(2);
        rect.setArcWidth(8);
        rect.setArcHeight(8);

        return entityBuilder(data)
                .type(EntityType.PLATFORM)
                .viewWithBBox(rect)
                .with(new PhysicsComponent()) // defaults to Static
                .build();
    }

    @Spawns("player")
    public Entity newPlayer(SpawnData data) {
        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(BodyType.DYNAMIC);
        return entityBuilder(data).type(EntityType.PLAYER).bbox(new HitBox("PLAYER_BODY", new Point2D(0, 0), BoundingShape.box(40, 40))).with(physics).with(new PlayerComponent()).build();
    }

    @Spawns("enemy")
    public Entity newEnemy(SpawnData data) {
        return Type1Enemy.create(data);
    }

    @Spawns("crumble")
    public Entity newCrumblePlatform(SpawnData data) {
        double width = number(data, "width", 80);
        double height = number(data, "height", 40);

        Rectangle rect = new Rectangle(width, height, Color.web("#221B2F"));
        rect.setStroke(Color.web("#FFB000"));
        rect.setStrokeWidth(2);
        rect.getStrokeDashArray().addAll(8.0, 6.0);

        return entityBuilder(data)
                .type(EntityType.PLATFORM)
                .viewWithBBox(rect)
                .with(new PhysicsComponent())
                .with("crumbles", true)
                .with("breaking", false)
                .build();
    }

    @Spawns("spike")
    public Entity newSpike(SpawnData data) {
        double width = number(data, "width", 40);
        double height = number(data, "height", 40);

        Group view = new Group();
        for (int x = 0; x < width; x += 40) {
            Polygon spike = new Polygon(
                    x + 2.0, height,
                    x + 20.0, 4.0,
                    x + 38.0, height
            );
            spike.setFill(Color.web("#F2F7FF"));
            spike.setStroke(Color.web("#00E5FF"));
            spike.setStrokeWidth(1.5);
            view.getChildren().add(spike);
        }
        view.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, Color.web("#00E5FF"), 10, 0.35, 0, 0));

        return entityBuilder(data)
                .type(EntityType.SPIKE)
                .bbox(new HitBox("SPIKE_FIELD", new Point2D(0, 4), BoundingShape.box(width, height - 4)))
                .view(view)
                .zIndex(2)
                .build();
    }

    @Spawns("checkpoint")
    public Entity newCheckpoint(SpawnData data) {
        Rectangle post = new Rectangle(10, 54, Color.web("#00E5FF", 0.5));
        post.setTranslateX(15);
        post.setTranslateY(6);

        Polygon pennant = new Polygon(25.0, 8.0, 68.0, 22.0, 25.0, 36.0);
        pennant.setFill(Color.web("#D500F9", 0.35));
        pennant.setStroke(Color.web("#D500F9"));
        pennant.setStrokeWidth(2);

        Group view = new Group(post, pennant);
        view.setOpacity(0.55);

        return entityBuilder(data)
                .type(EntityType.CHECKPOINT)
                .bbox(new HitBox("CHECKPOINT_FIELD", new Point2D(0, 0), BoundingShape.box(72, 64)))
                .view(view)
                .with("activated", false)
                .zIndex(3)
                .build();
    }

    @Spawns("key")
    public Entity newKey(SpawnData data) {
        Polygon shard = new Polygon(20.0, 0.0, 38.0, 20.0, 20.0, 40.0, 2.0, 20.0);
        shard.setFill(Color.web("#FFB000", 0.75));
        shard.setStroke(Color.web("#FFF1A8"));
        shard.setStrokeWidth(2);
        shard.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, Color.web("#FFB000"), 18, 0.5, 0, 0));

        return entityBuilder(data)
                .type(EntityType.KEY)
                .bbox(new HitBox("KEY_FIELD", new Point2D(2, 2), BoundingShape.box(36, 36)))
                .view(shard)
                .zIndex(4)
                .build();
    }

    @Spawns("dashCrystal")
    public Entity newDashCrystal(SpawnData data) {
        Polygon crystal = new Polygon(20.0, 0.0, 36.0, 12.0, 32.0, 34.0, 20.0, 44.0, 8.0, 34.0, 4.0, 12.0);
        crystal.setFill(Color.web("#00E5FF", 0.65));
        crystal.setStroke(Color.WHITE);
        crystal.setStrokeWidth(1.5);
        crystal.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, Color.web("#00E5FF"), 18, 0.6, 0, 0));

        return entityBuilder(data)
                .type(EntityType.DASH_CRYSTAL)
                .bbox(new HitBox("DASH_REFILL_FIELD", new Point2D(2, 0), BoundingShape.box(36, 44)))
                .view(crystal)
                .zIndex(4)
                .build();
    }

    @Spawns("pressurePlate")
    public Entity newPressurePlate(SpawnData data) {
        double width = number(data, "width", 80);
        double height = number(data, "height", 14);

        Rectangle base = new Rectangle(width, height, Color.web("#FF0055", 0.45));
        base.setStroke(Color.web("#FFB000"));
        base.setStrokeWidth(2);

        return entityBuilder(data)
                .type(EntityType.PRESSURE_PLATE)
                .bbox(new HitBox("PRESSURE_FIELD", new Point2D(0, -10), BoundingShape.box(width, height + 10)))
                .view(base)
                .with("activated", false)
                .zIndex(1)
                .build();
    }

    @Spawns("lock")
    public Entity newLock(SpawnData data) {
        double width = number(data, "width", 40);
        double height = number(data, "height", 120);

        Rectangle block = new Rectangle(width, height, Color.web("#17111F"));
        block.setStroke(Color.web("#FFB000"));
        block.setStrokeWidth(3);
        block.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, Color.web("#FFB000"), 18, 0.35, 0, 0));

        return entityBuilder(data)
                .type(EntityType.LOCK)
                .viewWithBBox(block)
                .with(new PhysicsComponent())
                .zIndex(2)
                .build();
    }

    @Spawns("gate")
    public Entity newGate(SpawnData data) {
        Rectangle frame = new Rectangle(72, 96, Color.web("#00E5FF", 0.14));
        frame.setStroke(Color.web("#00E5FF"));
        frame.setStrokeWidth(3);
        frame.setArcWidth(10);
        frame.setArcHeight(10);

        Rectangle core = new Rectangle(36, 68, Color.web("#D500F9", 0.28));
        core.setTranslateX(18);
        core.setTranslateY(14);
        core.setStroke(Color.web("#D500F9"));
        core.setStrokeWidth(2);
        core.setArcWidth(8);
        core.setArcHeight(8);

        Line scanLine = new Line(10, 48, 62, 48);
        scanLine.setStroke(Color.web("#FFFFFF", 0.8));
        scanLine.setStrokeWidth(2);

        Group view = new Group(frame, core, scanLine);
        view.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, Color.web("#00E5FF"), 24, 0.55, 0, 0));

        return entityBuilder(data)
                .type(EntityType.GATE)
                .bbox(new HitBox("GATE_FIELD", new Point2D(0, 0), BoundingShape.box(72, 96)))
                .view(view)
                .zIndex(5)
                .build();
    }
}
