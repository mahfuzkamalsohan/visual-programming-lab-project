package pkg.net;

import java.io.Serializable;

public class GameStatePacket implements Serializable {
    private static final long serialVersionUID = 1L;

    public double p1X;
    public double p1Y;
    public int p1DirIndex;
    public boolean p1Moving;

    public double p2X;
    public double p2Y;
    public int p2DirIndex;
    public boolean p2Moving;

    public double remainingTime;
    public int trashMask;
    public int collectedTrash;

    public long worldSeed;
    public int currentDistrict;
    public int generatorStageOrdinal;
    public int ecoScore;
    public int currentTaskChunkX;
    public int currentTaskChunkY;

    public GameStatePacket() {}

    public GameStatePacket(double p1X, double p1Y, int p1DirIndex, boolean p1Moving,
                           double p2X, double p2Y, int p2DirIndex, boolean p2Moving,
                           double remainingTime, int trashMask, int collectedTrash) {
        this(p1X, p1Y, p1DirIndex, p1Moving, p2X, p2Y, p2DirIndex, p2Moving, remainingTime, trashMask, collectedTrash, 0L, 1, 0, 0, 0, 0);
    }

    public GameStatePacket(double p1X, double p1Y, int p1DirIndex, boolean p1Moving,
                           double p2X, double p2Y, int p2DirIndex, boolean p2Moving,
                           double remainingTime, int trashMask, int collectedTrash,
                           long worldSeed, int currentDistrict, int generatorStageOrdinal, int ecoScore,
                           int currentTaskChunkX, int currentTaskChunkY) {
        this.p1X = p1X;
        this.p1Y = p1Y;
        this.p1DirIndex = p1DirIndex;
        this.p1Moving = p1Moving;
        this.p2X = p2X;
        this.p2Y = p2Y;
        this.p2DirIndex = p2DirIndex;
        this.p2Moving = p2Moving;
        this.remainingTime = remainingTime;
        this.trashMask = trashMask;
        this.collectedTrash = collectedTrash;
        this.worldSeed = worldSeed;
        this.currentDistrict = currentDistrict;
        this.generatorStageOrdinal = generatorStageOrdinal;
        this.ecoScore = ecoScore;
        this.currentTaskChunkX = currentTaskChunkX;
        this.currentTaskChunkY = currentTaskChunkY;
    }

    public GameStatePacket(double p1X, double p1Y, int p1DirIndex, boolean p1Moving,
                           double p2X, double p2Y, int p2DirIndex, boolean p2Moving,
                           double remainingTime) {
        this(p1X, p1Y, p1DirIndex, p1Moving, p2X, p2Y, p2DirIndex, p2Moving, remainingTime, 0xFF, 0, 0L, 1, 0, 0, 0, 0);
    }
}
