package pkg.restoration.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LevelShape {

    private final Set<GridPoint> tiles;
    private final LevelBounds bounds;

    private LevelShape(Set<GridPoint> tiles) {
        if (tiles.isEmpty()) {
            throw new IllegalArgumentException("A level shape must contain at least one tile.");
        }

        this.tiles = Collections.unmodifiableSet(new LinkedHashSet<>(tiles));
        this.bounds = calculateBounds(tiles);
    }

    public static LevelShape fromTiles(Set<GridPoint> tiles) {
        return new LevelShape(tiles);
    }

    public static LevelShape fromRows(int originX, int originY, String... rows) {
        Set<GridPoint> tiles = new LinkedHashSet<>();

        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int column = 0; column < line.length(); column++) {
                char marker = line.charAt(column);
                if (marker != ' ' && marker != '.') {
                    tiles.add(new GridPoint(originX + column, originY + row));
                }
            }
        }

        return new LevelShape(tiles);
    }

    public Set<GridPoint> tiles() {
        return tiles;
    }

    public LevelBounds bounds() {
        return bounds;
    }

    public boolean hasTile(int x, int y) {
        return tiles.contains(new GridPoint(x, y));
    }

    public List<IsoPoint> wallSlotsNear(IsoPoint anchor, int count, double minimumDistance) {
        List<IsoPoint> candidates = wallSlotCandidates();
        candidates.sort(Comparator.comparingDouble(anchor::distance));

        List<IsoPoint> selected = selectSeparated(candidates, count, minimumDistance);
        for (double distance = minimumDistance - 0.25; selected.size() < count && distance >= 0.75; distance -= 0.25) {
            selected = selectSeparated(candidates, count, distance);
        }

        if (selected.size() < count) {
            for (IsoPoint candidate : candidates) {
                if (!selected.contains(candidate)) {
                    selected.add(candidate);
                }

                if (selected.size() == count) {
                    break;
                }
            }
        }

        selected.sort(Comparator
                .comparingDouble(IsoPoint::x)
                .thenComparingDouble(IsoPoint::y));
        return List.copyOf(selected.subList(0, Math.min(count, selected.size())));
    }

    public IsoPoint wallSlotToward(IsoPoint target) {
        return wallSlotCandidates().stream()
                .min(Comparator.comparingDouble(target::distance))
                .orElse(bounds.center());
    }

    public List<WallSegment> wallSegments() {
        List<WallSegment> segments = new ArrayList<>();
        double wallOffset = 0.11;

        for (GridPoint tile : tiles) {
            if (!tiles.contains(tile.north())) {
                segments.add(new WallSegment(new IsoPoint(tile.x() + 0.5, tile.y() + wallOffset), WallSide.NORTH, tile));
            }
            if (!tiles.contains(tile.south())) {
                segments.add(new WallSegment(new IsoPoint(tile.x() + 0.5, tile.y() + 1.0 - wallOffset), WallSide.SOUTH, tile));
            }
            if (!tiles.contains(tile.west())) {
                segments.add(new WallSegment(new IsoPoint(tile.x() + wallOffset, tile.y() + 0.5), WallSide.WEST, tile));
            }
            if (!tiles.contains(tile.east())) {
                segments.add(new WallSegment(new IsoPoint(tile.x() + 1.0 - wallOffset, tile.y() + 0.5), WallSide.EAST, tile));
            }
        }

        return List.copyOf(segments);
    }

    public boolean contains(IsoPoint point, double margin) {
        int tileX = (int) Math.floor(point.x());
        int tileY = (int) Math.floor(point.y());
        GridPoint tile = new GridPoint(tileX, tileY);

        if (!tiles.contains(tile)) {
            return false;
        }

        double localX = point.x() - tileX;
        double localY = point.y() - tileY;

        if (!tiles.contains(tile.west()) && localX < margin) {
            return false;
        }
        if (!tiles.contains(tile.east()) && localX > 1.0 - margin) {
            return false;
        }
        if (!tiles.contains(tile.north()) && localY < margin) {
            return false;
        }
        if (!tiles.contains(tile.south()) && localY > 1.0 - margin) {
            return false;
        }

        return true;
    }

    public IsoPoint clamp(IsoPoint point, double margin) {
        if (contains(point, margin)) {
            return point;
        }

        IsoPoint best = null;
        double bestDistance = Double.MAX_VALUE;

        for (GridPoint tile : tiles) {
            double minX = tiles.contains(tile.west()) ? tile.x() : tile.x() + margin;
            double maxX = tiles.contains(tile.east()) ? tile.x() + 1.0 : tile.x() + 1.0 - margin;
            double minY = tiles.contains(tile.north()) ? tile.y() : tile.y() + margin;
            double maxY = tiles.contains(tile.south()) ? tile.y() + 1.0 : tile.y() + 1.0 - margin;

            IsoPoint candidate = new IsoPoint(
                    clamp(point.x(), minX, maxX),
                    clamp(point.y(), minY, maxY)
            );

            double distance = point.distance(candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        return best == null ? point : best;
    }

    private List<IsoPoint> wallSlotCandidates() {
        List<IsoPoint> candidates = new ArrayList<>();
        double wallOffset = 0.18;

        for (GridPoint tile : tiles) {
            if (!tiles.contains(tile.north())) {
                candidates.add(new IsoPoint(tile.x() + 0.5, tile.y() + wallOffset));
            }
            if (!tiles.contains(tile.south())) {
                candidates.add(new IsoPoint(tile.x() + 0.5, tile.y() + 1.0 - wallOffset));
            }
            if (!tiles.contains(tile.west())) {
                candidates.add(new IsoPoint(tile.x() + wallOffset, tile.y() + 0.5));
            }
            if (!tiles.contains(tile.east())) {
                candidates.add(new IsoPoint(tile.x() + 1.0 - wallOffset, tile.y() + 0.5));
            }
        }

        return candidates;
    }

    private static List<IsoPoint> selectSeparated(List<IsoPoint> candidates, int count, double minimumDistance) {
        List<IsoPoint> selected = new ArrayList<>();

        for (IsoPoint candidate : candidates) {
            boolean separated = selected.stream()
                    .allMatch(existing -> existing.distance(candidate) >= minimumDistance);
            if (separated) {
                selected.add(candidate);
            }

            if (selected.size() == count) {
                break;
            }
        }

        return selected;
    }

    private static LevelBounds calculateBounds(Set<GridPoint> tiles) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (GridPoint tile : tiles) {
            minX = Math.min(minX, tile.x());
            minY = Math.min(minY, tile.y());
            maxX = Math.max(maxX, tile.x() + 1);
            maxY = Math.max(maxY, tile.y() + 1);
        }

        return new LevelBounds(minX, minY, maxX - minX, maxY - minY);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
