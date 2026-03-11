package edu.upb.chatupb_v2.controller;

public abstract class ChatSessionObserver {
    public void onContactDiscovered(String ip) {
    }

    public void onConnectionStateChanged(String remoteIp, boolean connected, String detail) {
    }

    public void onChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel) {
    }

    public void onChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel, String messageId) {
        onChatMessage(contactIp, text, self, sentAt, senderLabel);
    }

    public void onSystemMessage(String text) {
    }

    public void onIncomingRequestWithoutObservers(ConnectionRequest request) {

    }

    public void onMessageSeen(String contactIp, String messageId) {
    }

    public void onMessageDeleted(String contactIp, String messageId) {
    }

    public void onBuzz(String contactIp) {
    }
}
