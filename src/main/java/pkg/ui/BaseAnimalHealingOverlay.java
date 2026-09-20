package pkg.ui;

import com.almasb.fxgl.dsl.FXGL;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import pkg.audio.AudioManager;

/**
 * In-game UI overlay for animal rescue healing mini-games (Rabbit and Puppy).
 * Integrated directly into the game scene rather than opening a separate OS window.
 */
public abstract class BaseAnimalHealingOverlay extends StackPane {

    private int state = 0; // 0 = injured, 1 = step 1 applied, 2 = step 2 applied
    private final Runnable onComplete;
    private final Runnable onClose;
    private boolean closed = false;

    private final ImageView backgroundView;
    private final VBox tool1Card;
    private final VBox tool2Card;
    private final Label statusLabel;
    private PauseTransition autoCloseTransition;

    public BaseAnimalHealingOverlay(
            String titleText,
            String initialWindowTexture,
            String step1WindowTexture,
            String step2WindowTexture,
            String tool1Texture,
            String tool1Name,
            String tool1Tag,
            String tool2Texture,
            String tool2Name,
            String tool2Tag,
            String step1Prompt,
            String step2Prompt,
            String successPrompt,
            Runnable onComplete,
            Runnable onClose
    ) {
        this.onComplete = onComplete;
        this.onClose = onClose;

        double appW = FXGL.getAppWidth();
        double appH = FXGL.getAppHeight();

        // 1. Fullscreen dimmed backdrop
        setPrefSize(appW, appH);
        setMinSize(appW, appH);
        setMaxSize(appW, appH);
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: rgba(0, 0, 0, 0.72);");
        setPickOnBounds(true);

        try {
            getStylesheets().add(getClass().getResource("/assets/ui/css/pixel_style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        // 2. Central pixel art modal card
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(720);
        card.setMaxHeight(560);
        card.setPrefWidth(720);
        card.setStyle(
                "-fx-background-color: rgba(14, 28, 18, 0.98);"
                        + "-fx-border-color: #39ff14 #144517 #144517 #39ff14;"
                        + "-fx-border-width: 4px;"
                        + "-fx-border-style: solid;"
                        + "-fx-padding: 14px 18px 16px 18px;"
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.88), 16, 0, 4, 4);"
        );

        // Header
        Label titleLabel = new Label(titleText);
        titleLabel.setStyle(
                "-fx-text-fill: #39ff14;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 12px;"
                        + "-fx-font-weight: bold;"
        );

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: #1a0808;"
                        + "-fx-border-color: #ff3344;"
                        + "-fx-border-width: 2px;"
                        + "-fx-text-fill: #ff4455;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 11px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-cursor: hand;"
                        + "-fx-padding: 3px 8px;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
                "-fx-background-color: #ff3344;"
                        + "-fx-border-color: #ffffff;"
                        + "-fx-border-width: 2px;"
                        + "-fx-text-fill: #ffffff;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 11px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-cursor: hand;"
                        + "-fx-padding: 3px 8px;"
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
                "-fx-background-color: #1a0808;"
                        + "-fx-border-color: #ff3344;"
                        + "-fx-border-width: 2px;"
                        + "-fx-text-fill: #ff4455;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 11px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-cursor: hand;"
                        + "-fx-padding: 3px 8px;"
        ));
        closeBtn.setOnAction(e -> cancelAndClose());

        HBox headerBox = new HBox(12, titleLabel, headerSpacer, closeBtn);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setStyle(
                "-fx-background-color: #172e1c;"
                        + "-fx-border-color: #39ff14;"
                        + "-fx-border-width: 0 0 2px 0;"
                        + "-fx-padding: 6px 10px 8px 10px;"
        );

        // Body: Animal Image on Left, Tools on Right
        HBox bodyBox = new HBox(18);
        bodyBox.setAlignment(Pos.CENTER);
        bodyBox.setPadding(new Insets(8, 0, 8, 0));

        // Animal View (128x128 pixel art scaled to 400x400)
        backgroundView = new ImageView(new Image(getClass().getResource(initialWindowTexture).toExternalForm()));
        backgroundView.setFitWidth(400);
        backgroundView.setFitHeight(400);
        backgroundView.setPreserveRatio(true);
        backgroundView.setSmooth(false);

        StackPane animalFrame = new StackPane(backgroundView);
        animalFrame.setStyle(
                "-fx-border-color: #1f4f25;"
                        + "-fx-border-width: 3px;"
                        + "-fx-background-color: black;"
        );

        // Tools Column
        VBox toolsBox = new VBox(14);
        toolsBox.setAlignment(Pos.CENTER);
        toolsBox.setPrefWidth(210);

        Label toolsTitle = new Label("TREATMENT TOOLS");
        toolsTitle.setStyle(
                "-fx-text-fill: #d7e77f;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 10px;"
                        + "-fx-font-weight: bold;"
        );

        // Tool 1
        Image tool1Img = new Image(getClass().getResource(tool1Texture).toExternalForm());
        ImageView tool1Iv = new ImageView(tool1Img);
        tool1Iv.setFitWidth(80);
        tool1Iv.setFitHeight(80);
        tool1Iv.setPreserveRatio(true);
        tool1Iv.setSmooth(false);

        Label tool1Lbl = new Label("1. " + tool1Name);
        tool1Lbl.setStyle(
                "-fx-text-fill: #f8f9fa;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 9px;"
        );

        tool1Card = new VBox(6, tool1Iv, tool1Lbl);
        tool1Card.setAlignment(Pos.CENTER);
        tool1Card.setPadding(new Insets(10));
        styleToolCard(tool1Card, true);

        // Tool 2
        Image tool2Img = new Image(getClass().getResource(tool2Texture).toExternalForm());
        ImageView tool2Iv = new ImageView(tool2Img);
        tool2Iv.setFitWidth(80);
        tool2Iv.setFitHeight(80);
        tool2Iv.setPreserveRatio(true);
        tool2Iv.setSmooth(false);

        Label tool2Lbl = new Label("2. " + tool2Name);
        tool2Lbl.setStyle(
                "-fx-text-fill: #f8f9fa;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 9px;"
        );

        tool2Card = new VBox(6, tool2Iv, tool2Lbl);
        tool2Card.setAlignment(Pos.CENTER);
        tool2Card.setPadding(new Insets(10));
        styleToolCard(tool2Card, false);
        tool2Card.setOpacity(0.5);

        toolsBox.getChildren().addAll(toolsTitle, tool1Card, tool2Card);
        bodyBox.getChildren().addAll(animalFrame, toolsBox);

        // Footer / Instructions Banner
        statusLabel = new Label("👉 " + step1Prompt);
        statusLabel.setStyle(
                "-fx-text-fill: #d7e77f;"
                        + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                        + "-fx-font-size: 10px;"
                        + "-fx-alignment: center;"
        );
        statusLabel.setWrapText(true);

        HBox footerBox = new HBox(statusLabel);
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setStyle(
                "-fx-background-color: #172e1c;"
                        + "-fx-border-color: #39ff14;"
                        + "-fx-border-width: 2px 0 0 0;"
                        + "-fx-padding: 8px 12px;"
        );

        card.getChildren().addAll(headerBox, bodyBox, footerBox);
        getChildren().add(card);

        // Setup actions
        Runnable applyStep1 = () -> {
            if (state != 0 || closed) return;
            state = 1;
            backgroundView.setImage(new Image(getClass().getResource(step1WindowTexture).toExternalForm()));
            tool1Card.setVisible(false);
            tool1Card.setManaged(false);
            tool2Card.setOpacity(1.0);
            styleToolCard(tool2Card, true);
            statusLabel.setText("👉 " + step2Prompt);
            statusLabel.setStyle(
                    "-fx-text-fill: #39ff14;"
                            + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                            + "-fx-font-size: 10px;"
            );
            AudioManager.playCorrectAnswer();
        };

        Runnable applyStep2 = () -> {
            if (state != 1 || closed) return;
            state = 2;
            backgroundView.setImage(new Image(getClass().getResource(step2WindowTexture).toExternalForm()));
            tool2Card.setVisible(false);
            tool2Card.setManaged(false);
            statusLabel.setText("✨ " + successPrompt);
            statusLabel.setStyle(
                    "-fx-text-fill: #39ff14;"
                            + "-fx-font-family: 'Press Start 2P', 'Monospaced', monospace;"
                            + "-fx-font-size: 11px;"
                            + "-fx-font-weight: bold;"
            );
            AudioManager.playCorrectAnswer();

            autoCloseTransition = new PauseTransition(Duration.seconds(1.5));
            autoCloseTransition.setOnFinished(ev -> finishAndClose());
            autoCloseTransition.play();
        };

        // Click-to-apply handlers
        tool1Card.setOnMouseClicked(e -> {
            applyStep1.run();
            e.consume();
        });

        tool2Card.setOnMouseClicked(e -> {
            if (state == 0) {
                statusLabel.setText("⚠ Use " + tool1Name + " first!");
                AudioManager.playWrongAnswer();
            } else if (state == 1) {
                applyStep2.run();
            }
            e.consume();
        });

        // Drag and drop for Tool 1
        tool1Card.setOnDragDetected(e -> {
            if (state != 0 || closed) return;
            Dragboard db = tool1Card.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            content.putString(tool1Tag);
            db.setContent(content);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            db.setDragView(tool1Iv.snapshot(sp, null), 40, 40);
            e.consume();
        });

        // Drag and drop for Tool 2
        tool2Card.setOnDragDetected(e -> {
            if (state != 1 || closed) return;
            Dragboard db = tool2Card.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            content.putString(tool2Tag);
            db.setContent(content);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            db.setDragView(tool2Iv.snapshot(sp, null), 40, 40);
            e.consume();
        });

        // Drop target on animal frame & card
        setupDropTarget(animalFrame, tool1Tag, tool2Tag, applyStep1, applyStep2);
        setupDropTarget(card, tool1Tag, tool2Tag, applyStep1, applyStep2);
    }

    private void setupDropTarget(Region target, String tag1, String tag2, Runnable onStep1, Runnable onStep2) {
        target.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });

        target.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String dragged = db.getString();
                if (tag1.equals(dragged) && state == 0) {
                    onStep1.run();
                    success = true;
                } else if (tag2.equals(dragged) && state == 1) {
                    onStep2.run();
                    success = true;
                }
            }
            e.setDropCompleted(success);
            e.consume();
        });
    }

    private void styleToolCard(VBox card, boolean active) {
        if (active) {
            card.setStyle(
                    "-fx-background-color: rgba(20, 41, 24, 0.90);"
                            + "-fx-border-color: #39ff14;"
                            + "-fx-border-width: 2px;"
                            + "-fx-cursor: hand;"
            );
            card.setOnMouseEntered(e -> card.setStyle(
                    "-fx-background-color: rgba(33, 66, 39, 0.95);"
                            + "-fx-border-color: #d7e77f;"
                            + "-fx-border-width: 2px;"
                            + "-fx-cursor: hand;"
            ));
            card.setOnMouseExited(e -> card.setStyle(
                    "-fx-background-color: rgba(20, 41, 24, 0.90);"
                            + "-fx-border-color: #39ff14;"
                            + "-fx-border-width: 2px;"
                            + "-fx-cursor: hand;"
            ));
        } else {
            card.setStyle(
                    "-fx-background-color: rgba(20, 41, 24, 0.40);"
                            + "-fx-border-color: #144517;"
                            + "-fx-border-width: 2px;"
                            + "-fx-cursor: default;"
            );
            card.setOnMouseEntered(null);
            card.setOnMouseExited(null);
        }
    }

    public void show() {
        if (getParent() == null) {
            FXGL.addUINode(this);
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (autoCloseTransition != null) {
            autoCloseTransition.stop();
        }
        FXGL.removeUINode(this);
    }

    private void finishAndClose() {
        close();
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void cancelAndClose() {
        close();
        if (onClose != null) {
            onClose.run();
        }
    }
}

