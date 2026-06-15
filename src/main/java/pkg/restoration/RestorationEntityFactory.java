package pkg.restoration;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;

import org.springframework.stereotype.Component;

import pkg.restoration.components.ChoiceDoorComponent;
import pkg.restoration.components.GateComponent;
import pkg.restoration.components.NpcComponent;
import pkg.restoration.components.PlayerIsoComponent;
import pkg.restoration.spring.RestorationGameProperties;

@Component
public final class RestorationEntityFactory implements EntityFactory {

    private final RestorationGameProperties gameProperties;

    public RestorationEntityFactory(RestorationGameProperties gameProperties) {
        this.gameProperties = gameProperties;
    }

    @Spawns("restorationPlayer")
    public Entity newPlayer(SpawnData data) {
        return entityBuilder(data)
                .type(RestorationEntityType.PLAYER)
                .with(new PlayerIsoComponent(
                        data.get("projection"),
                        data.get("levelSupplier"),
                        data.get("spawn"),
                        gameProperties.playerSpeedTiles()
                ))
                .build();
    }

    @Spawns("restorationGate")
    public Entity newGate(SpawnData data) {
        return entityBuilder(data)
                .type(RestorationEntityType.GATE)
                .with(new GateComponent(data.get("gate"), data.get("projection")))
                .with("levelIndex", data.get("levelIndex"))
                .build();
    }

    @Spawns("restorationChoiceDoor")
    public Entity newChoiceDoor(SpawnData data) {
        return entityBuilder(data)
                .type(RestorationEntityType.CHOICE_DOOR)
                .with(new ChoiceDoorComponent(
                        data.get("challenge"),
                        data.get("choiceIndex"),
                        data.get("position"),
                        data.get("projection")
                ))
                .build();
    }

    @Spawns("restorationNpc")
    public Entity newNpc(SpawnData data) {
        return entityBuilder(data)
                .type(RestorationEntityType.NPC)
                .with(new NpcComponent(data.get("npc"), data.get("projection")))
                .with("levelIndex", data.get("levelIndex"))
                .build();
    }
}
