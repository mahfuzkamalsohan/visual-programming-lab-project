package pkg.restoration.components;

final class RenderDepth {

    private static final int SCALE = 100;

    private RenderDepth() {
    }

    static int at(double screenY, int tieBreaker) {
        return (int) Math.round(screenY * SCALE) + tieBreaker;
    }
}
