# Multiplayer & Co-Op Implementation Documentation

This document outlines the architecture, data structures, network protocol, camera tracking, and synchronization flow for the multiplayer and co-operative (Co-Op) modes in **Restoration**.

---

## 1. Overview & Game Modes

Co-Op modes allow two players to explore, interact, and manage the restoration timer together. The mode of play is dictated by the `GameMode` enum ([GameMode.java](src/main/java/pkg/GameMode.java)):

| Game Mode | Mode Key | Description |
| :--- | :--- | :--- |
| **Single Player** | `SINGLE_PLAYER` | Solo gameplay with WASD or Arrow Keys controlling Player 1. |
| **Local Shared-Screen Co-Op** | `LOCAL_COOP_SPLITSCREEN` | Two players on a single keyboard (P1: WASD, P2: Arrow Keys) using dynamic midpoint camera tracking. |
| **LAN Host** | `LAN_HOST` | Host server on port 55555. Runs authoritative game physics, updates P1 locally, processes P2 network input, and broadcasts game state to client. |
| **LAN Join** | `LAN_JOIN` | Network client connecting to host IP. Captures P2 keyboard inputs, transmits input packets to host, and renders host-authoritative game state updates. |

---

## 2. System Architecture

Multiplayer functionality is divided across core game management, entity component logic, and socket networking:

```
                      +-----------------------------+
                      |     MovementApp (FXGL)      |
                      +--------------+--------------+
                                     |
           +-------------------------+-------------------------+
           |                                                   |
+----------v------------------+                     +----------v------------------+
|   PlayerComponent (P1 / P2) |                     |  NetworkManager (Socket)    |
+-----------------------------+                     +-----------------------------+
| • 8-Way Directional Logic   |                     | • TCP ServerSocket (Host)   |
| • Sprite Animations         |                     | • TCP Socket (Client)       |
| • Smooth Wall Collisions    |                     | • Object Streams (I/O)      |
+-----------------------------+                     +-----------------------------+
```

### Main Files Involved
- [MovementApp.java](src/main/java/pkg/MovementApp.java): Handles game lifecycle, input binding, viewport tracking, and network loop hooks.
- [NetworkManager.java](src/main/java/pkg/net/NetworkManager.java): TCP networking thread execution, socket I/O, and packet serialization.
- [GameStatePacket.java](src/main/java/pkg/net/GameStatePacket.java): Network payload for host-to-client state replication.
- [InputPacket.java](src/main/java/pkg/net/InputPacket.java): Network payload for client-to-host key input transmission.
- [PlayerComponent.java](src/main/java/pkg/PlayerComponent.java): Manages movement, collision detection, and remote state updates.

---

## 3. Input & Control Scheme

Input handling adapts based on the active `GameMode`:

### Local Shared-Screen Co-Op
- **Player 1**: `W`, `A`, `S`, `D` keys control Player 1 entity movement.
- **Player 2**: `UP`, `DOWN`, `LEFT`, `RIGHT` arrow keys control Player 2 entity movement.

### LAN Co-Op (Host vs. Client)
- **Host (Player 1)**: `WASD` / Arrow keys move Player 1 locally. The host evaluates physics and collision for both players.
- **Client (Player 2)**: Directional inputs (WASD or Arrow keys) update boolean flags (`clientUp`, `clientDown`, `clientLeft`, `clientRight`). Any change sends an [InputPacket](src/main/java/pkg/net/InputPacket.java) to the host via TCP socket.

---

## 4. Networking Protocol & Synchronization

LAN multiplayer operates under a **Host-Authoritative Pipeline** over TCP sockets (default port `55555`).

```
  +------------------+                                  +------------------+
  |    LAN Client    |                                  |     LAN Host     |
  |    (Player 2)    |                                  |    (Player 1)    |
  +--------+---------+                                  +--------+---------+
           |                                                     |
           | ---------- InputPacket (Up, Down, Left, Right) ---> | [Receives Input]
           |                                                     | [Updates Player 2 State]
           |                                                     | [Runs Physics & Collision]
           | <--- GameStatePacket (Positions, Dirs, Time) ------ | [Broadcasts State]
           v                                                     v
  [Applies Remote State]                                 [Renders Local Scene]
```

### Packet Definitions

1. **[InputPacket](src/main/java/pkg/net/InputPacket.java)** (Client $\to$ Host):
   ```java
   public boolean up;
   public boolean down;
   public boolean left;
   public boolean right;
   ```
2. **[GameStatePacket](src/main/java/pkg/net/GameStatePacket.java)** (Host $\to$ Client):
   ```java
   public double p1X, p1Y;
   public int p1DirIndex;
   public boolean p1Moving;

   public double p2X, p2Y;
   public int p2DirIndex;
   public boolean p2Moving;

   public double remainingTime;
   ```

### Thread Model & Thread Safety
- **Network Threads**: Socket I/O is handled on dedicated daemon threads (`LAN-Host-Thread` and `LAN-Client-Thread`) in [NetworkManager.java](src/main/java/pkg/net/NetworkManager.java).
- **FXGL UI Synchronization**: Received packets are executed on JavaFX Application Thread using `FXGL.runOnce(..., Duration.ZERO)` to ensure thread-safe updates to game entities and UI nodes.

---

## 5. Camera & Viewport Management

Viewport behavior dynamically configures based on the game mode in `setupViewports()` inside [MovementApp.java](src/main/java/pkg/MovementApp.java):

```java
if (selectedGameMode == GameMode.LOCAL_COOP_SPLITSCREEN) {
    // Dynamic Midpoint Tracking
    vp.setZoom(1.8);
    vp.unbind();
    updateCoopCamera();
} else if (selectedGameMode == GameMode.LAN_JOIN) {
    // Bound to Player 2
    vp.setZoom(2.0);
    vp.bindToEntity(playerEntity2, FXGL.getAppWidth() / 2.0, FXGL.getAppHeight() / 2.0);
} else {
    // Bound to Player 1 (Single Player or Host)
    vp.setZoom(2.0);
    vp.bindToEntity(playerEntity, FXGL.getAppWidth() / 2.0, FXGL.getAppHeight() / 2.0);
}
```

### Shared Screen Midpoint Camera Calculation
For Local Co-Op, the camera tracks the center of both players each frame via `updateCoopCamera()`:

$$\text{MidX} = \frac{X_{P1} + X_{P2}}{2} + 120$$
$$\text{MidY} = \frac{Y_{P1} + Y_{P2}}{2} + 120$$

---

## 6. Player Animation & Collision Handling

The [PlayerComponent](src/main/java/pkg/PlayerComponent.java) manages rendering and physical presence for both local and remote players:

- **Sprite Mapping**: Extracts 8-directional walking and idle frame sets from `src/main/resources/assets/textures/characters.png`.
  - Player 1 uses sprite rows 9–11.
  - Player 2 uses sprite rows 5–7.
- **Collision Resolution**: Axis-aligned bounding box (AABB) step collisions prevent players from walking through walls or overlapping each other.
- **Remote Animation Sync**: In `LAN_JOIN` mode, `playerComponent.setRemoteState(direction, isMoving)` directly sets animation channels based on network data without running local client physics predictions.
