package pkg.restoration.questions;

import java.util.List;
import java.util.Objects;

public record EnvironmentalQuestion(
        String id,
        String prompt,
        List<String> choices,
        int bestChoice,
        int secondBestChoice,
        double bestRewardSeconds,
        double wrongPenaltySeconds,
        String correctFeedback,
        String wrongFeedback) {

    public EnvironmentalQuestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(prompt, "prompt");
        choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
        if (id.isBlank() || prompt.isBlank() || choices.size() < 3) {
            throw new IllegalArgumentException("A question needs an ID, prompt, and at least three choices");
        }
        if (bestChoice < 0 || bestChoice >= choices.size()
                || secondBestChoice < 0 || secondBestChoice >= choices.size()
                || bestChoice == secondBestChoice) {
            throw new IllegalArgumentException("Best and second-best choices must be distinct valid indices");
        }
        if (!Double.isFinite(bestRewardSeconds) || bestRewardSeconds < 0
                || !Double.isFinite(wrongPenaltySeconds) || wrongPenaltySeconds < 0) {
            throw new IllegalArgumentException("Reward and penalty must be finite and non-negative");
        }
        correctFeedback = correctFeedback == null ? "Best answer" : correctFeedback;
        wrongFeedback = wrongFeedback == null ? "That choice harms the environment" : wrongFeedback;
    }

    public QuestionResult answer(int choiceIndex) {
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            throw new IllegalArgumentException("choiceIndex is outside the available choices");
        }
        if (choiceIndex == bestChoice) {
            return new QuestionResult(AnswerQuality.BEST, bestRewardSeconds, correctFeedback);
        }
        if (choiceIndex == secondBestChoice) {
            return new QuestionResult(AnswerQuality.SECOND_BEST, 0, "Acceptable answer; no time gained");
        }
        return new QuestionResult(AnswerQuality.WRONG, -wrongPenaltySeconds, wrongFeedback);
    }
}
