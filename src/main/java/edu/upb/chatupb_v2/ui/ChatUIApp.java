package edu.upb.chatupb_v2.ui;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;
import edu.upb.chatupb_v2.bl.server.ChatTransport;
import edu.upb.chatupb_v2.bl.server.Mediador;
import edu.upb.chatupb_v2.bl.server.SocketChatTransport;
import edu.upb.chatupb_v2.repository.BlacklistDao;
import edu.upb.chatupb_v2.repository.Contact;
import edu.upb.chatupb_v2.repository.ContactDao;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ChatUIApp extends Application {

    private static final int PORT = 1900;

    private final String localUserId = UUID.randomUUID().toString();
    private final String localName = "santiago d.";
    private final AtomicLong messageSeq = new AtomicLong(1);
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private final ChatTransport transport = new SocketChatTransport(PORT);
    private final Mediador mediador = Mediador.getInstance();
    private final BlacklistDao blacklistDao = new BlacklistDao();
    private final ContactDao contactDao = new ContactDao();
    private final ContactStatusRenderer contactRenderer = new ContactStatusRenderer();

    private Label localIpValue;
    private Label remoteIpValue;
    private Label statusValue;
    private VBox messages;
    private ScrollPane scrollPane;
    private TextArea input;
    private VBox contactsBox;
    private Stage primaryStage;
    private volatile boolean incomingRequestDialogOpen;
    private volatile boolean invitationAccepted;
    private final Map<String, ContactStatusRenderer.ContactItemView> contactItems = new LinkedHashMap<>();
    private Timeline mediatorEventPoller;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(buildHeader());
        root.setLeft(buildContactsSidebar(stage));
        root.setCenter(buildConversation());
        root.setBottom(buildComposer());

        Scene scene = new Scene(root, 1024, 680);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/edu/upb/chatupb_v2/ui/chat-ui.css")
        ).toExternalForm());

        stage.setTitle("ChatUPB - P2P en Red Local");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();

        loadSavedContacts();
        transport.start();
        startMediatorPolling();
        addSystemMessage("Escuchando en puerto " + PORT + ". Agrega contactos para conectar.");
    }

    @Override
    public void stop() {
        if (mediatorEventPoller != null) {
            mediatorEventPoller.stop();
        }
        transport.stop();
    }

    private void startMediatorPolling() {
        mediatorEventPoller = new Timeline(new KeyFrame(Duration.millis(80), e -> pollMediatorEvents()));
        mediatorEventPoller.setCycleCount(Timeline.INDEFINITE);
        mediatorEventPoller.play();
    }

    private void pollMediatorEvents() {
        for (Mediador.TransportEvent event : mediador.drainTransportEvents()) {
            switch (event.type()) {
                case CONNECTED -> {
                    addOrSelectContact(event.remoteIp());
                    setContactOnline(event.remoteIp());
                    setPendingState(event.remoteIp(), event.detail());
                    invitationAccepted = false;
                    if (event.detail() != null && event.detail().startsWith("Conectado a ")) {
                        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REQUEST, localUserId, localName));
                    }
                }
                case DISCONNECTED -> {
                    setContactOffline(resolveRemoteIp(event.remoteIp()));
                    invitationAccepted = false;
                    remoteIpValue.setText("-");
                    statusValue.setText("Sin conexión");
                    markActiveContact(null);
                    addSystemMessage(event.detail());
                }
                case MESSAGE -> handleProtocolLine(event.payload(), event.remoteIp());
                case ERROR -> addSystemMessage(event.detail());
            }
        }
    }

    private VBox buildHeader() {
        Label title = new Label("ChatUPB");
        title.getStyleClass().add("title");

        statusValue = new Label("Sin conexión");
        statusValue.getStyleClass().add("subtitle");

        VBox titleBox = new VBox(2, title, statusValue);
        titleBox.getStyleClass().add("title-box");

        localIpValue = new Label(resolveLocalIp());
        localIpValue.getStyleClass().add("meta-value");

        remoteIpValue = new Label("-");
        remoteIpValue.getStyleClass().add("meta-value");

        VBox statusBox = new VBox(6,
                buildMetaRow("IP local", localIpValue),
                buildMetaRow("IP remota", remoteIpValue)
        );
        statusBox.getStyleClass().add("status-box");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, titleBox, spacer, statusBox);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return new VBox(header);
    }

    private HBox buildMetaRow(String label, Label valueNode) {
        Label key = new Label(label);
        key.getStyleClass().add("meta-key");
        HBox row = new HBox(8, key, valueNode);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private ScrollPane buildConversation() {
        messages = new VBox(12);
        messages.getStyleClass().add("messages");

        scrollPane = new ScrollPane(messages);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("message-scroll");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVvalue(1.0);
        return scrollPane;
    }

    private VBox buildContactsSidebar(Stage owner) {
        Label title = new Label("Contactos");
        title.getStyleClass().add("contacts-title");

        Button add = new Button("Agregar");
        add.getStyleClass().add("secondary-button");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(e -> showAddContactPopup(owner));

        contactsBox = new VBox(8);
        contactsBox.getStyleClass().add("contacts-list");

        ScrollPane contactsScroll = new ScrollPane(contactsBox);
        contactsScroll.setFitToWidth(true);
        contactsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contactsScroll.getStyleClass().add("contacts-scroll");

        VBox sidebar = new VBox(12, title, add, contactsScroll);
        sidebar.getStyleClass().add("contacts-sidebar");
        sidebar.setPadding(new Insets(18));
        sidebar.setPrefWidth(250);
        return sidebar;
    }

    private VBox buildComposer() {
        Label hint = new Label("Escribe un mensaje");
        hint.getStyleClass().add("composer-hint");

        input = new TextArea();
        input.setPromptText("Escribe un mensaje...");
        input.getStyleClass().add("composer-input");
        input.setPrefRowCount(2);
        input.setWrapText(true);

        Button attach = new Button("Adjuntar imagen");
        attach.getStyleClass().add("secondary-button");
        attach.setOnAction(e -> addSystemMessage("Adjuntos aún no implementados."));

        Button send = new Button("Enviar");
        send.getStyleClass().add("primary-button");
        send.setOnAction(e -> sendChatMessage());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(12, attach, spacer, send);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox composer = new VBox(10, hint, input, actions);
        composer.getStyleClass().add("composer");
        composer.setPadding(new Insets(18, 26, 22, 26));
        return composer;
    }

    private void connectToRemote(String rawIp) {
        String ip = rawIp == null ? "" : rawIp.trim();
        if (ip.isEmpty()) {
            addSystemMessage("Ingresa una IP remota válida.");
            return;
        }
        addOrSelectContact(ip);
        transport.connect(ip);
    }

    private void handleProtocolLine(String line, String remoteIp) {
        try {
            ProtocolMessage msg = ProtocolMessage.parse(line);
            switch (msg.code()) {
                case REQUEST -> {
                    String requesterId = msg.param(0);
                    String requesterName = msg.param(1);
                    String ip = resolveRemoteIp(remoteIp);

                    if (isRequesterBlocked(requesterId)) {
                        transport.disconnect();
                        addSystemMessage("conexion rechazada automáticamente (usuario en lista negra).");
                        break;
                    }

                    addSystemMessage("solicitud recibida de " + requesterName);
                    Platform.runLater(() -> showMessageRequestPopup(ip, requesterId, requesterName));
                }
                case ACCEPT -> {
                    invitationAccepted = true;
                    Platform.runLater(() -> statusValue.setText("Conectado"));
                    addSystemMessage("Conectado con " + msg.param(1));
                }
                case REJECT -> {
                    addSystemMessage("La contraparte rechazó la solicitud.");
                    transport.disconnect();
                }
                case HELLO_BROADCAST -> sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_ACCEPT, localUserId));
                case HELLO_ACCEPT -> addSystemMessage("Handshake de hello confirmado.");
                case HELLO_REJECT -> addSystemMessage("Hello rechazado por contraparte.");
                case CHAT -> {
                    String sentAt = msg.params().size() > 3 ? formatEpochMillis(msg.param(3)) : LocalTime.now().format(timeFmt);
                    addChatBubble(msg.param(2), false, sentAt, resolveRemoteIp(remoteIp));
                    sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.RECEIPT, msg.param(1)));
                }
                case RECEIPT -> addSystemMessage("Mensaje confirmado: " + msg.param(0));
                case DELETE -> addSystemMessage("Solicitud eliminar mensaje: " + msg.param(0));
                case BUZZ -> addSystemMessage("Zumbido recibido: " + msg.param(0));
                case PIN -> addSystemMessage("Solicitud fijar mensaje: " + msg.param(0));
                case SEEN -> addSystemMessage("Visto por " + msg.param(0) + ": " + msg.param(2));
                case THEME -> addSystemMessage("Cambio de tema recibido: " + msg.param(1));
            }
        } catch (IllegalArgumentException ex) {
            addSystemMessage("Fragmento no reconocido: " + line);
        }
    }

    private void sendChatMessage() {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (!invitationAccepted) {
            addSystemMessage("La conversación sigue pendiente. Espera a que acepten la invitación.");
            return;
        }

        String messageId = localUserId + "-" + messageSeq.getAndIncrement();
        String sentAtMillis = String.valueOf(System.currentTimeMillis());
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.CHAT, localUserId, messageId, text, sentAtMillis));
        addChatBubble(text, true, formatEpochMillis(sentAtMillis), localName);
        input.clear();
    }

    private void sendProtocol(ProtocolMessage message) {
        if (!transport.isConnected()) {
            addSystemMessage("Sin conexión activa. Usa Conectar o espera conexión entrante.");
            return;
        }
        try {
            transport.send(message);
        } catch (IOException ex) {
            addSystemMessage("Error enviando protocolo " + message.code().value() + ": " + ex.getMessage());
        }
    }

    private void setPendingState(String remoteIp, String message) {
        Platform.runLater(() -> {
            remoteIpValue.setText(remoteIp);
            statusValue.setText("Pendiente");
            markActiveContact(remoteIp);
            addSystemMessage(message);
        });
    }

    private void showAddContactPopup(Window owner) {
        Stage popup = new Stage();
        popup.initOwner(owner);
        popup.initModality(Modality.WINDOW_MODAL);
        popup.setTitle("Agregar contacto");
        popup.setResizable(false);

        Label label = new Label("IP del contacto");
        label.getStyleClass().add("composer-hint");

        TextField ipInput = new TextField();
        ipInput.setPromptText("192.168.0.10");
        ipInput.getStyleClass().add("composer-input");
        ipInput.setPrefWidth(260);

        Button connect = new Button("Conectar");
        connect.getStyleClass().add("primary-button");
        connect.setOnAction(e -> {
            String ip = ipInput.getText() == null ? "" : ipInput.getText().trim();
            if (ip.isEmpty()) {
                addSystemMessage("Debes ingresar una IP para agregar un contacto.");
                return;
            }
            addOrSelectContact(ip);
            connectToRemote(ip);
            popup.close();
        });

        VBox popupRoot = new VBox(12, label, ipInput, connect);
        popupRoot.setPadding(new Insets(18));
        popupRoot.setAlignment(Pos.CENTER_LEFT);
        popupRoot.getStyleClass().add("root");

        Scene scene = new Scene(popupRoot, 320, 160);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/edu/upb/chatupb_v2/ui/chat-ui.css")
        ).toExternalForm());
        popup.setScene(scene);
        popup.show();
    }

    private void showMessageRequestPopup(String remoteIp, String requesterId, String requesterName) {
        if (primaryStage == null) {
            return;
        }
        if (incomingRequestDialogOpen) {
            addSystemMessage("Ya tienes una solicitud pendiente. La nueva solicitud fue rechazada.");
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REJECT));
            return;
        }
        incomingRequestDialogOpen = true;

        Stage popup = new Stage();
        popup.initOwner(primaryStage);
        popup.initModality(Modality.WINDOW_MODAL);
        popup.setTitle("Solicitud de mensaje");
        popup.setResizable(false);

        Label title = new Label("Solicitud de mensaje");
        title.getStyleClass().add("contacts-title");

        Label description = new Label(requesterName + " quiere iniciar una conversación.");
        description.getStyleClass().add("composer-hint");
        description.setWrapText(true);

        AtomicBoolean handled = new AtomicBoolean(false);

        Runnable rejectRequest = () -> {
            if (handled.compareAndSet(false, true)) {
                addToBlacklist(requesterId, requesterName, remoteIp);
                responderInvitacion(false);
                addSystemMessage("rechazaste solicitud de " + requesterName + ".");
            }
        };

        Button reject = new Button("rechazar");
        reject.getStyleClass().add("secondary-button");
        reject.setOnAction(e -> {
            rejectRequest.run();
            popup.close();
        });

        Button accept = new Button("aceptar");
        accept.getStyleClass().add("primary-button");
        accept.setOnAction(e -> {
            if (handled.compareAndSet(false, true)) {
                responderInvitacion(true);
                addSystemMessage("aceptaste la solicitud de " + requesterName + ".");
            }
            popup.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, reject, spacer, accept);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox popupRoot = new VBox(12, title, description, actions);
        popupRoot.setPadding(new Insets(18));
        popupRoot.setAlignment(Pos.CENTER_LEFT);
        popupRoot.getStyleClass().add("root");

        popup.setOnCloseRequest(e -> rejectRequest.run());
        popup.setOnHidden(e -> incomingRequestDialogOpen = false);

        Scene scene = new Scene(popupRoot, 360, 170);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/edu/upb/chatupb_v2/ui/chat-ui.css")
        ).toExternalForm());
        popup.setScene(scene);
        popup.show();
    }

    private void responderInvitacion(boolean aceptar) {
        sendProtocol(aceptar
                ? ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, localName)
                : ProtocolMessage.of(ProtocolMessage.Code.REJECT));
    }

    private String resolveRemoteIp(String eventRemoteIp) {
        String ip = eventRemoteIp == null ? "" : eventRemoteIp.trim();
        if (!ip.isEmpty()) {
            return ip;
        }
        if (remoteIpValue == null || remoteIpValue.getText() == null) {
            return null;
        }
        String fromUi = remoteIpValue.getText().trim();
        return fromUi.isEmpty() || "-".equals(fromUi) ? null : fromUi;
    }

    private boolean isRequesterBlocked(String requesterId) {
        try {
            return blacklistDao.isBlocked(requesterId);
        } catch (SQLException ex) {
            addSystemMessage("no se pudo verificar la blacklist: " + ex.getMessage());
            return false;
        }
    }

    private void addToBlacklist(String requesterId, String requesterName, String remoteIp) {
        try {
            blacklistDao.addBlockedRequester(requesterId, requesterName, remoteIp);
        } catch (SQLException ex) {
            addSystemMessage("no se pudo guardar en la blacklist: " + ex.getMessage());
        }
    }

    private void addOrSelectContact(String ip) {
        saveContactIfNeeded(ip);
        Platform.runLater(() -> {
            String cleanIp = ip == null ? "" : ip.trim();
            if (cleanIp.isEmpty()) {
                return;
            }
            ContactStatusRenderer.ContactItemView existing = contactItems.get(cleanIp);
            if (existing != null) {
                return;
            }
            ContactStatusRenderer.ContactItemView itemView = contactRenderer.create(cleanIp, e -> connectToRemote(cleanIp));
            contactsBox.getChildren().add(itemView.button());
            contactItems.put(cleanIp, itemView);
        });
    }

    private void loadSavedContacts() {
        try {
            for (Contact contact : contactDao.findAll()) {
                addOrSelectContact(contact.getIp());
            }
        } catch (SQLException ex) {
            addSystemMessage("no se pudieron cargar contactos guardados: " + ex.getMessage());
        } catch (Exception ex) {
            addSystemMessage("error cargando contactos: " + ex.getMessage());
        }
    }

    private void saveContactIfNeeded(String ip) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        try {
            Contact contact = Contact.builder()
                    .code(cleanIp)
                    .name(cleanIp)
                    .ip(cleanIp)
                    .build();
            contactDao.saveOrUpdateByIp(contact);
        } catch (Exception ex) {
            addSystemMessage("no se pudo guardar el contacto " + cleanIp + ": " + ex.getMessage());
        }
    }

    private void markActiveContact(String ip) {
        for (Map.Entry<String, ContactStatusRenderer.ContactItemView> entry : contactItems.entrySet()) {
            entry.getValue().button().getStyleClass().remove("contact-item-active");
            if (ip != null && ip.equals(entry.getKey())) {
                entry.getValue().button().getStyleClass().add("contact-item-active");
            }
        }
    }

    private void setContactOnline(String ip) {
        setContactStatus(ip, true);
    }

    private void setContactOffline(String ip) {
        setContactStatus(ip, false);
    }

    private void setContactStatus(String ip, boolean online) {
        Platform.runLater(() -> {
            String cleanIp = ip == null ? "" : ip.trim();
            if (cleanIp.isEmpty()) {
                return;
            }
            ContactStatusRenderer.ContactItemView itemView = contactItems.get(cleanIp);
            if (itemView == null) {
                return;
            }
            contactRenderer.renderStatus(itemView, online);
        });
    }

    private void addChatBubble(String text, boolean self, String timeText, String userLabel) {
        Platform.runLater(() -> {
            Label textNode = new Label(text);
            textNode.getStyleClass().add("message-text");
            textNode.setWrapText(true);

            Label timeNode = new Label(timeText);
            timeNode.getStyleClass().add("message-time");

            VBox bubble = new VBox(4, textNode, timeNode);
            bubble.getStyleClass().add(self ? "bubble-self" : "bubble-peer");
            bubble.setMaxWidth(420);

            StackPane avatar = ChatIcon.create(userLabel, self);

            HBox row = self ? new HBox(8, bubble, avatar) : new HBox(8, avatar, bubble);
            row.getStyleClass().add("message-row");
            row.setAlignment(self ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            messages.getChildren().add(row);
            scrollPane.setVvalue(1.0);
        });
    }

    private String formatEpochMillis(String rawMillis) {
        if (rawMillis == null || rawMillis.isBlank()) {
            return LocalTime.now().format(timeFmt);
        }
        try {
            long millis = Long.parseLong(rawMillis.trim());
            return java.time.Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(timeFmt);
        } catch (Exception ex) {
            return rawMillis.trim();
        }
    }

    private void addSystemMessage(String text) {
        Platform.runLater(() -> {
            Label textNode = new Label(text);
            textNode.getStyleClass().add("system-text");
            textNode.setWrapText(true);

            VBox bubble = new VBox(textNode);
            bubble.getStyleClass().add("bubble-system");
            bubble.setMaxWidth(320);

            HBox row = new HBox(bubble);
            row.getStyleClass().addAll("message-row", "system-row");
            row.setAlignment(Pos.CENTER);

            messages.getChildren().add(row);
            scrollPane.setVvalue(1.0);
        });
    }

    private String resolveLocalIp() {
        try {
            for (NetworkInterface networkInterface : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : java.util.Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (SocketException ex) {
            return "IP no disponible";
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
