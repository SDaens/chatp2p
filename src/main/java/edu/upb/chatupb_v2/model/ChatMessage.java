package edu.upb.chatupb_v2.model;

public class ChatMessage implements Model {

    private long id;
    private String messageId;
    private String contactIp;
    private String text;
    private boolean selfSent;
    private String senderLabel;
    private long sentAtMillis;

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
    }

    public String getContactIp() {
        return contactIp;
    }

    public void setContactIp(String contactIp) {
        this.contactIp = contactIp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isSelfSent() {
        return selfSent;
    }

    public void setSelfSent(boolean selfSent) {
        this.selfSent = selfSent;
    }

    public String getSenderLabel() {
        return senderLabel;
    }

    public void setSenderLabel(String senderLabel) {
        this.senderLabel = senderLabel;
    }

    public long getSentAtMillis() {
        return sentAtMillis;
    }

    public void setSentAtMillis(long sentAtMillis) {
        this.sentAtMillis = sentAtMillis;
    }
}
