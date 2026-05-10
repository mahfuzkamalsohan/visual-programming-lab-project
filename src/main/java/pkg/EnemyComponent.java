package pkg;

import static com.almasb.fxgl.dsl.FXGL.getGameTimer;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.ui.ProgressBar;

import javafx.geometry.Point2D;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class EnemyComponent extends Component {
    private PhysicsComponent physics;
    private final EnemyAttributeData attributes;
    private double hp;
    private boolean canAttack = true;
    private ProgressBar hpBar;
    private boolean hacked = false;
    private Entity playerRef = null;
    private int patrolDir = 1; // 1 for right, -1 for left

    public EnemyComponent(EnemyAttributeData attributes) {
        this.attributes = attributes;
        this.hp = attributes.maxHp;
    }

    @Override
    public void onAdded() {
        physics = entity.getComponent(PhysicsComponent.class);
        
        // Floating HP Bar over Enemy Head
        hpBar = new ProgressBar(false);
        hpBar.setMaxValue(hp);
        hpBar.setCurrentValue(hp);
        hpBar.setWidth(40);
        hpBar.setHeight(8);
        hpBar.setFill(Color.web("#FF0055")); // Neon Pink
        hpBar.setTranslateX(-10);
        hpBar.setTranslateY(-15);
        
        // Add a neon glow to the enemy health bar
        hpBar.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, Color.web("#FF0055"), 10, 0.3, 0, 0));
        
        entity.getViewComponent().addChild(hpBar);
    }

    @Override
    public void onUpdate(double tpf) {
        Entity target = null;
        
        // CYBERPUNK MECHANIC: If hacked, target other enemies instead of the player!
        if (hacked) {
            var enemyOpt = getGameWorld().getClosestEntity(entity, e -> e.isType(EntityType.ENEMY) && e != entity && !e.getComponent(EnemyComponent.class).isHacked());
            if (enemyOpt.isPresent()) target = enemyOpt.get();
        } else {
            if (playerRef == null || !playerRef.isActive()) {
                var playerOpt = getGameWorld().getSingletonOptional(EntityType.PLAYER);
                if (playerOpt.isPresent()) playerRef = playerOpt.get();
            }
            target = playerRef;
        }
        
        // Normal enemies only see 250px. Hacked enemies will hunt across the whole map!
        double aggroRange = hacked ? 2000 : 250;
        double dist = (target != null && target.isActive()) ? entity.distance(target) : Double.MAX_VALUE;
        
        if (dist < aggroRange) {
            // === AGGRO STATE (Chasing / Attacking) ===
            if (dist > attributes.attackRange) {
                physics.setVelocityX(target.getX() < entity.getX() ? -attributes.speed : attributes.speed);
            } else if (canAttack) {
                attack(target);
            } else {
                physics.setVelocityX(physics.getVelocityX() * 0.9);
            }
        } else {
            // === PATROL STATE ===
            Point2D center = entity.getCenter();
            
            // Only check for edges if the enemy is mostly grounded (not falling)
            if (Math.abs(physics.getVelocityY()) < 5) {
                // Raycast ahead to check for a wall
                boolean wallAhead = com.almasb.fxgl.dsl.FXGL.getPhysicsWorld()
                        .raycast(center, center.add(patrolDir * 25, 0))
                        .getEntity().map(e -> e.isType(EntityType.PLATFORM)).orElse(false);
                        
                // Raycast diagonally down-ahead to check for a floor/edge
                boolean floorAhead = com.almasb.fxgl.dsl.FXGL.getPhysicsWorld()
                        .raycast(center.add(patrolDir * 25, 0), center.add(patrolDir * 25, 40))
                        .getEntity().map(e -> e.isType(EntityType.PLATFORM)).orElse(false);
        
                if (wallAhead || !floorAhead) {
                    patrolDir *= -1; // Turn around!
                }
            }
            
            // Patrol back and forth at 50% of their normal speed
            physics.setVelocityX(patrolDir * (attributes.speed * 0.5));
        }
    }

    private void attack(Entity target) {
        canAttack = false;
        physics.setVelocityX(0);

        // Push the target back
        var playerAnim = target.getComponentOptional(PlayerComponent.class);
        var enemyAnim = target.getComponentOptional(EnemyComponent.class);
        var targetPhysics = target.getComponentOptional(PhysicsComponent.class);
        
        playerAnim.ifPresent(p -> p.takeDamage(10));
        enemyAnim.ifPresent(e -> e.takeDamage(15, entity)); // Hacked enemies deal 15 dmg to each other
        
        targetPhysics.ifPresent(p -> {
            p.setVelocityX((target.getX() > entity.getX() ? 1 : -1) * 250);
            p.setVelocityY(-150);
        });

        // 1.5 second cooldown before attacking again
        getGameTimer().runOnceAfter(() -> {
            if (entity != null && entity.isActive()) canAttack = true;
        }, Duration.seconds(1.5));
    }

    public void takeDamage(double damage, Entity source) {
        hp -= damage;
        hpBar.setCurrentValue(hp);
        
        // Visual Polish: Impact flicker!
        entity.getViewComponent().setOpacity(0.3);
        getGameTimer().runOnceAfter(() -> {
            if (entity != null && entity.isActive()) {
                entity.getViewComponent().setOpacity(1.0);
            }
        }, Duration.seconds(0.1));

        if (hp <= 0) {
            entity.removeFromWorld(); // Die
        } else {
            // Take knockback from player's punch
            physics.setVelocityX((entity.getX() > source.getX() ? 1 : -1) * 150);
            physics.setVelocityY(-100);
        }
    }

    public void setHacked(boolean h) {
        this.hacked = h;
        entity.getViewComponent().getChildren().forEach(n -> {
            if (n instanceof javafx.scene.shape.Rectangle rect) {
                rect.setFill(h ? Color.CYAN : Color.CRIMSON);
            }
        });
    }

    public boolean isHacked() {
        return hacked;
    }
}