package pkg;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.GameWorld;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.level.Level;
import com.almasb.fxgl.entity.level.LevelLoader;
import com.almasb.fxgl.logging.Logger;

public class CustomMapLoader implements LevelLoader {

    private static final Logger log = Logger.get(CustomMapLoader.class);

    private final int tileSize;

    public CustomMapLoader(int tileSize) {
        this.tileSize = tileSize;
    }

    @Override
    public Level load(URL url, GameWorld world) {
        List<Entity> entities = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            int numCols = Integer.parseInt(br.readLine().trim());
            int numRows = Integer.parseInt(br.readLine().trim());

            for (int row = 0; row < numRows; row++) {
                String line = br.readLine();
                if (line == null) break;
                
                String[] tokens = line.trim().split("\\s+");
                for (int col = 0; col < numCols && col < tokens.length; col++) {
                    int tileId = Integer.parseInt(tokens[col]);
                    
                    // 0 is empty space. Anything else is a solid tile (20 = grass, 34-36 = dirt)
                    if (tileId != 0) {
                        // "0" maps to the solid platform block in your GameFactory
                        Entity platform = world.create("0", new SpawnData(col * tileSize, row * tileSize));
                        entities.add(platform);
                    }
                }
            }
        } catch (Exception e) {
            log.warning("Failed to load map file!", e);
        }
        
        return new Level(0, 0, entities);
    }
}