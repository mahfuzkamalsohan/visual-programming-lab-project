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
import pkg.restoration.world.IsoPoint;
import pkg.restoration.world.IsoProjection;
import pkg.restoration.world.NpcDefinition;

public final class NpcComponent extends Component {

    private final NpcDefinition definition;
    private final IsoProjection projection;
    private int messageIndex;

    public NpcComponent(NpcDefinition definition, IsoProjection projection) {
        this.definition = definition;
        this.projection = projection;
    }

    @Override
    public void onAdded() {
        ImageView npc = new ImageView(image(definition.asset()));
        npc.setViewport(new javafx.geometry.Rectangle2D(0, 0, 96, 128));

        Text name = new Text(definition.name());
        name.setFill(Color.web("#eff7d4"));
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        name.setWrappingWidth(120);
        name.setTranslateX(-12);
        name.setTranslateY(124);

        entity.getViewComponent().addChild(new Group(npc, name));
        syncEntityPosition();
    }

    public String nextMessage() {
        String message = definition.messages().get(messageIndex % definition.messages().size());
        messageIndex++;
        return definition.name() + ": " + message;
    }

    public boolean isNear(IsoPoint playerPosition, double radius) {
        return definition.position().distance(playerPosition) <= radius;
    }

    private void syncEntityPosition() {
        Point2D foot = projection.toScreen(definition.position());
        entity.setPosition(foot.getX() - 48, foot.getY() - 108);
        entity.setZIndex((int) foot.getY() + 70);
    }
}
