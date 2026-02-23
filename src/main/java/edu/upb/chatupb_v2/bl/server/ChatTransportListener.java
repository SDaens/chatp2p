package edu.upb.chatupb_v2.bl.server;

public abstract class ChatTransportListener {
    public void onConnected(String remoteIp, String contextMessage) {
    }

    public void onDisconnected(String remoteIp, String reason) {
    }

    public void onMessageReceived(String line) {
    }

    public void onError(String message, Exception exception){

    }
}
