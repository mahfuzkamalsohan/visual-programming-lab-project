package pkg.restoration.world;

import java.util.List;

public record NpcDefinition(
        String id,
        IsoPoint position,
        String name,
        String asset,
        List<String> messages
) {
}
