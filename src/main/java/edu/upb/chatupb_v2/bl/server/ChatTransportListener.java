package edu.upb.chatupb_v2.bl.server;

public interface ChatTransportListener {
    default void onConnected(String remoteIp, String contextMessage) {
    }

    default void onDisconnected(String remoteIp, String reason) {
    }

    default void onMessageReceived(String line) {
    }

    default void onError(String message, Exception exception) {
    }
}
