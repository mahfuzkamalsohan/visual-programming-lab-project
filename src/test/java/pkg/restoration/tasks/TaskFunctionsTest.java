package pkg.restoration.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pkg.restoration.questions.AnswerQuality;
import pkg.restoration.questions.EnvironmentalQuestion;
import pkg.restoration.systems.RestorationTimer;

class TaskFunctionsTest {

    @Test
    void collectionRewardsOnlyWhenCompleted() {
        CollectionTask task = new CollectionTask(2, 12);

        assertEquals(0, task.collect("can").timeDeltaSeconds());
        TaskResult completion = task.collect("bottle");
        assertTrue(completion.completedNow());
        assertEquals(12, completion.timeDeltaSeconds());
        assertEquals(0, task.collect("extra").timeDeltaSeconds());
    }

    @Test
    void collectionDoesNotCountAnItemTwice() {
        CollectionTask task = new CollectionTask(2, 12);
        task.collect("can");

        assertEquals(TaskStatus.REJECTED, task.collect("can").status());
        assertEquals(1, task.collectedItems());
        assertFalse(task.isComplete());
    }

    @Test
    void sortingRejectsWrongBinAndRewardsCompletionOnce() {
        SortingTask task = new SortingTask(Map.of("newspaper", "paper", "bottle", "plastic"), 20);

        assertEquals(TaskStatus.REJECTED, task.sort("newspaper", "plastic").status());
        assertEquals(TaskStatus.IN_PROGRESS, task.sort("newspaper", "paper").status());
        TaskResult completion = task.sort("bottle", "plastic");
        assertEquals(20, completion.timeDeltaSeconds());
        assertEquals(0, task.sort("bottle", "plastic").timeDeltaSeconds());
    }

    @Test
    void sortingCanRewardEachCorrectItemAndPenalizeWrongBins() {
        SortingTask task = new SortingTask(Map.of("battery", "red", "peel", "green"), 15, 7, 8);

        assertEquals(-8, task.sort("battery", "blue").timeDeltaSeconds());
        assertEquals(7, task.sort("battery", "red").timeDeltaSeconds());
        assertEquals(22, task.sort("peel", "green").timeDeltaSeconds());
    }

    @Test
    void questionRanksBestSecondAndWrongAnswers() {
        EnvironmentalQuestion question = new EnvironmentalQuestion(
                "q1", "Choose", java.util.List.of("best", "okay", "bad"),
                0, 1, 15, 10, "good", "bad");

        assertEquals(AnswerQuality.BEST, question.answer(0).quality());
        assertEquals(15, question.answer(0).timeDeltaSeconds());
        assertEquals(0, question.answer(1).timeDeltaSeconds());
        assertEquals(-10, question.answer(2).timeDeltaSeconds());
    }

    @Test
    void timerClampsTaskRewardsAndPenalties() {
        RestorationTimer timer = new RestorationTimer(5, 20);
        TaskTimer.apply(timer, new TaskResult(TaskStatus.COMPLETED, 1, 1, 30, "reward"));
        assertEquals(20, timer.currentSeconds());

        TaskTimer.apply(timer, new TaskResult(TaskStatus.REJECTED, 0, 1, -30, "penalty"));
        assertEquals(0, timer.currentSeconds());
    }
}
