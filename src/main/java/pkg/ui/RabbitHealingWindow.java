package pkg.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import pkg.audio.AudioManager;

public class RabbitHealingWindow extends Stage {
    private int state = 0; // 0 = injured, 1 = ointment, 2 = bandaid

    public RabbitHealingWindow(Runnable onComplete) {
        initStyle(StageStyle.UTILITY);
        setTitle("Animal Rescue");

        // 176x128 aspect ratio -> Scale 4x -> 704x512
        // HBox with Image on left, VBox on right
        javafx.scene.layout.HBox root = new javafx.scene.layout.HBox();
        root.setStyle("-fx-background-color: black;");
        
        ImageView backgroundView = new ImageView(new Image(getClass().getResource("/assets/textures/injured_window.png").toExternalForm()));
        // Image is 128x128 -> Scale 4x -> 512x512
        backgroundView.setFitWidth(512);
        backgroundView.setFitHeight(512);
        backgroundView.setPreserveRatio(true);

        VBox itemsBox = new VBox(32); // some spacing between icons
        itemsBox.setAlignment(Pos.CENTER); // centers vertically in the right 192px column

        Image ointmentImg = new Image(getClass().getResource("/assets/textures/ointment.png").toExternalForm());
        ImageView ointmentView = new ImageView(ointmentImg);
        // Icons are 48x48 -> Scale 4x -> 192x192
        ointmentView.setFitWidth(192);
        ointmentView.setFitHeight(192);
        ointmentView.setPreserveRatio(true);
        
        Image bandaidImg = new Image(getClass().getResource("/assets/textures/bandaid.png").toExternalForm());
        ImageView bandaidView = new ImageView(bandaidImg);
        bandaidView.setFitWidth(192);
        bandaidView.setFitHeight(192);
        bandaidView.setPreserveRatio(true);

        itemsBox.getChildren().addAll(ointmentView, bandaidView);
        root.getChildren().addAll(backgroundView, itemsBox);

        // Drag and Drop
        ointmentView.setOnDragDetected(e -> {
            Dragboard db = ointmentView.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            content.putString("ointment");
            db.setContent(content);
            db.setDragView(ointmentImg, 96, 96); // drag view centered
            e.consume();
        });

        bandaidView.setOnDragDetected(e -> {
            Dragboard db = bandaidView.startDragAndDrop(TransferMode.ANY);
            ClipboardContent content = new ClipboardContent();
            content.putString("bandaid");
            db.setContent(content);
            db.setDragView(bandaidImg, 96, 96); // drag view centered
            e.consume();
        });

        // Attach drag events to the root layout
        root.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });

        root.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String dragged = db.getString();
                if ("ointment".equals(dragged) && state == 0) {
                    state = 1;
                    backgroundView.setImage(new Image(getClass().getResource("/assets/textures/ointment_window.png").toExternalForm()));
                    ointmentView.setVisible(false);
                    AudioManager.playCorrectAnswer();
                    success = true;
                } else if ("bandaid".equals(dragged) && state == 1) {
                    state = 2;
                    backgroundView.setImage(new Image(getClass().getResource("/assets/textures/bandaid_window.png").toExternalForm()));
                    bandaidView.setVisible(false);
                    AudioManager.playCorrectAnswer();
                    success = true;

                    // Close after 2 seconds
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        Platform.runLater(() -> {
                            close();
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        });
                    }).start();
                }
            }
            e.setDropCompleted(success);
            e.consume();
        });

        Scene scene = new Scene(root, 704, 512);
        setScene(scene);
        setResizable(false);
    }
}
