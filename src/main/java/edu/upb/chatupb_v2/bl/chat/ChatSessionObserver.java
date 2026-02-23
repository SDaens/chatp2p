package edu.upb.chatupb_v2.bl.chat;

public abstract class ChatSessionObserver {
    public void onContactDiscovered(String ip) {
    }

    public void onConnectionStateChanged(String remoteIp, boolean connected, String detail) {
    }

    public void onChatMessage(String text, boolean self) {
    }

    public void onSystemMessage(String text) {
    }

    public void onIncomingRequestWithoutObservers(ConnectionRequest request) {

    }
}
