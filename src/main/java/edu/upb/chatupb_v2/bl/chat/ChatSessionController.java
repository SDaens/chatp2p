package edu.upb.chatupb_v2.bl.chat;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;
import edu.upb.chatupb_v2.bl.server.ChatTransport;
import edu.upb.chatupb_v2.bl.server.ChatTransportListener;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ChatSessionController implements ChatTransportListener {

    private final ChatTransport transport;
    private final String localUserId = UUID.randomUUID().toString();
    private final String localName = "ChatUPB-" + localUserId.substring(0, 8);
    private final AtomicLong messageSeq = new AtomicLong(1);
    private final CopyOnWriteArrayList<ChatSessionObserver> sessionObservers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionRequestObserver> requestObservers = new CopyOnWriteArrayList<>();

    public ChatSessionController(ChatTransport transport) {
        this.transport = transport;
        this.transport.setListener(this);
    }

    public void start() {
        transport.start();
    }

    public void stop() {
        transport.stop();
    }

    public void connect(String ip) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            publishSystemMessage("Ingresa una IP remota válida.");
            return;
        }
        publishContactDiscovered(cleanIp);
        transport.connect(cleanIp);
    }

    public void sendChatMessage(String text) {
        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isEmpty()) {
            return;
        }
        String messageId = localUserId + "-" + messageSeq.getAndIncrement();
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.CHAT, localUserId, messageId, cleanText));
        publishChatMessage(cleanText, true);
    }

    public void addSessionObserver(ChatSessionObserver observer) {
        if (observer != null) {
            sessionObservers.addIfAbsent(observer);
        }
    }

    public void removeSessionObserver(ChatSessionObserver observer) {
        if (observer != null) {
            sessionObservers.remove(observer);
        }
    }

    public void addConnectionRequestObserver(ConnectionRequestObserver observer) {
        if (observer != null) {
            requestObservers.addIfAbsent(observer);
        }
    }

    public void removeConnectionRequestObserver(ConnectionRequestObserver observer) {
        if (observer != null) {
            requestObservers.remove(observer);
        }
    }

    public boolean hasConnectionRequestObservers() {
        return !requestObservers.isEmpty();
    }

    @Override
    public void onConnected(String remoteIp, String contextMessage) {
        publishContactDiscovered(remoteIp);
        publishConnectionState(remoteIp, true, contextMessage);
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REQUEST, localUserId, localName));
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_BROADCAST, localUserId));
    }

    @Override
    public void onDisconnected(String remoteIp, String reason) {
        publishConnectionState(remoteIp, false, reason);
    }

    @Override
    public void onMessageReceived(String line) {
        handleProtocolLine(line);
    }

    @Override
    public void onError(String message, Exception exception) {
        publishSystemMessage(message);
    }

    private void handleProtocolLine(String line) {
        try {
            ProtocolMessage msg = ProtocolMessage.parse(line);
            switch (msg.code()) {
                case REQUEST -> handleRequest(msg.param(0), msg.param(1));
                case ACCEPT -> publishSystemMessage("Conectado con " + msg.param(1));
                case REJECT -> publishSystemMessage("La contraparte rechazó la solicitud.");
                case HELLO_BROADCAST -> sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_ACCEPT, localUserId));
                case HELLO_ACCEPT -> publishSystemMessage("Handshake de hello confirmado.");
                case HELLO_REJECT -> publishSystemMessage("Hello rechazado por contraparte.");
                case CHAT -> {
                    publishChatMessage(msg.param(2), false);
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.RECEIPT, msg.param(1)));
                }
                case RECEIPT -> publishSystemMessage("Mensaje confirmado: " + msg.param(0));
                case DELETE -> publishSystemMessage("Solicitud eliminar mensaje: " + msg.param(0));
                case BUZZ -> publishSystemMessage("Zumbido recibido: " + msg.param(0));
                case PIN -> publishSystemMessage("Solicitud fijar mensaje: " + msg.param(0));
                case SEEN -> publishSystemMessage("Visto por " + msg.param(0) + ": " + msg.param(2));
                case THEME -> publishSystemMessage("Cambio de tema recibido: " + msg.param(1));
            }
        } catch (IllegalArgumentException ex) {
            publishSystemMessage("Fragmento no reconocido: " + line);
        }
    }

    private void handleRequest(String requesterId, String requesterName) {
        publishSystemMessage("Solicitud recibida de " + requesterName);

        ConnectionRequest request = new ConnectionRequest(
                requesterId,
                requesterName,
                () -> {
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, localName));
                    publishSystemMessage("Aceptaste la solicitud de " + requesterName + ".");
                },
                () -> {
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REJECT));
                    publishSystemMessage("Rechazaste la solicitud de " + requesterName + ".");
                }
        );

        if (requestObservers.isEmpty()) {
            for (ChatSessionObserver observer : sessionObservers) {
                observer.onIncomingRequestWithoutObservers(request);
            }
            return;
        }

        for (ConnectionRequestObserver observer : requestObservers) {
            observer.onIncomingConnectionRequest(request);
        }
    }

    private void sendProtocol(ProtocolMessage message) {
        if (!transport.isConnected()) {
            publishSystemMessage("Sin conexión activa. Usa Conectar o espera conexión entrante.");
            return;
        }
        try {
            transport.send(message);
        } catch (IOException ex) {
            publishSystemMessage("Error enviando protocolo " + message.code().value() + ": " + ex.getMessage());
        }
    }

    private void publishContactDiscovered(String ip) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onContactDiscovered(ip);
        }
    }

    private void publishConnectionState(String remoteIp, boolean connected, String detail) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onConnectionStateChanged(remoteIp, connected, detail);
        }
    }

    private void publishChatMessage(String text, boolean self) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onChatMessage(text, self);
        }
    }

    private void publishSystemMessage(String text) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onSystemMessage(text);
        }
    }
}
