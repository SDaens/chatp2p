package edu.upb.chatupb_v2.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketClient {
    private final Socket socket;
    private volatile SocketListener listener = new SocketListener() {
        @Override
        public void onConnected(SocketClient socketClient, String remoteIp, String detail) {
        }

        @Override
        public void onMessage(SocketClient socketClient, String remoteIp, String line) {
        }

        @Override
        public void onDisconnected(SocketClient socketClient, String remoteIp, String reason) {
        }

        @Override
        public void onError(SocketClient socketClient, String detail, Exception exception) {
        }
    };
    private volatile Thread readerThread;
    private final AtomicBoolean disconnectedNotified = new AtomicBoolean(false);
    private volatile boolean listening;
    private volatile String remoteIp;

    public SocketClient(Socket socket) {
        this.socket = socket;
        this.remoteIp = socket != null && socket.getInetAddress() != null
                ? socket.getInetAddress().getHostAddress()
                : null;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setListener(SocketListener listener) {
        if (listener == null) {
            return;
        }
        this.listener = listener;
    }

    public void startListening(String connectedDetail) throws IOException {
        if (socket == null) {
            throw new IOException("Socket no inicializado");
        }
        if (listening) {
            return;
        }
        listening = true;
        disconnectedNotified.set(false);
        listener.onConnected(this, remoteIp, connectedDetail);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        readerThread = new Thread(() -> listenLoop(reader), "socket-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void listenLoop(BufferedReader reader) {
        try {
            String line;
            while (listening && (line = reader.readLine()) != null) {
                listener.onMessage(this, remoteIp, line);
            }
        } catch (IOException ex) {
            if (listening) {
                listener.onError(this, "Conexión cerrada: " + ex.getMessage(), ex);
            }
        } finally {
            listening = false;
            notifyDisconnectedOnce("Conexión finalizada");
            try {
                reader.close();
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void send(ProtocolMessage message) throws IOException {
        if (message == null) {
            return;
        }
        String payload = message.serialize() + System.lineSeparator();
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void close() {
        listening = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            notifyDisconnectedOnce("Conexión finalizada");
        }
    }

    private void notifyDisconnectedOnce(String reason) {
        if (disconnectedNotified.compareAndSet(false, true)) {
            listener.onDisconnected(this, remoteIp, reason);
        }
    }
}
