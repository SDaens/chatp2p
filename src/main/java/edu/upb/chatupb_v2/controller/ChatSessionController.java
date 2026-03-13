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
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final EnumMap<ProtocolMessage.Code, ProtocolCommand> protocolCommands = new EnumMap<>(ProtocolMessage.Code.class);
    private static final Pattern CONNECT_ERROR_PATTERN = Pattern.compile("^No se pudo conectar a\\s+([^:]+):\\d+\\s+-\\s+.*");
    private IchatIU iChatIU;

    private volatile boolean invitationAccepted;
    private volatile String activeRemoteIp;
    private volatile String lastOutboundIp;
    private final Object probeLock = new Object();
    private final ArrayDeque<String> probeQueue = new ArrayDeque<>();
    private volatile boolean probing;
    private volatile String probeIp;

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
        registerProtocolCommands();
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
        cancelProbe();
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

    public boolean sendImageBase64(String base64Image) {
        String cleanBase64 = base64Image == null ? "" : base64Image.trim();
        if (cleanBase64.isEmpty()) {
            return false;
        }
        if (!invitationAccepted) {
            publishSystemMessage("La conversación sigue pendiente. Espera a que acepten la invitación.");
        }

        String messageId = localUserId + "-img-" + messageSeq.getAndIncrement();
        long sentAtMillis = System.currentTimeMillis();
        String senderLabel = resolveLocalName();
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.IMAGE, localUserId, messageId, cleanBase64));
        publishChatMessage(activeRemoteIp, cleanBase64, true, formatEpochMillis(sentAtMillis), senderLabel, messageId);
        persistChatMessage(activeRemoteIp, messageId, cleanBase64, true, senderLabel, sentAtMillis);
        return true;
    }

    public boolean sendImageBytes(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return false;
        }
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return sendImageBase64(base64);
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
        if (isProbeConnection(remoteIp)) {
            publishPresence(remoteIp, true);
            scheduleProbeDisconnect();
            return;
        }
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
        publishPresence(remoteIp, true);
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
        if (isProbeConnection(remoteIp)) {
            startNextProbe();
            return;
        }
        invitationAccepted = false;
        activeRemoteIp = null;
        lastOutboundIp = null;
        publishConnectionState(remoteIp, false, reason);
        publishPresence(remoteIp, false);
    }

    @Override
    public void onMessageReceived(String line) {
        handleProtocolLine(line);
    }

    @Override
    public void onError(String message, Exception exception) {
        if (probing) {
            String ip = extractConnectErrorIp(message);
            if (ip != null && ip.equals(probeIp)) {
                publishPresence(ip, false);
                startNextProbe();
                return;
            }
        }
        publishSystemMessage(message);
    }

    private void handleProtocolLine(String line) {
        try {
            ProtocolMessage msg = ProtocolMessage.parse(line);
            ProtocolCommand command = protocolCommands.get(msg.code());
            if (command == null) {
                publishSystemMessage("Comando de protocolo no registrado: " + msg.code().value());
                return;
            }
            command.execute(msg);
        } catch (IllegalArgumentException ex) {
            publishSystemMessage("Fragmento no reconocido: " + line);
        }
    }

    private void registerProtocolCommands() {
        protocolCommands.put(ProtocolMessage.Code.REQUEST, new RequestCommand());
        protocolCommands.put(ProtocolMessage.Code.ACCEPT, new AcceptCommand());
        protocolCommands.put(ProtocolMessage.Code.REJECT, new RejectCommand());
        protocolCommands.put(ProtocolMessage.Code.HELLO_BROADCAST, new HelloBroadcastCommand());
        protocolCommands.put(ProtocolMessage.Code.HELLO_ACCEPT, new HelloAcceptCommand());
        protocolCommands.put(ProtocolMessage.Code.HELLO_REJECT, new HelloRejectCommand());
        protocolCommands.put(ProtocolMessage.Code.CHAT, new ChatCommand());
        protocolCommands.put(ProtocolMessage.Code.RECEIPT, new ReceiptCommand());
        protocolCommands.put(ProtocolMessage.Code.DELETE, new DeleteCommand());
        protocolCommands.put(ProtocolMessage.Code.BUZZ, new BuzzCommand());
        protocolCommands.put(ProtocolMessage.Code.PIN, new PinCommand());
        protocolCommands.put(ProtocolMessage.Code.SEEN, new SeenCommand());
        protocolCommands.put(ProtocolMessage.Code.THEME, new ThemeCommand());
        protocolCommands.put(ProtocolMessage.Code.OUTLINE, new OutlineCommand());
        protocolCommands.put(ProtocolMessage.Code.SHARE, new ShareCommand());
        protocolCommands.put(ProtocolMessage.Code.IMAGE, new ImageCommand());
    }

    private interface ProtocolCommand {
        void execute(ProtocolMessage message);
    }

    private final class RequestCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            handleRequest(message.param(0), message.param(1));
        }
    }

    private final class AcceptCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            invitationAccepted = true;
            saveContact(activeRemoteIp, message.param(1));
            publishContactDiscovered(activeRemoteIp);
            publishSystemMessage("Conectado con " + message.param(1));
        }
    }

    private final class RejectCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            invitationAccepted = false;
            publishSystemMessage("La contraparte rechazó la solicitud.");
            transport.disconnect();
        }
    }

    private final class HelloBroadcastCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_ACCEPT, localUserId));
        }
    }

    private final class HelloAcceptCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishSystemMessage("Handshake de hello confirmado.");
        }
    }

    private final class HelloRejectCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishSystemMessage("Hello rechazado por contraparte.");
        }
    }

    private final class ChatCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            long sentAtMillis = System.currentTimeMillis();
            String sentAt = formatEpochMillis(sentAtMillis);
            String remoteLabel = resolveRemoteLabel();
            if (!invitationAccepted) {
                invitationAccepted = true;
                sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, resolveLocalName()));
                publishSystemMessage("Conectado con " + remoteLabel);
            }
            publishChatMessage(activeRemoteIp, message.param(2), false, sentAt, remoteLabel, message.param(1));
            persistChatMessage(activeRemoteIp, message.param(1), message.param(2), false, remoteLabel, sentAtMillis);
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.RECEIPT, message.param(1)));
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.SEEN, localUserId, message.param(1), message.param(2)));
        }
    }

    private final class ImageCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            long sentAtMillis = System.currentTimeMillis();
            String sentAt = formatEpochMillis(sentAtMillis);
            String remoteLabel = resolveRemoteLabel();
            if (!invitationAccepted) {
                invitationAccepted = true;
                sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, resolveLocalName()));
                publishSystemMessage("Conectado con " + remoteLabel);
            }
            publishChatMessage(activeRemoteIp, message.param(2), false, sentAt, remoteLabel, message.param(1));
            persistChatMessage(activeRemoteIp, message.param(1), message.param(2), false, remoteLabel, sentAtMillis);
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.RECEIPT, message.param(1)));
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.SEEN, localUserId, message.param(1), message.param(2)));
        }
    }

    private final class ReceiptCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishSystemMessage("Mensaje confirmado: " + message.param(0));
            publishMessageSeen(activeRemoteIp, message.param(0));
        }
    }

    private final class DeleteCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            handleDeleteMessage(activeRemoteIp, message.param(0));
        }
    }

    private final class BuzzCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishBuzz(activeRemoteIp);
        }
    }

    private final class PinCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishSystemMessage("Solicitud fijar mensaje: " + message.param(0));
        }
    }

    private final class SeenCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishMessageSeen(activeRemoteIp, message.param(1));
        }
    }

    private final class ThemeCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishSystemMessage("Cambio de tema recibido: " + message.param(1));
        }
    }

    private final class OutlineCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            publishSystemMessage("Estoy offline. " + message.param(0));
        }
    }

    private final class ShareCommand implements ProtocolCommand {
        @Override
        public void execute(ProtocolMessage message) {
            handleSharedContact(message.param(0), message.param(1), message.param(2));
        }
    }

    private void handleRequest(String requesterId, String requesterName) {
        publishSystemMessage("Solicitud recibida de " + requesterName);
        boolean knownBefore = isKnownContactIp(activeRemoteIp);
        saveContact(activeRemoteIp, requesterName);
        publishContactDiscovered(activeRemoteIp);

        if (knownBefore) {
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

    private void publishPresence(String remoteIp, boolean online) {
        for (ChatSessionObserver observer : sessionObservers) {
            observer.onPresenceChanged(remoteIp, online);
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

    public void probeContacts(List<String> contactIps) {
        if (contactIps == null || contactIps.isEmpty()) {
            return;
        }
        if (transport.isConnected()) {
            return;
        }
        synchronized (probeLock) {
            probeQueue.clear();
            for (String rawIp : contactIps) {
                String ip = rawIp == null ? "" : rawIp.trim();
                if (ip.isEmpty()) {
                    continue;
                }
                if (probeQueue.contains(ip)) {
                    continue;
                }
                probeQueue.add(ip);
            }
            if (probeQueue.isEmpty()) {
                probing = false;
                probeIp = null;
                return;
            }
            probing = true;
        }
        startNextProbe();
    }

    private void startNextProbe() {
        String next;
        synchronized (probeLock) {
            if (!probing) {
                return;
            }
            next = probeQueue.poll();
            if (next == null) {
                probing = false;
                probeIp = null;
                return;
            }
            probeIp = next;
        }
        transport.connect(next);
    }

    private void scheduleProbeDisconnect() {
        Thread disconnect = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            transport.disconnect();
        }, "probe-disconnect");
        disconnect.setDaemon(true);
        disconnect.start();
    }

    private boolean isProbeConnection(String remoteIp) {
        return probing && probeIp != null && probeIp.equals(remoteIp);
    }

    private void cancelProbe() {
        synchronized (probeLock) {
            probing = false;
            probeIp = null;
            probeQueue.clear();
        }
    }

    private String extractConnectErrorIp(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = CONNECT_ERROR_PATTERN.matcher(message.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }
}
