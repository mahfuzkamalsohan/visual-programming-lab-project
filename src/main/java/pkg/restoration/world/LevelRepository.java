package pkg.restoration.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import pkg.restoration.assets.AssetCatalog;
import pkg.restoration.questions.Difficulty;

@Service
public final class LevelRepository {

    public static final int GENERATED_AHEAD = 6;

    private static final int INITIAL_GENERATED_LEVELS = 8;
    private static final String[] TITLES = {
            "Civic Steps",
            "Silt Market",
            "Glass Orchard",
            "Ashen Reservoir",
            "Canopy Core",
            "Rainline Arcade",
            "Rootlight Station",
            "Harbor Gardens",
            "Solar Ward",
            "Moss Foundry"
    };
    private static final String[] SUBTITLES = {
            "A low terrace at the city's damaged southern edge.",
            "A tilted marketplace of dry canals and half-lit stalls.",
            "Greenhouses and civic gardens rise above the old market.",
            "A long waterworks balcony with choices under pressure.",
            "The high district where the restored canopy reconnects.",
            "A bright commercial spine where rain collectors feed the streets.",
            "A transit platform wrapped by repaired roots and signal lights.",
            "A waterfront garden district slowly taking back the concrete.",
            "A sunlit civic block where panels and parks share the skyline.",
            "A reclaimed factory district breathing through moss and glass."
    };
    private static final String[] HUMAN_NAMES = {
            "Warden Iko",
            "Planner Nara",
            "Archivist Sen",
            "Engineer Vale",
            "Keeper Oren"
    };
    private static final String[] HUMAN_ASSETS = {
            AssetCatalog.NPC_KEEPER,
            AssetCatalog.NPC_GIRL,
            AssetCatalog.NPC_BOY
    };
    private static final double NPC_WALL_CLEARANCE = 0.85;
    private static final double WALL_SPAN_PADDING = 0.18;

    private final CityMap cityMap;
    private final List<LevelDefinition> levels = new ArrayList<>();

    public LevelRepository() {
        cityMap = CityMapGenerator.generateDefaultCity();
        ensureGeneratedThrough(INITIAL_GENERATED_LEVELS - 1);
    }

    public CityMap cityMap() {
        return cityMap;
    }

    public List<LevelDefinition> all() {
        return List.copyOf(levels);
    }

    public LevelDefinition get(int index) {
        ensureGeneratedThrough(index);
        return levels.get(index);
    }

    public int count() {
        return levels.size();
    }

    public void ensureGeneratedThrough(int index) {
        while (levels.size() <= index) {
            levels.add(generateLevel(levels.size()));
        }
    }

    private LevelDefinition generateLevel(int index) {
        CityMapGenerator.ensureDistrict(cityMap, index);
        CityMapGenerator.ensureDistrict(cityMap, index + 1);

        GridPoint entry = CityMapGenerator.routeAnchor(index);
        GridPoint exit = CityMapGenerator.routeAnchor(index + 1);
        LevelShape shape = districtShape(entry, exit, index);
        LevelShape destinationShape = districtShape(exit, CityMapGenerator.routeAnchor(index + 2), index + 1);

        IsoPoint spawn = shape.clamp(new IsoPoint(entry.x() + 0.6, entry.y() + 0.4), 0.55);
        IsoPoint destinationAnchor = new IsoPoint(exit.x() + 0.5, exit.y() + 0.5);
        IsoPoint gatePosition = shape.wallSlotToward(destinationAnchor);
        IsoPoint destinationPosition = destinationShape.clamp(destinationShape.wallSlotToward(gatePosition), 0.62);
        GateKind gateKind = gateKindFor(index);

        GateDefinition gate = new GateDefinition(
                "district-" + index + "-gate",
                gateKind,
                gatePosition,
                index + 1,
                destinationPosition,
                minimumDifficultyFor(index),
                choicesFor(gateKind, index),
                gateLabelFor(index, gateKind)
        );

        return new LevelDefinition(
                "district-" + index,
                titleFor(index),
                subtitleFor(index),
                shape,
                spawn,
                List.of(gate),
                npcsFor(index, shape, spawn)
        );
    }

    private LevelShape districtShape(GridPoint entry, GridPoint exit, int index) {
        int halfWidth = 6 + Math.floorMod(index, 3);
        int halfHeight = 5 + Math.floorMod(index + 1, 3);
        int biasX = Integer.compare(exit.x(), entry.x());
        int biasY = Integer.compare(exit.y(), entry.y());
        int minX = entry.x() - halfWidth + Math.min(0, biasX * 2);
        int maxX = entry.x() + halfWidth + Math.max(0, biasX * 2);
        int minY = entry.y() - halfHeight + Math.min(0, biasY * 2);
        int maxY = entry.y() + halfHeight + Math.max(0, biasY * 2);
        Set<GridPoint> tiles = new LinkedHashSet<>();

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (cityMap.isWalkable(x, y)) {
                    tiles.add(new GridPoint(x, y));
                }
            }
        }

        return LevelShape.fromTiles(tiles);
    }

    private List<NpcDefinition> npcsFor(int index, LevelShape shape, IsoPoint spawn) {
        List<NpcDefinition> npcs = new ArrayList<>();
        List<IsoPoint> occupied = new ArrayList<>();

        if (index < 5 || index % 3 == 0) {
            IsoPoint position = npcPosition(shape, spawn.add(1.7, -1.1), occupied);
            occupied.add(position);
            npcs.add(new NpcDefinition(
                    "guide-" + index,
                    position,
                    HUMAN_NAMES[Math.floorMod(index, HUMAN_NAMES.length)],
                    HUMAN_ASSETS[Math.floorMod(index, HUMAN_ASSETS.length)],
                    guideMessages(index)
            ));
        }

        AnimalNpc animal = animalFor(index);
        IsoPoint animalPosition = npcPosition(shape, spawn.add(2.6 + Math.floorMod(index, 2), 1.4), occupied);
        npcs.add(new NpcDefinition(
                animal.idPrefix() + "-" + index,
                animalPosition,
                animal.name(),
                animal.asset(),
                animal.messages()
        ));

        return List.copyOf(npcs);
    }

    private IsoPoint npcPosition(LevelShape shape, IsoPoint preferred, List<IsoPoint> occupied) {
        return shape.tiles().stream()
                .map(tile -> new IsoPoint(tile.x() + 0.5, tile.y() + 0.5))
                .filter(point -> shape.contains(point, 0.62))
                .filter(point -> isAwayFromWalls(shape, point))
                .filter(point -> occupied.stream().allMatch(existing -> existing.distance(point) >= 1.35))
                .min(Comparator.comparingDouble(preferred::distance))
                .orElseGet(() -> fallbackNpcPosition(shape, preferred));
    }

    private boolean isAwayFromWalls(LevelShape shape, IsoPoint point) {
        return shape.wallSegments().stream()
                .noneMatch(wall -> isNearWallPlane(point, wall));
    }

    private boolean isNearWallPlane(IsoPoint point, WallSegment wall) {
        GridPoint tile = wall.ownerTile();

        return switch (wall.side()) {
            case NORTH, SOUTH -> {
                double minX = tile.x() - WALL_SPAN_PADDING;
                double maxX = tile.x() + 1.0 + WALL_SPAN_PADDING;
                yield point.x() >= minX
                        && point.x() <= maxX
                        && Math.abs(point.y() - wall.position().y()) < NPC_WALL_CLEARANCE;
            }
            case WEST, EAST -> {
                double minY = tile.y() - WALL_SPAN_PADDING;
                double maxY = tile.y() + 1.0 + WALL_SPAN_PADDING;
                yield point.y() >= minY
                        && point.y() <= maxY
                        && Math.abs(point.x() - wall.position().x()) < NPC_WALL_CLEARANCE;
            }
        };
    }

    private IsoPoint fallbackNpcPosition(LevelShape shape, IsoPoint preferred) {
        return shape.tiles().stream()
                .map(tile -> new IsoPoint(tile.x() + 0.5, tile.y() + 0.5))
                .filter(point -> shape.contains(point, 0.62))
                .filter(point -> isAwayFromWalls(shape, point))
                .min(Comparator.comparingDouble(preferred::distance))
                .orElseGet(() -> fallbackAnyNpcPosition(shape, preferred));
    }

    private IsoPoint fallbackAnyNpcPosition(LevelShape shape, IsoPoint preferred) {
        return shape.tiles().stream()
                .map(tile -> new IsoPoint(tile.x() + 0.5, tile.y() + 0.5))
                .filter(point -> shape.contains(point, 0.62))
                .min(Comparator.comparingDouble(preferred::distance))
                .orElseGet(() -> shape.clamp(preferred, 0.62));
    }

    private List<String> guideMessages(int index) {
        return switch (Math.floorMod(index, 5)) {
            case 0 -> List.of(
                    "Restoration reacts to your time reserve. Keep it high and the land remembers how to breathe.",
                    "Questions open every gate, even when you miss. Correct answers buy you more sky.");
            case 1 -> List.of(
                    "The route climbs through the city. Follow the terraces up and right.",
                    "The city keeps unfolding as long as the reserve holds.");
            case 2 -> List.of(
                    "Wrong turns still move you forward. The cost is time.",
                    "A clean future is not a single answer. It is a sequence you can survive.");
            case 3 -> List.of(
                    "The old pumps still work when the reserve is strong enough.",
                    "Every sealed gate is also a lock behind you. Choose with momentum.");
            default -> List.of(
                    "Animals return before people trust a district again.",
                    "When the streets look greener, you have bought the city more time.");
        };
    }

    private AnimalNpc animalFor(int index) {
        return switch (Math.floorMod(index, 3)) {
            case 0 -> new AnimalNpc(
                    "rescue-dog",
                    "Rescue Dog",
                    AssetCatalog.NPC_RESCUE_DOG,
                    List.of(
                            "The dog noses toward the safest route through the restored block.",
                            "Its vest light blinks faster near sealed gates."));
            case 1 -> new AnimalNpc(
                    "canal-duck",
                    "Canal Duck",
                    AssetCatalog.NPC_CANAL_DUCK,
                    List.of(
                            "The duck follows the clean water channels between plazas.",
                            "It settles only where the runoff has cleared."));
            default -> new AnimalNpc(
                    "orchard-deer",
                    "Orchard Deer",
                    AssetCatalog.NPC_ORCHARD_DEER,
                    List.of(
                            "The deer listens at the edge of the new canopy.",
                            "It will not cross a district that has gone quiet again."));
        };
    }

    private String titleFor(int index) {
        if (index < TITLES.length) {
            return TITLES[index];
        }

        return TITLES[Math.floorMod(index, TITLES.length)] + " " + (index + 1);
    }

    private String subtitleFor(int index) {
        return SUBTITLES[Math.floorMod(index, SUBTITLES.length)];
    }

    private GateKind gateKindFor(int index) {
        return index % 2 == 1 ? GateKind.DECISION : GateKind.QUESTION;
    }

    private Difficulty minimumDifficultyFor(int index) {
        if (index < 2) {
            return Difficulty.EASY;
        }

        if (index < 5) {
            return Difficulty.MEDIUM;
        }

        return Difficulty.HARD;
    }

    private int choicesFor(GateKind kind, int index) {
        if (kind == GateKind.QUESTION) {
            return 3;
        }

        return index % 4 == 3 ? 2 : 3;
    }

    private String gateLabelFor(int index, GateKind kind) {
        String noun = kind == GateKind.DECISION ? "Fork" : "Gate";
        return titleFor(index) + " " + noun;
    }

    private record AnimalNpc(String idPrefix, String name, String asset, List<String> messages) {
    }
}
