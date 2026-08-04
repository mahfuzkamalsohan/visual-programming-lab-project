package pkg.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class NetworkManager {

    public static final int DEFAULT_PORT = 55555;

    private DatagramSocket socket;
    private InetAddress remoteAddress;
    private int remotePort = -1;

    private volatile boolean running = false;
    private boolean isHost = false;

    private Consumer<InputPacket> onInputPacketReceived;
    private Consumer<GameStatePacket> onGameStatePacketReceived;

    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LAN-Send-Thread");
        t.setDaemon(true);
        return t;
    });

    public void startHost(int port, Consumer<InputPacket> onInputReceived) {
        this.isHost = true;
        this.onInputPacketReceived = onInputReceived;
        this.running = true;

        Thread serverThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                System.out.println("Host UDP server listening on port " + port);

                byte[] buf = new byte[1024];
                while (running && socket != null && !socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    this.remoteAddress = packet.getAddress();
                    this.remotePort = packet.getPort();

                    String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (text.startsWith("INP:")) {
                        InputPacket input = parseInputPacket(text.substring(4));
                        if (input != null && onInputPacketReceived != null) {
                            onInputPacketReceived.accept(input);
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.out.println("Host network loop ended: " + e.getMessage());
                }
            }
        }, "LAN-Host-Thread");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void startClient(String hostIp, int port, Consumer<GameStatePacket> onStateReceived) {
        this.isHost = false;
        this.onGameStatePacketReceived = onStateReceived;
        this.running = true;

        Thread clientThread = new Thread(() -> {
            try {
                System.out.println("Connecting UDP client to host " + hostIp + ":" + port + "...");
                socket = new DatagramSocket();
                this.remoteAddress = InetAddress.getByName(hostIp);
                this.remotePort = port;
                System.out.println("UDP Client target set to " + hostIp + ":" + port);

                // Initial handshake packet so host knows client port
                sendInputPacket(new InputPacket(false, false, false, false, false));

                byte[] buf = new byte[1024];
                while (running && socket != null && !socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (text.startsWith("STATE:")) {
                        GameStatePacket state = parseGameStatePacket(text.substring(6));
                        if (state != null && onGameStatePacketReceived != null) {
                            onGameStatePacketReceived.accept(state);
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.out.println("Client network loop ended: " + e.getMessage());
                }
            }
        }, "LAN-Client-Thread");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    public void sendGameState(GameStatePacket state) {
        if (!isHost || socket == null || socket.isClosed() || remoteAddress == null || remotePort <= 0) {
            return;
        }
        String payload = String.format(Locale.US,
                "STATE:%.2f,%.2f,%d,%d,%.2f,%.2f,%d,%d,%.2f,%d,%d",
                state.p1X, state.p1Y, state.p1DirIndex, state.p1Moving ? 1 : 0,
                state.p2X, state.p2Y, state.p2DirIndex, state.p2Moving ? 1 : 0,
                state.remainingTime, state.trashMask, state.collectedTrash);
        sendRawPayload(payload);
    }

    public void sendInputPacket(InputPacket input) {
        if (isHost || socket == null || socket.isClosed() || remoteAddress == null || remotePort <= 0) {
            return;
        }
        String payload = String.format(Locale.US,
                "INP:%d,%d,%d,%d,%d",
                input.up ? 1 : 0, input.down ? 1 : 0, input.left ? 1 : 0, input.right ? 1 : 0, input.interact ? 1 : 0);
        sendRawPayload(payload);
    }

    private void sendRawPayload(String payload) {
        if (socket == null || socket.isClosed() || remoteAddress == null || remotePort <= 0) {
            return;
        }
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, remoteAddress, remotePort);
        sendExecutor.execute(() -> {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.send(packet);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private InputPacket parseInputPacket(String str) {
        try {
            String[] parts = str.split(",");
            if (parts.length >= 5) {
                boolean up = "1".equals(parts[0]);
                boolean down = "1".equals(parts[1]);
                boolean left = "1".equals(parts[2]);
                boolean right = "1".equals(parts[3]);
                boolean interact = "1".equals(parts[4]);
                return new InputPacket(up, down, left, right, interact);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private GameStatePacket parseGameStatePacket(String str) {
        try {
            String[] parts = str.split(",");
            if (parts.length >= 9) {
                double p1X = Double.parseDouble(parts[0]);
                double p1Y = Double.parseDouble(parts[1]);
                int p1Dir = Integer.parseInt(parts[2]);
                boolean p1Moving = "1".equals(parts[3]);

                double p2X = Double.parseDouble(parts[4]);
                double p2Y = Double.parseDouble(parts[5]);
                int p2Dir = Integer.parseInt(parts[6]);
                boolean p2Moving = "1".equals(parts[7]);

                double time = Double.parseDouble(parts[8]);
                int mask = parts.length >= 10 ? Integer.parseInt(parts[9]) : 0xFF;
                int collected = parts.length >= 11 ? Integer.parseInt(parts[10]) : 0;

                return new GameStatePacket(p1X, p1Y, p1Dir, p1Moving, p2X, p2Y, p2Dir, p2Moving, time, mask, collected);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        try {
            sendExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && remoteAddress != null;
    }

    public boolean isHost() {
        return isHost;
    }
}
