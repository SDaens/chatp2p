package edu.upb.chatupb_v2.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AppProfileDao {
    private static final String KEY_DISPLAY_NAME = "display_name";

    public AppProfileDao() {
        ensureTable();
    }

    public String getDisplayName() throws SQLException {
        String sql = "SELECT value FROM app_profile WHERE key = ?";
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, KEY_DISPLAY_NAME);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    return "";
                }
                String value = rs.getString("value");
                return value == null ? "" : value.trim();
            }
        }
    }

    public void saveDisplayName(String displayName) throws SQLException {
        String cleanName = displayName == null ? "" : displayName.trim();
        String sql = """
                INSERT INTO app_profile(key, value)
                VALUES(?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, KEY_DISPLAY_NAME);
            pst.setString(2, cleanName);
            pst.executeUpdate();
        }
    }

    private void ensureTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS app_profile (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.execute();
        } catch (SQLException ex) {
            throw new IllegalStateException("no se pudo crear la tabla app_profile", ex);
        }
    }
}
