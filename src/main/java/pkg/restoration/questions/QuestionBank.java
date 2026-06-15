package pkg.restoration.questions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuestionBank {

    private static final String RESOURCE_ROOT = "assets/restoration/questions/";

    private final Map<Difficulty, List<QuestionChallenge>> challenges = new EnumMap<>(Difficulty.class);
    private final Map<Difficulty, Integer> cursors = new EnumMap<>(Difficulty.class);

    public static QuestionBank loadDefault() {
        QuestionBank bank = new QuestionBank();
        for (Difficulty difficulty : Difficulty.values()) {
            bank.load(difficulty, RESOURCE_ROOT + difficulty.name().toLowerCase() + ".dat");
        }
        return bank;
    }

    public QuestionChallenge next(Difficulty requestedDifficulty, ChallengeType type) {
        Difficulty difficulty = requestedDifficulty;

        for (int attempts = 0; attempts < Difficulty.values().length; attempts++) {
            List<QuestionChallenge> filtered = challenges.getOrDefault(difficulty, List.of()).stream()
                    .filter(challenge -> challenge.type() == type)
                    .toList();

            if (!filtered.isEmpty()) {
                int cursor = cursors.getOrDefault(difficulty, 0);
                QuestionChallenge challenge = filtered.get(cursor % filtered.size());
                cursors.put(difficulty, cursor + 1);
                return challenge;
            }

            difficulty = fallbackDifficulty(difficulty);
        }

        throw new IllegalStateException("No " + type + " challenges were loaded.");
    }

    private void load(Difficulty difficulty, String resourceName) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                challenges.put(difficulty, List.of());
                return;
            }

            challenges.put(difficulty, parse(inputStream, difficulty));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load question data from " + resourceName, exception);
        }
    }

    private List<QuestionChallenge> parse(InputStream inputStream, Difficulty difficulty) throws IOException {
        List<QuestionChallenge> loaded = new ArrayList<>();
        Map<String, String> current = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) {
                    flushRecord(loaded, current, difficulty);
                    current.clear();
                    continue;
                }

                if (trimmed.startsWith("#")) {
                    continue;
                }

                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("Invalid question line: " + line);
                }

                current.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
            }
        }

        flushRecord(loaded, current, difficulty);
        return Collections.unmodifiableList(loaded);
    }

    private void flushRecord(List<QuestionChallenge> loaded, Map<String, String> record, Difficulty difficulty) {
        if (record.isEmpty()) {
            return;
        }

        String id = required(record, "id");
        ChallengeType type = ChallengeType.valueOf(required(record, "type").toUpperCase());
        List<String> choices = parseChoices(required(record, "choices"));
        Set<Integer> answers = parseAnswers(required(record, "answer"), choices.size());
        int reward = intValue(record, "reward", difficulty.rewardSeconds());
        int penalty = intValue(record, "penalty", difficulty.penaltySeconds());

        loaded.add(new QuestionChallenge(
                id,
                difficulty,
                type,
                required(record, "prompt"),
                choices,
                answers,
                reward,
                penalty,
                record.getOrDefault("feedback.correct", "Correct. Restoration time increased."),
                record.getOrDefault("feedback.wrong", "Incorrect. The gate opens, but time is lost.")
        ));
    }

    private static String required(Map<String, String> record, String key) {
        String value = record.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Question record is missing required key: " + key);
        }
        return value;
    }

    private static List<String> parseChoices(String value) {
        String[] split = value.split("\\|");
        List<String> choices = new ArrayList<>();
        for (String choice : split) {
            if (!choice.isBlank()) {
                choices.add(choice.trim());
            }
        }

        if (choices.size() < 2 || choices.size() > 3) {
            throw new IllegalArgumentException("Challenges must have two or three choices.");
        }

        return Collections.unmodifiableList(choices);
    }

    private static Set<Integer> parseAnswers(String value, int choiceCount) {
        Set<Integer> answers = new HashSet<>();
        for (String token : value.split(",")) {
            int index = Integer.parseInt(token.trim());
            if (index < 0 || index >= choiceCount) {
                throw new IllegalArgumentException("Answer index " + index + " is outside the choice range.");
            }
            answers.add(index);
        }
        return Collections.unmodifiableSet(answers);
    }

    private static int intValue(Map<String, String> record, String key, int fallback) {
        String value = record.get(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static Difficulty fallbackDifficulty(Difficulty difficulty) {
        return switch (difficulty) {
            case HARD -> Difficulty.MEDIUM;
            case MEDIUM -> Difficulty.EASY;
            case EASY -> Difficulty.HARD;
        };
    }
}
