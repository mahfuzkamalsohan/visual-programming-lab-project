package pkg.restoration.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class QuestionLoaderTest {

    private static final int EXPECTED_QUESTION_COUNT = 53;

    @Test
    void loadsBundledQuestionResource() throws IOException {
        List<EnvironmentalQuestion> questions =
                new QuestionLoader().loadResource("assets/questions/environment.dat");

        assertEquals(EXPECTED_QUESTION_COUNT, questions.size());
        assertEquals(AnswerQuality.BEST, questions.getFirst().answer(0).quality());
        assertEquals(AnswerQuality.SECOND_BEST, questions.getFirst().answer(1).quality());
        assertEquals(AnswerQuality.WRONG, questions.getFirst().answer(2).quality());
    }

    @Test
    void loadsEveryDatFileInAQuestionFolder() throws IOException {
        List<EnvironmentalQuestion> questions = new QuestionLoader()
                .loadDirectory(Path.of("src/main/resources/assets/questions"));

        assertEquals(EXPECTED_QUESTION_COUNT, questions.size());
    }

    @Test
    void selectsSixUniqueDemoQuestions() throws IOException {
        List<EnvironmentalQuestion> questions =
                new QuestionLoader().loadResource("assets/questions/environment.dat");

        List<EnvironmentalQuestion> selected =
                QuestionSelector.randomUnique(questions, 6, new Random(1234));

        assertEquals(6, selected.size());
        assertEquals(6, new HashSet<>(selected.stream().map(EnvironmentalQuestion::id).toList()).size());
    }
}
