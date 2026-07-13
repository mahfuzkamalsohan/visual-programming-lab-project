package pkg.restoration;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pkg.restoration.components.ChoiceDoorComponent;
import pkg.restoration.components.GateComponent;
import pkg.restoration.components.NpcComponent;
import pkg.restoration.components.PlayerIsoComponent;
import pkg.restoration.spring.RestorationGameProperties;
import pkg.restoration.world.GateDefinition;
import pkg.restoration.world.NpcDefinition;

import java.util.Map;
import java.util.NoSuchElementException;

@Component
public final class RestorationEntityFactory implements EntityFactory {

    private final RestorationGameProperties gameProperties;

    @Autowired(required = false)
    private Map<String, GateDefinition> gateRegistry;

    @Autowired(required = false)
    private Map<String, NpcDefinition> npcRegistry;

    public RestorationEntityFactory(RestorationGameProperties gameProperties) {
        this.gameProperties = gameProperties;
    }

 @Spawns("wall")
    public Entity newWall(SpawnData data) {
        // Read safely as a generic Number to handle both Integer and Double underlying types
        Number widthNum = data.get("width");
        Number heightNum = data.get("height");

        double width = widthNum != null ? widthNum.doubleValue() : 0.0;
        double height = heightNum != null ? heightNum.doubleValue() : 0.0;

        return entityBuilder(data)
                .type(RestorationEntityType.WALL)
                .bbox(new com.almasb.fxgl.physics.HitBox(
                        com.almasb.fxgl.physics.BoundingShape.box(width, height)
                ))
                .build();
    }

    @Spawns("restorationPlayer")
    public Entity newPlayer(SpawnData data) {
        double speedPixels = gameProperties.playerSpeedTiles() * 50.0;

        return entityBuilder(data)
                .type(RestorationEntityType.PLAYER)
                .with(new PlayerIsoComponent(speedPixels))
                .build();
    }

    @Spawns("restorationGate")
    public Entity newGate(SpawnData data) {
        String gateId = data.get("gateId");
        if (gateId == null || gateRegistry == null || !gateRegistry.containsKey(gateId)) {
            throw new NoSuchElementException("Missing or unresolved custom property 'gateId': " + gateId);
        }
        
        GateDefinition definition = gateRegistry.get(gateId);

        return entityBuilder(data)
                .type(RestorationEntityType.GATE)
                .with(new GateComponent(definition))
                .build();
    }

    @Spawns("restorationChoiceDoor")
    public Entity newChoiceDoor(SpawnData data) {
        return entityBuilder(data)
                .type(RestorationEntityType.CHOICE_DOOR)
                .with(new ChoiceDoorComponent(
                        data.get("challenge"),
                        data.get("choiceIndex"),
                        data.get("position")
                ))
                .build();
    }

    @Spawns("restorationNpc")
    public Entity newNpc(SpawnData data) {
        String npcId = data.get("npcId");
        if (npcId == null || npcRegistry == null || !npcRegistry.containsKey(npcId)) {
            throw new NoSuchElementException("Missing or unresolved custom property 'npcId': " + npcId);
        }

        NpcDefinition definition = npcRegistry.get(npcId);

        return entityBuilder(data)
                .type(RestorationEntityType.NPC)
                .with(new NpcComponent(definition))
                .build();
    }
}