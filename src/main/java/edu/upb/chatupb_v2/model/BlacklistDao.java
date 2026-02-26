package edu.upb.chatupb_v2.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BlacklistDao {

    public BlacklistDao() {
        ensureTable();
    }

    private void ensureTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS blacklist (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    requester_id TEXT NOT NULL UNIQUE,
                    requester_name TEXT,
                    remote_ip TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.execute();
        } catch (SQLException ex) {
            throw new IllegalStateException("no se pudo crear la blacklist", ex);
        }
    }

    public boolean isBlocked(String requesterId) throws SQLException {
        if (requesterId == null || requesterId.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM blacklist WHERE requester_id = ? LIMIT 1";
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, requesterId.trim());
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void addBlockedRequester(String requesterId, String requesterName, String remoteIp) throws SQLException {
        if (requesterId == null || requesterId.isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO blacklist(requester_id, requester_name, remote_ip)
                VALUES (?, ?, ?)
                ON CONFLICT(requester_id) DO UPDATE SET
                    requester_name = excluded.requester_name,
                    remote_ip = excluded.remote_ip,
                    created_at = CURRENT_TIMESTAMP
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, requesterId.trim());
            pst.setString(2, requesterName);
            pst.setString(3, remoteIp);
            pst.executeUpdate();
        }
    }
}
