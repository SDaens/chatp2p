package edu.upb.chatupb_v2.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class ContactStatusRenderer {

    public ContactItemView create(String label, EventHandler<ActionEvent> onOpenChat) {
        Region statusDot = new Region();
        statusDot.getStyleClass().addAll("contact-status-dot", "contact-status-offline");

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("contact-item-label");
        nameLabel.setWrapText(false);

        Label buzzLabel = new Label("🔔");
        buzzLabel.getStyleClass().add("contact-buzz-icon");
        buzzLabel.setVisible(false);
        buzzLabel.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox content = new HBox(6, statusDot, nameLabel, spacer, buzzLabel);
        content.getStyleClass().add("contact-item-graphic");

        Button button = new Button();
        button.getStyleClass().add("contact-item");
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(onOpenChat);
        return new ContactItemView(button, statusDot, nameLabel, buzzLabel);
    }

    public void renderStatus(ContactItemView itemView, boolean online) {
        itemView.statusDot().getStyleClass().removeAll("contact-status-online", "contact-status-offline");
        itemView.statusDot().getStyleClass().add(online ? "contact-status-online" : "contact-status-offline");
    }

    public void renderBuzz(ContactItemView itemView, boolean active) {
        itemView.buzzLabel().setVisible(active);
        itemView.buzzLabel().setManaged(active);
    }

    public record ContactItemView(Button button, Region statusDot, Label label, Label buzzLabel) {
    }
}
