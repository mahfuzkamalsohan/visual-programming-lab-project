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

public final class GateComponent extends Component {

    private static final int DEPTH_TIE_BREAKER = 7;

    private final GateDefinition definition;
    private final ImageView imageView = new ImageView();
    private GateState state = GateState.SEALED;

    // FIX: Single-argument constructor matching your factory pattern
    public GateComponent(GateDefinition definition) {
        this.definition = definition;
    }

    @Override
    public void onAdded() {
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(128);
        imageView.setFitHeight(128);

        Text label = new Text(definition.label());
        label.setFill(Color.web("#f6f3df"));
        label.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        label.setWrappingWidth(118);
        label.setTranslateX(5);
        label.setTranslateY(122);

        Group view = new Group(imageView, label);
        entity.getViewComponent().addChild(view);
        
        updateSprite();
        // Rely on Tiled for basic positioning, but recalculate sorting layers
        syncEntityZIndex();
    }

    public GateDefinition definition() {
        return definition;
    }

    public GateState state() {
        return state;
    }

    // FIX: Accept a standard Point2D for proximity checks
    public boolean isNear(Point2D playerPosition, double radius) {
        if (entity == null) return false;
        return entity.getPosition().distance(playerPosition) <= radius;
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

    private void syncEntityZIndex() {
        if (entity == null) return;
        
        // Maps the base y-coordinate line directly for flat engine depth rendering
        double footY = entity.getY() + 104;
        entity.setZIndex(RenderDepth.at(footY, DEPTH_TIE_BREAKER));
    }
}