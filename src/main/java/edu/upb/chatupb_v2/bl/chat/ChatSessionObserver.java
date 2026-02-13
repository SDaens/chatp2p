package edu.upb.chatupb_v2.bl.chat;

public interface ChatSessionObserver {
    default void onContactDiscovered(String ip) {
    }

    default void onConnectionStateChanged(String remoteIp, boolean connected, String detail) {
    }

    default void onChatMessage(String text, boolean self) {
    }

    default void onSystemMessage(String text) {
    }

    default void onIncomingRequestWithoutObservers(ConnectionRequest request) {
    }
}
