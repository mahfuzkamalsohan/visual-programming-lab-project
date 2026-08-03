package pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.almasb.fxgl.entity.level.tiled.TMXLevelLoader;
import com.almasb.fxgl.entity.level.tiled.TiledObject;
import com.almasb.fxgl.entity.SpawnData;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemoMapTest {

    @Test
    void demoBinFactoryAcceptsStringPropertiesFromTiled() {
        SpawnData data = new SpawnData(0, 0)
                .put("width", 72)
                .put("height", 58)
                .put("color", "#272727")
                .put("label", "BLACK - General")
                .put("logicalId", "bin_black")
                .put("binId", "black");

        var entity = new MovementApp.GameEntityFactory().spawnDemoBin(data);

        assertEquals("black", entity.getProperties().getString("binId"));
    }

    @Test
    void demoMapContainsThreeCompleteStagesWithUniqueLogicalIds() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/assets/levels/tmx/level_demo.tmx")) {
            assertNotNull(stream);
            var map = new TMXLevelLoader().parse(stream);
            List<TiledObject> objects = map.getLayers().stream()
                    .flatMap(layer -> layer.getObjects().stream())
                    .toList();

            assertEquals("collection,question,sorting", map.getProperties().get("stageOrder"));
            assertEquals(15.0f, map.getProperties().get("collectionReward"));
            assertEquals(8.0f, map.getProperties().get("sortingWrongPenalty"));
            assertEquals(4, countType(objects, "demoCollectable"));
            assertEquals(1, countType(objects, "demoQuestionPoint"));
            assertEquals(1, countType(objects, "demoSortingZone"));
            assertEquals(1, countType(objects, "demoIntake"));
            assertEquals(4, countType(objects, "demoBin"));
            assertEquals(8, countType(objects, "demoSortingWaste"));

            try (InputStream levelZeroStream = getClass().getResourceAsStream(
                    "/assets/levels/tmx/level_0.tmx")) {
                assertNotNull(levelZeroStream);
                var levelZero = new TMXLevelLoader().parse(levelZeroStream);
                assertEquals(levelZero.getOrientation(), map.getOrientation());
                assertEquals(levelZero.getTilewidth(), map.getTilewidth());
                assertEquals(levelZero.getTileheight(), map.getTileheight());
                assertTrue(map.getWidth() > levelZero.getWidth());
                assertTrue(map.getHeight() > levelZero.getHeight());
                assertEquals(map.getWidth() * map.getHeight(),
                        map.getLayerByName("Tile Layer 1").getData().size());
            }

            try (InputStream source = getClass().getResourceAsStream(
                    "/assets/levels/tmx/level_demo.tmx")) {
                assertNotNull(source);
                String xml = new String(source.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(xml.contains("<data encoding=\"csv\">"));
            }

            int collectionX = objects.stream()
                    .filter(object -> "demoCollectable".equals(object.getType()))
                    .mapToInt(TiledObject::getX)
                    .max().orElseThrow();
            int questionX = objects.stream()
                    .filter(object -> "demoQuestionPoint".equals(object.getType()))
                    .findFirst().orElseThrow().getX();
            int sortingX = objects.stream()
                    .filter(object -> "demoSortingZone".equals(object.getType()))
                    .findFirst().orElseThrow().getX();
            assertTrue(collectionX < questionX && questionX < sortingX,
                    "Stages must occupy separate west-to-east districts");

            Set<Object> logicalIds = new HashSet<>();
            for (TiledObject object : objects) {
                Object logicalId = object.getProperties().get("logicalId");
                assertNotNull(logicalId, "Missing logicalId on Tiled object " + object.getId());
                assertTrue(logicalIds.add(logicalId), "Duplicate logicalId: " + logicalId);
            }
        }
    }

    private long countType(List<TiledObject> objects, String type) {
        return objects.stream().filter(object -> type.equals(object.getType())).count();
    }
}
