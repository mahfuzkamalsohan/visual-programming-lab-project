package pkg.restoration.questions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class QuestionSelector {
    private QuestionSelector() {
    }

    public static List<EnvironmentalQuestion> randomUnique(
            List<EnvironmentalQuestion> questions, int count) {
        return randomUnique(questions, count, ThreadLocalRandom.current());
    }

    public static List<EnvironmentalQuestion> randomUnique(
            List<EnvironmentalQuestion> questions, int count, Random random) {
        Objects.requireNonNull(questions, "questions");
        Objects.requireNonNull(random, "random");
        if (count < 0 || count > questions.size()) {
            throw new IllegalArgumentException(
                    "Requested " + count + " questions from a bank of " + questions.size());
        }
        List<EnvironmentalQuestion> shuffled = new ArrayList<>(questions);
        Collections.shuffle(shuffled, random);
        return List.copyOf(shuffled.subList(0, count));
    }
}
