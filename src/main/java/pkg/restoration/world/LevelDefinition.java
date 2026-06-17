package pkg.restoration.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record LevelDefinition(
        String id,
        String title,
        String subtitle,
        LevelShape shape,
        IsoPoint playerSpawn,
        List<GateDefinition> gates,
        List<NpcDefinition> npcs
) {

    private static final double WALL_SPAN_PADDING = 0.16;
    private static final double NPC_COLLISION_RADIUS = 0.68;

    public LevelBounds bounds() {
        return shape.bounds();
    }

    public IsoPoint clamp(IsoPoint point, double margin) {
        return shape.clamp(point, margin);
    }

    public boolean contains(IsoPoint point, double margin) {
        return shape.contains(point, margin);
    }

    public boolean containsPlayer(IsoPoint point, double margin, double wallClearance) {
        if (!shape.contains(point, margin)) {
            return false;
        }

        return gateWallSegments().stream()
                .noneMatch(wall -> blocksPlayer(point, wall, wallClearance))
                && npcs.stream().noneMatch(npc -> blocksPlayer(point, npc.position()));
    }

    public List<IsoPoint> wallSlotsNear(IsoPoint anchor, int count, double minimumDistance) {
        return shape.wallSlotsNear(anchor, count, minimumDistance);
    }

    public List<WallSegment> gateWallSegments() {
        List<WallSegment> allWalls = shape.wallSegments();
        List<WallSegment> gateWalls = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        for (GateDefinition gate : gates) {
            allWalls.stream()
                    .min(Comparator.comparingDouble(wall -> wall.position().distance(gate.position())))
                    .ifPresent(nearest -> addGateSideWalls(allWalls, nearest, gateWalls, keys));
        }

        return List.copyOf(gateWalls);
    }

    private static void addGateSideWalls(List<WallSegment> allWalls, WallSegment gateWall,
                                         List<WallSegment> gateWalls, Set<String> keys) {
        int run = wallRunCoordinate(gateWall);
        int order = wallOrderCoordinate(gateWall);

        for (int offset : new int[] {-1, 1}) {
            allWalls.stream()
                    .filter(wall -> wall.side() == gateWall.side())
                    .filter(wall -> wallRunCoordinate(wall) == run)
                    .filter(wall -> wallOrderCoordinate(wall) == order + offset)
                    .findFirst()
                    .ifPresent(wall -> addUniqueWall(gateWalls, keys, wall));
        }
    }

    private static void addUniqueWall(List<WallSegment> walls, Set<String> keys, WallSegment wall) {
        if (keys.add(wallKey(wall))) {
            walls.add(wall);
        }
    }

    private static boolean blocksPlayer(IsoPoint point, WallSegment wall, double clearance) {
        GridPoint tile = wall.ownerTile();

        return switch (wall.side()) {
            case NORTH, SOUTH -> {
                double minX = tile.x() - WALL_SPAN_PADDING;
                double maxX = tile.x() + 1.0 + WALL_SPAN_PADDING;
                yield isWithin(point.x(), minX, maxX)
                        && Math.abs(point.y() - wall.position().y()) < clearance;
            }
            case WEST, EAST -> {
                double minY = tile.y() - WALL_SPAN_PADDING;
                double maxY = tile.y() + 1.0 + WALL_SPAN_PADDING;
                yield isWithin(point.y(), minY, maxY)
                        && Math.abs(point.x() - wall.position().x()) < clearance;
            }
        };
    }

    private static boolean isWithin(double value, double min, double max) {
        return value >= min && value <= max;
    }

    private static int wallRunCoordinate(WallSegment wall) {
        return switch (wall.side()) {
            case NORTH, SOUTH -> wall.ownerTile().y();
            case WEST, EAST -> wall.ownerTile().x();
        };
    }

    private static int wallOrderCoordinate(WallSegment wall) {
        return switch (wall.side()) {
            case NORTH, SOUTH -> wall.ownerTile().x();
            case WEST, EAST -> wall.ownerTile().y();
        };
    }

    private static String wallKey(WallSegment wall) {
        return Math.round(wall.position().x() * 100)
                + ":"
                + Math.round(wall.position().y() * 100)
                + ":"
                + wall.side();
    }

    private static boolean blocksPlayer(IsoPoint point, IsoPoint npcPosition) {
        return isSameTile(point, npcPosition) || point.distance(npcPosition) < NPC_COLLISION_RADIUS;
    }

    private static boolean isSameTile(IsoPoint first, IsoPoint second) {
        return tileCoordinate(first.x()) == tileCoordinate(second.x())
                && tileCoordinate(first.y()) == tileCoordinate(second.y());
    }

    private static int tileCoordinate(double value) {
        return (int) Math.floor(value);
    }
}
