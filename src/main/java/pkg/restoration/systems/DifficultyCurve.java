package pkg.restoration.systems;

import org.springframework.stereotype.Service;

import pkg.restoration.questions.Difficulty;
import pkg.restoration.world.GateDefinition;

@Service
public final class DifficultyCurve {

    public Difficulty forGate(int levelIndex, GateDefinition gate, double elapsedSeconds) {
        Difficulty curveDifficulty = switch (Math.min(levelIndex / 2, 2)) {
            case 0 -> Difficulty.EASY;
            case 1 -> Difficulty.MEDIUM;
            default -> Difficulty.HARD;
        };

        if (elapsedSeconds > 120 && curveDifficulty == Difficulty.EASY) {
            curveDifficulty = Difficulty.MEDIUM;
        }

        return higherOf(curveDifficulty, gate.minimumDifficulty());
    }

    private Difficulty higherOf(Difficulty first, Difficulty second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
