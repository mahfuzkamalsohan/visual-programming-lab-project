package pkg.restoration.tasks;

import java.util.Objects;
import pkg.restoration.systems.RestorationTimer;

/** Applies a task or question result to the countdown timer. */
public final class TaskTimer {
    private TaskTimer() {
    }

    public static double apply(RestorationTimer timer, TaskResult result) {
        Objects.requireNonNull(timer, "timer");
        Objects.requireNonNull(result, "result");
        double before = timer.currentSeconds();
        timer.applyDelta(result.timeDeltaSeconds());
        return timer.currentSeconds() - before;
    }
}
