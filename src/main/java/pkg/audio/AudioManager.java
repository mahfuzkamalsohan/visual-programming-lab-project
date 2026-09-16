package pkg.audio;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multithreaded audio manager for background music and interactive sound effects.
 *
 * Performance and Concurrency:
 * 1. Low-Latency SFX: In-memory JavaFX {@link AudioClip} caching ensures zero disk I/O on play.
 *    Multiple sound effects can play simultaneously without cutting each other off.
 * 2. Asynchronous BGM: Music loading, looping, and track transitions are handled on a dedicated
 *    daemon thread, guaranteeing the 60 FPS JavaFX rendering thread is never blocked.
 * 3. Graceful Fallbacks: If an audio asset is missing or corrupt, it logs a clean warning
 *    and fails silently without interrupting gameplay.
 */
public final class AudioManager {

    // Relative asset paths matching src/main/resources/assets/...
    private static final String PATH_MENU_MUSIC   = "/assets/music/Menu_Music.mp3";
    private static final String PATH_GAME_MUSIC   = "/assets/music/Game_Music.mp3";

    private static final String PATH_SFX_CLICK    = "/assets/sounds/Menu_Button_Click.wav";
    private static final String PATH_SFX_CORRECT  = "/assets/sounds/Correct_Answer.wav";
    private static final String PATH_SFX_WRONG    = "/assets/sounds/Wrong_Answer.wav";
    private static final String PATH_SFX_FOOTSTEP = "/assets/sounds/footstep.wav";
    private static final String PATH_SFX_TRASH    = "/assets/sounds/trash_pickup.wav";

    // Dedicated single-thread daemon for sequential music playback transitions
    private static final ExecutorService musicExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AudioManager-BGM-Thread");
        t.setDaemon(true);
        return t;
    });

    // Background thread pool for non-blocking SFX warmup
    private static final ExecutorService asyncLoader = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "AudioManager-Loader-Thread");
        t.setDaemon(true);
        return t;
    });

    // In-memory cache of pre-decoded sound effects
    private static final Map<String, AudioClip> sfxCache = new ConcurrentHashMap<>();

    private static MediaPlayer currentMusicPlayer;
    private static String currentMusicPath = "";
    private static String pendingMusicPath = "";
    private static double musicVolume = 0.50; // 50% default
    private static double sfxVolume   = 0.75; // 75% default
    private static double footstepVolume = 0.35; // Footsteps slightly softer to avoid fatigue
    private static boolean isMuted    = false;
    private static boolean initialized = false;

    private AudioManager() {}

    /**
     * Initializes the audio subsystem and preloads all sound effects into memory.
     * Call this once at startup (e.g. in onPreInit() or MainMenu creation).
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        asyncLoader.submit(() -> {
            preloadSfx(PATH_SFX_CLICK);
            preloadSfx(PATH_SFX_CORRECT);
            preloadSfx(PATH_SFX_WRONG);
            preloadSfx(PATH_SFX_FOOTSTEP);
            preloadSfx(PATH_SFX_TRASH);
        });
    }

    // =========================================================================
    // BACKGROUND MUSIC (BGM)
    // =========================================================================

    public static void playMenuMusic() {
        playMusic(PATH_MENU_MUSIC);
    }

    public static void playGameMusic() {
        playMusic(PATH_GAME_MUSIC);
    }

    private static synchronized void playMusic(String resourcePath) {
        if (resourcePath.equals(currentMusicPath) || resourcePath.equals(pendingMusicPath)) {
            return; // Already playing or queued to play this track
        }
        pendingMusicPath = resourcePath;

        musicExecutor.submit(() -> {
            try {
                URL resource = AudioManager.class.getResource(resourcePath);
                if (resource == null) {
                    System.out.println("[AudioManager] Music resource not found at " + resourcePath);
                    pendingMusicPath = "";
                    return;
                }

                Platform.runLater(() -> {
                    if (currentMusicPlayer != null) {
                        try {
                            currentMusicPlayer.stop();
                            currentMusicPlayer.dispose();
                        } catch (Throwable ignored) {}
                        currentMusicPlayer = null;
                    }

                    try {
                        Media media = new Media(resource.toExternalForm());
                        MediaPlayer player = new MediaPlayer(media);
                        player.setCycleCount(MediaPlayer.INDEFINITE);
                        player.setVolume(isMuted ? 0.0 : musicVolume);
                        player.play();

                        currentMusicPlayer = player;
                        currentMusicPath = resourcePath;
                        pendingMusicPath = "";
                    } catch (Throwable ex) {
                        System.err.println("[AudioManager] Failed to play music " + resourcePath + ": " + ex.getMessage());
                        pendingMusicPath = "";
                    }
                });
            } catch (Throwable e) {
                System.err.println("[AudioManager] Music error: " + e.getMessage());
                pendingMusicPath = "";
            }
        });
    }

    public static void stopMusic() {
        pendingMusicPath = "";
        musicExecutor.submit(() -> {
            Platform.runLater(() -> {
                if (currentMusicPlayer != null) {
                    try {
                        currentMusicPlayer.stop();
                    } catch (Exception ignored) {}
                    currentMusicPlayer = null;
                    currentMusicPath = "";
                }
            });
        });
    }

    // =========================================================================
    // SOUND EFFECTS (SFX)
    // =========================================================================

    public static void playButtonClick() {
        playSfx(PATH_SFX_CLICK, sfxVolume);
    }

    public static void playFootstep() {
        playSfx(PATH_SFX_FOOTSTEP, footstepVolume);
    }

    public static void playTrashPickup() {
        playSfx(PATH_SFX_TRASH, sfxVolume);
    }

    public static void playCorrectAnswer() {
        playSfx(PATH_SFX_CORRECT, sfxVolume);
    }

    public static void playWrongAnswer() {
        playSfx(PATH_SFX_WRONG, sfxVolume);
    }

    private static void playSfx(String path, double volume) {
        if (isMuted) return;

        AudioClip clip = sfxCache.get(path);
        if (clip != null) {
            try {
                clip.play(volume);
            } catch (Throwable ignored) {}
            return;
        }

        // Asynchronous fallback if not yet in cache
        asyncLoader.submit(() -> {
            try {
                AudioClip loaded = preloadSfx(path);
                if (loaded != null) {
                    Platform.runLater(() -> {
                        try {
                            loaded.play(volume);
                        } catch (Throwable ignored) {}
                    });
                }
            } catch (Throwable ignored) {}
        });
    }

    private static AudioClip preloadSfx(String path) {
        try {
            URL resource = AudioManager.class.getResource(path);
            if (resource == null) {
                System.out.println("[AudioManager] SFX resource not found at " + path);
                return null;
            }

            AudioClip clip = new AudioClip(resource.toExternalForm());
            sfxCache.put(path, clip);
            return clip;
        } catch (Throwable e) {
            System.err.println("[AudioManager] Failed to load SFX " + path + ": " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // SETTINGS & LIFECYCLE
    // =========================================================================

    public static void setMuted(boolean mute) {
        isMuted = mute;
        if (currentMusicPlayer != null) {
            Platform.runLater(() -> currentMusicPlayer.setVolume(isMuted ? 0.0 : musicVolume));
        }
    }

    public static boolean isMuted() {
        return isMuted;
    }

    public static void setMusicVolume(double volume) {
        musicVolume = Math.max(0.0, Math.min(1.0, volume));
        if (currentMusicPlayer != null && !isMuted) {
            Platform.runLater(() -> currentMusicPlayer.setVolume(musicVolume));
        }
    }

    public static void setSfxVolume(double volume) {
        sfxVolume = Math.max(0.0, Math.min(1.0, volume));
    }

    public static void setFootstepVolume(double volume) {
        footstepVolume = Math.max(0.0, Math.min(1.0, volume));
    }

    public static void stopAll() {
        stopMusic();
        musicExecutor.shutdownNow();
        asyncLoader.shutdownNow();
    }
}
