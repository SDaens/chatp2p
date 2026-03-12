package edu.upb.chatupb_v2.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LocalProfileDao {

    public LocalProfileDao() {
        ensureTable();
    }

    private void ensureTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS local_profile (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    display_name TEXT NOT NULL
                )
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.execute();
        } catch (SQLException ex) {
            throw new IllegalStateException("no se pudo crear la tabla local_profile", ex);
        }
    }

    public String loadName() throws SQLException {
        String sql = "SELECT display_name FROM local_profile WHERE id = 1";
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return rs.getString("display_name");
        }
    }

    public void saveName(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO local_profile(id, display_name)
                VALUES (1, ?)
                ON CONFLICT(id) DO UPDATE SET
                    display_name = excluded.display_name
                """;
        try (Connection conn = ConnectionDB.getInstance().getConection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, name.trim());
            pst.executeUpdate();
        }
    }
}
