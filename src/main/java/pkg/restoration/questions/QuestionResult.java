package pkg.restoration.questions;

import pkg.restoration.tasks.TaskResult;
import pkg.restoration.tasks.TaskStatus;

public record QuestionResult(AnswerQuality quality, double timeDeltaSeconds, String feedback) {
    public TaskResult asTaskResult() {
        TaskStatus status = quality == AnswerQuality.WRONG ? TaskStatus.REJECTED : TaskStatus.COMPLETED;
        return new TaskResult(status, 1, 1, timeDeltaSeconds, feedback);
    }
}
