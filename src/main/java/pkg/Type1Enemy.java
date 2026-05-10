package pkg;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;

import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class Type1Enemy {
    public static Entity create(SpawnData data) {
        PhysicsComponent ep = new PhysicsComponent();
        ep.setBodyType(BodyType.DYNAMIC);
        
        // Melee attacker, speed 80, range 50px, no AOE, 30 HP
        EnemyAttributeData attrs = new EnemyAttributeData("melee", 80.0, 50.0, 0.0, 30.0);

        Rectangle view = new Rectangle(40, 40, Color.web("#FF0055"));
        view.setArcWidth(10);
        view.setArcHeight(10);

        return entityBuilder(data).type(EntityType.ENEMY).viewWithBBox(view).with(ep).with(new EnemyComponent(attrs)).build();
    }
}