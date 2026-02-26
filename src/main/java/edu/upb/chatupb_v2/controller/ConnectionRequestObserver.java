package edu.upb.chatupb_v2.controller;

public abstract class ConnectionRequestObserver {
    public abstract void onIncomingConnectionRequest(ConnectionRequest request);
}
