# Restoration

**Restoration** is an 8-bit retro 2D ecological adventure game built with Java 21, JavaFX, and FXGL. Players restore polluted ecosystems by collecting garbage, sorting waste into recycling bins, answering environmental quizzes, and healing habitats.

---

## Tech Stack

- **Java 21**
- **Gradle**
- **JavaFX 21.0.6** (FXML, Controls, Media)
- **FXGL 17.3** (Game loop, Entity Component System, Physics, Input)
- **Java Sockets** (LAN Co-op Networking)

---

## Game Modes

- **Single Player**: Restore polluted districts, collect waste, and answer quizzes to unlock gates.
- **Shared-Screen Co-op**: Local 2-player mode on the same device.
- **LAN Co-op (Host / Join)**: Real-time networked multiplayer over local IP.


---

## Controls

| Action | Player 1 | Player 2 (Local Co-op) |
|---|---|---|
| **Movement** | `W` `A` `S` `D` | Arrow Keys (`↑` `←` `↓` `→`) |
| **Interact / Action** | `Space` / `E` | `Enter` / `Shift` |
| **Quiz Selection** | `1`, `2`, `3` (or Mouse Click) | `1`, `2`, `3` (or Mouse Click) |
| **Pause / Settings** | `ESC` | `ESC` |

---

## Getting Started

### Prerequisites
- JDK 21 or higher installed.

### Run the Game
```bash
# On Linux/macOS
./gradlew run

# On Windows
.\gradlew run
```

---

## 📂 Project Structure

- `pkg.MovementApp`: Main game entry point and application launcher.
- `pkg.PlayerComponent`, `pkg.BirdComponent`: Entity components and behaviors.
- `pkg.net`: Socket networking (`NetworkManager`) & packet definitions for LAN multiplayer.
- `pkg.audio`: Central audio manager for BGM, SFX, and volume persistence.
- `pkg.ui` / `src/main/resources/assets/ui`: Pixel art FXML overlays, HUDs, and CSS styling.
- `src/main/resources/assets/questions`: DAT format question files for environmental quizzes.
