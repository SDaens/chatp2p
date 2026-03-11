package edu.upb.chatupb_v2.controller;

import edu.upb.chatupb_v2.model.ChatMessage;
import edu.upb.chatupb_v2.model.ChatMessageDao;
import edu.upb.chatupb_v2.model.ChatTransport;
import edu.upb.chatupb_v2.model.ChatTransportListener;
import edu.upb.chatupb_v2.model.Contact;
import edu.upb.chatupb_v2.model.ContactDao;
import edu.upb.chatupb_v2.model.ProtocolMessage;
import edu.upb.chatupb_v2.model.SocketChatTransport;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ChatSessionController extends ChatTransportListener {

    private static final int DEFAULT_PORT = 1900;
    private static final String DEFAULT_LOCAL_NAME = "Usuario";

    private final ChatTransport transport;
    private final ContactDao contactDao = new ContactDao();
    private final ChatMessageDao chatMessageDao = new ChatMessageDao();
    private final String localUserId = UUID.randomUUID().toString();
    private volatile String localName = DEFAULT_LOCAL_NAME;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private final AtomicLong messageSeq = new AtomicLong(1);
    private final CopyOnWriteArrayList<ChatSessionObserver> sessionObservers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionRequestObserver> requestObservers = new CopyOnWriteArrayList<>();
    private IchatIU iChatIU;

    private volatile boolean invitationAccepted;
    private volatile String activeRemoteIp;
    private volatile String lastOutboundIp;

    public ChatSessionController() {
        this(new SocketChatTransport(DEFAULT_PORT));
    }

    public ChatSessionController(IchatIU iChatIU) {
        this(new SocketChatTransport(DEFAULT_PORT));
        this.iChatIU = iChatIU;
    }

    public ChatSessionController(ChatTransport transport) {
        this.transport = transport;
        this.transport.setListener(this);
    }

    public void setIChatIU(IchatIU iChatIU) {
        this.iChatIU = iChatIU;
    }

    public void setLocalName(String localName) {
        if (localName == null || localName.isBlank()) {
            return;
        }
        this.localName = localName.trim();
    }

    public void start() {
        loadSavedContacts();
        transport.start();
        publishSystemMessage("Escuchando en puerto " + DEFAULT_PORT + ". Agrega contactos para conectar.");
    }

    public void stop() {
        transport.stop();
    }

    public void unload() {
        stop();
        invitationAccepted = false;
        activeRemoteIp = null;
        sessionObservers.clear();
        requestObservers.clear();
        iChatIU = null;
    }

    public void connect(String ip) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            publishSystemMessage("Ingresa una IP remota válida.");
            return;
        }
        lastOutboundIp = cleanIp;
        saveContact(cleanIp);
        publishContactDiscovered(cleanIp);
        transport.connect(cleanIp);
    }

    public boolean sendChatMessage(String text) {
        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isEmpty()) {
            return false;
        }
        if (!invitationAccepted) {
            publishSystemMessage("La conversación sigue pendiente. Espera a que acepten la invitación.");
        }

        String messageId = localUserId + "-" + messageSeq.getAndIncrement();
        long sentAtMillis = System.currentTimeMillis();
        String senderLabel = resolveLocalName();
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.CHAT, localUserId, messageId, cleanText));
        publishChatMessage(activeRemoteIp, cleanText, true, formatEpochMillis(sentAtMillis), senderLabel, messageId);
        persistChatMessage(activeRemoteIp, messageId, cleanText, true, senderLabel, sentAtMillis);
        return true;
    }

    public void disconnect() {
        transport.disconnect();
    }

    public boolean shareContact(Contact contactToShare) {
        if (!transport.isConnected()) {
            publishSystemMessage("No hay conexión activa para compartir contacto.");
            return false;
        }
        if (contactToShare == null) {
            publishSystemMessage("Debes seleccionar un contacto para compartir.");
            return false;
        }

        String sharedIp = contactToShare.getIp() == null ? "" : contactToShare.getIp().trim();
        if (sharedIp.isEmpty()) {
            publishSystemMessage("El contacto seleccionado no tiene IP valida.");
            return false;
        }
        String sharedId = contactToShare.getCode() == null ? "" : contactToShare.getCode().trim();
        if (sharedId.isEmpty()) {
            sharedId = sharedIp;
        }
        String sharedName = contactToShare.getName() == null ? "" : contactToShare.getName().trim();
        if (sharedName.isEmpty()) {
            sharedName = sharedIp;
        }

        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.SHARE, sharedId, sharedName, sharedIp));
        publishSystemMessage("Contacto compartido: " + sharedName + " (" + sharedIp + ")");
        return true;
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

    @Override
    public void onConnected(String remoteIp, String contextMessage) {
        activeRemoteIp = remoteIp;
        invitationAccepted = false;
        boolean knownContact = isKnownContactIp(remoteIp);
        boolean initiated = contextMessage != null && contextMessage.startsWith("Conectado a ");
        if (!initiated) {
            initiated = remoteIp != null && remoteIp.equals(lastOutboundIp);
        }
        lastOutboundIp = null;
        if (initiated) {
            saveContact(remoteIp);
            publishContactDiscovered(remoteIp);
        } else if (knownContact) {
            publishContactDiscovered(remoteIp);
        }
        publishConnectionState(remoteIp, true, contextMessage);
        if (initiated) {
            if (knownContact) {
                invitationAccepted = true;
                sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_BROADCAST, localUserId));
            }
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REQUEST, localUserId, resolveLocalName()));
        }
    }

    @Override
    public void onDisconnected(String remoteIp, String reason) {
        invitationAccepted = false;
        activeRemoteIp = null;
        lastOutboundIp = null;
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
                case ACCEPT -> {
                    invitationAccepted = true;
                    saveContact(activeRemoteIp, msg.param(1));
                    publishContactDiscovered(activeRemoteIp);
                    publishSystemMessage("Conectado con " + msg.param(1));
                }
                case REJECT -> {
                    invitationAccepted = false;
                    publishSystemMessage("La contraparte rechazó la solicitud.");
                    transport.disconnect();
                }
                case HELLO_BROADCAST -> sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_ACCEPT, localUserId));
                case HELLO_ACCEPT -> publishSystemMessage("Handshake de hello confirmado.");
                case HELLO_REJECT -> publishSystemMessage("Hello rechazado por contraparte.");
                case CHAT -> {
                    long sentAtMillis = System.currentTimeMillis();
                    String sentAt = formatEpochMillis(sentAtMillis);
                    String remoteLabel = resolveRemoteLabel();
                    if (!invitationAccepted) {
                        invitationAccepted = true;
                        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, resolveLocalName()));
                        publishSystemMessage("Conectado con " + remoteLabel);
                    }
                    publishChatMessage(activeRemoteIp, msg.param(2), false, sentAt, remoteLabel, msg.param(1));
                    persistChatMessage(activeRemoteIp, msg.param(1), msg.param(2), false, remoteLabel, sentAtMillis);
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.RECEIPT, msg.param(1)));
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.SEEN, localUserId, msg.param(1), msg.param(2)));
                }
                case RECEIPT -> {
                    publishSystemMessage("Mensaje confirmado: " + msg.param(0));
                    publishMessageSeen(activeRemoteIp, msg.param(0));
                }
                case DELETE -> handleDeleteMessage(activeRemoteIp, msg.param(0));
                case BUZZ -> publishBuzz(activeRemoteIp);
                case PIN -> publishSystemMessage("Solicitud fijar mensaje: " + msg.param(0));
                case SEEN -> publishMessageSeen(activeRemoteIp, msg.param(1));
                case THEME -> publishSystemMessage("Cambio de tema recibido: " + msg.param(1));
                case OUTLINE -> publishSystemMessage("Estoy offline. " + msg.param(0));
                case SHARE -> handleSharedContact(msg.param(0), msg.param(1), msg.param(2));
            }
        } catch (IllegalArgumentException ex) {
            publishSystemMessage("Fragmento no reconocido: " + line);
        }
    }

    private void handleRequest(String requesterId, String requesterName) {
        publishSystemMessage("Solicitud recibida de " + requesterName);
        saveContact(activeRemoteIp, requesterName);
        publishContactDiscovered(activeRemoteIp);

        if (isKnownContactIp(activeRemoteIp)) {
            invitationAccepted = true;
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, resolveLocalName()));
            publishSystemMessage("Solicitud autoaceptada para contacto guardado: " + requesterName + ".");
            return;
        }

        ConnectionRequest request = new ConnectionRequest(
                activeRemoteIp,
                requesterId,
                requesterName,
                () -> {
                    invitationAccepted = true;
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, resolveLocalName()));
                    publishSystemMessage("Aceptaste la solicitud de " + requesterName + ".");
                },
                () -> {
                    invitationAccepted = false;
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

    private void saveContact(String ip) {
        saveContact(ip, ip);
    }

    private void saveContact(String ip, String name) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            cleanName = cleanIp;
        }
        try {
            Contact existing = contactDao.findByIp(cleanIp);
            String finalName = cleanName;
            if (existing != null && existing.getName() != null && !existing.getName().isBlank()) {
                if (finalName.equalsIgnoreCase(cleanIp) || finalName.isBlank()) {
                    finalName = existing.getName().trim();
                }
            }
            Contact contact = Contact.builder().code(cleanIp).name(finalName).ip(cleanIp).build();
            contactDao.saveOrUpdateByIp(contact);
        } catch (Exception ex) {
            publishSystemMessage("no se pudo guardar el contacto " + cleanIp + ": " + ex.getMessage());
        }
    }

    private void handleSharedContact(String userId, String name, String ip) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            publishSystemMessage("contacto sin ip");
            return;
        }
        try {
            Contact contact = Contact.builder()
                    .code(userId)
                    .name(name)
                    .ip(cleanIp)
                    .build();
            contactDao.saveOrUpdateByIp(contact);
            publishContactDiscovered(cleanIp);
            publishSystemMessage("Contacto recibido: " + name + " (" + cleanIp + ")");
        } catch (Exception ex) {
            publishSystemMessage("no se guardó: " + ex.getMessage());
        }
    }

    private void loadSavedContacts() {
        try {
            for (Contact contact : contactDao.findAll()) {
                publishContactDiscovered(contact.getIp());
            }
        } catch (Exception ex) {
            publishSystemMessage("no se pudieron cargar contactos guardados: " + ex.getMessage());
        }
    }

    public List<ChatMessage> getChatHistory(String contactIp) {
        try {
            return chatMessageDao.findByContactIp(contactIp);
        } catch (SQLException ex) {
            publishSystemMessage("no se pudo cargar el historial de chat: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    private String formatEpochMillis(long millis) {
        if (millis <= 0) {
            return LocalTime.now().format(timeFmt);
        }
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFmt);
    }

    private String formatEpochMillis(String rawMillis) {
        if (rawMillis == null || rawMillis.isBlank()) {
            return LocalTime.now().format(timeFmt);
        }
        return formatEpochMillis(parseEpochMillis(rawMillis));
    }

    private long parseEpochMillis(String rawMillis) {
        try {
            return Long.parseLong(rawMillis.trim());
        } catch (Exception ex) {
            return System.currentTimeMillis();
        }
    }

    private String resolveRemoteLabel() {
        if (activeRemoteIp == null || activeRemoteIp.isBlank()) {
            return "Remoto";
        }
        return activeRemoteIp;
    }

    private String resolveLocalName() {
        if (localName == null || localName.isBlank()) {
            return DEFAULT_LOCAL_NAME;
        }
        return localName.trim();
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

    public void sendSeen(String messageId, String messageText) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        String safeText = messageText == null ? "" : messageText;
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.SEEN, localUserId, messageId, safeText));
    }

    public void sendBuzz(String contactIp) {
        if (contactIp == null || contactIp.isBlank()) {
            return;
        }
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.BUZZ, localUserId));
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

    private boolean isKnownContactIp(String remoteIp) {
        if (remoteIp == null || remoteIp.isBlank()) {
            return false;
        }
        try {
            return contactDao.existByIp(remoteIp);
        } catch (Exception ex) {
            publishSystemMessage("no se pudo validar contacto guardado: " + ex.getMessage());
            return false;
        }
    }

    private void persistChatMessage(String contactIp, String text, boolean selfSent, String senderLabel, long sentAtMillis) {
        try {
            chatMessageDao.save(contactIp, null, text, selfSent, senderLabel, sentAtMillis);
        } catch (SQLException ex) {
            publishSystemMessage("no se pudo persistir mensaje: " + ex.getMessage());
        }
    }

    private void persistChatMessage(String contactIp, String messageId, String text, boolean selfSent, String senderLabel, long sentAtMillis) {
        try {
            chatMessageDao.save(contactIp, messageId, text, selfSent, senderLabel, sentAtMillis);
        } catch (SQLException ex) {
            publishSystemMessage("no se pudo persistir mensaje: " + ex.getMessage());
        }
    }

    public void deleteMessage(String contactIp, String messageId) {
        if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
            return;
        }
        deleteMessageLocal(contactIp, messageId);
        publishMessageDeleted(contactIp, messageId);
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.DELETE, messageId));
    }

    private void handleDeleteMessage(String contactIp, String messageId) {
        deleteMessageLocal(contactIp, messageId);
        publishMessageDeleted(contactIp, messageId);
    }

    private void deleteMessageLocal(String contactIp, String messageId) {
        try {
            chatMessageDao.deleteByMessageId(contactIp, messageId);
        } catch (SQLException ex) {
            publishSystemMessage("no se pudo borrar mensaje: " + ex.getMessage());
        }
    }

    private void publishChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel, String messageId) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onChatMessage(contactIp, text, self, sentAt, senderLabel, messageId);
        }
    }

    private void publishMessageSeen(String contactIp, String messageId) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onMessageSeen(contactIp, messageId);
        }
    }

    private void publishMessageDeleted(String contactIp, String messageId) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onMessageDeleted(contactIp, messageId);
        }
    }

    private void publishBuzz(String contactIp) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onBuzz(contactIp);
        }
    }

    private void publishSystemMessage(String text) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onSystemMessage(text);
        }
    }
}
