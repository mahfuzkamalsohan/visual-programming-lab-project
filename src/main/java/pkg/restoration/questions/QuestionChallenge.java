package pkg.restoration.questions;

import java.util.List;
import java.util.Set;

public record QuestionChallenge(
        String id,
        Difficulty difficulty,
        ChallengeType type,
        String prompt,
        List<String> choices,
        Set<Integer> correctChoiceIndexes,
        int rewardSeconds,
        int penaltySeconds,
        String correctFeedback,
        String wrongFeedback
) {

    public ChallengeResult evaluate(int selectedChoiceIndex) {
        boolean correct = correctChoiceIndexes.contains(selectedChoiceIndex);
        int delta = correct ? rewardSeconds : -penaltySeconds;
        String feedback = correct ? correctFeedback : wrongFeedback;
        return new ChallengeResult(correct, delta, feedback);
    }
}
