package edu.upb.chatupb_v2.ui;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;
import edu.upb.chatupb_v2.bl.server.ChatTransport;
import edu.upb.chatupb_v2.bl.server.ChatTransportListener;
import edu.upb.chatupb_v2.bl.server.SocketChatTransport;
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
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalTime;
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
    private final String localName = "ChatUPB-" + localUserId.substring(0, 8);
    private final AtomicLong messageSeq = new AtomicLong(1);
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private final ChatTransport transport = new SocketChatTransport(PORT);

    private Label localIpValue;
    private Label remoteIpValue;
    private Label statusValue;
    private VBox messages;
    private ScrollPane scrollPane;
    private TextArea input;
    private VBox contactsBox;
    private Stage primaryStage;
    private volatile boolean incomingRequestDialogOpen;
    private final Map<String, Button> contactButtons = new LinkedHashMap<>();

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

        configureTransport();
        transport.start();
        addSystemMessage("Escuchando en puerto " + PORT + ". Agrega contactos para conectar.");
    }

    @Override
    public void stop() {
        transport.stop();
    }

    private void configureTransport() {
        transport.setListener(new ChatTransportListener() {
            @Override
            public void onConnected(String remoteIp, String contextMessage) {
                addOrSelectContact(remoteIp);
                setConnectionState(remoteIp, contextMessage);
                sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REQUEST, localUserId, localName));
                sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_BROADCAST, localUserId));
            }

            @Override
            public void onDisconnected(String remoteIp, String reason) {
                Platform.runLater(() -> {
                    remoteIpValue.setText("-");
                    statusValue.setText("Sin conexión");
                    markActiveContact(null);
                });
                addSystemMessage(reason);
            }

            @Override
            public void onMessageReceived(String line) {
                handleProtocolLine(line);
            }

            @Override
            public void onError(String message, Exception exception) {
                addSystemMessage(message);
            }
        });
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

    private void handleProtocolLine(String line) {
        try {
            ProtocolMessage msg = ProtocolMessage.parse(line);
            switch (msg.code()) {
                case REQUEST -> {
                    addSystemMessage("Solicitud recibida de " + msg.param(1));
                    Platform.runLater(() -> showMessageRequestPopup(msg.param(1)));
                }
                case ACCEPT -> addSystemMessage("Conectado con " + msg.param(1));
                case REJECT -> addSystemMessage("La contraparte rechazó la solicitud.");
                case HELLO_BROADCAST -> sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.HELLO_ACCEPT, localUserId));
                case HELLO_ACCEPT -> addSystemMessage("Handshake de hello confirmado.");
                case HELLO_REJECT -> addSystemMessage("Hello rechazado por contraparte.");
                case CHAT -> {
                    addChatBubble(msg.param(2), false);
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

        String messageId = localUserId + "-" + messageSeq.getAndIncrement();
        sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.CHAT, localUserId, messageId, text));
        addChatBubble(text, true);
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

    private void setConnectionState(String remoteIp, String message) {
        Platform.runLater(() -> {
            remoteIpValue.setText(remoteIp);
            statusValue.setText("Conectado");
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

    private void showMessageRequestPopup(String requesterName) {
        if (primaryStage == null) {
            return;
        }
        if (incomingRequestDialogOpen) {
            addSystemMessage("Ya tienes una solicitud pendiente. La nueva solicitud fue rechazada.");
            sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REJECT, localUserId));
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
                sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.REJECT, localUserId));
                addSystemMessage("Rechazaste la solicitud de " + requesterName + ".");
            }
        };

        Button reject = new Button("Rechazar");
        reject.getStyleClass().add("secondary-button");
        reject.setOnAction(e -> {
            rejectRequest.run();
            popup.close();
        });

        Button accept = new Button("Aceptar");
        accept.getStyleClass().add("primary-button");
        accept.setOnAction(e -> {
            if (handled.compareAndSet(false, true)) {
                sendProtocol(ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, localName));
                addSystemMessage("Aceptaste la solicitud de " + requesterName + ".");
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

    private void addOrSelectContact(String ip) {
        Platform.runLater(() -> {
            String cleanIp = ip == null ? "" : ip.trim();
            if (cleanIp.isEmpty()) {
                return;
            }
            Button existing = contactButtons.get(cleanIp);
            if (existing != null) {
                return;
            }
            Button contactBtn = new Button(cleanIp);
            contactBtn.getStyleClass().add("contact-item");
            contactBtn.setMaxWidth(Double.MAX_VALUE);
            contactBtn.setOnAction(e -> connectToRemote(cleanIp));
            contactsBox.getChildren().add(contactBtn);
            contactButtons.put(cleanIp, contactBtn);
        });
    }

    private void markActiveContact(String ip) {
        for (Map.Entry<String, Button> entry : contactButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("contact-item-active");
            if (ip != null && ip.equals(entry.getKey())) {
                entry.getValue().getStyleClass().add("contact-item-active");
            }
        }
    }

    private void addChatBubble(String text, boolean self) {
        Platform.runLater(() -> {
            Label textNode = new Label(text);
            textNode.getStyleClass().add("message-text");
            textNode.setWrapText(true);

            Label timeNode = new Label(LocalTime.now().format(timeFmt));
            timeNode.getStyleClass().add("message-time");

            VBox bubble = new VBox(4, textNode, timeNode);
            bubble.getStyleClass().add(self ? "bubble-self" : "bubble-peer");
            bubble.setMaxWidth(420);

            HBox row = new HBox(bubble);
            row.getStyleClass().add("message-row");
            row.setAlignment(self ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            messages.getChildren().add(row);
            scrollPane.setVvalue(1.0);
        });
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
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
