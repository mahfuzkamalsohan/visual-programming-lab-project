package pkg.restoration.components;

public enum Direction {
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W,
    NW;

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
