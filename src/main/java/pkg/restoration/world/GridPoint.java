package pkg.restoration.world;

public record GridPoint(int x, int y) {

    public GridPoint north() {
        return new GridPoint(x, y - 1);
    }

    public GridPoint south() {
        return new GridPoint(x, y + 1);
    }

    public GridPoint east() {
        return new GridPoint(x + 1, y);
    }

    public GridPoint west() {
        return new GridPoint(x - 1, y);
    }
}
