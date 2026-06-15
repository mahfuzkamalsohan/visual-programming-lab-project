package pkg.restoration.world;

import java.util.List;

public record NpcDefinition(
        String id,
        IsoPoint position,
        String name,
        List<String> messages
) {
}
