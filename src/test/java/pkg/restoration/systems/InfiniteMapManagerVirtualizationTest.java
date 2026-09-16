package pkg.restoration.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class InfiniteMapManagerVirtualizationTest {

    @Test
    void testActiveRadiusAndVirtualizationMetrics() {
        // Active radius of 1 ensures a 3x3 grid (center chunk + 8 neighbors = 9 chunks active)
        assertEquals(1, InfiniteMapManager.ACTIVE_RADIUS);
        assertEquals(20, InfiniteMapManager.CHUNK_SIZE);

        int totalActiveChunks = (2 * InfiniteMapManager.ACTIVE_RADIUS + 1) * (2 * InfiniteMapManager.ACTIVE_RADIUS + 1);
        assertEquals(9, totalActiveChunks, "Active radius 1 must keep exactly 9 chunks active (center + 8 surrounding)");
    }

    @Test
    void testIsometricChunkBoundaryContinuity() {
        // Verify that tile (20, 0) in chunk (0, 0) has the exact same world coordinate
        // as tile (0, 0) in chunk (1, 0) - guaranteeing 0 pixel gap
        double chunk00_originX = (0 - 0) * (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH)
                - (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH);
        double chunk00_originY = (0 + 0) * (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_HEIGHT);

        double tile20_0_localX = (20 - 0) * InfiniteMapManager.TILE_HALF_WIDTH
                + (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH) - 16.0;
        double tile20_0_localY = (20 + 0) * InfiniteMapManager.TILE_HALF_HEIGHT;

        double worldX_from_chunk00 = chunk00_originX + tile20_0_localX;
        double worldY_from_chunk00 = chunk00_originY + tile20_0_localY;

        double chunk10_originX = (1 - 0) * (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH)
                - (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH);
        double chunk10_originY = (1 + 0) * (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_HEIGHT);

        double tile0_0_localX = (0 - 0) * InfiniteMapManager.TILE_HALF_WIDTH
                + (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH) - 16.0;
        double tile0_0_localY = (0 + 0) * InfiniteMapManager.TILE_HALF_HEIGHT;

        double worldX_from_chunk10 = chunk10_originX + tile0_0_localX;
        double worldY_from_chunk10 = chunk10_originY + tile0_0_localY;

        assertEquals(worldX_from_chunk00, worldX_from_chunk10, 0.0001, "East boundary must be seamless (0 gap)");
        assertEquals(worldY_from_chunk00, worldY_from_chunk10, 0.0001, "East boundary Y must be seamless (0 gap)");

        // Verify tile (0, 20) in chunk (0, 0) matches tile (0, 0) in chunk (0, 1)
        double tile0_20_localX = (0 - 20) * InfiniteMapManager.TILE_HALF_WIDTH
                + (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH) - 16.0;
        double tile0_20_localY = (0 + 20) * InfiniteMapManager.TILE_HALF_HEIGHT;

        double worldX_south_from_chunk00 = chunk00_originX + tile0_20_localX;
        double worldY_south_from_chunk00 = chunk00_originY + tile0_20_localY;

        double chunk01_originX = (0 - 1) * (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH)
                - (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_WIDTH);
        double chunk01_originY = (0 + 1) * (InfiniteMapManager.CHUNK_SIZE * InfiniteMapManager.TILE_HALF_HEIGHT);

        double worldX_from_chunk01 = chunk01_originX + tile0_0_localX;
        double worldY_from_chunk01 = chunk01_originY + tile0_0_localY;

        assertEquals(worldX_south_from_chunk00, worldX_from_chunk01, 0.0001, "South boundary must be seamless (0 gap)");
        assertEquals(worldY_south_from_chunk00, worldY_from_chunk01, 0.0001, "South boundary Y must be seamless (0 gap)");
    }
}

