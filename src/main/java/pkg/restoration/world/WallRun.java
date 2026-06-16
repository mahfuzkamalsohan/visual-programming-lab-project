package pkg.restoration.world;

import java.util.List;

public record WallRun(WallSide side, List<WallSegment> segments) {

    public WallRun {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("A wall run must contain at least one segment.");
        }

        segments = List.copyOf(segments);
    }

    public IsoPoint start() {
        WallSegment first = segments.get(0);
        return switch (side) {
            case NORTH, SOUTH -> first.position().add(-0.5, 0);
            case WEST, EAST -> first.position().add(0, -0.5);
        };
    }

    public IsoPoint end() {
        WallSegment last = segments.get(segments.size() - 1);
        return switch (side) {
            case NORTH, SOUTH -> last.position().add(0.5, 0);
            case WEST, EAST -> last.position().add(0, 0.5);
        };
    }
}
