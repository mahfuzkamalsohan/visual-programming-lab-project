package pkg.restoration.tools;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

public final class DemoAssetGenerator {

    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
    private static final int PLAYER_W = 96;
    private static final int PLAYER_H = 128;

    private DemoAssetGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
                ? Paths.get("src/main/resources/assets/textures/restoration")
                : Paths.get(args[0]);

        Files.createDirectories(output);

        for (String direction : DIRECTIONS) {
            writePlayerSheet(output.resolve("player_idle_" + direction + ".png"), direction, false);
            writePlayerSheet(output.resolve("player_walk_" + direction + ".png"), direction, true);
        }

        writeNpc(output.resolve("npc_keeper.png"));
        writeRescueDog(output.resolve("npc_rescue_dog.png"));
        writeCanalDuck(output.resolve("npc_canal_duck.png"));
        writeOrchardDeer(output.resolve("npc_orchard_deer.png"));
        writeTile(output.resolve("tile_dust.png"), new Color(116, 92, 68), new Color(71, 57, 48), new Color(159, 132, 89));
        writeTile(output.resolve("tile_recovering.png"), new Color(101, 129, 72), new Color(67, 85, 61), new Color(183, 168, 92));
        writeTile(output.resolve("tile_green.png"), new Color(94, 157, 83), new Color(51, 103, 72), new Color(191, 222, 119));
        writeTile(output.resolve("tile_path.png"), new Color(136, 122, 86), new Color(83, 72, 58), new Color(200, 177, 105));
        writeBoundary(output.resolve("boundary_wall.png"));
        writeGate(output.resolve("gate_sealed.png"), new Color(84, 106, 114), new Color(230, 91, 76), true);
        writeGate(output.resolve("gate_open.png"), new Color(100, 142, 111), new Color(118, 226, 148), false);
        writeGate(output.resolve("gate_closed.png"), new Color(69, 73, 75), new Color(116, 92, 68), true);
        writeGate(output.resolve("gate_decision.png"), new Color(102, 95, 145), new Color(216, 226, 111), false);
    }

    private static void writePlayerSheet(Path path, String direction, boolean walking) throws IOException {
        BufferedImage image = new BufferedImage(PLAYER_W * 4, PLAYER_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        for (int frame = 0; frame < 4; frame++) {
            Graphics2D frameGraphics = (Graphics2D) g.create(frame * PLAYER_W, 0, PLAYER_W, PLAYER_H);
            drawPlayerFrame(frameGraphics, direction, walking, frame);
            frameGraphics.dispose();
        }

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void drawPlayerFrame(Graphics2D g, String direction, boolean walking, int frame) {
        int bob = walking ? Math.abs(1 - frame) * 2 : frame % 2;
        int[] facing = directionVector(direction);

        g.setColor(new Color(0, 0, 0, 62));
        g.fill(new Ellipse2D.Double(24, 102, 48, 14));

        int legSpread = walking ? (frame % 2 == 0 ? 6 : -6) : 0;
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(50, 59, 54));
        g.drawLine(43, 82 - bob, 36 - legSpread, 104);
        g.drawLine(53, 82 - bob, 60 + legSpread, 104);

        g.setColor(new Color(50, 87, 76));
        g.fill(new RoundRectangle2D.Double(31, 42 - bob, 34, 48, 15, 15));
        g.setColor(new Color(112, 210, 131));
        g.fill(new RoundRectangle2D.Double(36, 48 - bob, 24, 26, 11, 11));

        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(61, 73, 66));
        g.drawLine(33, 55 - bob, 21 + facing[0] * 2, 75 - bob + facing[1]);
        g.drawLine(63, 55 - bob, 75 + facing[0] * 2, 75 - bob + facing[1]);

        g.setColor(new Color(217, 177, 132));
        g.fillOval(35 + facing[0] * 2, 20 - bob + facing[1] * 2, 26, 28);
        g.setColor(new Color(47, 44, 40));
        g.fillArc(32 + facing[0] * 2, 15 - bob + facing[1] * 2, 33, 25, 0, 180);

        g.setColor(new Color(27, 35, 31));
        int eyeY = 32 - bob + Math.max(0, facing[1]);
        g.fillOval(43 + facing[0], eyeY, 3, 3);
        g.fillOval(52 + facing[0], eyeY, 3, 3);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(238, 247, 212, 160));
        drawCentered(g, direction.toUpperCase(), 48, 119);
    }

    private static void writeNpc(Path path) throws IOException {
        BufferedImage image = new BufferedImage(96, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        g.setColor(new Color(0, 0, 0, 62));
        g.fill(new Ellipse2D.Double(24, 102, 48, 14));
        g.setColor(new Color(68, 61, 93));
        g.fill(new RoundRectangle2D.Double(28, 45, 40, 54, 18, 18));
        g.setColor(new Color(216, 226, 111));
        g.fill(new RoundRectangle2D.Double(34, 55, 28, 22, 10, 10));
        g.setColor(new Color(154, 121, 94));
        g.fillOval(34, 21, 28, 29);
        g.setColor(new Color(231, 238, 204));
        g.fillArc(30, 15, 36, 28, 0, 180);
        g.setColor(new Color(31, 38, 35));
        g.fillOval(42, 33, 3, 3);
        g.fillOval(52, 33, 3, 3);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(238, 247, 212, 170));
        drawCentered(g, "NPC", 48, 119);

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeRescueDog(Path path) throws IOException {
        BufferedImage image = new BufferedImage(96, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        g.setColor(new Color(0, 0, 0, 58));
        g.fill(new Ellipse2D.Double(18, 94, 60, 14));
        g.setColor(new Color(139, 104, 74));
        g.fill(new RoundRectangle2D.Double(24, 58, 46, 30, 16, 16));
        g.setColor(new Color(92, 63, 48));
        g.fillOval(54, 42, 27, 25);
        g.fillOval(58, 50, 28, 15);
        g.setColor(new Color(66, 52, 43));
        g.fillOval(57, 41, 8, 21);
        g.fillOval(72, 43, 8, 20);
        g.setColor(new Color(230, 238, 104));
        g.fill(new RoundRectangle2D.Double(30, 61, 29, 17, 8, 8));
        g.setColor(new Color(66, 111, 98));
        g.fill(new RoundRectangle2D.Double(34, 63, 20, 12, 6, 6));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(92, 63, 48));
        g.drawLine(27, 84, 23, 100);
        g.drawLine(38, 86, 36, 103);
        g.drawLine(58, 86, 58, 103);
        g.drawLine(68, 84, 73, 99);
        g.drawLine(23, 63, 12, 50);
        g.setColor(new Color(27, 35, 31));
        g.fillOval(67, 51, 3, 3);
        g.fillOval(79, 57, 5, 4);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(238, 247, 212, 170));
        drawCentered(g, "DOG", 48, 119);

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeCanalDuck(Path path) throws IOException {
        BufferedImage image = new BufferedImage(96, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        g.setColor(new Color(0, 0, 0, 52));
        g.fill(new Ellipse2D.Double(22, 95, 52, 13));
        g.setColor(new Color(51, 112, 94));
        g.fillOval(27, 66, 42, 28);
        g.setColor(new Color(34, 85, 72));
        g.fillOval(52, 48, 24, 24);
        g.setColor(new Color(244, 191, 72));
        g.fill(new RoundRectangle2D.Double(69, 57, 18, 8, 6, 6));
        g.setColor(new Color(230, 238, 204));
        g.fillOval(34, 69, 22, 15);
        g.setColor(new Color(27, 35, 31));
        g.fillOval(65, 55, 3, 3);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(214, 139, 46));
        g.drawLine(42, 91, 38, 103);
        g.drawLine(56, 90, 59, 103);
        g.drawLine(36, 103, 30, 106);
        g.drawLine(60, 103, 66, 106);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(238, 247, 212, 170));
        drawCentered(g, "DUCK", 48, 119);

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeOrchardDeer(Path path) throws IOException {
        BufferedImage image = new BufferedImage(96, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        g.setColor(new Color(0, 0, 0, 56));
        g.fill(new Ellipse2D.Double(18, 95, 60, 14));
        g.setColor(new Color(155, 105, 66));
        g.fill(new RoundRectangle2D.Double(25, 62, 44, 28, 16, 16));
        g.setColor(new Color(123, 80, 50));
        g.fillOval(55, 41, 23, 27);
        g.setColor(new Color(201, 169, 106));
        g.fillOval(68, 54, 17, 11);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(96, 60, 38));
        g.drawLine(31, 88, 27, 104);
        g.drawLine(43, 89, 43, 104);
        g.drawLine(58, 89, 59, 104);
        g.drawLine(68, 87, 73, 103);
        g.setColor(new Color(190, 149, 86));
        g.drawLine(60, 42, 51, 25);
        g.drawLine(72, 42, 80, 25);
        g.drawLine(53, 29, 45, 25);
        g.drawLine(78, 29, 86, 25);
        g.setColor(new Color(27, 35, 31));
        g.fillOval(66, 50, 3, 3);
        g.setColor(new Color(231, 238, 204, 190));
        g.fillOval(36, 68, 5, 4);
        g.fillOval(47, 71, 5, 4);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(238, 247, 212, 170));
        drawCentered(g, "DEER", 48, 119);

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeTile(Path path, Color fill, Color edge, Color highlight) throws IOException {
        BufferedImage image = new BufferedImage(96, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        Polygon diamond = diamond(48, 24, 47, 23);
        g.setColor(edge);
        g.fill(diamond);
        g.setColor(fill);
        g.fill(diamond(48, 22, 44, 20));
        g.setColor(new Color(highlight.getRed(), highlight.getGreen(), highlight.getBlue(), 112));
        g.setStroke(new BasicStroke(2));
        g.drawLine(12, 23, 48, 5);
        g.drawLine(48, 5, 84, 23);
        g.setColor(new Color(255, 255, 255, 28));
        g.fill(diamond(48, 20, 28, 12));

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeBoundary(Path path) throws IOException {
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        g.setColor(new Color(0, 0, 0, 52));
        g.fill(new Ellipse2D.Double(14, 68, 68, 14));
        g.setColor(new Color(71, 73, 68));
        g.fill(diamond(48, 67, 43, 20));
        g.setColor(new Color(104, 110, 92));
        g.fillRoundRect(20, 34, 56, 38, 8, 8);
        g.setColor(new Color(151, 157, 130));
        g.fillRoundRect(24, 26, 48, 20, 8, 8);
        g.setColor(new Color(43, 47, 43));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(20, 34, 56, 38, 8, 8);

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeGate(Path path, Color stone, Color energy, boolean sealed) throws IOException {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(26, 104, 76, 16));
        g.setColor(stone.darker());
        g.fillRoundRect(23, 36, 18, 75, 8, 8);
        g.fillRoundRect(87, 36, 18, 75, 8, 8);
        g.fillRoundRect(28, 24, 72, 20, 10, 10);

        g.setColor(stone);
        g.fillRoundRect(28, 31, 13, 76, 7, 7);
        g.fillRoundRect(87, 31, 13, 76, 7, 7);
        g.fillRoundRect(33, 20, 62, 20, 10, 10);

        g.setStroke(new BasicStroke(4));
        g.setColor(energy);
        g.drawRoundRect(40, 36, 48, 68, 18, 18);

        if (sealed) {
            g.setColor(new Color(energy.getRed(), energy.getGreen(), energy.getBlue(), 150));
            g.fillRoundRect(45, 43, 38, 54, 14, 14);
            g.setColor(new Color(255, 255, 255, 150));
            g.setStroke(new BasicStroke(3));
            g.drawLine(48, 52, 80, 88);
            g.drawLine(80, 52, 48, 88);
        } else {
            g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
            g.setColor(energy);
            g.fillRoundRect(45, 43, 38, 54, 14, 14);
            g.setComposite(AlphaComposite.SrcOver);
        }

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static Polygon diamond(int cx, int cy, int rx, int ry) {
        return new Polygon(
                new int[] {cx, cx + rx, cx, cx - rx},
                new int[] {cy - ry, cy, cy + ry, cy},
                4
        );
    }

    private static int[] directionVector(String direction) {
        return switch (direction) {
            case "n" -> new int[] {0, -1};
            case "ne" -> new int[] {1, -1};
            case "e" -> new int[] {1, 0};
            case "se" -> new int[] {1, 1};
            case "s" -> new int[] {0, 1};
            case "sw" -> new int[] {-1, 1};
            case "w" -> new int[] {-1, 0};
            case "nw" -> new int[] {-1, -1};
            default -> new int[] {0, 1};
        };
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
