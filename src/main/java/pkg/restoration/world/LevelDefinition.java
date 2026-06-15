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

    public List<IsoPoint> wallSlotsNear(IsoPoint anchor, int count, double minimumDistance) {
        return shape.wallSlotsNear(anchor, count, minimumDistance);
    }
}
