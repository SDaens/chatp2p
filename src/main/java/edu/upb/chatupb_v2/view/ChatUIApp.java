package edu.upb.chatupb_v2.view;

import edu.upb.chatupb_v2.controller.ChatSessionController;
import edu.upb.chatupb_v2.controller.ChatSessionObserver;
import edu.upb.chatupb_v2.controller.ContactController;
import edu.upb.chatupb_v2.controller.ConnectionRequest;
import edu.upb.chatupb_v2.controller.ConnectionRequestObserver;
import edu.upb.chatupb_v2.controller.IchatIU;
import edu.upb.chatupb_v2.model.ChatMessage;
import edu.upb.chatupb_v2.model.Contact;
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
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUIApp extends Application implements IchatIU {
    private static final Pattern CONNECT_ERROR_PATTERN = Pattern.compile("^No se pudo conectar a\\s+([^:]+):\\d+\\s+-\\s+.*");

    private final ChatSessionController chatController = new ChatSessionController();
    private final ContactController contactController = new ContactController(this);
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
    private final Map<String, ContactStatusRenderer.ContactItemView> contactItems = new LinkedHashMap<>();
    private volatile String selectedContactIp;
    private Button connectSelectedButton;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

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
                getClass().getResource("/edu/upb/chatupb_v2/view/chat-ui.css")
        ).toExternalForm());

        stage.setTitle("ChatUPB - P2P en Red Local");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();

        bindControllerEvents();
        loadSavedContactsInUI();
        chatController.setIChatIU(this);
        chatController.start();
    }

    @Override
    public void stop() {
        chatController.unload();
    }

    private void bindControllerEvents() {
        chatController.addSessionObserver(new ChatSessionObserver() {
            @Override
            public void onContactDiscovered(String ip) {
                ChatUIApp.this.onContactDiscovered(ip);
            }

            @Override
            public void onConnectionStateChanged(String remoteIp, boolean connected, String detail) {
                ChatUIApp.this.onConnectionStateChanged(remoteIp, connected, detail);
            }

            @Override
            public void onChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel) {
                ChatUIApp.this.onChatMessage(contactIp, text, self, sentAt, senderLabel);
            }

            @Override
            public void onSystemMessage(String text) {
                ChatUIApp.this.onSystemMessage(text);
            }

            @Override
            public void onIncomingRequestWithoutObservers(ConnectionRequest request) {
                ChatUIApp.this.onIncomingRequest(request);
            }
        });

        chatController.addConnectionRequestObserver(new ConnectionRequestObserver() {
            @Override
            public void onIncomingConnectionRequest(ConnectionRequest request) {
                ChatUIApp.this.onIncomingRequest(request);
            }
        });
    }

    private void loadSavedContactsInUI() {
        for (Contact contact : contactController.findAll()) {
            onContactDiscovered(contact.getIp());
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

        connectSelectedButton = new Button("Contactar");
        connectSelectedButton.getStyleClass().add("primary-button");
        connectSelectedButton.setOnAction(e -> connectSelectedContact());
        connectSelectedButton.setDisable(true);

        HBox localRow = buildMetaRow("IP local", localIpValue);
        localRow.getChildren().add(connectSelectedButton);

        VBox statusBox = new VBox(6,
                localRow,
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

        contactsBox = new VBox(4);
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

    private void sendChatMessage() {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        boolean sent = chatController.sendChatMessage(text);
        if (sent) {
            input.clear();
        }
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
            contactController.saveByIp(ip);
            onContactDiscovered(ip);
            selectContact(ip, false);
            popup.close();
        });

        VBox popupRoot = new VBox(12, label, ipInput, connect);
        popupRoot.setPadding(new Insets(18));
        popupRoot.setAlignment(Pos.CENTER_LEFT);
        popupRoot.getStyleClass().add("root");

        Scene scene = new Scene(popupRoot, 320, 160);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/edu/upb/chatupb_v2/view/chat-ui.css")
        ).toExternalForm());
        popup.setScene(scene);
        popup.show();
    }

    private void showMessageRequestPopup(ConnectionRequest request) {
        Platform.runLater(() -> {
            if (primaryStage == null) {
                return;
            }
            if (incomingRequestDialogOpen) {
                addSystemMessage("Ya tienes una solicitud pendiente. La nueva solicitud fue rechazada.");
                request.reject();
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

            Label description = new Label(request.requesterName() + " quiere iniciar una conversación.");
            description.getStyleClass().add("composer-hint");
            description.setWrapText(true);

            AtomicBoolean handled = new AtomicBoolean(false);

            Runnable rejectRequest = () -> {
                if (handled.compareAndSet(false, true)) {
                    request.reject();
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
                    request.accept();
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
                    getClass().getResource("/edu/upb/chatupb_v2/view/chat-ui.css")
            ).toExternalForm());
            popup.setScene(scene);
            popup.show();
        });
    }

    private void addOrSelectContact(String ip) {
        Platform.runLater(() -> {
            String cleanIp = ip == null ? "" : ip.trim();
            if (cleanIp.isEmpty()) {
                return;
            }
            ContactStatusRenderer.ContactItemView existing = contactItems.get(cleanIp);
            if (existing != null) {
                return;
            }
            ContactStatusRenderer.ContactItemView itemView = contactRenderer.create(cleanIp, e -> selectContact(cleanIp, false));
            contactsBox.getChildren().add(itemView.button());
            contactItems.put(cleanIp, itemView);
        });
    }

    @Override
    public void onContactDiscovered(String ip) {
        addOrSelectContact(ip);
    }

    @Override
    public void onConnectionStateChanged(String remoteIp, boolean connected, String detail) {
        if (connected) {
            if (remoteIp != null && !remoteIp.isBlank()) {
                selectedContactIp = remoteIp;
                renderChatHistory(remoteIp);
            }
            setContactOnline(remoteIp);
            setPendingState(remoteIp, detail);
            return;
        }
        setContactOffline(remoteIp);
        statusValue.setText("Sin conexión");
        addSystemMessage(detail);
    }

    @Override
    public void onChatMessage(String text, boolean self, String sentAt, String senderLabel) {
        onChatMessage(selectedContactIp, text, self, sentAt, senderLabel);
    }

    public void onChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel) {
        if (contactIp == null || contactIp.isBlank()) {
            addChatBubble(text, self, sentAt, senderLabel);
            return;
        }
        addOrSelectContact(contactIp);
        if (selectedContactIp == null || selectedContactIp.isBlank()) {
            selectedContactIp = contactIp;
            markActiveContact(contactIp);
            remoteIpValue.setText(contactIp);
        }
        if (!contactIp.equals(selectedContactIp)) {
            return;
        }
        addChatBubble(text, self, sentAt, senderLabel);
    }

    @Override
    public void onSystemMessage(String text) {
        addSystemMessage(text);
        showOfflinePopupIfNeeded(text);
    }

    @Override
    public void onIncomingRequest(ConnectionRequest request) {
        showMessageRequestPopup(request);
    }

    private void markActiveContact(String ip) {
        for (Map.Entry<String, ContactStatusRenderer.ContactItemView> entry : contactItems.entrySet()) {
            entry.getValue().button().getStyleClass().remove("contact-item-active");
            if (ip != null && ip.equals(entry.getKey())) {
                entry.getValue().button().getStyleClass().add("contact-item-active");
            }
        }
    }

    private void setPendingState(String remoteIp, String message) {
        Platform.runLater(() -> {
            remoteIpValue.setText(remoteIp == null || remoteIp.isBlank() ? "-" : remoteIp);
            statusValue.setText("Pendiente");
            markActiveContact(remoteIp);
            addSystemMessage(message);
        });
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
            addChatBubbleNow(text, self, timeText, userLabel);
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
            row.setMaxWidth(Double.MAX_VALUE);

            messages.getChildren().add(row);
            scrollPane.setVvalue(1.0);
        });
    }

    private void addChatBubbleNow(String text, boolean self, String timeText, String userLabel) {
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
        row.setMaxWidth(Double.MAX_VALUE);

        messages.getChildren().add(row);
        scrollPane.setVvalue(1.0);
    }

    private void selectContact(String ip, boolean connect) {
        String cleanIp = ip == null ? "" : ip.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        selectedContactIp = cleanIp;
        Platform.runLater(() -> {
            markActiveContact(cleanIp);
            remoteIpValue.setText(cleanIp);
            if (connectSelectedButton != null) {
                connectSelectedButton.setDisable(false);
            }
            statusValue.setText(connect ? "Conectando..." : statusValue.getText());
            renderChatHistory(cleanIp);
        });
        if (connect) {
            chatController.connect(cleanIp);
        }
    }

    private void connectSelectedContact() {
        String cleanIp = selectedContactIp == null ? "" : selectedContactIp.trim();
        if (cleanIp.isEmpty()) {
            addSystemMessage("Selecciona un contacto primero.");
            return;
        }
        selectContact(cleanIp, true);
    }

    private void renderChatHistory(String contactIp) {
        String cleanIp = contactIp == null ? "" : contactIp.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        List<ChatMessage> history = chatController.getChatHistory(cleanIp);
        Platform.runLater(() -> {
            messages.getChildren().clear();
            for (ChatMessage chatMessage : history) {
                String senderLabel = chatMessage.getSenderLabel();
                if (senderLabel == null || senderLabel.isBlank()) {
                    senderLabel = cleanIp;
                }
                addChatBubbleNow(
                        chatMessage.getText(),
                        chatMessage.isSelfSent(),
                        formatHistoryTime(chatMessage.getSentAtMillis()),
                        senderLabel
                );
            }
        });
    }

    private String formatHistoryTime(long millis) {
        if (millis <= 0) {
            return LocalTime.now().format(timeFmt);
        }
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFmt);
    }

    private void showOfflinePopupIfNeeded(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = CONNECT_ERROR_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return;
        }
        String ip = matcher.group(1);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            if (primaryStage != null) {
                alert.initOwner(primaryStage);
            }
            alert.setTitle("Contacto offline");
            alert.setHeaderText("El contacto no está online");
            alert.setContentText("No se pudo conectar con " + ip + ". Verifica que tenga el chat abierto y esté en la misma red.");
            alert.show();
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
