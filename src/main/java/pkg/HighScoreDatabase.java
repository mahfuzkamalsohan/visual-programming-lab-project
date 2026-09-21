package pkg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simple file-based database for storing and managing high scores across all game modes.
 * Uses a plain text file format (MODE|PLAYER|SCORE|TIMESTAMP).
 */
public class HighScoreDatabase {

    public record ScoreRecord(GameMode mode, String playerName, int score, String timestamp) implements Comparable<ScoreRecord> {
        @Override
        public int compareTo(ScoreRecord o) {
            // Sort descending by score, then ascending by timestamp/name
            int cmp = Integer.compare(o.score, this.score);
            if (cmp != 0) return cmp;
            return this.timestamp.compareTo(o.timestamp);
        }
    }

    private static final String DEFAULT_FILE_NAME = "highscores.txt";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static HighScoreDatabase instance;

    private final Path filePath;
    private final Map<GameMode, List<ScoreRecord>> scoresByMode = new ConcurrentHashMap<>();

    public HighScoreDatabase() {
        this(Paths.get(DEFAULT_FILE_NAME));
    }

    public HighScoreDatabase(Path filePath) {
        this.filePath = filePath;
        for (GameMode mode : GameMode.values()) {
            scoresByMode.put(mode, new ArrayList<>());
        }
        load();
    }

    public static synchronized HighScoreDatabase getInstance() {
        if (instance == null) {
            instance = new HighScoreDatabase();
        }
        return instance;
    }

    /**
     * Loads high score records from the database file.
     */
    public synchronized void load() {
        // Clear in-memory cache
        for (GameMode mode : GameMode.values()) {
            scoresByMode.put(mode, new ArrayList<>());
        }

        if (!Files.exists(filePath)) {
            // No file yet; initialize empty file
            save();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip comments and empty lines
                }
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 3) {
                    try {
                        GameMode mode = GameMode.valueOf(parts[0].trim());
                        String player = parts[1].trim();
                        int score = Integer.parseInt(parts[2].trim());
                        String timestamp = parts.length >= 4 ? parts[3].trim() : LocalDateTime.now().format(DATE_FORMATTER);

                        scoresByMode.computeIfAbsent(mode, k -> new ArrayList<>())
                                .add(new ScoreRecord(mode, player, score, timestamp));
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed records
                    }
                }
            }

            // Sort all lists descending
            for (List<ScoreRecord> list : scoresByMode.values()) {
                Collections.sort(list);
            }
        } catch (IOException e) {
            System.err.println("[HighScoreDatabase] Error loading database file: " + e.getMessage());
        }
    }

    /**
     * Saves current in-memory high score records to the database file.
     */
    public synchronized void save() {
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                writer.write("# High Score Database File\n");
                writer.write("# Format: GAME_MODE|PLAYER_NAME|SCORE|TIMESTAMP\n");

                for (GameMode mode : GameMode.values()) {
                    List<ScoreRecord> list = scoresByMode.getOrDefault(mode, Collections.emptyList());
                    for (ScoreRecord record : list) {
                        writer.write(String.format("%s|%s|%d|%s\n",
                                record.mode().name(),
                                record.playerName().replace("|", "_"),
                                record.score(),
                                record.timestamp()));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[HighScoreDatabase] Error saving database file: " + e.getMessage());
        }
    }

    /**
     * Records a new score for the specified game mode.
     * Returns true if this score sets a NEW HIGH SCORE for the mode.
     */
    public synchronized boolean recordScore(GameMode mode, String playerName, int score) {
        if (mode == null || score <= 0) return false;
        String player = (playerName == null || playerName.isBlank()) ? "Player" : playerName.trim();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);

        int currentHigh = getHighScore(mode);
        if (score <= currentHigh) {
            return false; // Only write if it's a new high score!
        }

        ScoreRecord record = new ScoreRecord(mode, player, score, timestamp);
        List<ScoreRecord> list = scoresByMode.computeIfAbsent(mode, k -> new ArrayList<>());
        list.add(record);
        Collections.sort(list);

        // Keep top 100 entries per mode to keep DB file compact
        if (list.size() > 100) {
            scoresByMode.put(mode, new ArrayList<>(list.subList(0, 100)));
        }

        save();
        return true;
    }

    /**
     * Gets the highest score for a specific game mode. Returns 0 if no score recorded yet.
     */
    public synchronized int getHighScore(GameMode mode) {
        List<ScoreRecord> records = scoresByMode.get(mode);
        if (records == null || records.isEmpty()) {
            return 0;
        }
        return records.get(0).score();
    }

    /**
     * Gets top N scores for a game mode sorted descending.
     */
    public synchronized List<ScoreRecord> getTopScores(GameMode mode, int limit) {
        List<ScoreRecord> records = scoresByMode.getOrDefault(mode, Collections.emptyList());
        return records.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Gets all high scores mapped by GameMode.
     */
    public synchronized Map<GameMode, Integer> getAllHighScores() {
        Map<GameMode, Integer> result = new EnumMap<>(GameMode.class);
        for (GameMode mode : GameMode.values()) {
            result.put(mode, getHighScore(mode));
        }
        return result;
    }

    /**
     * Gets all score records for a game mode.
     */
    public synchronized List<ScoreRecord> getAllRecordsForMode(GameMode mode) {
        return new ArrayList<>(scoresByMode.getOrDefault(mode, Collections.emptyList()));
    }

    /**
     * Clears all stored high scores.
     */
    public synchronized void clearScores() {
        for (GameMode mode : GameMode.values()) {
            scoresByMode.put(mode, new ArrayList<>());
        }
        save();
    }
}
