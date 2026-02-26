package edu.upb.chatupb_v2.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class ContactStatusRenderer {

    public ContactItemView create(String ip, EventHandler<ActionEvent> onClick) {
        Region statusDot = new Region();
        statusDot.getStyleClass().addAll("contact-status-dot", "contact-status-offline");

        Label ipLabel = new Label(ip);
        ipLabel.getStyleClass().add("contact-item-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox content = new HBox(10, statusDot, ipLabel, spacer);
        content.getStyleClass().add("contact-item-graphic");

        Button button = new Button();
        button.getStyleClass().add("contact-item");
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(onClick);

        return new ContactItemView(button, statusDot);
    }

    public void renderStatus(ContactItemView itemView, boolean online) {
        itemView.statusDot().getStyleClass().removeAll("contact-status-online", "contact-status-offline");
        itemView.statusDot().getStyleClass().add(online ? "contact-status-online" : "contact-status-offline");
    }

    public record ContactItemView(Button button, Region statusDot) {
    }
}
