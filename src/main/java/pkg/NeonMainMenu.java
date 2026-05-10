package pkg;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class NeonMainMenu extends FXGLMenu {

    public NeonMainMenu(MenuType type) {
        super(type);
        
        // Matrix Grid Background
        Canvas bgCanvas = new Canvas(FXGL.getAppWidth(), FXGL.getAppHeight());
        GraphicsContext gc = bgCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#050914"));
        gc.fillRect(0, 0, FXGL.getAppWidth(), FXGL.getAppHeight());
        gc.setStroke(Color.web("#00E5FF", 0.05));
        gc.setLineWidth(1);
        for (int x = 0; x <= FXGL.getAppWidth(); x += 40) gc.strokeLine(x, 0, x, FXGL.getAppHeight());
        for (int y = 0; y <= FXGL.getAppHeight(); y += 40) gc.strokeLine(0, y, FXGL.getAppWidth(), y);
        
        // Neon Title
        Text title = new Text("SYS.BREACH");
        title.setFill(Color.web("#00ff66"));
        title.setFont(javafx.scene.text.Font.font("Courier New", javafx.scene.text.FontWeight.BOLD, 72));
        title.setEffect(new DropShadow(20, Color.web("#00ff66")));
        
        // Neon Buttons
        Button btnPlay = new Button("INITIALIZE // PLAY");
        btnPlay.getStyleClass().add("button");
        btnPlay.setOnAction(e -> fireNewGame()); // Built-in FXGL method to start the game
        
        Button btnExit = new Button("DISCONNECT // EXIT");
        btnExit.getStyleClass().add("button");
        btnExit.setOnAction(e -> fireExit()); // Built-in FXGL method to exit
        
        VBox box = new VBox(30, title, btnPlay, btnExit);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(FXGL.getAppWidth(), FXGL.getAppHeight()); // Centers the VBox on screen
        
        getContentRoot().getChildren().addAll(bgCanvas, box);
        getContentRoot().getStylesheets().add(getClass().getResource("style.css").toExternalForm());
    }
}