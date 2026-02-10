package edu.upb.chatupb_v2.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Objects;

public class ChatUIApp extends Application {

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(buildHeader());
        root.setCenter(buildConversation());
        root.setBottom(buildComposer());

        Scene scene = new Scene(root, 1024, 680);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/edu/upb/chatupb_v2/ui/chat-ui.css")
        ).toExternalForm());

        stage.setTitle("ChatUPB - Vista previa P2P");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildHeader() {
        Label title = new Label("ChatUPB");
        title.getStyleClass().add("title");

        VBox titleBox = new VBox(0, title);
        titleBox.getStyleClass().add("title-box");

        VBox statusBox = new VBox(6,
                buildMetaRow("IP local", "192.168.0.24"),
                buildMetaRow("IP remota", "127.0.0.1")
        );
        statusBox.getStyleClass().add("status-box");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(24, titleBox, spacer, statusBox);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return new VBox(header);
    }

    private HBox buildMetaRow(String label, String value) {
        Label key = new Label(label);
        key.getStyleClass().add("meta-key");
        Label data = new Label(value);
        data.getStyleClass().add("meta-value");
        HBox row = new HBox(8, key, data);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private ScrollPane buildConversation() {
        VBox messages = new VBox(18);
        messages.getStyleClass().add("messages");

        ScrollPane scrollPane = new ScrollPane(messages);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("message-scroll");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVvalue(1.0);
        return scrollPane;
    }

    private VBox buildComposer() {
        Label hint = new Label("Escribe un mensaje");
        hint.getStyleClass().add("composer-hint");

        TextArea input = new TextArea();
        input.setPromptText("Escribe un mensaje...");
        input.getStyleClass().add("composer-input");
        input.setPrefRowCount(2);
        input.setWrapText(true);

        Button attach = new Button("Adjuntar imagen");
        attach.getStyleClass().add("secondary-button");
        Button send = new Button("Enviar");
        send.getStyleClass().add("primary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(12, attach, spacer, send);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox composer = new VBox(10, hint, input, actions);
        composer.getStyleClass().add("composer");
        composer.setPadding(new Insets(18, 26, 22, 26));
        return composer;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
