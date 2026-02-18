package edu.upb.chatupb_v2.bl.server;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketClient {
    private final Socket socket;

    public SocketClient(Socket socket) {
        this.socket = socket;
    }

    public Socket getSocket() {
        return socket;
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
}
