package pkg.restoration.world;

import java.util.List;

import org.springframework.stereotype.Service;

import pkg.restoration.questions.Difficulty;

@Service
public final class LevelRepository {

    private final List<LevelDefinition> levels;

    public LevelRepository() {
        levels = List.of(
                new LevelDefinition(
                        "verdant-approach",
                        "Civic Steps",
                        "A low terrace at the city's damaged southern edge.",
                        LevelShape.fromRows(0, 24,
                                "   ########  ",
                                "  ########## ",
                                "############ ",
                                "#############",
                                " ########### ",
                                "  #########  ",
                                "   #######   ",
                                "   ######    ",
                                "    ####     "
                        ),
                        new IsoPoint(5.6, 31.4),
                        List.of(new GateDefinition("steps-overlook", GateKind.QUESTION, new IsoPoint(9.6, 24.65), Difficulty.EASY, 3, "Overlook Gate")),
                        List.of(new NpcDefinition("warden-01", new IsoPoint(5.0, 29.2), "Warden Iko", List.of(
                                "Restoration reacts to your time reserve. Keep it high and the land remembers how to breathe.",
                                "Questions open every gate, even when you miss. Correct answers buy you more sky.")))),
                new LevelDefinition(
                        "silt-causeway",
                        "Silt Market",
                        "A tilted marketplace of dry canals and half-lit stalls.",
                        LevelShape.fromRows(2, 15,
                                "    ######### ",
                                "   ###########",
                                "  ############",
                                "##############",
                                "############# ",
                                " ###########  ",
                                "  #########   ",
                                "   ########   ",
                                "    #####     "
                        ),
                        new IsoPoint(8.2, 22.35),
                        List.of(new GateDefinition("market-choice", GateKind.DECISION, new IsoPoint(13.8, 15.65), Difficulty.EASY, 3, "Market Fork")),
                        List.of(new NpcDefinition("planner-01", new IsoPoint(9.4, 19.4), "Planner Nara", List.of(
                                "The route climbs through the city. Follow the terraces up and right.",
                                "A district can be small, wide, broken, or crowded. The gates only mark decisions; the city is one place.")))),
                new LevelDefinition(
                        "glass-orchard",
                        "Glass Orchard",
                        "Greenhouses and civic gardens rise above the old market.",
                        LevelShape.fromRows(5, 6,
                                "     ##########",
                                "   ############",
                                "  #############",
                                "###############",
                                "############## ",
                                " ###########   ",
                                "  ##########   ",
                                "    #######    ",
                                "     #####     "
                        ),
                        new IsoPoint(10.4, 13.35),
                        List.of(new GateDefinition("orchard-overpass", GateKind.QUESTION, new IsoPoint(18.2, 6.65), Difficulty.MEDIUM, 3, "Orchard Overpass")),
                        List.of(new NpcDefinition("warden-02", new IsoPoint(13.2, 10.6), "Archivist Sen", List.of(
                                "Wrong turns still move you forward. The cost is time.",
                                "A clean future is not a single answer. It is a sequence you can survive.")))),
                new LevelDefinition(
                        "ashen-reservoir",
                        "Ashen Reservoir",
                        "A long waterworks balcony with choices under pressure.",
                        LevelShape.fromRows(7, -3,
                                "      ##########",
                                "    ############",
                                "  ##############",
                                "################",
                                "############### ",
                                " #############  ",
                                "  ###########   ",
                                "   ########     ",
                                "    ######      "
                        ),
                        new IsoPoint(12.2, 4.35),
                        List.of(new GateDefinition("reservoir-choice", GateKind.DECISION, new IsoPoint(21.1, -2.35), Difficulty.MEDIUM, 2, "Reservoir Fork")),
                        List.of(new NpcDefinition("engineer-01", new IsoPoint(15.2, 1.4), "Engineer Vale", List.of(
                                "The old pumps still work when the reserve is strong enough.",
                                "Every sealed gate is also a lock behind you. Choose with momentum.")))),
                new LevelDefinition(
                        "canopy-core",
                        "Canopy Core",
                        "The high district where the restored canopy reconnects.",
                        LevelShape.fromRows(10, -12,
                                "      ###########",
                                "    #############",
                                "  ###############",
                                "#################",
                                "################ ",
                                " ##############  ",
                                "  ############   ",
                                "    #########    ",
                                "      #######    "
                        ),
                        new IsoPoint(16.5, -5.15),
                        List.of(new GateDefinition("core-canopy", GateKind.QUESTION, new IsoPoint(25.1, -11.35), Difficulty.HARD, 3, "Canopy Gate")),
                        List.of())
        );
    }

    public List<LevelDefinition> all() {
        return levels;
    }

    public LevelDefinition get(int index) {
        return levels.get(index);
    }

    public int count() {
        return levels.size();
    }

    public boolean isFinalLevel(int index) {
        return index == levels.size() - 1;
    }
}
