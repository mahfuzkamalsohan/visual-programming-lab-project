package pkg.restoration.tasks;

/** A UI-independent description of what happened while attempting a task. */
public record TaskResult(
        TaskStatus status,
        int completedItems,
        int requiredItems,
        double timeDeltaSeconds,
        String message) {

    public boolean completedNow() {
        return status == TaskStatus.COMPLETED;
    }
}
