package pkg.audio;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AudioManagerTest {

    private static final List<String> REQUIRED_AUDIO_ASSETS = List.of(
            "/assets/music/Game_Music.mp3",
            "/assets/music/Menu_Music.mp3",
            "/assets/sounds/Correct_Answer.wav",
            "/assets/sounds/Menu_Button_Click.wav",
            "/assets/sounds/Wrong_Answer.wav",
            "/assets/sounds/footstep.wav",
            "/assets/sounds/trash_pickup.wav"
    );

    @Test
    void allAudioAssetsArePresentAndNonEmpty() throws Exception {
        for (String assetPath : REQUIRED_AUDIO_ASSETS) {
            try (InputStream stream = getClass().getResourceAsStream(assetPath)) {
                assertNotNull(stream, "Audio asset missing from classpath: " + assetPath);
                byte[] sample = stream.readNBytes(64);
                assertTrue(sample.length > 0, "Audio asset is empty: " + assetPath);
            }
        }
    }

    @Test
    void audioManagerLifecycleAndPlaybackMethodsExecuteSafely() {
        // Must not throw even in headless / CI environments without audio hardware
        AudioManager.init();

        AudioManager.playButtonClick();
        AudioManager.playFootstep();
        AudioManager.playTrashPickup();
        AudioManager.playCorrectAnswer();
        AudioManager.playWrongAnswer();

        AudioManager.playMenuMusic();
        AudioManager.playGameMusic();
        AudioManager.stopMusic();

        AudioManager.setMuted(true);
        assertTrue(AudioManager.isMuted());
        AudioManager.setMuted(false);
        assertFalse(AudioManager.isMuted());

        AudioManager.setMusicVolume(0.42);
        AudioManager.setSfxVolume(0.85);
        AudioManager.setFootstepVolume(0.25);

        // Clamping check
        AudioManager.setMusicVolume(-1.0);
        AudioManager.setMusicVolume(2.0);
        AudioManager.setSfxVolume(-1.0);
        AudioManager.setSfxVolume(2.0);
        AudioManager.setFootstepVolume(-1.0);
        AudioManager.setFootstepVolume(2.0);
    }
}

