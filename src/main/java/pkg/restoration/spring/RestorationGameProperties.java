package pkg.restoration.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "restoration.game")
public record RestorationGameProperties(
        int width,
        int height,
        String title,
        String version,
        double startTimeSeconds,
        double maxTimeSeconds,
        double playerSpeedTiles
) {
}
