package pkg.restoration.components;

import static com.almasb.fxgl.dsl.FXGL.image;

import com.almasb.fxgl.entity.component.Component;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import pkg.restoration.assets.AssetCatalog;
import pkg.restoration.questions.QuestionChallenge;
import pkg.restoration.world.IsoPoint;
import pkg.restoration.world.IsoProjection;

public final class ChoiceDoorComponent extends Component {

    private final QuestionChallenge challenge;
    private final int choiceIndex;
    private final IsoPoint position;
    private final IsoProjection projection;

    public ChoiceDoorComponent(QuestionChallenge challenge, int choiceIndex, IsoPoint position, IsoProjection projection) {
        this.challenge = challenge;
        this.choiceIndex = choiceIndex;
        this.position = position;
        this.projection = projection;
    }

    @Override
    public void onAdded() {
        ImageView gate = new ImageView(image(AssetCatalog.GATE_DECISION));
        gate.setFitWidth(102);
        gate.setFitHeight(102);

        Rectangle labelPlate = new Rectangle(174, 46, Color.web("#17231e", 0.86));
        labelPlate.setArcWidth(8);
        labelPlate.setArcHeight(8);
        labelPlate.setStroke(Color.web("#d8e26f"));
        labelPlate.setTranslateX(-36);
        labelPlate.setTranslateY(96);

        Text label = new Text((choiceIndex + 1) + ". " + challenge.choices().get(choiceIndex));
        label.setFill(Color.web("#f8ffe8"));
        label.setFont(Font.font("Verdana", FontWeight.BOLD, 10.5));
        label.setWrappingWidth(160);
        label.setTranslateX(-28);
        label.setTranslateY(114);

        entity.getViewComponent().addChild(new Group(gate, labelPlate, label));
        syncEntityPosition();
    }

    public int choiceIndex() {
        return choiceIndex;
    }

    public QuestionChallenge challenge() {
        return challenge;
    }

    public boolean isNear(IsoPoint playerPosition, double radius) {
        return position.distance(playerPosition) <= radius;
    }

    private void syncEntityPosition() {
        Point2D foot = projection.toScreen(position);
        entity.setPosition(foot.getX() - 51, foot.getY() - 88);
        entity.setZIndex((int) foot.getY() + 80);
    }
}
