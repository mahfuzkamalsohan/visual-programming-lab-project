package pkg.restoration.components;

public enum Direction {
    N("n"),
    NE("ne"),
    E("e"),
    SE("se"),
    S("s"),
    SW("sw"),
    W("w"),
    NW("nw");

    private final String assetSuffix;

    Direction(String assetSuffix) {
        this.assetSuffix = assetSuffix;
    }

    public String assetSuffix() {
        return assetSuffix;
    }

    public static Direction fromVector(double x, double y, Direction fallback) {
        if (Math.abs(x) < 0.001 && Math.abs(y) < 0.001) {
            return fallback;
        }

        if (y < -0.35) {
            if (x < -0.35) {
                return NW;
            }
            if (x > 0.35) {
                return NE;
            }
            return N;
        }

        if (y > 0.35) {
            if (x < -0.35) {
                return SW;
            }
            if (x > 0.35) {
                return SE;
            }
            return S;
        }

        return x < 0 ? W : E;
    }
}
