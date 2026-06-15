Restoration
===========

Restoration is a Java / JavaFX / FXGL 2D isometric survival-question game. The player moves through bounded rectangular regions, opens sealed gates by answering questions or walking through decision doors, and survives by keeping the restoration timer above zero.

Stack
-----

- Java 21
- Maven
- JavaFX 21.0.6, including FXML
- FXGL 17.3 for the game loop, entities, input, and scene system
- Spring Boot 4.1.0 as a non-web application context for services, configuration, scene factory wiring, and FXML controllers

Run
---

The default entry point is:

```text
pkg.restoration.RestorationGameApp
```

The Maven JavaFX plugin is also configured to launch that class:

```bash
mvn javafx:run
```

Controls
--------

- WASD or arrow keys: move
- E: interact with NPCs and sealed gates
- 1, 2, 3: answer question overlays
- Esc: pause menu

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

District Layouts
----------------

Districts are defined as packed tile shapes in `LevelRepository` with `LevelShape.fromRows(...)`. Any non-space character in a row is a walkable city tile. The renderer draws only those tiles and places walls around the true perimeter, so districts can be L-shaped, tapered, maze-like, or otherwise non-rectangular without changing player movement code.

Question DAT Format
-------------------

Question files live in `src/main/resources/assets/restoration/questions`.

Required keys per record:

```text
id=easy-q-001
type=QUESTION
prompt=Question text?
choices=Choice A|Choice B|Choice C
answer=0
```

Records are separated by a blank line. `answer` is zero-based and can be comma-separated. Optional keys: `reward`, `penalty`, `feedback.correct`, `feedback.wrong`.

Regenerate Demo PNG Assets
--------------------------

```bash
javac -d target/tools src/main/java/pkg/restoration/tools/DemoAssetGenerator.java
java -cp target/tools pkg.restoration.tools.DemoAssetGenerator
```

Generated textures are written to `src/main/resources/assets/textures/restoration`.
