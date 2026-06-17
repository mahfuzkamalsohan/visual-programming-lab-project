package pkg.restoration.world;

import java.util.List;

public record LevelDefinition(
        String id,
        String title,
        String subtitle,
        LevelShape shape,
        IsoPoint playerSpawn,
        List<GateDefinition> gates,
        List<NpcDefinition> npcs
) {

    private static final double WALL_GATE_OPENING_RADIUS = 0.58;
    private static final double WALL_SPAN_PADDING = 0.16;

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

        return shape.wallSegments().stream()
                .filter(wall -> gates.stream().noneMatch(gate -> gate.position().distance(wall.position()) <= WALL_GATE_OPENING_RADIUS))
                .noneMatch(wall -> blocksPlayer(point, wall, wallClearance));
    }

    public List<IsoPoint> wallSlotsNear(IsoPoint anchor, int count, double minimumDistance) {
        return shape.wallSlotsNear(anchor, count, minimumDistance);
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
}
