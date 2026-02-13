package edu.upb.chatupb_v2.bl.chat;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConnectionRequest {
    private final String requesterId;
    private final String requesterName;
    private final AtomicBoolean handled = new AtomicBoolean(false);
    private final Runnable acceptAction;
    private final Runnable rejectAction;

    public ConnectionRequest(String requesterId, String requesterName, Runnable acceptAction, Runnable rejectAction) {
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.acceptAction = Objects.requireNonNull(acceptAction, "acceptAction");
        this.rejectAction = Objects.requireNonNull(rejectAction, "rejectAction");
    }

    public String requesterId() {
        return requesterId;
    }

    public String requesterName() {
        return requesterName;
    }

    public boolean accept() {
        if (!handled.compareAndSet(false, true)) {
            return false;
        }
        acceptAction.run();
        return true;
    }

    public boolean reject() {
        if (!handled.compareAndSet(false, true)) {
            return false;
        }
        rejectAction.run();
        return true;
    }
}
