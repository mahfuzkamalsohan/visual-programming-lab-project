package pkg.restoration.spring;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "pkg.restoration")
@EnableConfigurationProperties(RestorationGameProperties.class)
public class RestorationSpringApplication {
}
