package pkg.net;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkManager {

    public static final int DEFAULT_PORT = 55555;

    private ServerSocket serverSocket;
    private Socket clientSocket;

    private ObjectOutputStream outStream;
    private ObjectInputStream inStream;

    private volatile boolean running = false;
    private boolean isHost = false;

    private Consumer<InputPacket> onInputPacketReceived;
    private Consumer<GameStatePacket> onGameStatePacketReceived;

    public void startHost(int port, Consumer<InputPacket> onInputReceived) {
        this.isHost = true;
        this.onInputPacketReceived = onInputReceived;
        this.running = true;

        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Host server listening on port " + port);
                clientSocket = serverSocket.accept();
                System.out.println("Client connected from: " + clientSocket.getInetAddress());

                outStream = new ObjectOutputStream(clientSocket.getOutputStream());
                outStream.flush();
                inStream = new ObjectInputStream(clientSocket.getInputStream());

                while (running && !clientSocket.isClosed()) {
                    Object obj = inStream.readObject();
                    if (obj instanceof InputPacket inputPacket && onInputPacketReceived != null) {
                        onInputPacketReceived.accept(inputPacket);
                    }
                }
            } catch (Exception e) {
                System.out.println("Host network loop ended: " + e.getMessage());
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
                System.out.println("Connecting to host " + hostIp + ":" + port + "...");
                clientSocket = new Socket(hostIp, port);
                System.out.println("Connected to host!");

                outStream = new ObjectOutputStream(clientSocket.getOutputStream());
                outStream.flush();
                inStream = new ObjectInputStream(clientSocket.getInputStream());

                while (running && !clientSocket.isClosed()) {
                    Object obj = inStream.readObject();
                    if (obj instanceof GameStatePacket statePacket && onGameStatePacketReceived != null) {
                        onGameStatePacketReceived.accept(statePacket);
                    }
                }
            } catch (Exception e) {
                System.out.println("Client network loop ended: " + e.getMessage());
            }
        }, "LAN-Client-Thread");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    public void sendGameState(GameStatePacket packet) {
        if (isHost && outStream != null) {
            try {
                outStream.reset();
                outStream.writeObject(packet);
                outStream.flush();
            } catch (Exception e) {
                // Ignore transient write errors
            }
        }
    }

    public void sendInputPacket(InputPacket packet) {
        if (!isHost && outStream != null) {
            try {
                outStream.reset();
                outStream.writeObject(packet);
                outStream.flush();
            } catch (Exception e) {
                // Ignore transient write errors
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (outStream != null) outStream.close();
            if (inStream != null) inStream.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
    }

    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }

    public boolean isHost() {
        return isHost;
    }
}
