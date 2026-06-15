package pkg.restoration.questions;

import java.util.Locale;

public enum Difficulty {
    EASY(8, 5),
    MEDIUM(12, 8),
    HARD(18, 12);

    private final int rewardSeconds;
    private final int penaltySeconds;

    Difficulty(int rewardSeconds, int penaltySeconds) {
        this.rewardSeconds = rewardSeconds;
        this.penaltySeconds = penaltySeconds;
    }

    public int rewardSeconds() {
        return rewardSeconds;
    }

    public int penaltySeconds() {
        return penaltySeconds;
    }

    public static Difficulty fromFileName(String fileName) {
        String normalized = fileName.toUpperCase(Locale.ROOT).replace(".DAT", "");
        return Difficulty.valueOf(normalized);
    }
}
