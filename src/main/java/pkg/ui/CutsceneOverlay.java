package pkg.ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public class CutsceneOverlay extends StackPane {

    private final Runnable onFinishedCallback;
    private final ImageView imageView;
    private final VBox dialogueBox;
    private final Label dialogueTextLabel;
    private final Label promptLabel;

    private List<WritableImage> frames = new ArrayList<>();
    private List<Integer> frameDelays = new ArrayList<>();

    private int currentFrameIndex = 0;
    private boolean cutsceneFinished = false;
    private long lastFrameTime = 0;

    private int dialogueIndex = 0;
    private final String[] dialogueLines = {
            "The world throws away about 3.5 million tons of trash every single day...",
            "Mismanaged waste, pollution and deforestation are damaging our beautiful planet.",
            "We have to do something..."
    };

    private AnimationTimer cutsceneTimer;
    private static Font customPressStartFont = null;

    public static Font getPressStartFont(double size) {
        if (customPressStartFont == null) {
            try (InputStream is = CutsceneOverlay.class
                    .getResourceAsStream("/assets/ui/fonts/PressStart2P-Regular.ttf")) {
                if (is != null) {
                    customPressStartFont = Font.loadFont(is, 12);
                }
            } catch (Exception ignored) {
            }
        }
        if (customPressStartFont != null) {
            return Font.font(customPressStartFont.getFamily(), size);
        }
        return Font.font("Monospaced", FontWeight.BOLD, size);
    }

    public CutsceneOverlay(double width, double height, Runnable onFinishedCallback) {
        this.onFinishedCallback = onFinishedCallback;

        // Preload font into JavaFX registry
        getPressStartFont(12);

        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
        setStyle("-fx-background-color: #000000;");

        // Background Cutscene Image View - fills the entire 1280x720 screen
        imageView = new ImageView();
        imageView.setPreserveRatio(false);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        StackPane.setAlignment(imageView, Pos.CENTER);

        // Stardew Valley NPC Dialogue Box Overlay - occupying strictly bottom 1/3rd of
        // screen
        dialogueBox = createStardewDialogueBox(width, height);
        dialogueBox.setVisible(false);
        dialogueBox.setOpacity(0.0);
        StackPane.setAlignment(dialogueBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(dialogueBox, new Insets(0, 0, 16, 0));

        dialogueTextLabel = (Label) dialogueBox.lookup("#dialogueTextLabel");
        promptLabel = (Label) dialogueBox.lookup("#promptLabel");

        getChildren().addAll(imageView, dialogueBox);

        // Load Cutscene Frames
        loadCutsceneFrames();

        // Setup Cutscene Playback Loop
        setupCutscenePlayback();

        // Mouse Click Listener to advance dialogue or skip to dialogue
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY || event.getButton() == MouseButton.SECONDARY) {
                handleMouseClick();
            }
        });
    }

    private void loadCutsceneFrames() {
        try (InputStream is = getClass().getResourceAsStream("/assets/cutscenes/cutscene1.gif")) {
            if (is != null) {
                ImageInputStream stream = ImageIO.createImageInputStream(is);
                ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
                reader.setInput(stream);
                int count = reader.getNumImages(true);

                for (int i = 0; i < count; i++) {
                    java.awt.image.BufferedImage bImg = reader.read(i);
                    int w = bImg.getWidth();
                    int h = bImg.getHeight();

                    WritableImage fxImg = new WritableImage(w, h);
                    PixelWriter pw = fxImg.getPixelWriter();
                    int[] pixels = new int[w * h];
                    bImg.getRGB(0, 0, w, h, pixels, 0, w);
                    pw.setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);

                    frames.add(fxImg);
                    frameDelays.add(getFrameDelayMs(reader, i));
                }
                reader.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (frames.isEmpty()) {
            try (InputStream is = getClass().getResourceAsStream("/assets/cutscenes/cutscene1.gif")) {
                if (is != null) {
                    Image fallbackImg = new Image(is);
                    imageView.setImage(fallbackImg);
                }
            } catch (Exception ignored) {
            }
            cutsceneFinished = true;
            showDialogueBox();
        } else {
            imageView.setImage(frames.getFirst());
        }
    }

    private int getFrameDelayMs(ImageReader reader, int index) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(index);
            String metaFormat = metadata.getNativeMetadataFormatName();
            if (metaFormat != null) {
                org.w3c.dom.Node root = metadata.getAsTree(metaFormat);
                for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                    org.w3c.dom.Node node = root.getChildNodes().item(i);
                    if (node.getNodeName().equalsIgnoreCase("GraphicControlExtension")) {
                        for (int j = 0; j < node.getAttributes().getLength(); j++) {
                            org.w3c.dom.Node attr = node.getAttributes().item(j);
                            if (attr.getNodeName().equalsIgnoreCase("delayTime")) {
                                int delayHundredths = Integer.parseInt(attr.getNodeValue());
                                if (delayHundredths > 0) {
                                    return delayHundredths * 10;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 100;
    }

    private void setupCutscenePlayback() {
        if (cutsceneFinished || frames.isEmpty()) {
            return;
        }

        lastFrameTime = System.currentTimeMillis();

        cutsceneTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (cutsceneFinished) {
                    stop();
                    return;
                }

                long currentTime = System.currentTimeMillis();
                int currentDelay = frameDelays.get(currentFrameIndex);

                if (currentTime - lastFrameTime >= currentDelay) {
                    lastFrameTime = currentTime;
                    currentFrameIndex++;

                    if (currentFrameIndex >= frames.size()) {
                        // Pause on the last frame
                        currentFrameIndex = frames.size() - 1;
                        imageView.setImage(frames.get(currentFrameIndex));
                        cutsceneFinished = true;
                        stop();
                        showDialogueBox();
                    } else {
                        imageView.setImage(frames.get(currentFrameIndex));
                    }
                }
            }
        };

        cutsceneTimer.start();
    }

    private VBox createStardewDialogueBox(double viewportWidth, double viewportHeight) {
        VBox box = new VBox(8);
        double boxWidth = Math.min(960, viewportWidth - 40);
        double boxHeight = viewportHeight * 0.30; // Covering strictly bottom 1/3rd of the screen

        box.setMaxWidth(boxWidth);
        box.setPrefWidth(boxWidth);
        box.setMinHeight(boxHeight);
        box.setPrefHeight(boxHeight);
        box.setMaxHeight(boxHeight + 20);
        box.setPadding(new Insets(14, 20, 14, 20));

        // Stardew Valley NPC Dialogue Box: Rich warm wood brown panel with golden brass
        // pixel border
        box.setStyle(
                "-fx-background-color: rgba(92, 55, 24, 0.96);" +
                        "-fx-border-color: #ffd700 #361e0b #361e0b #ffd700;" +
                        "-fx-border-width: 5px;" +
                        "-fx-border-style: solid;" +
                        "-fx-background-radius: 0px;" +
                        "-fx-border-radius: 0px;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.88), 12, 0, 0, 5);");

        // Dialogue Content Label with loaded Press Start 2P Font
        Label dialogueText = new Label(dialogueLines[0]);
        dialogueText.setId("dialogueTextLabel");
        dialogueText.setFont(getPressStartFont(20));
        dialogueText.setWrapText(true);
        dialogueText.setMinHeight(65);
        dialogueText.setStyle(
                "-fx-text-fill: #fffaed;" +
                        "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 20px;" +
                        "-fx-line-spacing: 6px;");

        // Bottom Right Click Prompt Indicator with loaded Press Start 2P Font
        Label prompt = new Label("▼ [ Click mouse to continue ]");
        prompt.setId("promptLabel");
        prompt.setFont(getPressStartFont(10));
        prompt.setStyle(
                "-fx-text-fill: #ffe066;" +
                        "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 10px;");

        HBox promptRow = new HBox(prompt);
        promptRow.setAlignment(Pos.BOTTOM_RIGHT);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        box.getChildren().addAll(dialogueText, spacer, promptRow);

        // Pulsing animation for prompt label
        Timeline pulseTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(prompt.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(500), new KeyValue(prompt.opacityProperty(), 0.35)),
                new KeyFrame(Duration.millis(1000), new KeyValue(prompt.opacityProperty(), 1.0)));
        pulseTimeline.setCycleCount(Timeline.INDEFINITE);
        pulseTimeline.play();

        return box;
    }

    private void showDialogueBox() {
        if (!dialogueBox.isVisible()) {
            dialogueBox.setVisible(true);
            FadeTransition ft = new FadeTransition(Duration.millis(350), dialogueBox);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }
    }

    private void handleMouseClick() {
        if (!cutsceneFinished) {
            // Skip directly to final frame and show dialogue box
            cutsceneFinished = true;
            if (cutsceneTimer != null) {
                cutsceneTimer.stop();
            }
            if (!frames.isEmpty()) {
                currentFrameIndex = frames.size() - 1;
                imageView.setImage(frames.get(currentFrameIndex));
            }
            showDialogueBox();
            return;
        }

        // Advance Dialogue
        dialogueIndex++;

        if (dialogueIndex < dialogueLines.length) {
            dialogueTextLabel.setText(dialogueLines[dialogueIndex]);
            if (dialogueIndex == dialogueLines.length - 1) {
                promptLabel.setText("▼ [ Click mouse to start game ]");
            }
        } else {
            // Dialogue complete -> Fade out and launch game
            FadeTransition ft = new FadeTransition(Duration.millis(300), this);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(e -> {
                if (onFinishedCallback != null) {
                    onFinishedCallback.run();
                }
            });
            ft.play();
        }
    }
}
