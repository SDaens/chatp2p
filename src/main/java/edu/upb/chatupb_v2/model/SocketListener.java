package edu.upb.chatupb_v2.model;

public interface SocketListener {
    void onConnected(SocketClient socketClient, String remoteIp, String detail);

    void onMessage(SocketClient socketClient, String remoteIp, String line);

    void onDisconnected(SocketClient socketClient, String remoteIp, String reason);

    void onError(SocketClient socketClient, String detail, Exception exception);
}
