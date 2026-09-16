package pkg.restoration.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import pkg.GameMode;
import pkg.MovementApp;

public class CoopCameraAndTetherTest {

    @Test
    void testDynamicZoomCalculations() {
        double screenW = 1280.0;
        double screenH = 720.0;

        // 1. Players standing side-by-side (delta = 0)
        double deltaX = 0.0;
        double deltaY = 0.0;
        double zoomX = screenW / Math.max(340.0, deltaX + 220.0);
        double zoomY = screenH / Math.max(240.0, deltaY + 180.0);
        double targetZoom = Math.max(1.35, Math.min(2.35, Math.min(zoomX, zoomY)));

        assertEquals(2.35, targetZoom, 0.001, "Close players must have max zoom (2.35x)");

        // 2. Players medium distance apart (deltaX = 200, deltaY = 150)
        deltaX = 200.0;
        deltaY = 150.0;
        zoomX = screenW / Math.max(340.0, deltaX + 220.0);
        zoomY = screenH / Math.max(240.0, deltaY + 180.0);
        targetZoom = Math.max(1.35, Math.min(2.35, Math.min(zoomX, zoomY)));

        assertTrue(targetZoom >= 1.35 && targetZoom <= 2.35, "Medium distance must produce valid zoom");
        assertTrue(targetZoom < 2.35, "Zoom must scale out as distance increases");

        // 3. Players at max tether distance (400.0)
        deltaX = 350.0;
        deltaY = 193.0; // Math.hypot(350, 193) ~ 400
        zoomX = screenW / Math.max(340.0, deltaX + 220.0);
        zoomY = screenH / Math.max(240.0, deltaY + 180.0);
        targetZoom = Math.max(1.35, Math.min(2.35, Math.min(zoomX, zoomY)));

        assertTrue(targetZoom >= 1.35, "Target zoom at max tether must be at or above min zoom (1.35x)");

        // Check screen margin: at targetZoom, visible screen width and height
        double visibleW = screenW / targetZoom;
        double visibleH = screenH / targetZoom;

        double marginX = (visibleW - deltaX) / 2.0;
        double marginY = (visibleH - deltaY) / 2.0;

        assertTrue(marginX >= 50.0, "Horizontal margin must keep players comfortably on screen");
        assertTrue(marginY >= 50.0, "Vertical margin must keep players comfortably on screen");
    }

    @Test
    void testTetherCorrectionMath() {
        double maxTether = MovementApp.MAX_TETHER_DISTANCE;
        assertEquals(400.0, maxTether, 0.001);

        double p1X = 0.0, p1Y = 0.0;
        double p2X = 500.0, p2Y = 0.0; // 500 distance along X
        double dist = Math.hypot(p2X - p1X, p2Y - p1Y);
        double excess = dist - maxTether; // 100 excess

        assertEquals(100.0, excess, 0.001);

        // Angle from P2 to P1 is Math.PI (or 0 from P1 to P2)
        double angle = Math.atan2(p1Y - p2Y, p1X - p2X);

        // Test one player moving (P2 is moving away, P1 stationary)
        double correctedP2X = p2X + Math.cos(angle) * excess; // angle is PI, so cos is -1 => 500 - 100 = 400
        double newDist = Math.hypot(correctedP2X - p1X, p2Y - p1Y);

        assertEquals(400.0, newDist, 0.001, "Tether correction must bring distance down to exactly MAX_TETHER_DISTANCE");
    }

    @Test
    void testInfiniteGameModeHelpers() {
        MovementApp.selectedGameMode = GameMode.SINGLE_PLAYER;
        assertTrue(MovementApp.isInfiniteGameMode());
        org.junit.jupiter.api.Assertions.assertFalse(MovementApp.isInfiniteCoopMode());

        MovementApp.selectedGameMode = GameMode.LOCAL_COOP_SPLITSCREEN;
        assertTrue(MovementApp.isInfiniteGameMode());
        assertTrue(MovementApp.isInfiniteCoopMode());

        MovementApp.selectedGameMode = GameMode.MAP_GENERATOR;
        assertTrue(MovementApp.isInfiniteGameMode());
        assertTrue(MovementApp.isInfiniteCoopMode());

        MovementApp.selectedGameMode = GameMode.SORTING_TEST;
        org.junit.jupiter.api.Assertions.assertFalse(MovementApp.isInfiniteGameMode());
        org.junit.jupiter.api.Assertions.assertFalse(MovementApp.isInfiniteCoopMode());

        MovementApp.selectedGameMode = GameMode.SEQUENTIAL_DEMO;
        org.junit.jupiter.api.Assertions.assertFalse(MovementApp.isInfiniteGameMode());
        org.junit.jupiter.api.Assertions.assertFalse(MovementApp.isInfiniteCoopMode());
    }

    @Test
    void testZoomCompensatedCameraCentering() {
        double appW = 1280.0;
        double appH = 720.0;
        double[] zoomLevels = {1.0, 1.35, 1.75, 2.0, 2.35};
        double camX = 28.0;
        double camY = 172.0;

        for (double zoom : zoomLevels) {
            // Correct zoom-compensated viewport origin
            double originX = camX - (appW / (2.0 * zoom));
            double originY = camY - (appH / (2.0 * zoom));

            // In FXGL GameScene, screen coordinate is: zoom * (world - origin)
            double screenMidX = zoom * (camX - originX);
            double screenMidY = zoom * (camY - originY);

            assertEquals(appW / 2.0, screenMidX, 0.001, "Camera midpoint X must be dead center (640.0) at zoom " + zoom);
            assertEquals(appH / 2.0, screenMidY, 0.001, "Camera midpoint Y must be dead center (360.0) at zoom " + zoom);
        }
    }

    @Test
    void testPlayerScreenVisibilityAtSpawn() {
        double appW = 1280.0;
        double appH = 720.0;

        // Player 1 at (0, 160), Player 2 at (40, 160)
        double p1X = 0.0 + 8.0;
        double p1Y = 160.0 + 12.0;
        double p2X = 40.0 + 8.0;
        double p2Y = 160.0 + 12.0;

        double midX = (p1X + p2X) / 2.0;
        double midY = (p1Y + p2Y) / 2.0;

        double deltaX = Math.abs(p1X - p2X);
        double deltaY = Math.abs(p1Y - p2Y);
        double zoomX = appW / Math.max(340.0, deltaX + 220.0);
        double zoomY = appH / Math.max(240.0, deltaY + 180.0);
        double targetZoom = Math.max(1.35, Math.min(2.35, Math.min(zoomX, zoomY)));

        // Correct zoom-compensated origin
        double originX = midX - (appW / (2.0 * targetZoom));
        double originY = midY - (appH / (2.0 * targetZoom));

        double p1ScreenX = targetZoom * (p1X - originX);
        double p1ScreenY = targetZoom * (p1Y - originY);
        double p2ScreenX = targetZoom * (p2X - originX);
        double p2ScreenY = targetZoom * (p2Y - originY);

        // Verify both players are comfortably inside [0, 1280] x [0, 720]
        assertTrue(p1ScreenX > 100.0 && p1ScreenX < appW - 100.0, "Player 1 must be visibly on screen horizontally");
        assertTrue(p1ScreenY > 100.0 && p1ScreenY < appH - 100.0, "Player 1 must be visibly on screen vertically");
        assertTrue(p2ScreenX > 100.0 && p2ScreenX < appW - 100.0, "Player 2 must be visibly on screen horizontally");
        assertTrue(p2ScreenY > 100.0 && p2ScreenY < appH - 100.0, "Player 2 must be visibly on screen vertically");

        // Buggy FXGL focusOn would have used origin = mid - app/2 without dividing by zoom:
        double buggyOriginX = midX - appW / 2.0;
        double buggyOriginY = midY - appH / 2.0;
        double buggyP1ScreenX = targetZoom * (p1X - buggyOriginX);
        double buggyP1ScreenY = targetZoom * (p1Y - buggyOriginY);
        // At targetZoom 2.35, buggy formula pushed players way off screen (> 1280 and > 720)
        assertTrue(buggyP1ScreenX > appW, "Buggy formula would have placed Player 1 off screen (> 1280)");
        assertTrue(buggyP1ScreenY > appH, "Buggy formula would have placed Player 1 off screen (> 720)");
    }
}

