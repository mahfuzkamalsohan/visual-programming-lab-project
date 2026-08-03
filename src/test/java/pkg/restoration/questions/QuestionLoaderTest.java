package pkg.restoration.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionLoaderTest {

    @Test
    void loadsBundledQuestionResource() throws IOException {
        List<EnvironmentalQuestion> questions =
                new QuestionLoader().loadResource("assets/questions/environment.dat");

        assertEquals(3, questions.size());
        assertEquals(AnswerQuality.BEST, questions.getFirst().answer(0).quality());
        assertEquals(AnswerQuality.SECOND_BEST, questions.getFirst().answer(1).quality());
        assertEquals(AnswerQuality.WRONG, questions.getFirst().answer(2).quality());
    }

    @Test
    void loadsEveryDatFileInAQuestionFolder() throws IOException {
        List<EnvironmentalQuestion> questions = new QuestionLoader()
                .loadDirectory(Path.of("src/main/resources/assets/questions"));

        assertEquals(3, questions.size());
    }
}
