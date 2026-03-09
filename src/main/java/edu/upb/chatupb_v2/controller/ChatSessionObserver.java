package edu.upb.chatupb_v2.controller;

public abstract class ChatSessionObserver {
    public void onContactDiscovered(String ip) {
    }

    public void onConnectionStateChanged(String remoteIp, boolean connected, String detail) {
    }

    public void onChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel) {
    }

    public void onChatMessage(String contactIp, String messageId, String text, boolean self, String sentAt, String senderLabel) {
        onChatMessage(contactIp, text, self, sentAt, senderLabel);
    }

    public void onOutgoingMessageStatus(String contactIp, String messageId, String status) {
    }

    public void onSystemMessage(String text) {
    }

    public void onIncomingRequestWithoutObservers(ConnectionRequest request) {

    }
}
