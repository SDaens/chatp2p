package edu.upb.chatupb_v2.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.io.InputStream;

import static java.util.Objects.requireNonNullElse;

public final class ChatIcon {

    private static final double SIZE = 34;

    private ChatIcon() {
    }

    public static StackPane create(String userLabel, boolean self) {
        return create(userLabel, self, null);
    }

    public static StackPane create(String userLabel, boolean self, String profileImagePath) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().addAll("chat-avatar", self ? "chat-avatar-self" : "chat-avatar-peer");
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(SIZE, SIZE);
        avatar.setPrefSize(SIZE, SIZE);
        avatar.setMaxSize(SIZE, SIZE);

        ImageView photo = createPhoto(profileImagePath);
        if (photo != null) {
            avatar.getChildren().add(photo);
            return avatar;
        }

        String initials = initialsOf(userLabel);

        Label initialsLabel = new Label(initials);
        initialsLabel.getStyleClass().add("chat-avatar-text");

        avatar.getChildren().add(initialsLabel);
        return avatar;
    }

    private static ImageView createPhoto(String profileImagePath) {
        String path = requireNonNullElse(profileImagePath, "").trim();
        if (path.isEmpty()) {
            return null;
        }
        InputStream stream = ChatIcon.class.getResourceAsStream(path);
        if (stream == null) {
            return null;
        }
        Image image = new Image(stream);
        ImageView view = new ImageView(image);
        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(false);
        view.setClip(new Circle(SIZE / 2, SIZE / 2, SIZE / 2));
        return view;
    }

    private static String initialsOf(String rawUserLabel) {
        String clean = rawUserLabel == null ? "" : rawUserLabel.trim();
        if (clean.isEmpty()) {
            return "U";
        }
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        String first = parts[0].substring(0, 1);
        String second = parts[parts.length - 1].substring(0, 1);
        return (first + second).toUpperCase();
    }
}
