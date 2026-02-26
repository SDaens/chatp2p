package edu.upb.chatupb_v2.model;

import edu.upb.chatupb_v2.model.ProtocolMessage;

import java.io.IOException;

public interface ChatTransport {
    void setListener(ChatTransportListener listener);

    void start();

    void stop();

    void connect(String ip);

    void disconnect();

    void send(ProtocolMessage message) throws IOException;

    boolean isConnected();
}
