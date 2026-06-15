package pkg.restoration.questions;

public record ChallengeResult(
        boolean correct,
        int deltaSeconds,
        String feedback
) {
}
