package edu.upb.chatupb_v2.controller;

import edu.upb.chatupb_v2.model.BlacklistDao;
import edu.upb.chatupb_v2.model.AppProfileDao;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ChatSessionController extends ChatTransportListener {

    private static final int DEFAULT_PORT = 1900;
    private static final int MAX_LOCAL_NAME_LENGTH = 60;

    private final ChatTransport transport;
    private final AppProfileDao appProfileDao = new AppProfileDao();
    private final BlacklistDao blacklistDao = new BlacklistDao();
    private final ContactDao contactDao = new ContactDao();
    private final ChatMessageDao chatMessageDao = new ChatMessageDao();
    private final String localUserId = UUID.randomUUID().toString();
    private volatile String localName;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private final AtomicLong messageSeq = new AtomicLong(1);
    private final CopyOnWriteArrayList<ChatSessionObserver> sessionObservers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionRequestObserver> requestObservers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Long> pendingReceipts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> outgoingMessageContacts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> pendingSeenByContact = new ConcurrentHashMap<>();
    private final Set<String> receivedMessageKeys = ConcurrentHashMap.newKeySet();
    private IchatIU iChatIU;

    private volatile boolean helloAccepted;
    private volatile boolean invitationRequestSent;
    private volatile boolean invitationAccepted;
    private volatile String activeRemoteIp;
    private volatile String visibleConversationIp;

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
        this.localName = loadLocalName();
    }

    public boolean hasLocalDisplayName() {
        return localName != null && !localName.isBlank();
    }

    public String getLocalDisplayName() {
        return effectiveLocalName();
    }

    public boolean setLocalDisplayName(String displayName) {
        String cleanName = displayName == null ? "" : displayName.trim();
        if (cleanName.isEmpty()) {
            return false;
        }
        if (cleanName.length() > MAX_LOCAL_NAME_LENGTH) {
            publishSystemMessage("El nombre local no puede superar " + MAX_LOCAL_NAME_LENGTH + " caracteres.");
            return false;
        }
        try {
            appProfileDao.saveDisplayName(cleanName);
            localName = cleanName;
            return true;
        } catch (Exception ex) {
            publishSystemMessage("no se pudo guardar el nombre local: " + ex.getMessage());
            return false;
        }
    }

    public void setIChatIU(IchatIU iChatIU) {
        this.iChatIU = iChatIU;
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
        saveContact(cleanIp);
        publishContactDiscovered(cleanIp);
        transport.connect(cleanIp);
    }

    public boolean sendChatMessage(String text) {
        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isEmpty()) {
            return false;
        }
        if (!invitationAccepted && isKnownContactIp(activeRemoteIp)) {
            invitationAccepted = true;
            publishSystemMessage("Contacto conocido: invitación autoaceptada.");
        }
        if (!invitationAccepted) {
            publishSystemMessage("La conversación sigue pendiente. Espera a que acepten la invitación.");
            return false;
        }

        String messageId = localUserId + "-" + messageSeq.getAndIncrement();
        long sentAtMillis = System.currentTimeMillis();
        boolean sent = sendProtocol(ProtocolMessage.of(
                ProtocolMessage.Code.CHAT,
                localUserId,
                messageId,
                cleanText
        ));
        if (!sent) {
            return false;
        }
        pendingReceipts.put(messageId, sentAtMillis);
        outgoingMessageContacts.put(messageId, activeRemoteIp);
        publishChatMessage(activeRemoteIp, messageId, cleanText, true, formatEpochMillis(sentAtMillis), effectiveLocalName());
        publishOutgoingMessageStatus(activeRemoteIp, messageId, "Enviado");
        persistChatMessage(activeRemoteIp, cleanText, true, effectiveLocalName(), sentAtMillis);
        return true;
    }

    public void disconnect() {
        transport.disconnect();
    }

    public void setVisibleConversation(String contactIp) {
        visibleConversationIp = contactIp == null ? null : contactIp.trim();
        flushPendingSeenIfVisible();
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
        helloAccepted = false;
        invitationRequestSent = false;
        invitationAccepted = isKnownContactIp(remoteIp);
        pendingReceipts.clear();
        outgoingMessageContacts.clear();
        pendingSeenByContact.clear();
        receivedMessageKeys.clear();
        saveContact(remoteIp);
        publishContactDiscovered(remoteIp);
        publishConnectionState(remoteIp, true, contextMessage);
        if (invitationAccepted) {
            publishSystemMessage("Contacto guardado: chat habilitado.");
        }
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_BROADCAST, localUserId));
        maybeSendInvitationRequest();
    }

    @Override
    public void onDisconnected(String remoteIp, String reason) {
        helloAccepted = false;
        invitationRequestSent = false;
        invitationAccepted = false;
        pendingReceipts.clear();
        outgoingMessageContacts.clear();
        pendingSeenByContact.clear();
        receivedMessageKeys.clear();
        activeRemoteIp = null;
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
                    invitationRequestSent = false;
                    invitationAccepted = true;
                    saveContact(activeRemoteIp, msg.param(1));
                    publishSystemMessage("Conectado con " + msg.param(1));
                }
                case REJECT -> {
                    invitationRequestSent = false;
                    invitationAccepted = false;
                    publishSystemMessage("La contraparte rechazó la solicitud.");
                    transport.disconnect();
                }
                case HELLO_BROADCAST -> handleHelloBroadcast(msg.param(0));
                case HELLO_ACCEPT -> handleHelloAccept(msg.param(0));
                case HELLO_REJECT -> handleHelloReject();
                case CHAT -> {
                    ChatIncomingData incoming = parseIncomingChat(msg);
                    if (!invitationAccepted && isKnownContactIp(activeRemoteIp)) {
                        invitationAccepted = true;
                        publishSystemMessage("Contacto conocido detectado por mensaje entrante: chat habilitado automáticamente.");
                    }
                    if (incoming == null) {
                        publishSystemMessage("CHAT 007 inválido recibido.");
                        return;
                    }
                    if (receivedMessageKeys.add(incoming.dedupKey())) {
                        String sentAt = formatEpochMillis(incoming.sentAtMillis());
                        String remoteLabel = resolveRemoteLabel();
                        publishChatMessage(activeRemoteIp, incoming.messageId(), incoming.text(), false, sentAt, remoteLabel);
                        persistChatMessage(activeRemoteIp, incoming.text(), false, remoteLabel, incoming.sentAtMillis());
                    }
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.RECEIPT, incoming.messageId()));
                    handleSeenForIncomingMessage(incoming.messageId());
                }
                case RECEIPT -> handleReceipt(msg.param(0));
                case DELETE -> publishSystemMessage("Solicitud eliminar mensaje: " + msg.param(0));
                case BUZZ -> publishSystemMessage("Zumbido recibido: " + msg.param(0));
                case PIN -> publishSystemMessage("Solicitud fijar mensaje: " + msg.param(0));
                case SEEN -> handleSeen(msg.param(0), msg.param(1), msg.param(2));
                case THEME -> publishSystemMessage("Cambio de tema recibido: " + msg.param(1));
                case OUTLINE -> publishSystemMessage("Estoy offline. " + msg.param(0));
            }
        } catch (IllegalArgumentException ex) {
            publishSystemMessage("Fragmento no reconocido: " + line);
        }
    }

    private void handleHelloBroadcast(String requesterId) {
        if (isRequesterBlocked(requesterId)) {
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_REJECT));
            publishSystemMessage("Hello rechazado automáticamente (blacklist).");
            transport.disconnect();
            return;
        }
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_ACCEPT, localUserId));
    }

    private void handleHelloAccept(String remoteUserId) {
        helloAccepted = true;
        publishSystemMessage("Handshake de hello confirmado con " + remoteUserId + ".");
        maybeSendInvitationRequest();
    }

    private void handleHelloReject() {
        helloAccepted = false;
        invitationAccepted = false;
        publishSystemMessage("Hello rechazado por contraparte.");
        transport.disconnect();
    }

    private void handleReceipt(String messageId) {
        String resolvedMessageId = resolveOutgoingMessageId(messageId);
        if (resolvedMessageId == null) {
            return;
        }
        Long sentAtMillis = pendingReceipts.remove(resolvedMessageId);
        String contactIp = outgoingMessageContacts.get(resolvedMessageId);
        if (contactIp != null) {
            publishOutgoingMessageStatus(contactIp, resolvedMessageId, "Entregado");
        }
        if (sentAtMillis != null) {
            publishSystemMessage("Mensaje entregado (" + formatEpochMillis(sentAtMillis) + ")");
        }
    }

    private void handleSeen(String seenByUserId, String messageId, String stateText) {
        String resolvedMessageId = resolveOutgoingMessageId(messageId);
        if (resolvedMessageId == null) {
            return;
        }
        String contactIp = outgoingMessageContacts.get(resolvedMessageId);
        if (contactIp == null) {
            return;
        }
        publishOutgoingMessageStatus(contactIp, resolvedMessageId, "Visto");
        publishSystemMessage("Mensaje visto por " + seenByUserId + ".");
    }

    private void handleSeenForIncomingMessage(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        if (isVisibleConversation(activeRemoteIp)) {
            sendSeenAck(messageId);
            return;
        }
        queuePendingSeen(activeRemoteIp, messageId);
    }

    private void queuePendingSeen(String contactIp, String messageId) {
        if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
            return;
        }
        pendingSeenByContact.computeIfAbsent(contactIp, key -> ConcurrentHashMap.newKeySet()).add(messageId);
    }

    private boolean isVisibleConversation(String contactIp) {
        if (contactIp == null || contactIp.isBlank()) {
            return false;
        }
        String visible = visibleConversationIp;
        return visible != null && !visible.isBlank() && visible.equals(contactIp);
    }

    private void flushPendingSeenIfVisible() {
        String visible = visibleConversationIp;
        if (visible == null || visible.isBlank()) {
            return;
        }
        if (activeRemoteIp == null || !visible.equals(activeRemoteIp)) {
            return;
        }
        Set<String> pending = pendingSeenByContact.remove(visible);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (String messageId : pending) {
            sendSeenAck(messageId);
        }
    }

    private void sendSeenAck(String messageId) {
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.SEEN, localUserId, messageId, "VISTO"));
    }

    private ChatIncomingData parseIncomingChat(ProtocolMessage msg) {
        List<String> params = msg.params();
        if (params.size() >= 4) {
            String ackId = params.get(1);
            String text = params.get(2);
            long sentAt = parseEpochMillis(params.get(3));
            String dedupKey = ackId + "|" + sentAt + "|" + text;
            return new ChatIncomingData(ackId, text, sentAt, dedupKey);
        }
        if (params.size() == 3) {
            String p0 = params.get(0);
            String p1 = params.get(1);
            String p2 = params.get(2);
            if (isLikelyEpochMillis(p2)) {
                long sentAt = parseEpochMillis(p2);
                String syntheticId = p0 + "-" + p2 + "-" + Math.abs(p1.hashCode());
                String dedupKey = syntheticId + "|" + sentAt + "|" + p1;
                return new ChatIncomingData(syntheticId, p1, sentAt, dedupKey);
            }
            long sentAt = System.currentTimeMillis();
            String dedupKey = p1 + "|" + sentAt + "|" + p2;
            return new ChatIncomingData(p1, p2, sentAt, dedupKey);
        }
        return null;
    }

    private boolean isLikelyEpochMillis(String raw) {
        try {
            long value = Long.parseLong(raw.trim());
            return value > 1_000_000_000_000L;
        } catch (Exception ex) {
            return false;
        }
    }

    private record ChatIncomingData(String messageId, String text, long sentAtMillis, String dedupKey) {
    }

    private String loadLocalName() {
        try {
            return appProfileDao.getDisplayName();
        } catch (Exception ex) {
            publishSystemMessage("no se pudo cargar el nombre local: " + ex.getMessage());
            return "";
        }
    }

    private String effectiveLocalName() {
        String value = localName == null ? "" : localName.trim();
        return value.isEmpty() ? "Usuario" : value;
    }

    private void maybeSendInvitationRequest() {
        if (invitationAccepted || invitationRequestSent) {
            return;
        }
        boolean sent = sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REQUEST, localUserId, effectiveLocalName()));
        if (sent) {
            invitationRequestSent = true;
        }
    }

    private String resolveOutgoingMessageId(String incomingId) {
        if (incomingId != null && !incomingId.isBlank() && outgoingMessageContacts.containsKey(incomingId)) {
            return incomingId;
        }
        // Interop fallback: if peer does not preserve message ids, resolve by the single in-flight message.
        if (pendingReceipts.size() == 1) {
            return pendingReceipts.keySet().iterator().next();
        }
        return null;
    }

    private void handleRequest(String requesterId, String requesterName) {
        saveContact(activeRemoteIp, requesterName);
        if (isRequesterBlocked(requesterId)) {
            transport.disconnect();
            publishSystemMessage("conexion rechazada automáticamente (usuario en lista negra).");
            return;
        }

        publishSystemMessage("Solicitud recibida de " + requesterName);

        if (isKnownContactIp(activeRemoteIp)) {
            invitationAccepted = true;
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, effectiveLocalName()));
            publishSystemMessage("Solicitud autoaceptada para contacto guardado: " + requesterName + ".");
            return;
        }

        ConnectionRequest request = new ConnectionRequest(
                activeRemoteIp,
                requesterId,
                requesterName,
                () -> {
                    invitationAccepted = true;
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, effectiveLocalName()));
                    publishSystemMessage("Aceptaste la solicitud de " + requesterName + ".");
                },
                () -> {
                    invitationAccepted = false;
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REJECT));
                    addToBlacklist(requesterId, requesterName, activeRemoteIp);
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

    private boolean isRequesterBlocked(String requesterId) {
        try {
            return blacklistDao.isBlocked(requesterId);
        } catch (SQLException ex) {
            publishSystemMessage("no se pudo verificar la blacklist: " + ex.getMessage());
            return false;
        }
    }

    private void addToBlacklist(String requesterId, String requesterName, String remoteIp) {
        try {
            blacklistDao.addBlockedRequester(requesterId, requesterName, remoteIp);
        } catch (SQLException ex) {
            publishSystemMessage("no se pudo guardar en la blacklist: " + ex.getMessage());
        }
    }

    private void saveContact(String ip) {
        saveContact(ip, "");
    }

    private void saveContact(String ip, String name) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.equals(cleanIp)) {
            cleanName = "";
        }
        if (cleanName.isEmpty()) {
            try {
                Contact existing = contactDao.findByIp(cleanIp);
                if (existing != null && existing.getName() != null && !existing.getName().isBlank()) {
                    String existingName = existing.getName().trim();
                    if (!existingName.equals(cleanIp)) {
                        cleanName = existingName;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            Contact contact = Contact.builder().code(cleanIp).name(cleanName).ip(cleanIp).build();
            contactDao.saveOrUpdateByIp(contact);
        } catch (Exception ex) {
            publishSystemMessage("no se pudo guardar el contacto " + cleanIp + ": " + ex.getMessage());
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

    private boolean sendProtocol(ProtocolMessage message) {
        if (!transport.isConnected()) {
            publishSystemMessage("Sin conexión activa. Usa Conectar o espera conexión entrante.");
            return false;
        }
        try {
            transport.send(message);
            return true;
        } catch (IOException ex) {
            publishSystemMessage("Error enviando protocolo " + message.code().value() + ": " + ex.getMessage());
            return false;
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
            chatMessageDao.save(contactIp, text, selfSent, senderLabel, sentAtMillis);
        } catch (SQLException ex) {
            publishSystemMessage("no se pudo persistir mensaje: " + ex.getMessage());
        }
    }

    private void publishChatMessage(String contactIp, String messageId, String text, boolean self, String sentAt, String senderLabel) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onChatMessage(contactIp, messageId, text, self, sentAt, senderLabel);
        }
    }

    private void publishOutgoingMessageStatus(String contactIp, String messageId, String status) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onOutgoingMessageStatus(contactIp, messageId, status);
        }
    }

    private void publishSystemMessage(String text) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onSystemMessage(text);
        }
    }
}
