package pkg.net;

import java.io.Serializable;

public class InputPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean interact;

    public InputPacket() {}

    public InputPacket(boolean up, boolean down, boolean left, boolean right, boolean interact) {
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.interact = interact;
    }

    public InputPacket(boolean up, boolean down, boolean left, boolean right) {
        this(up, down, left, right, false);
    }
}
