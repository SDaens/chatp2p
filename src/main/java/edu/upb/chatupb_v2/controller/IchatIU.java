package edu.upb.chatupb_v2.controller;

public interface IchatIU {
    void onContactDiscovered(String ip);

    void onConnectionStateChanged(String remoteIp, boolean connected, String detail);

    void onChatMessage(String text, boolean self, String sentAt, String senderLabel);

    void onSystemMessage(String text);

    void onIncomingRequest(ConnectionRequest request);
}
