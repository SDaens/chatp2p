package edu.upb.chatupb_v2.bl.server;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;

import java.io.IOException;

public interface ChatTransport {
    void setListener(ChatTransportListener listener);

    void start();

    void stop();

    void connect(String ip);

    void send(ProtocolMessage message) throws IOException;

    boolean isConnected();
}
