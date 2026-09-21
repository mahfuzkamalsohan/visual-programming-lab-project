package pkg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HighScoreDatabaseTest {

    @TempDir
    Path tempDir;

    private Path testDbFile;
    private HighScoreDatabase db;

    @BeforeEach
    void setUp() {
        testDbFile = tempDir.resolve("test_highscores.txt");
        db = new HighScoreDatabase(testDbFile);
    }

    @Test
    void testInitialStateIsEmpty() {
        for (GameMode mode : GameMode.values()) {
            assertEquals(0, db.getHighScore(mode));
            assertTrue(db.getTopScores(mode, 10).isEmpty());
        }
    }

    @Test
    void testRecordScoreSinglePlayer() {
        boolean firstHigh = db.recordScore(GameMode.SINGLE_PLAYER, "Alice", 100);
        assertTrue(firstHigh, "First non-zero score should be a high score");
        assertEquals(100, db.getHighScore(GameMode.SINGLE_PLAYER));

        boolean lowerScore = db.recordScore(GameMode.SINGLE_PLAYER, "Bob", 50);
        assertFalse(lowerScore, "Lower score should not set new high score record");
        assertEquals(100, db.getHighScore(GameMode.SINGLE_PLAYER));

        boolean higherScore = db.recordScore(GameMode.SINGLE_PLAYER, "Charlie", 250);
        assertTrue(higherScore, "Higher score should set new high score record");
        assertEquals(250, db.getHighScore(GameMode.SINGLE_PLAYER));
    }

    @Test
    void testScoresForMultipleGameModes() {
        db.recordScore(GameMode.SINGLE_PLAYER, "P1", 100);
        db.recordScore(GameMode.QUESTION_TEST, "P2", 200);
        db.recordScore(GameMode.SORTING_TEST, "P3", 300);
        db.recordScore(GameMode.ANIMAL_RESCUE, "P4", 400);

        assertEquals(100, db.getHighScore(GameMode.SINGLE_PLAYER));
        assertEquals(200, db.getHighScore(GameMode.QUESTION_TEST));
        assertEquals(300, db.getHighScore(GameMode.SORTING_TEST));
        assertEquals(400, db.getHighScore(GameMode.ANIMAL_RESCUE));
        assertEquals(0, db.getHighScore(GameMode.LAN_HOST));

        Map<GameMode, Integer> allHighs = db.getAllHighScores();
        assertEquals(100, allHighs.get(GameMode.SINGLE_PLAYER));
        assertEquals(200, allHighs.get(GameMode.QUESTION_TEST));
        assertEquals(300, allHighs.get(GameMode.SORTING_TEST));
        assertEquals(400, allHighs.get(GameMode.ANIMAL_RESCUE));
    }

    @Test
    void testPersistenceAcrossInstances() {
        db.recordScore(GameMode.SEQUENTIAL_DEMO, "Player1", 500);
        db.recordScore(GameMode.LOCAL_COOP_SPLITSCREEN, "Player2", 750);

        // Load new DB instance from same file
        HighScoreDatabase reloadedDb = new HighScoreDatabase(testDbFile);
        assertEquals(500, reloadedDb.getHighScore(GameMode.SEQUENTIAL_DEMO));
        assertEquals(750, reloadedDb.getHighScore(GameMode.LOCAL_COOP_SPLITSCREEN));
    }

    @Test
    void testTopScoresSorting() {
        db.recordScore(GameMode.SINGLE_PLAYER, "Player A", 150);
        db.recordScore(GameMode.SINGLE_PLAYER, "Player C", 300);
        db.recordScore(GameMode.SINGLE_PLAYER, "Player B", 500);

        List<HighScoreDatabase.ScoreRecord> top = db.getTopScores(GameMode.SINGLE_PLAYER, 10);
        assertEquals(3, top.size());
        assertEquals(500, top.get(0).score());
        assertEquals("Player B", top.get(0).playerName());
        assertEquals(300, top.get(1).score());
        assertEquals(150, top.get(2).score());
    }

    @Test
    void testClearScores() {
        db.recordScore(GameMode.SINGLE_PLAYER, "Test", 1000);
        db.clearScores();
        assertEquals(0, db.getHighScore(GameMode.SINGLE_PLAYER));

        HighScoreDatabase reloaded = new HighScoreDatabase(testDbFile);
        assertEquals(0, reloaded.getHighScore(GameMode.SINGLE_PLAYER));
    }

    @Test
    void testCorruptedFileHandling() throws IOException {
        Files.writeString(testDbFile, "INVALID_DATA\nNOT_A_MODE|Player|abc|time\nSINGLE_PLAYER|ValidPlayer|999|2026-09-21 12:00\n");

        HighScoreDatabase corruptedDb = new HighScoreDatabase(testDbFile);
        assertEquals(999, corruptedDb.getHighScore(GameMode.SINGLE_PLAYER));
    }
}
