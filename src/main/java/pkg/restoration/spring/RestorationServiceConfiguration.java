package pkg.restoration.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pkg.restoration.questions.QuestionBank;

@Configuration
public class RestorationServiceConfiguration {

    @Bean
    QuestionBank questionBank() {
        return QuestionBank.loadDefault();
    }
}
