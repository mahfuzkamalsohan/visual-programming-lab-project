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
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class GameFactory implements EntityFactory {

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
}