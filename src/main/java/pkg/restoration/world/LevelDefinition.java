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
                .filter(wall -> gates.stream().noneMatch(gate -> gate.position().distance(wall.position()) <= 0.42))
                .noneMatch(wall -> wall.position().distance(point) < wallClearance);
    }

    public List<IsoPoint> wallSlotsNear(IsoPoint anchor, int count, double minimumDistance) {
        return shape.wallSlotsNear(anchor, count, minimumDistance);
    }
}
