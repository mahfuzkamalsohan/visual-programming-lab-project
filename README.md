Restoration
===========

Restoration is a fullscreen Java / JavaFX / FXGL 2D isometric survival-question game. The player moves through endless generated districts inside a continuous city map, opens sealed gates by answering questions or walking through decision doors, and survives by keeping the restoration timer above zero.

Stack
-----

- Java 21
- Gradle
- JavaFX 21.0.6, including FXML
- FXGL 17.3 for the game loop, entities, input, and scene system
- Spring Boot 4.1.0 as a non-web application context for services, configuration, scene factory wiring, and FXML controllers

Run
---

The default entry point is:

```text
pkg.restoration.RestorationGameApp
```

The Gradle application plugin is configured to launch that class:

```bash
./gradlew run
```

Controls
--------

- WASD or arrow keys: move
- E: interact with NPCs and sealed gates
- 1, 2, 3: answer question overlays
- Esc: pause menu

Sorting Test Mode
-----------------

Choose `Sorting Test Mode` from the main menu. Player 1 uses WASD to collect one
concealed waste item at a time and presses E at the sorting-zone intake to deliver
it. Player 2 uses the arrow keys, is confined inside the zone, and presses E at the
intake to reveal and carry the next item. Its name floats above Player 2 until E is
pressed near the appropriate black, blue, green, or red bin. Correct items add seven
seconds, wrong bins subtract eight seconds, and completing all items adds a further
fifteen seconds. Red rectangles show the exact player, pickup, intake, and bin
interaction bounds. The editable debug dimensions are grouped with the `SORT_ZONE_*`
constants near the top of `MovementApp`.

Three-Stage Demo Map
--------------------

Choose `3-Stage Demo Map` to load `level_demo.tmx` and run the standalone systems
in sequence:

1. Collect all four mapped garbage bags with E.
2. Approach the mapped question point and answer with 1, 2, or 3.
3. Complete the two-player intake and four-bin sorting stage with E.

The demo uses WASD for Player 1 and arrow keys for Player 2. Later stages remain
hidden until the preceding stage is complete. The 72x32 open world places collection
in the western district, questions in the center, and sorting in the eastern district,
so players travel across the map between stages. Player 2 is only locked after walking
inside the sorting zone. `level_demo.tmx` contains separate
object layers for players, collection objects, the question point, the sorting zone,
the intake, bins, waste, and boundaries. Reward and penalty tuning values are stored
as map properties. Its isometric orientation, tileset, tile dimensions, and
CSV-encoded `Tile Layer 1` use the same format as `level_0.tmx`, so it can be edited
with the same Tiled workflow. Every gameplay object has
a stable `logicalId`; task objects also have `itemId`, `binId`, or
`questionResource` properties as appropriate. Their current coordinates and visuals
are placeholders intended to be edited directly in Tiled.

Project Layout
--------------

- `pkg.restoration.RestorationGameApp`: game orchestration and flow.
- `pkg.restoration.world`: level definitions, isometric projection, gate definitions, and renderer.
- `pkg.restoration.questions`: DAT parser, difficulty model, and challenge result model.
- `pkg.restoration.components`: player, gate, decision door, NPC, and sprite animation components.
- `pkg.restoration.systems`: timer and difficulty curve.
- `pkg.restoration.spring`: Spring Boot bootstrap, application properties binding, and bean configuration.
- `pkg.restoration.ui`: FXML-backed HUD, question/decision overlay, menus, and toast popups.
- `pkg.restoration.assets.AssetCatalog`: central texture filenames.
- `pkg.restoration.tools.DemoAssetGenerator`: deterministic placeholder PNG generator.
- `src/main/resources/fxml/restoration`: FXML layouts for menu and game UI.
- `src/main/resources/application.properties`: game tuning and Spring Boot desktop-mode configuration.

World Layout
------------

`CityMapGenerator` builds and extends the isometric city procedurally: water, piers, roads, plazas, parks, and building blocks. `LevelRepository` lazily generates districts ahead of the current run, derives each district from the walkable tiles inside that city map, and never stops at a fixed final level. `WorldRenderer` draws the generated city before drawing district perimeters, which keeps the map visually continuous instead of showing isolated rooms over blank space.

Decision doors are selected from real boundary slots on the current district shape, so choice doors stay along walls. NPC definitions carry their own asset path, allowing human guides and animal NPCs to share the same interaction component.

Question DAT Format
-------------------

Question files live in `src/main/resources/assets/questions`. The standalone
`QuestionLoader` can load one bundled DAT resource or every `.dat` file in a directory.

Required keys per record:

```text
id=easy-q-001
type=QUESTION
prompt=Question text?
choices=Choice A|Choice B|Choice C
answer=0,1
```

Records are separated by a blank line. `answer` contains zero-based indices with the
best answer first and the second-best answer second. The best answer adds `reward`
seconds, the second-best answer changes no time, and every other answer subtracts
`penalty` seconds. Optional keys: `reward`, `penalty`, `feedback.correct`,
`feedback.wrong`.

Standalone Task Logic
---------------------

`CollectionTask` and `SortingTask` track progress without depending on FXGL. Each
returns a `TaskResult`; pass that result to `TaskTimer.apply(timer, result)` to apply
its reward or penalty to a `RestorationTimer`. Rewards are issued once, when a task
becomes complete. These classes are intentionally not connected to the current game
scene yet.

Regenerate Demo PNG Assets
--------------------------

```bash
./gradlew generateDemoAssets
```

By default this only fills missing textures in `src/main/resources/assets/textures/restoration`, so stronger hand-made or generated art is not overwritten. To intentionally replace every deterministic placeholder asset:

```bash
./gradlew generateDemoAssets -PoverwriteAssets
```
