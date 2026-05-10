package pkg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.almasb.fxgl.core.math.FXGLMath;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class HackingMinigameUI extends StackPane {
    private final int BOARD_SIZE = 400; // Increased slightly for better grid visibility
    private final int gridSize = 6;     // 6x6 matches the denser feel of the reference
    private double cellSize;

    private final List<Point2D> path = new ArrayList<>();
    private final List<Point2D> obstacles = new ArrayList<>();
    private final List<Point2D> bonusDots = new ArrayList<>();
    private Point2D startDot, endDot;

    private final AnimationTimer timer;
    private final Consumer<Integer> onWin;
    private final Runnable onFail;

    private double shakeTime = 0;
    private int level = 1;
    
    private long lastTime = 0;
    private double maxTime = 15.0;
    private double timeLeft = 15.0;
    private boolean isFinished = false;
    
    private boolean isWon = false;
    private double winTimer = 0;
    private int collectedBonuses = 0;
    private boolean isDamaged = false;
    private double damageTimer = 0;

    // Pragmata HUD Colors
    private final Color GRID_COLOR = Color.web("#4a8ba8", 0.3);
    private final Color PATH_COLOR = Color.web("#00e5ff", 0.6);
    private final Color OBSTACLE_COLOR = Color.web("#ff003c", 0.5);
    private final Color GOAL_COLOR = Color.web("#00ff66", 0.5);
    private final Color BONUS_COLOR = Color.web("#d400ff", 0.5);
    private final Color PLAYER_COLOR = Color.web("#ffffff", 0.8);

    public HackingMinigameUI(Consumer<Integer> onWin, Runnable onFail) {
        this.onWin = onWin;
        this.onFail = onFail;
        
        this.getStyleClass().add("game-container");
        Canvas canvas = new Canvas(BOARD_SIZE, BOARD_SIZE + 40);
        this.getChildren().add(canvas);
        
        generateLevel();

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTime == 0) { lastTime = now; return; }
                double tpf = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;
                
                update(tpf);
                draw(canvas.getGraphicsContext2D(), now);
            }
        };
        timer.start();
    }
    
    private void update(double tpf) {
        if (isWon) {
            winTimer -= tpf;
            if (winTimer <= 0) {
                isWon = false;
                onWin.accept(collectedBonuses);
                level++;
                generateLevel();
            }
            return;
        }

        if (isDamaged) {
            damageTimer -= tpf;
            if (damageTimer <= 0) {
                isDamaged = false;
                if (timeLeft <= 0) {
                    timeLeft = 0;
                    isFinished = true;
                    if (onFail != null) onFail.run();
                }
            }
            return;
        }

        if (isFinished) return;
        
        timeLeft -= tpf;
        if (timeLeft <= 0) {
            timeLeft = 0;
            isFinished = true;
            if (onFail != null) onFail.run();
        }
    }

    private void generateLevel() {
        cellSize = (double) BOARD_SIZE / gridSize;
        path.clear();
        obstacles.clear();
        bonusDots.clear();
        
        isFinished = false;
        isWon = false;
        isDamaged = false;
        // The time limit gets tighter as the level increases!
        maxTime = Math.max(8.0, 20.0 - (level * 1.5));
        timeLeft = maxTime;

        int requiredBonuses = 3;
        int requiredObstacles = 5 + (level / 2);
        
        // Make the master path stretch throughout the maze so bonuses are scattered randomly along it
        // Cap the length so it doesn't try to fill the entire grid and fail
        int minPathLength = Math.min((gridSize * gridSize) - 6, 12 + (level * 2)); 
        
        // Distance of 4.0 ensures Start and End nodes spawn far away from each other
        List<Point2D> masterPath = generateMasterPath(minPathLength, 4.0);

        // 1. Set Start and End
        startDot = masterPath.get(0);
        endDot = masterPath.get(masterPath.size() - 1);

        // 2. Place Bonuses
        List<Point2D> middleNodes = new ArrayList<>(masterPath.subList(1, masterPath.size() - 1));
        if (!middleNodes.isEmpty()) {
            int actualBonuses = Math.min(requiredBonuses, middleNodes.size());
            double step = (double) middleNodes.size() / actualBonuses;
            
            // Pick bonuses evenly spaced along the entire path length
            for (int i = 0; i < actualBonuses; i++) {
                int targetIndex = (int) (i * step + (step / 2.0));
                targetIndex = Math.min(targetIndex, middleNodes.size() - 1);
                if (!bonusDots.contains(middleNodes.get(targetIndex))) {
                    bonusDots.add(middleNodes.get(targetIndex));
                }
            }
        }

        // 3. Place Obstacles safely
        // To prevent infinite loops if the grid fills up, cap the maximum possible obstacles
        int maxPossibleObstacles = (gridSize * gridSize) - masterPath.size();
        requiredObstacles = Math.min(requiredObstacles, maxPossibleObstacles);

        int attempts = 0;
        while (obstacles.size() < requiredObstacles && attempts < 1000) {
            attempts++;
            Point2D randomBlock = new Point2D(FXGLMath.random(0, gridSize - 1), FXGLMath.random(0, gridSize - 1));
            if (!masterPath.contains(randomBlock) && !obstacles.contains(randomBlock)) {
                // Enforce a minimum distance between obstacles to prevent red clumps
                // We relax this rule if it takes too many attempts on a crowded board
                double minDistance = attempts < 100 ? 1.5 : 0.0;
                
                boolean tooClose = obstacles.stream().anyMatch(obs -> randomBlock.distance(obs) < minDistance);
                if (!tooClose) {
                    obstacles.add(randomBlock);
                }
            }
        }
        
        path.add(startDot);
    }

    private List<Point2D> generateMasterPath(int minLength, double minDistance) {
        Point2D[] dirsArray = {new Point2D(0, -1), new Point2D(0, 1), new Point2D(-1, 0), new Point2D(1, 0)};
        List<Point2D> directions = Arrays.asList(dirsArray);

        for (int tries = 0; tries < 1000; tries++) {
            Point2D startNode = new Point2D(FXGLMath.random(0, gridSize - 1), FXGLMath.random(0, gridSize - 1));
            List<Point2D> tempPath = new ArrayList<>();
            tempPath.add(startNode);
            
            boolean[][] visited = new boolean[gridSize][gridSize];
            visited[(int) startNode.getX()][(int) startNode.getY()] = true;
            
            while (tempPath.size() < minLength) {
                Collections.shuffle(directions);
                boolean moved = false;
                
                Point2D lastNode = tempPath.get(tempPath.size() - 1);
                for (Point2D dir : directions) {
                    Point2D nextNode = lastNode.add(dir);
                    int nx = (int) nextNode.getX();
                    int ny = (int) nextNode.getY();
                    
                    if (nx >= 0 && nx < gridSize && ny >= 0 && ny < gridSize && !visited[nx][ny]) {
                        visited[nx][ny] = true;
                        tempPath.add(nextNode);
                        moved = true;
                        break;
                    }
                }
                
                if (!moved) {
                    break; // Hit a dead end
                }
            }
            
            if (tempPath.size() >= minLength) {
                Point2D lastNode = tempPath.get(tempPath.size() - 1);
                if (startNode.distance(lastNode) >= minDistance) {
                    return tempPath;
                }
            }
        }
        
        // Fallback path in case 1000 iterations fail to find a valid route
        return Arrays.asList(
            new Point2D(0, 0), new Point2D(1, 0), new Point2D(2, 0), new Point2D(3, 0), new Point2D(4, 0), new Point2D(5, 0),
            new Point2D(5, 1), new Point2D(4, 1), new Point2D(3, 1), new Point2D(2, 1), new Point2D(1, 1), new Point2D(0, 1),
            new Point2D(0, 2), new Point2D(1, 2), new Point2D(2, 2), new Point2D(3, 2), new Point2D(4, 2), new Point2D(5, 2)
        );
    }

    public void moveHacker(int dx, int dy) {
        if (isFinished || isWon || isDamaged) return;
        
        Point2D current = path.get(path.size() - 1);
        int gx = (int) current.getX() + dx;
        int gy = (int) current.getY() + dy;

        if (gx < 0 || gx >= gridSize || gy < 0 || gy >= gridSize) return;

        Point2D next = new Point2D(gx, gy);

        if (obstacles.contains(next)) {
            shakeTime = 15; // Trigger Screen Shake
            timeLeft -= 4.0; // Huge time penalty for hitting a firewall!
            isDamaged = true;
            damageTimer = 0.3; // 0.3s delay to show the red glitch effect
            return;
        }

        if (path.contains(next)) {
            int idx = path.indexOf(next);
            path.subList(idx + 1, path.size()).clear();
        } else {
            path.add(next);
        }

        if (next.equals(endDot) && onWin != null) {
            collectedBonuses = 0;
            for (Point2D p : path) {
                if (bonusDots.contains(p)) collectedBonuses++;
            }
            isFinished = true;
            isWon = true;
            winTimer = 0.3; // 0.3 second delay for a fast, snappy success effect
        }
    }

    public void stop() {
        timer.stop();
    }

    public void resume() {
        lastTime = 0; // Reset delta time
        timer.start();
    }

    private void draw(GraphicsContext g, long now) {
        g.clearRect(0, 0, BOARD_SIZE, BOARD_SIZE + 40);

        if (shakeTime > 0) {
            g.save();
            g.translate(FXGLMath.random(-4.0, 4.0), FXGLMath.random(-4.0, 4.0));
            shakeTime--;
        }

        // 1. Draw Background HUD Elements (Radar circles)
        g.setStroke(Color.web("#ffffff", 0.05));
        g.setLineWidth(1);
        g.strokeOval(BOARD_SIZE/2 - 150, BOARD_SIZE/2 - 150, 300, 300);
        g.strokeOval(BOARD_SIZE/2 - 250, BOARD_SIZE/2 - 250, 500, 500);

        // 2. Draw Tech Grid
        g.setStroke(GRID_COLOR);
        g.setLineWidth(1.5);
        for (int i = 0; i <= gridSize; i++) {
            g.strokeLine(i * cellSize, 0, i * cellSize, BOARD_SIZE);
            g.strokeLine(0, i * cellSize, BOARD_SIZE, i * cellSize);
        }
        // Draw grid intersection crosses
        g.setStroke(Color.web("#ffffff", 0.4));
        for (int x = 0; x <= gridSize; x++) {
            for (int y = 0; y <= gridSize; y++) {
                g.strokeLine(x * cellSize - 3, y * cellSize, x * cellSize + 3, y * cellSize);
                g.strokeLine(x * cellSize, y * cellSize - 3, x * cellSize, y * cellSize + 3);
            }
        }

        // 3. Draw Elements (Cells)
        double pulse = Math.abs(Math.sin(now * 0.000000003)); // Smooth pulsing
        
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                Point2D p = new Point2D(x, y);
                double cx = x * cellSize;
                double cy = y * cellSize;

                if (p.equals(endDot)) {
                    drawTechBox(g, cx, cy, GOAL_COLOR, "POWER");
                } else if (obstacles.contains(p)) {
                    // Pragmata visual difficulty: Security nodes glitch and jitter!
                    double jx = 0; double jy = 0;
                    if (FXGLMath.randomBoolean(0.15)) {
                        jx = FXGLMath.random(-3.0, 3.0);
                        jy = FXGLMath.random(-3.0, 3.0);
                    }
                    // Occasional transparency flicker
                    Color glitchColor = FXGLMath.randomBoolean(0.05) ? Color.TRANSPARENT : OBSTACLE_COLOR;
                    
                    drawTechBox(g, cx + jx, cy + jy, glitchColor, "WARNING");
                } else if (bonusDots.contains(p) && !path.contains(p)) {
                    // Pulse opacity for bonuses
                    Color pulsingBonus = Color.color(BONUS_COLOR.getRed(), BONUS_COLOR.getGreen(), BONUS_COLOR.getBlue(), 0.3 + (pulse * 0.4));
                    drawTechBox(g, cx, cy, pulsingBonus, "DATA");
                } else if (!path.contains(p) && !p.equals(startDot)) {
                    // Empty grid cells have faint blue tech icons
                    drawTechBox(g, cx, cy, Color.web("#00e5ff", 0.05), "EMPTY");
                }
            }
        }

        // 4. Draw Circuit Path
        if (path.size() > 1) {
            g.setStroke(PATH_COLOR);
            g.setLineWidth(4);
            g.setLineJoin(javafx.scene.shape.StrokeLineJoin.MITER);

            g.beginPath();
            g.moveTo(path.get(0).getX() * cellSize + cellSize/2, path.get(0).getY() * cellSize + cellSize/2);
            for (int i = 1; i < path.size(); i++) {
                g.lineTo(path.get(i).getX() * cellSize + cellSize/2, path.get(i).getY() * cellSize + cellSize/2);
            }
            g.stroke();
            
            // Draw path nodes
            for (Point2D p : path) {
                drawTechBox(g, p.getX() * cellSize, p.getY() * cellSize, Color.web("#00e5ff", 0.3), "NODE");
            }
        }

        // 5. Draw Player / Current Position (Targeting Reticle)
        if (!path.isEmpty()) {
            Point2D head = path.get(path.size() - 1);
            drawReticle(g, head.getX() * cellSize, head.getY() * cellSize);
        }

        if (shakeTime > 0) g.restore();
        
        // 6. Draw System Trace Timer
        double barWidth = BOARD_SIZE;
        double timeRatio = Math.max(0, timeLeft / maxTime);
        
        g.setFill(Color.web("#131d27", 0.8));
        g.fillRect(0, BOARD_SIZE + 25, barWidth, 10);
        
        // Gradual color transition from Green -> Yellow -> Red
        Color barColor;
        if (timeRatio > 0.5) {
            // 50% to 100%: Interpolate from Yellow to Green
            barColor = Color.web("#ffea00").interpolate(Color.web("#00ff66"), (timeRatio - 0.5) * 2.0);
        } else {
            // 0% to 50%: Interpolate from Red to Yellow
            barColor = Color.web("#ff003c").interpolate(Color.web("#ffea00"), timeRatio * 2.0);
        }
        
        g.setFill(barColor);
        g.fillRect(0, BOARD_SIZE + 25, barWidth * timeRatio, 10);
        
        // Make the text pulsate rapidly if time is below 25%
        if (timeRatio < 0.25) {
            double textPulse = 0.2 + 0.8 * Math.abs(Math.sin(now * 0.000000015)); // Fast sine wave
            g.setFill(Color.color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), textPulse));
        }
        
        g.fillText(String.format("SYS.TRACE // %.1fs", Math.max(0, timeLeft)), 0, BOARD_SIZE + 20);

        if (isWon || isDamaged) {
            g.save();
            
            // Dynamic progression (0.0 to 1.0)
            double currentTimer = isWon ? winTimer : damageTimer;
            double progress = Math.max(0, Math.min(1.0, 1.0 - (currentTimer / 0.3)));
            
            Color effectColor = isWon ? Color.web("#00ff66") : Color.web("#ff003c");

            // Fast pulsing background
            double overlayPulse = 0.3 + 0.4 * Math.abs(Math.sin(now * 0.00000005));
            g.setFill(Color.color(effectColor.getRed(), effectColor.getGreen(), effectColor.getBlue(), overlayPulse * (1.0 - progress * 0.5)));
            g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE + 40);
            
            // Lightning fast multiple scanlines
            g.setStroke(Color.web("#ffffff", 0.8 - (progress * 0.8)));
            g.setLineWidth(3);
            for (int i = 0; i < 4; i++) {
                double scanlineY = ((now / 200_000.0) + (i * 110)) % (BOARD_SIZE + 40);
                g.strokeLine(0, scanlineY, BOARD_SIZE, scanlineY);
            }
            
            g.restore();
        }
    }

    // --- HUD ASSET DRAWING HELPERS ---

    private void drawTechBox(GraphicsContext g, double x, double y, Color color, String iconType) {
        double padding = 2;
        double s = cellSize - (padding * 2);
        double bx = x + padding;
        double by = y + padding;

        // Semi-transparent background
        g.setFill(color);
        g.fillRect(bx, by, s, s);

        // Bright border
        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.8));
        g.setLineWidth(1.5);
        g.strokeRect(bx, by, s, s);

        // Draw inner icons based on type
        double cx = bx + s/2;
        double cy = by + s/2;

        g.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.9));
        g.setLineWidth(2);

        switch (iconType) {
            case "WARNING" -> { // Triangle
                g.strokePolygon(new double[]{cx, cx - 10, cx + 10}, new double[]{cy - 10, cy + 8, cy + 8}, 3);
                g.setFill(g.getStroke());
                g.fillText("!", cx - 2, cy + 5);
            }
            case "POWER" -> { // Segmented Circle / Power Button
                g.strokeArc(cx - 10, cy - 10, 20, 20, 120, 300, javafx.scene.shape.ArcType.OPEN);
                g.strokeLine(cx, cy - 12, cx, cy + 2);
            }
            case "DATA" -> { // Diamond / Gun placeholder
                g.strokePolygon(new double[]{cx, cx + 8, cx, cx - 8}, new double[]{cy - 8, cy, cy + 8, cy}, 4);
                g.strokeRect(cx - 3, cy - 3, 6, 6);
            }
            case "EMPTY" -> { // Faint inner arrows/brackets
                g.setLineWidth(1);
                g.strokeRect(cx - 6, cy - 6, 12, 12);
                g.strokeLine(cx - 10, cy, cx - 6, cy); // Left
                g.strokeLine(cx + 10, cy, cx + 6, cy); // Right
            }
        }
    }

    private void drawReticle(GraphicsContext g, double x, double y) {
        double padding = 2;
        double s = cellSize - (padding * 2);
        double bx = x + padding;
        double by = y + padding;

        // Player gets a strong white box with thick corner brackets
        g.setFill(Color.web("#ffffff", 0.3));
        g.fillRect(bx, by, s, s);
        
        g.setStroke(PLAYER_COLOR);
        g.setLineWidth(1);
        g.strokeRect(bx, by, s, s);

        // Thick Corner Brackets
        g.setStroke(Color.WHITE);
        g.setLineWidth(3);
        double length = 12; // Length of the bracket edge
        
        // Top Left
        g.strokeLine(bx, by, bx + length, by);
        g.strokeLine(bx, by, bx, by + length);
        // Top Right
        g.strokeLine(bx + s, by, bx + s - length, by);
        g.strokeLine(bx + s, by, bx + s, by + length);
        // Bottom Left
        g.strokeLine(bx, by + s, bx + length, by + s);
        g.strokeLine(bx, by + s, bx, by + s - length);
        // Bottom Right
        g.strokeLine(bx + s, by + s, bx + s - length, by + s);
        g.strokeLine(bx + s, by + s, bx + s, by + s - length);

        // Center crosshair
        double cx = bx + s/2;
        double cy = by + s/2;
        g.setLineWidth(1);
        g.strokeLine(cx - 5, cy, cx + 5, cy);
        g.strokeLine(cx, cy - 5, cx, cy + 5);
    }
}