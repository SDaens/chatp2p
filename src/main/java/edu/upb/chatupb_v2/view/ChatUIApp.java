package edu.upb.chatupb_v2.view;

import edu.upb.chatupb_v2.controller.ChatSessionController;
import edu.upb.chatupb_v2.controller.ChatSessionObserver;
import edu.upb.chatupb_v2.controller.ContactController;
import edu.upb.chatupb_v2.controller.ConnectionRequest;
import edu.upb.chatupb_v2.controller.ConnectionRequestObserver;
import edu.upb.chatupb_v2.controller.IchatIU;
import edu.upb.chatupb_v2.controller.LocalProfileController;
import edu.upb.chatupb_v2.model.ChatMessage;
import edu.upb.chatupb_v2.model.Contact;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.geometry.Side;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;
import javafx.util.Duration;

public class ChatUIApp extends Application implements IchatIU {
    private static final Pattern CONNECT_ERROR_PATTERN = Pattern.compile("^No se pudo conectar a\\s+([^:]+):\\d+\\s+-\\s+.*");
    private static final int IMAGE_MAX_WIDTH = 260;

    private final ChatSessionController chatController = new ChatSessionController();
    private final ContactController contactController = new ContactController(this);
    private final LocalProfileController localProfileController = new LocalProfileController();
    private final ContactStatusRenderer contactRenderer = new ContactStatusRenderer();

    private Label localIpValue;
    private Label remoteIpValue;
    private Label statusValue;
    private BorderPane rootPane;
    private VBox messages;
    private ScrollPane scrollPane;
    private TextArea input;
    private VBox contactsBox;
    private Stage primaryStage;
    private volatile boolean incomingRequestDialogOpen;
    private final Map<String, ContactStatusRenderer.ContactItemView> contactItems = new LinkedHashMap<>();
    private final Map<String, Map<String, Label>> sentMessageTicks = new HashMap<>();
    private final Map<String, Map<String, HBox>> messageRows = new HashMap<>();
    private final Map<String, Map<String, String>> messageTexts = new HashMap<>();
    private final Map<String, Map<String, String>> uniquePayloads = new HashMap<>();
    private final Map<String, Set<String>> viewedUniqueMessages = new HashMap<>();
    private final Map<String, Map<String, Label>> uniquePlaceholders = new HashMap<>();
    private final Map<String, String> pinnedMessageByContact = new HashMap<>();
    private final Map<String, Boolean> presenceByContact = new HashMap<>();
    private final Map<String, PauseTransition> buzzTimers = new HashMap<>();
    private final Map<String, Long> lastBuzzAt = new HashMap<>();
    private static final long BUZZ_MIN_INTERVAL_MS = 300;
    private volatile String selectedContactIp;
    private Button connectSelectedButton;
    private Button attachButton;
    private Button shareButton;
    private Button buzzButton;
    private Button sendButton;
    private Button themeButton;
    private Button uniqueButton;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private TranslateTransition buzzAnimation;
    private HBox pinnedBar;
    private Label pinnedText;
    private Button unpinButton;
    private HBox offlineBar;
    private Label offlineText;
    private String currentThemeId = "default";

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("root", "theme-default");
        rootPane = root;

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
        applyTheme(currentThemeId);
        loadSavedContactsInUI();
        chatController.setIChatIU(this);
        ensureLocalProfileName();
        chatController.start();
        probeSavedContacts();
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
            public void onChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel, String messageId) {
                ChatUIApp.this.onChatMessage(contactIp, text, self, sentAt, senderLabel, messageId);
            }

            @Override
            public void onSystemMessage(String text) {
                ChatUIApp.this.onSystemMessage(text);
            }

            @Override
            public void onIncomingRequestWithoutObservers(ConnectionRequest request) {
                ChatUIApp.this.onIncomingRequest(request);
            }

            @Override
            public void onMessageSeen(String contactIp, String messageId) {
                ChatUIApp.this.onMessageSeen(contactIp, messageId);
            }

            @Override
            public void onMessageDeleted(String contactIp, String messageId) {
                ChatUIApp.this.onMessageDeleted(contactIp, messageId);
            }

            @Override
            public void onBuzz(String contactIp) {
                ChatUIApp.this.onBuzz(contactIp);
            }

            @Override
            public void onMessagePinned(String contactIp, String messageId, boolean pinned) {
                ChatUIApp.this.onMessagePinned(contactIp, messageId, pinned);
            }

            @Override
            public void onThemeChanged(String themeId, boolean remote) {
                ChatUIApp.this.onThemeChanged(themeId, remote);
            }

            @Override
            public void onPresenceChanged(String contactIp, boolean online) {
                ChatUIApp.this.onPresenceChanged(contactIp, online);
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

    private void probeSavedContacts() {
        List<String> ips = new ArrayList<>();
        for (Contact contact : contactController.findAll()) {
            String ip = contact.getIp() == null ? "" : contact.getIp().trim();
            if (!ip.isEmpty()) {
                ips.add(ip);
            }
        }
        chatController.probeContacts(ips);
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

    private VBox buildConversation() {
        messages = new VBox(12);
        messages.getStyleClass().add("messages");

        scrollPane = new ScrollPane(messages);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("message-scroll");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVvalue(1.0);

        Label pinnedLabel = new Label("Mensaje fijado");
        pinnedLabel.getStyleClass().add("pinned-title");
        pinnedText = new Label("-");
        pinnedText.getStyleClass().add("pinned-text");
        pinnedText.setWrapText(true);

        unpinButton = new Button("Quitar");
        unpinButton.getStyleClass().add("pinned-action");
        unpinButton.setOnAction(event -> unpinSelectedMessage());

        VBox pinnedContent = new VBox(2, pinnedLabel, pinnedText);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        pinnedBar = new HBox(10, pinnedContent, spacer, unpinButton);
        pinnedBar.getStyleClass().add("pinned-bar");
        pinnedBar.setAlignment(Pos.CENTER_LEFT);
        pinnedBar.setVisible(false);
        pinnedBar.setManaged(false);

        offlineText = new Label("Contacto offline. No puedes enviar mensajes.");
        offlineText.getStyleClass().add("offline-text");
        offlineBar = new HBox(offlineText);
        offlineBar.getStyleClass().add("offline-bar");
        offlineBar.setAlignment(Pos.CENTER_LEFT);
        offlineBar.setVisible(false);
        offlineBar.setManaged(false);

        VBox conversation = new VBox(offlineBar, pinnedBar, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return conversation;
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
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                sendChatMessage();
            }
        });

        attachButton = new Button("Adjuntar imagen");
        attachButton.getStyleClass().add("secondary-button");
        attachButton.setOnAction(e -> attachImage());

        shareButton = new Button("Compartir contacto");
        shareButton.getStyleClass().add("secondary-button");
        shareButton.setOnAction(e -> shareContact());

        buzzButton = new Button("Buzz");
        buzzButton.getStyleClass().add("secondary-button");
        buzzButton.setOnAction(e -> sendBuzz());

        themeButton = new Button("Tema");
        themeButton.getStyleClass().add("secondary-button");
        themeButton.setOnAction(e -> chooseTheme());

        uniqueButton = new Button("Único");
        uniqueButton.getStyleClass().add("secondary-button");
        uniqueButton.setOnAction(e -> sendUniqueMessage());

        sendButton = new Button("Enviar");
        sendButton.getStyleClass().add("primary-button");
        sendButton.setOnAction(e -> sendChatMessage());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(12, attachButton, shareButton, buzzButton, themeButton, uniqueButton, spacer, sendButton);
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

    private void sendUniqueMessage() {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        boolean sent = chatController.sendUniqueMessage(text);
        if (sent) {
            input.clear();
        }
    }

    private void attachImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona una imagen");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp")
        );
        java.io.File file = chooser.showOpenDialog(primaryStage);
        if (file == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            boolean sent = chatController.sendImageBytes(bytes);
            if (!sent) {
                addSystemMessage("No se pudo enviar la imagen.");
            }
        } catch (Exception ex) {
            addSystemMessage("Error leyendo imagen: " + ex.getMessage());
        }
    }

    private void shareContact() {
        List<Contact> contacts = contactController.findAll();
        if (contacts.isEmpty()) {
            addSystemMessage("no hay contactos");
            return;
        }

        Map<String, Contact> options = new LinkedHashMap<>();
        for (Contact contact : contacts) {
            String ip = contact.getIp() == null ? "" : contact.getIp().trim();
            if (ip.isEmpty()) {
                continue;
            }
            String name = contact.getName() == null || contact.getName().isBlank() ? ip : contact.getName().trim();
            String code = contact.getCode() == null || contact.getCode().isBlank() ? "-" : contact.getCode().trim();
            String label = name + " | " + ip + " | id: " + code;
            options.put(label, contact);
        }

        if (options.isEmpty()) {
            addSystemMessage("no hay contactos para compartir");
            return;
        }

        List<String> labels = new ArrayList<>(options.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
        }
        dialog.setTitle("Compartir contacto");
        dialog.setHeaderText("Selecciona un contacto para compartir");
        dialog.setContentText("Contacto:");

        dialog.showAndWait().ifPresent(selectedLabel -> {
            Contact selected = options.get(selectedLabel);
            chatController.shareContact(selected);
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
            contactController.saveByIp(ip);
            onContactDiscovered(ip);
            selectContact(ip, true);
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
                existing.label().setText(buildContactLabel(cleanIp));
                return;
            }
            String label = buildContactLabel(cleanIp);
            ContactStatusRenderer.ContactItemView itemView = contactRenderer.create(label, e -> selectContact(cleanIp, false));
            contactsBox.getChildren().add(itemView.button());
            contactItems.put(cleanIp, itemView);
            Boolean online = presenceByContact.get(cleanIp);
            if (online != null) {
                contactRenderer.renderStatus(itemView, online);
            }
        });
    }

    private void ensureLocalProfileName() {
        String savedName = localProfileController.getDisplayName();
        if (savedName != null && !savedName.isBlank()) {
            chatController.setLocalName(savedName);
            return;
        }

        while (true) {
            TextInputDialog dialog = new TextInputDialog("");
            if (primaryStage != null) {
                dialog.initOwner(primaryStage);
            }
            dialog.setTitle("Nombre");
            dialog.setHeaderText("Ingresa tu nombre para el chat");
            dialog.setContentText("Nombre:");

            var result = dialog.showAndWait();
            if (result.isPresent()) {
                String name = result.get().trim();
                if (!name.isBlank()) {
                    localProfileController.saveDisplayName(name);
                    chatController.setLocalName(name);
                    return;
                }
                Alert alert = new Alert(Alert.AlertType.WARNING);
                if (primaryStage != null) {
                    alert.initOwner(primaryStage);
                }
                alert.setTitle("Nombre requerido");
                alert.setHeaderText("El nombre no puede estar vacío");
                alert.setContentText("Ingresa un nombre para continuar.");
                alert.showAndWait();
                continue;
            }

            String fallback = "Usuario";
            localProfileController.saveDisplayName(fallback);
            chatController.setLocalName(fallback);
            return;
        }
    }

    private String buildContactLabel(String ip) {
        Contact contact = contactController.findByIp(ip);
        String name = contact == null || contact.getName() == null ? "" : contact.getName().trim();
        String cleanIp = ip == null ? "" : ip.trim();
        if (name.isBlank() || cleanIp.isEmpty()) {
            return cleanIp;
        }
        if (name.equalsIgnoreCase(cleanIp)) {
            return cleanIp;
        }
        return name + " (" + cleanIp + ")";
    }

    @Override
    public void onContactDiscovered(String ip) {
        addOrSelectContact(ip);
    }

    @Override
    public void onConnectionStateChanged(String remoteIp, boolean connected, String detail) {
        Platform.runLater(() -> {
            if (connected) {
                boolean initiated = detail != null && detail.startsWith("Conectado a ");
                boolean shouldSelect = initiated || (selectedContactIp != null && selectedContactIp.equals(remoteIp));
                if (shouldSelect && remoteIp != null && !remoteIp.isBlank()) {
                    selectedContactIp = remoteIp;
                    renderChatHistory(remoteIp);
                    setPendingState(remoteIp, detail);
                } else {
                    addSystemMessage(detail);
                }
                return;
            }
            statusValue.setText("Sin conexión");
            addSystemMessage(detail);
        });
    }

    private void onPresenceChanged(String contactIp, boolean online) {
        if (contactIp == null || contactIp.isBlank()) {
            return;
        }
        presenceByContact.put(contactIp, online);
        if (online) {
            setContactOnline(contactIp);
        } else {
            setContactOffline(contactIp);
        }
        if (contactIp.equals(selectedContactIp)) {
            updateConversationAvailability(contactIp);
        }
    }

    @Override
    public void onChatMessage(String text, boolean self, String sentAt, String senderLabel) {
        onChatMessage(selectedContactIp, text, self, sentAt, senderLabel, null);
    }

    public void onChatMessage(String contactIp, String text, boolean self, String sentAt, String senderLabel, String messageId) {
        if (contactIp == null || contactIp.isBlank()) {
            addChatBubble(contactIp, text, self, sentAt, senderLabel, messageId);
            return;
        }
        if (!self && messageId != null && !messageId.isBlank()) {
            chatController.sendSeen(messageId);
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
        addChatBubble(contactIp, text, self, sentAt, senderLabel, messageId);
    }

    @Override
    public void onSystemMessage(String text) {
        addSystemMessage(text);
        showOfflinePopupIfNeeded(text);
    }

    private void onThemeChanged(String themeId, boolean remote) {
        if (themeId == null || themeId.isBlank()) {
            return;
        }
        Platform.runLater(() -> {
            currentThemeId = themeId.trim();
            applyTheme(currentThemeId);
        });
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

    private void addChatBubble(String contactIp, String text, boolean self, String timeText, String userLabel, String messageId) {
        Platform.runLater(() -> {
            addChatBubbleNow(contactIp, text, self, timeText, userLabel, messageId);
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

    private void addChatBubbleNow(String contactIp, String text, boolean self, String timeText, String userLabel, String messageId) {
        boolean uniqueMessage = isUniqueMessage(messageId);
        String uniqueText = uniqueMessage ? decodeUniqueMessage(text) : null;
        boolean viewedUnique = uniqueMessage && isUniqueViewed(contactIp, messageId);
        Label uniquePlaceholder = uniqueMessage ? buildUniquePlaceholder() : null;
        javafx.scene.Node contentNode = uniqueMessage
                ? uniquePlaceholder
                : buildMessageContentNode(text, messageId);

        Label timeNode = new Label(timeText);
        timeNode.getStyleClass().add("message-time");

        VBox bubble;
        if (self) {
            Label tickNode = new Label("✓");
            tickNode.getStyleClass().add("message-tick");
            HBox metaRow;
            if (messageId != null && !messageId.isBlank()) {
                Button menuButton = buildMessageMenuButton(contactIp, messageId, true);
                metaRow = new HBox(6, timeNode, tickNode, menuButton);
            } else {
                metaRow = new HBox(6, timeNode, tickNode);
            }
            metaRow.setAlignment(Pos.CENTER_RIGHT);
            bubble = new VBox(4, contentNode, metaRow);
            if (contactIp != null && !contactIp.isBlank() && messageId != null && !messageId.isBlank()) {
                sentMessageTicks
                        .computeIfAbsent(contactIp, key -> new LinkedHashMap<>())
                        .put(messageId, tickNode);
            }
        } else {
            if (messageId != null && !messageId.isBlank()) {
                Button menuButton = buildMessageMenuButton(contactIp, messageId, false);
                HBox metaRow = new HBox(6, timeNode, menuButton);
                metaRow.setAlignment(Pos.CENTER_LEFT);
                bubble = new VBox(4, contentNode, metaRow);
            } else {
                bubble = new VBox(4, contentNode, timeNode);
            }
        }
        bubble.getStyleClass().add(self ? "bubble-self" : "bubble-peer");
        bubble.setMaxWidth(420);

        StackPane avatar = ChatIcon.create(userLabel, self);
        HBox row = self ? new HBox(8, bubble, avatar) : new HBox(8, avatar, bubble);
        row.getStyleClass().addAll("message-row", self ? "message-row-self" : "message-row-peer");
        row.setMaxWidth(Double.MAX_VALUE);
        if (contactIp != null && !contactIp.isBlank() && messageId != null && !messageId.isBlank()) {
            messageRows
                    .computeIfAbsent(contactIp, key -> new LinkedHashMap<>())
                    .put(messageId, row);
            messageTexts
                    .computeIfAbsent(contactIp, key -> new LinkedHashMap<>())
                    .put(messageId, text == null ? "" : text);
            if (uniqueMessage) {
                uniquePayloads
                        .computeIfAbsent(contactIp, key -> new LinkedHashMap<>())
                        .put(messageId, uniqueText == null ? "" : uniqueText);
                uniquePlaceholders
                        .computeIfAbsent(contactIp, key -> new LinkedHashMap<>())
                        .put(messageId, uniquePlaceholder);
            }
        }

        messages.getChildren().add(row);
        scrollPane.setVvalue(1.0);
        if (uniqueMessage && contactIp != null && !contactIp.isBlank() && messageId != null && !messageId.isBlank()) {
            attachUniqueClickHandler(row, contactIp, messageId);
            if (viewedUnique) {
                markUniqueViewedVisual(contactIp, messageId);
            }
        }
        if (contactIp != null && !contactIp.isBlank() && messageId != null && !messageId.isBlank()) {
            String pinnedId = pinnedMessageByContact.get(contactIp);
            if (messageId.equals(pinnedId) && contactIp.equals(selectedContactIp)) {
                updatePinnedBar(contactIp);
            }
        }
    }

    private javafx.scene.Node buildMessageContentNode(String text, String messageId) {
        if (text != null && !text.isBlank() && (isImageMessage(messageId) || looksLikeBase64(text))) {
            try {
                String normalized = text.replaceAll("\\s", "");
                byte[] bytes = Base64.getDecoder().decode(normalized);
                Image image = new Image(new ByteArrayInputStream(bytes), IMAGE_MAX_WIDTH, 0, true, true);
                if (!image.isError() && image.getWidth() > 0) {
                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    imageView.setFitWidth(IMAGE_MAX_WIDTH);
                    return imageView;
                }
            } catch (Exception ex) {
                // fallback below
            }
        }

        Label textNode = new Label(text == null ? "" : text);
        textNode.getStyleClass().add("message-text");
        textNode.setWrapText(true);
        return textNode;
    }

    private Label buildUniquePlaceholder() {
        Label placeholder = new Label(" ");
        placeholder.getStyleClass().add("message-unique");
        placeholder.setMinHeight(16);
        return placeholder;
    }

    private boolean isUniqueMessage(String messageId) {
        return messageId != null && messageId.contains("-uniq-");
    }

    private String decodeUniqueMessage(String text) {
        return text == null ? "" : text;
    }

    private void attachUniqueClickHandler(HBox row, String contactIp, String messageId) {
        row.setOnMouseClicked(event -> {
            if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
                return;
            }
            Set<String> viewed = viewedUniqueMessages.computeIfAbsent(contactIp, key -> new HashSet<>());
            if (viewed.contains(messageId)) {
                return;
            }
            Map<String, String> payloads = uniquePayloads.get(contactIp);
            if (payloads == null) {
                return;
            }
            String payload = payloads.get(messageId);
            if (payload == null) {
                return;
            }
            showUniquePopup(payload);
            viewed.add(messageId);
            chatController.markUniqueMessageViewed(contactIp, messageId);
            Map<String, String> payloadsMutable = uniquePayloads.get(contactIp);
            if (payloadsMutable != null) {
                payloadsMutable.remove(messageId);
            }
            markUniqueViewedVisual(contactIp, messageId);
        });
    }

    private boolean isUniqueViewed(String contactIp, String messageId) {
        if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
            return false;
        }
        Set<String> viewed = viewedUniqueMessages.get(contactIp);
        if (viewed != null && viewed.contains(messageId)) {
            return true;
        }
        return false;
    }

    private void markUniqueViewedVisual(String contactIp, String messageId) {
        if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
            return;
        }
        Map<String, Label> placeholders = uniquePlaceholders.get(contactIp);
        if (placeholders != null) {
            Label placeholder = placeholders.get(messageId);
            if (placeholder != null) {
                placeholder.setText("Visto");
                placeholder.getStyleClass().add("message-unique-viewed");
            }
        }
    }

    private void showUniquePopup(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.setTitle("Mensaje único");
        alert.setHeaderText("Mensaje único");
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }

    private Button buildMessageMenuButton(String contactIp, String messageId, boolean allowDelete) {
        Button menuButton = new Button("▾");
        menuButton.getStyleClass().add("message-menu");
        menuButton.setFocusTraversable(false);

        ContextMenu menu = new ContextMenu();
        MenuItem pinItem = new MenuItem();
        updatePinMenuLabel(pinItem, contactIp, messageId);
        pinItem.setOnAction(event -> chatController.togglePinMessage(contactIp, messageId));
        menu.getItems().add(pinItem);

        if (allowDelete) {
            MenuItem deleteItem = new MenuItem("Eliminar");
            deleteItem.setOnAction(event -> chatController.deleteMessage(contactIp, messageId));
            menu.getItems().add(deleteItem);
        }

        menu.setOnShowing(event -> updatePinMenuLabel(pinItem, contactIp, messageId));

        menuButton.setOnAction(event -> {
            if (!menu.isShowing()) {
                menu.show(menuButton, Side.BOTTOM, 0, 0);
            }
        });
        return menuButton;
    }

    private void updatePinMenuLabel(MenuItem pinItem, String contactIp, String messageId) {
        if (pinItem == null) {
            return;
        }
        pinItem.setText(isPinned(contactIp, messageId) ? "Quitar fijado" : "Fijar");
    }

    private boolean isImageMessage(String messageId) {
        return messageId != null && messageId.contains("-img-");
    }

    private boolean looksLikeBase64(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s", "");
        if (normalized.length() < 120) {
            return false;
        }
        if (normalized.length() % 4 != 0) {
            return false;
        }
        return normalized.matches("^[A-Za-z0-9+/=]+$");
    }

    private boolean isPinned(String contactIp, String messageId) {
        if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
            return false;
        }
        String current = pinnedMessageByContact.get(contactIp);
        return messageId.equals(current);
    }

    private void updatePinnedBar(String contactIp) {
        if (pinnedBar == null || pinnedText == null) {
            return;
        }
        String pinnedId = pinnedMessageByContact.get(contactIp);
        if (pinnedId == null || pinnedId.isBlank()) {
            pinnedBar.setVisible(false);
            pinnedBar.setManaged(false);
            return;
        }
        pinnedText.setText(resolvePinnedPreview(contactIp, pinnedId));
        pinnedBar.setVisible(true);
        pinnedBar.setManaged(true);
    }

    private void chooseTheme() {
        Map<String, String> options = themeOptions();
        String currentLabel = options.getOrDefault(currentThemeId, options.get("default"));
        ChoiceDialog<String> dialog = new ChoiceDialog<>(currentLabel, options.values());
        dialog.setTitle("Seleccionar tema");
        dialog.setHeaderText("Elige un tema para la interfaz");
        dialog.setContentText("Tema:");
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
        }
        dialog.showAndWait().ifPresent(label -> {
            String themeId = resolveThemeId(options, label);
            if (themeId != null) {
                chatController.setTheme(themeId);
            }
        });
    }

    private Map<String, String> themeOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("default", "Azul");
        options.put("sunset", "Naranja");
        options.put("forest", "Verde");
        options.put("midnight", "Morado");
        options.put("citrus", "Amarillo");
        return options;
    }

    private String resolveThemeId(Map<String, String> options, String label) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (Objects.equals(entry.getValue(), label)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void applyTheme(String themeId) {
        if (rootPane == null) {
            return;
        }
        String normalized = themeId == null ? "default" : themeId.trim();
        if (normalized.isEmpty()) {
            normalized = "default";
        }
        String themeClass = "theme-" + normalized;
        rootPane.getStyleClass().removeIf(name -> name.startsWith("theme-"));
        rootPane.getStyleClass().add(themeClass);
    }

    private void updateConversationAvailability(String contactIp) {
        if (offlineBar == null || input == null || sendButton == null) {
            return;
        }
        boolean online = presenceByContact.getOrDefault(contactIp, true);
        boolean disable = !online;
        offlineBar.setVisible(disable);
        offlineBar.setManaged(disable);
        input.setDisable(disable);
        sendButton.setDisable(disable);
        if (attachButton != null) {
            attachButton.setDisable(disable);
        }
        if (shareButton != null) {
            shareButton.setDisable(disable);
        }
        if (buzzButton != null) {
            buzzButton.setDisable(disable);
        }
        if (uniqueButton != null) {
            uniqueButton.setDisable(disable);
        }
    }

    private String resolvePinnedPreview(String contactIp, String messageId) {
        Map<String, String> texts = messageTexts.get(contactIp);
        String text = texts == null ? null : texts.get(messageId);
        if (text == null) {
            return "Mensaje fijado";
        }
        if (isUniqueMessage(messageId)) {
            return "Mensaje único";
        }
        if (isImageMessage(messageId) || looksLikeBase64(text)) {
            return "Imagen";
        }
        String clean = text.trim();
        if (clean.length() > 90) {
            return clean.substring(0, 90) + "...";
        }
        return clean;
    }

    private void unpinSelectedMessage() {
        String cleanIp = selectedContactIp == null ? "" : selectedContactIp.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        String pinnedId = pinnedMessageByContact.get(cleanIp);
        if (pinnedId == null || pinnedId.isBlank()) {
            return;
        }
        chatController.togglePinMessage(cleanIp, pinnedId);
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
            updatePinnedBar(cleanIp);
            updateConversationAvailability(cleanIp);
        });
        if (connect) {
            chatController.connect(cleanIp);
        }
    }

    private void connectSelectedContact() {
        String cleanIp = resolveConnectTargetIp();
        if (cleanIp.isEmpty()) {
            addSystemMessage("Selecciona un contacto primero.");
            return;
        }
        addSystemMessage("Conectando a " + cleanIp + "...");
        selectContact(cleanIp, true);
    }

    private String resolveConnectTargetIp() {
        String cleanIp = selectedContactIp == null ? "" : selectedContactIp.trim();
        if (!cleanIp.isEmpty()) {
            return cleanIp;
        }
        String remoteIp = remoteIpValue == null || remoteIpValue.getText() == null
                ? ""
                : remoteIpValue.getText().trim();
        if (!remoteIp.isEmpty() && contactItems.containsKey(remoteIp)) {
            return remoteIp;
        }
        if (contactItems.size() == 1) {
            return contactItems.keySet().iterator().next();
        }
        return "";
    }

    private void renderChatHistory(String contactIp) {
        String cleanIp = contactIp == null ? "" : contactIp.trim();
        if (cleanIp.isEmpty()) {
            return;
        }
        List<ChatMessage> history = chatController.getChatHistory(cleanIp);
        Platform.runLater(() -> {
            messages.getChildren().clear();
            sentMessageTicks.remove(cleanIp);
            messageRows.remove(cleanIp);
            messageTexts.remove(cleanIp);
            uniquePayloads.remove(cleanIp);
            viewedUniqueMessages.remove(cleanIp);
            uniquePlaceholders.remove(cleanIp);
            Set<String> viewedSet = new HashSet<>();
            for (ChatMessage chatMessage : history) {
                String senderLabel = chatMessage.getSenderLabel();
                if (senderLabel == null || senderLabel.isBlank()) {
                    senderLabel = cleanIp;
                }
                if (chatMessage.isUniqueViewed() && chatMessage.getMessageId() != null) {
                    viewedSet.add(chatMessage.getMessageId());
                }
                addChatBubbleNow(
                        cleanIp,
                        chatMessage.getText(),
                        chatMessage.isSelfSent(),
                        formatHistoryTime(chatMessage.getSentAtMillis()),
                        senderLabel,
                        chatMessage.getMessageId()
                );
            }
            if (!viewedSet.isEmpty()) {
                viewedUniqueMessages.put(cleanIp, viewedSet);
            }
        });
    }

    private void onMessageSeen(String contactIp, String messageId) {
        if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
            return;
        }
        Platform.runLater(() -> {
            Map<String, Label> ticks = sentMessageTicks.get(contactIp);
            if (ticks == null) {
                return;
            }
            Label tick = ticks.get(messageId);
            if (tick != null) {
                tick.setText("✓✓");
                return;
            }
            Label last = null;
            for (Label value : ticks.values()) {
                last = value;
            }
            if (last != null) {
                last.setText("✓✓");
            }
        });
    }

    private void onMessageDeleted(String contactIp, String messageId) {
        if (contactIp == null || contactIp.isBlank() || messageId == null || messageId.isBlank()) {
            return;
        }
        Platform.runLater(() -> {
            Map<String, HBox> rows = messageRows.get(contactIp);
            if (rows == null) {
                return;
            }
            HBox row = rows.remove(messageId);
            if (row != null) {
                messages.getChildren().remove(row);
            }
            Map<String, Label> ticks = sentMessageTicks.get(contactIp);
            if (ticks != null) {
                ticks.remove(messageId);
            }
            Map<String, String> texts = messageTexts.get(contactIp);
            if (texts != null) {
                texts.remove(messageId);
            }
            Map<String, String> uniqueMap = uniquePayloads.get(contactIp);
            if (uniqueMap != null) {
                uniqueMap.remove(messageId);
            }
            Map<String, Label> placeholders = uniquePlaceholders.get(contactIp);
            if (placeholders != null) {
                placeholders.remove(messageId);
            }
            Set<String> viewed = viewedUniqueMessages.get(contactIp);
            if (viewed != null) {
                viewed.remove(messageId);
            }
        });
    }

    private void onMessagePinned(String contactIp, String messageId, boolean pinned) {
        if (contactIp == null || contactIp.isBlank()) {
            return;
        }
        Platform.runLater(() -> {
            if (pinned) {
                pinnedMessageByContact.put(contactIp, messageId);
            } else {
                pinnedMessageByContact.remove(contactIp);
            }
            if (contactIp.equals(selectedContactIp)) {
                updatePinnedBar(contactIp);
            }
        });
    }

    private void sendBuzz() {
        String cleanIp = selectedContactIp == null ? "" : selectedContactIp.trim();
        if (cleanIp.isEmpty()) {
            addSystemMessage("Selecciona un contacto primero.");
            return;
        }
        chatController.sendBuzz(cleanIp);
    }

    private void onBuzz(String contactIp) {
        if (contactIp == null || contactIp.isBlank()) {
            return;
        }
        Platform.runLater(() -> {
            long now = System.currentTimeMillis();
            Long last = lastBuzzAt.get(contactIp);
            if (last != null && now - last < BUZZ_MIN_INTERVAL_MS) {
                return;
            }
            lastBuzzAt.put(contactIp, now);
            ContactStatusRenderer.ContactItemView itemView = contactItems.get(contactIp);
            if (itemView != null) {
                contactRenderer.renderBuzz(itemView, true);
                PauseTransition previous = buzzTimers.get(contactIp);
                if (previous != null) {
                    previous.stop();
                }
                PauseTransition hide = new PauseTransition(Duration.seconds(2));
                hide.setOnFinished(event -> contactRenderer.renderBuzz(itemView, false));
                buzzTimers.put(contactIp, hide);
                hide.play();
            }
            triggerBuzzShake();
        });
    }

    private void triggerBuzzShake() {
        if (scrollPane == null) {
            return;
        }
        if (buzzAnimation != null && buzzAnimation.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        scrollPane.setTranslateX(0);
        TranslateTransition transition = new TranslateTransition(Duration.millis(40), scrollPane);
        transition.setByX(6);
        transition.setAutoReverse(true);
        transition.setCycleCount(50);
        transition.setOnFinished(event -> scrollPane.setTranslateX(0));
        buzzAnimation = transition;
        transition.play();
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
