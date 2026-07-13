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
import pkg.restoration.world.NpcDefinition;

public final class NpcComponent extends Component {

    private static final double HUMAN_WIDTH = 96;
    private static final double HUMAN_HEIGHT = 128;
    private static final double HUMAN_FOOT_OFFSET_Y = 108;
    private static final double ANIMAL_WIDTH = 66;
    private static final double ANIMAL_HEIGHT = 84;
    private static final double ANIMAL_FOOT_OFFSET_Y = 72;
    private static final int DEPTH_TIE_BREAKER = 5;

    private final NpcDefinition definition;
    private int messageIndex;

    public NpcComponent(NpcDefinition definition) {
        this.definition = definition;
    }

    @Override
    public void onAdded() {
        NpcRenderProfile profile = renderProfile();
        ImageView npc = new ImageView(image(definition.asset()));
        npc.setPreserveRatio(true);
        npc.setFitWidth(profile.width());
        npc.setFitHeight(profile.height());

        Text name = new Text(definition.name());
        name.setFill(Color.web("#eff7d4"));
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        name.setWrappingWidth(120);
        name.setTranslateX((profile.width() - name.getWrappingWidth()) / 2.0);
        name.setTranslateY(profile.height() + 14);

        entity.getViewComponent().addChild(new Group(npc, name));
        
        // Let Tiled handle the X,Y coordinates, but update the rendering Z-index depth
        syncEntityZIndex();
    }

    public String nextMessage() {
        String message = definition.messages().get(messageIndex % definition.messages().size());
        messageIndex++;
        return definition.name() + ": " + message;
    }

    public boolean isNear(Point2D playerPosition, double radius) {
        if (entity == null) return false;
        return entity.getPosition().distance(playerPosition) <= radius;
    }

    private void syncEntityZIndex() {
        if (entity == null) return;
        NpcRenderProfile profile = renderProfile();
        
        // Dynamically calculates the foot line based on the Tiled placement
        double footY = entity.getY() + profile.footOffsetY();
        entity.setZIndex(RenderDepth.at(footY, DEPTH_TIE_BREAKER));
    }

    private NpcRenderProfile renderProfile() {
        return isAnimalAsset(definition.asset())
                ? new NpcRenderProfile(ANIMAL_WIDTH, ANIMAL_HEIGHT, ANIMAL_FOOT_OFFSET_Y)
                : new NpcRenderProfile(HUMAN_WIDTH, HUMAN_HEIGHT, HUMAN_FOOT_OFFSET_Y);
    }

    private static boolean isAnimalAsset(String asset) {
        return AssetCatalog.NPC_RESCUE_DOG.equals(asset)
                || AssetCatalog.NPC_CANAL_DUCK.equals(asset)
                || AssetCatalog.NPC_ORCHARD_DEER.equals(asset);
    }

    private record NpcRenderProfile(double width, double height, double footOffsetY) {
    }
}