package pkg.restoration.world;

import pkg.restoration.questions.Difficulty;

public record GateDefinition(
        String id,
        GateKind kind,
        IsoPoint position,
        int destinationLevelIndex,
        IsoPoint destinationPosition,
        Difficulty minimumDifficulty,
        int choices,
        String label
) {
}
