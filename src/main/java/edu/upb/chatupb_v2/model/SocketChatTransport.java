package edu.upb.chatupb_v2.model;

import edu.upb.chatupb_v2.model.ProtocolMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketChatTransport implements ChatTransport {

    private final int port;
    private final Mediador mediador = Mediador.getInstance();
    private final Object connectionLock = new Object();

    private volatile ChatTransportListener listener = new ChatTransportListener() {
    };
    private volatile boolean running;
    private volatile ServerSocket listenerSocket;
    private volatile Thread listenerThread;

    private volatile SocketClient peerClient;

    public SocketChatTransport(int port) {
        this.port = port;
    }

    @Override
    public void setListener(ChatTransportListener listener) {
        if (listener == null) {
            this.listener = new ChatTransportListener() {
            };
            mediador.setTransportListener(this.listener);
            return;
        }
        this.listener = listener;
        mediador.setTransportListener(listener);
    }

    @Override
    public void start() {
        running = true;
        listenerThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                listenerSocket = ss;
                while (running) {
                    Socket accepted = ss.accept();
                    attachPeerSocket(accepted, "Conexión entrante");
                }
            } catch (IOException ex) {
                if (running) {
                    mediador.publishError("No se pudo abrir listener en puerto " + port + ": " + ex.getMessage(), ex);
                }
            }
        }, "transport-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @Override
    public void stop() {
        running = false;
        closeListener();
        synchronized (connectionLock) {
            closePeerLocked();
        }
    }

    @Override
    public void connect(String ip) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            mediador.publishError("Ingresa una IP remota válida.", null);
            return;
        }

        Thread connector = new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(cleanIp, port), 2500);
                attachPeerSocket(socket, "Conectado a " + cleanIp);
            } catch (IOException ex) {
                mediador.publishError("No se pudo conectar a " + cleanIp + ":" + port + " - " + ex.getMessage(), ex);
            }
        }, "transport-connector");
        connector.setDaemon(true);
        connector.start();
    }

    @Override
    public void disconnect() {
        synchronized (connectionLock) {
            closePeerLocked();
        }
    }

    @Override
    public void send(ProtocolMessage message) throws IOException {
        if (message == null) {
            return;
        }
        synchronized (connectionLock) {
            if (peerClient == null) {
                throw new IOException("Sin conexión activa");
            }
            peerClient.send(message);
        }
    }

    @Override
    public boolean isConnected() {
        return peerClient != null && peerClient.isConnected();
    }

    private void attachPeerSocket(Socket socket, String contextMessage) throws IOException {
        SocketClient client = new SocketClient(socket);
        client.setListener(mediador);

        synchronized (connectionLock) {
            closePeerLocked();
            peerClient = client;
        }
        client.startListening(contextMessage);
    }

    private void closeListener() {
        ServerSocket ss = listenerSocket;
        listenerSocket = null;
        if (ss != null && !ss.isClosed()) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closePeerLocked() {
        if (peerClient != null) {
            peerClient.close();
        }
        peerClient = null;
    }
}
