package edu.upb.chatupb_v2.model;

import edu.upb.chatupb_v2.model.ProtocolMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketChatTransport implements ChatTransport {

    private final int port;
    private final Mediador mediador = Mediador.getInstance();
    private final Object connectionLock = new Object();

    private volatile ChatTransportListener listener = new ChatTransportListener() {
    };
    private volatile boolean running;
    private volatile ServerSocket listenerSocket;
    private volatile Thread listenerThread;

    private volatile Socket peerSocket;
    private volatile BufferedReader peerReader;
    private volatile OutputStream peerOutput;
    private volatile Thread peerReaderThread;

    public SocketChatTransport(int port) {
        this.port = port;
    }

    @Override
    public void setListener(ChatTransportListener listener) {
        if (listener == null) {
            this.listener = new ChatTransportListener() {
            };
            return;
        }
        this.listener = listener;
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

        String payload = message.serialize() + System.lineSeparator();
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        synchronized (connectionLock) {
            if (peerOutput == null) {
                throw new IOException("Sin conexión activa");
            }
            peerOutput.write(bytes);
            peerOutput.flush();
        }
    }

    @Override
    public boolean isConnected() {
        return peerSocket != null && peerSocket.isConnected() && !peerSocket.isClosed();
    }

    private void attachPeerSocket(Socket socket, String contextMessage) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream output = socket.getOutputStream();

        synchronized (connectionLock) {
            closePeerLocked();
            peerSocket = socket;
            peerReader = reader;
            peerOutput = output;
        }

        String remoteIp = socket.getInetAddress().getHostAddress();
        mediador.registrarCliente(remoteIp, new SocketClient(socket));
        mediador.publishConnected(remoteIp, contextMessage);

        peerReaderThread = new Thread(() -> listenPeer(socket, reader), "transport-peer-reader");
        peerReaderThread.setDaemon(true);
        peerReaderThread.start();
    }

    private void listenPeer(Socket socket, BufferedReader reader) {
        String remoteIp = socket.getInetAddress().getHostAddress();
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                mediador.publishIncomingMessage(remoteIp, line);
            }
        } catch (IOException ex) {
            if (running) {
                mediador.publishError("Conexión cerrada: " + ex.getMessage(), ex);
            }
        } finally {
            synchronized (connectionLock) {
                if (socket == peerSocket) {
                    closePeerLocked();
                    mediador.publishDisconnected(remoteIp, "Conexión finalizada");
                }
            }
        }
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
        String remoteIp = peerSocket != null ? peerSocket.getInetAddress().getHostAddress() : null;

        try {
            if (peerReader != null) {
                peerReader.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (peerOutput != null) {
                peerOutput.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (peerSocket != null) {
                peerSocket.close();
            }
        } catch (IOException ignored) {
        }

        peerReader = null;
        peerOutput = null;
        peerSocket = null;

        if (remoteIp != null && !remoteIp.isBlank()) {
            mediador.eliminarCliente(remoteIp);
        }
    }
}
