package pkg.restoration.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Tracks items that must each be placed in their configured destination bin. */
public final class SortingTask {
    private final Map<String, String> expectedBins;
    private final Map<String, String> sortedItems = new HashMap<>();
    private final double completionRewardSeconds;
    private final double correctItemRewardSeconds;
    private final double wrongBinPenaltySeconds;

    public SortingTask(Map<String, String> expectedBins, double completionRewardSeconds) {
        this(expectedBins, completionRewardSeconds, 0, 0);
    }

    public SortingTask(Map<String, String> expectedBins, double completionRewardSeconds,
                       double correctItemRewardSeconds, double wrongBinPenaltySeconds) {
        Objects.requireNonNull(expectedBins, "expectedBins");
        if (expectedBins.isEmpty()) {
            throw new IllegalArgumentException("expectedBins must not be empty");
        }
        if (!Double.isFinite(completionRewardSeconds) || completionRewardSeconds < 0) {
            throw new IllegalArgumentException("completionRewardSeconds must be finite and non-negative");
        }
        if (!Double.isFinite(correctItemRewardSeconds) || correctItemRewardSeconds < 0
                || !Double.isFinite(wrongBinPenaltySeconds) || wrongBinPenaltySeconds < 0) {
            throw new IllegalArgumentException("Item reward and wrong-bin penalty must be finite and non-negative");
        }
        this.expectedBins = Map.copyOf(expectedBins);
        if (this.expectedBins.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue().isBlank())) {
            throw new IllegalArgumentException("item IDs and bin IDs must not be blank");
        }
        this.completionRewardSeconds = completionRewardSeconds;
        this.correctItemRewardSeconds = correctItemRewardSeconds;
        this.wrongBinPenaltySeconds = wrongBinPenaltySeconds;
    }

    public TaskResult sort(String itemId, String binId) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(binId, "binId");
        if (isComplete()) {
            return result(TaskStatus.ALREADY_COMPLETED, 0, "Sorting task is already complete");
        }
        if (!expectedBins.containsKey(itemId)) {
            return result(TaskStatus.REJECTED, 0, "Unknown item");
        }
        if (sortedItems.containsKey(itemId)) {
            return result(TaskStatus.REJECTED, 0, "Item has already been sorted");
        }
        if (!expectedBins.get(itemId).equals(binId)) {
            return result(TaskStatus.REJECTED, -wrongBinPenaltySeconds, "Wrong bin");
        }

        sortedItems.put(itemId, binId);
        if (isComplete()) {
            return result(TaskStatus.COMPLETED, correctItemRewardSeconds + completionRewardSeconds,
                    "Sorting task complete");
        }
        return result(TaskStatus.IN_PROGRESS, correctItemRewardSeconds, "Item sorted correctly");
    }

    public boolean isComplete() {
        return sortedItems.size() == expectedBins.size();
    }

    public int sortedItems() {
        return sortedItems.size();
    }

    private TaskResult result(TaskStatus status, double delta, String message) {
        return new TaskResult(status, sortedItems(), expectedBins.size(), delta, message);
    }
}
