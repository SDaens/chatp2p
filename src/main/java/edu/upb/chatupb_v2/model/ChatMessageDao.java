package edu.upb.chatupb_v2.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageDao {

    public ChatMessageDao() {
        ensureTable();
    }

    private void ensureTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS chat_message (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contact_ip TEXT NOT NULL,
                    text TEXT NOT NULL,
                    self_sent INTEGER NOT NULL,
                    sender_label TEXT,
                    sent_at_millis INTEGER NOT NULL
                )
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.execute();
        } catch (SQLException ex) {
            throw new IllegalStateException("no se pudo crear la tabla chat_message", ex);
        }
    }

    public void save(String contactIp, String text, boolean selfSent, String senderLabel, long sentAtMillis) throws SQLException {
        if (contactIp == null || contactIp.isBlank() || text == null || text.isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO chat_message(contact_ip, text, self_sent, sender_label, sent_at_millis)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, contactIp.trim());
            pst.setString(2, text);
            pst.setInt(3, selfSent ? 1 : 0);
            pst.setString(4, senderLabel);
            pst.setLong(5, sentAtMillis);
            pst.executeUpdate();
        }
    }

    public List<ChatMessage> findByContactIp(String contactIp) throws SQLException {
        List<ChatMessage> messages = new ArrayList<>();
        if (contactIp == null || contactIp.isBlank()) {
            return messages;
        }
        String sql = """
                SELECT id, contact_ip, text, self_sent, sender_label, sent_at_millis
                FROM chat_message
                WHERE contact_ip = ?
                ORDER BY sent_at_millis ASC, id ASC
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, contactIp.trim());
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    ChatMessage message = new ChatMessage();
                    message.setId(rs.getLong("id"));
                    message.setContactIp(rs.getString("contact_ip"));
                    message.setText(rs.getString("text"));
                    message.setSelfSent(rs.getInt("self_sent") == 1);
                    message.setSenderLabel(rs.getString("sender_label"));
                    message.setSentAtMillis(rs.getLong("sent_at_millis"));
                    messages.add(message);
                }
            }
        }
        return messages;
    }
}
