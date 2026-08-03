package pkg.restoration.tasks;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Tracks distinct collectables and awards time once, when the target is reached. */
public final class CollectionTask {
    private final int requiredItems;
    private final double completionRewardSeconds;
    private final Set<String> collectedItemIds = new HashSet<>();

    public CollectionTask(int requiredItems, double completionRewardSeconds) {
        if (requiredItems <= 0) {
            throw new IllegalArgumentException("requiredItems must be positive");
        }
        if (!Double.isFinite(completionRewardSeconds) || completionRewardSeconds < 0) {
            throw new IllegalArgumentException("completionRewardSeconds must be finite and non-negative");
        }
        this.requiredItems = requiredItems;
        this.completionRewardSeconds = completionRewardSeconds;
    }

    public TaskResult collect(String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        if (isComplete()) {
            return result(TaskStatus.ALREADY_COMPLETED, 0, "Collection task is already complete");
        }
        if (itemId.isBlank() || !collectedItemIds.add(itemId)) {
            return result(TaskStatus.REJECTED, 0, "Item is blank or has already been collected");
        }
        if (isComplete()) {
            return result(TaskStatus.COMPLETED, completionRewardSeconds, "Collection task complete");
        }
        return result(TaskStatus.IN_PROGRESS, 0, "Item collected");
    }

    public boolean isComplete() {
        return collectedItemIds.size() >= requiredItems;
    }

    public int collectedItems() {
        return collectedItemIds.size();
    }

    private TaskResult result(TaskStatus status, double delta, String message) {
        return new TaskResult(status, collectedItems(), requiredItems, delta, message);
    }
}
