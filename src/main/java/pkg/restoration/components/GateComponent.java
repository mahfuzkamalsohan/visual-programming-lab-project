package pkg.restoration.components;

import static com.almasb.fxgl.dsl.FXGL.image;

import com.almasb.fxgl.entity.component.Component;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import pkg.restoration.assets.AssetCatalog;
import pkg.restoration.world.GateDefinition;
import pkg.restoration.world.GateState;
import pkg.restoration.world.IsoPoint;
import pkg.restoration.world.IsoProjection;

public final class GateComponent extends Component {

    private final GateDefinition definition;
    private final IsoProjection projection;
    private final ImageView imageView = new ImageView();
    private GateState state = GateState.SEALED;

    public GateComponent(GateDefinition definition, IsoProjection projection) {
        this.definition = definition;
        this.projection = projection;
    }

    @Override
    public void onAdded() {
        Text label = new Text(definition.label());
        label.setFill(Color.web("#f6f3df"));
        label.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        label.setWrappingWidth(118);
        label.setTranslateX(5);
        label.setTranslateY(122);

        Group view = new Group(imageView, label);
        entity.getViewComponent().addChild(view);
        updateSprite();
        syncEntityPosition();
    }

    public GateDefinition definition() {
        return definition;
    }

    public GateState state() {
        return state;
    }

    public boolean isNear(IsoPoint playerPosition, double radius) {
        return definition.position().distance(playerPosition) <= radius;
    }

    public void awaitDecision() {
        state = GateState.AWAITING_DECISION;
        updateSprite();
    }

    public void open() {
        state = GateState.OPEN;
        updateSprite();
    }

    public void closeBehind() {
        state = GateState.CLOSED_BEHIND;
        updateSprite();
    }

    private void updateSprite() {
        if (imageView == null) {
            return;
        }

        String asset = switch (state) {
            case SEALED -> AssetCatalog.GATE_SEALED;
            case AWAITING_DECISION -> AssetCatalog.GATE_DECISION;
            case OPEN -> AssetCatalog.GATE_OPEN;
            case CLOSED_BEHIND -> AssetCatalog.GATE_CLOSED;
        };

        imageView.setImage(image(asset));
    }

    private void syncEntityPosition() {
        Point2D foot = projection.toScreen(definition.position());
        entity.setPosition(foot.getX() - 64, foot.getY() - 104);
        entity.setZIndex((int) foot.getY() + 60);
    }
}
