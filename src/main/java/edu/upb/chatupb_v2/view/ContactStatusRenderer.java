package edu.upb.chatupb_v2.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ContactStatusRenderer {

    public ContactItemView create(String name, String ip, EventHandler<ActionEvent> onOpenChat) {
        Region statusDot = new Region();
        statusDot.getStyleClass().addAll("contact-status-dot", "contact-status-offline");

        Label nameLabel = new Label(name == null || name.isBlank() ? ip : name);
        nameLabel.getStyleClass().add("contact-item-label");
        nameLabel.setWrapText(false);

        Label ipLabel = new Label(ip);
        ipLabel.getStyleClass().add("contact-item-ip");
        ipLabel.setWrapText(false);

        VBox labelBox = new VBox(1, nameLabel, ipLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox content = new HBox(8, statusDot, labelBox, spacer);
        content.getStyleClass().add("contact-item-graphic");

        Button button = new Button();
        button.getStyleClass().add("contact-item");
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(onOpenChat);
        return new ContactItemView(button, statusDot, nameLabel, ipLabel);
    }

    public void renderStatus(ContactItemView itemView, boolean online) {
        itemView.statusDot().getStyleClass().removeAll("contact-status-online", "contact-status-offline");
        itemView.statusDot().getStyleClass().add(online ? "contact-status-online" : "contact-status-offline");
    }

    public record ContactItemView(Button button, Region statusDot, Label nameLabel, Label ipLabel) {
    }
}
