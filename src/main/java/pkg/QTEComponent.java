package pkg;

import com.almasb.fxgl.entity.component.Component;
import javafx.scene.Group;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;

public class QTEComponent extends Component {

    private KeyCode triggerKey;
    private double speed;
    private double progress = 0;
    private Arc progressBar;
    private boolean success = false;
    private boolean pressed = false;
    private Group view;

    public QTEComponent(KeyCode key, double speed) {
        this.triggerKey = key;
        this.speed = speed;

        double radius = 30;

        Circle bg = new Circle(0, 0, radius, Color.rgb(30, 30, 30, 0.8));
        bg.setStroke(Color.WHITE);

        progressBar = new Arc(0, 0, radius, radius, 90, 0);
        progressBar.setType(ArcType.OPEN);
        progressBar.setFill(null);
        progressBar.setStroke(Color.CYAN);
        progressBar.setStrokeWidth(4);
        progressBar.setStrokeType(StrokeType.CENTERED);

        Text label = new Text(triggerKey.toString());

        javafx.scene.text.Font font = javafx.scene.text.Font.font(14);
        label.setFont(font);
        label.setFill(Color.WHITE);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Apply the font first, then measure
        javafx.geometry.Bounds b = label.getBoundsInLocal();
        label.setTranslateX(-b.getWidth() / 2);
        label.setTranslateY(b.getHeight() / 4);
        view = new Group(bg, progressBar, label);
    }

    @Override
    public void onAdded() {
        // view is built in constructor, nothing needed here
    }

    @Override
    public void onUpdate(double tpf) {
        if (pressed) return;
        progress += tpf * speed;
        if (progress > 1.0) progress = 0;
        progressBar.setLength(-progress * 360);
    }

    public void press() {
        pressed = true;
        if (progress >= 0.75 && progress <= 1.0) {
            success = true;
            progressBar.setStroke(Color.LIGHTGREEN);
        } else {
            success = false;
            progressBar.setStroke(Color.RED);
        }
    }

    public Group getView()        { return view; }
    public boolean isSuccess()    { return success; }
    public boolean isPressed()    { return pressed; }
    public KeyCode getTriggerKey(){ return triggerKey; }
}