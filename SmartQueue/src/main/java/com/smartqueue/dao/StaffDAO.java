package com.smartqueue.dao;

import com.smartqueue.db.DatabaseConnection;
import com.smartqueue.model.Staff;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StaffDAO {

    public boolean createStaff(Staff staff) {
        String sql = "INSERT INTO StaffAccount (name, username, password_hash) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, staff.getName());
            stmt.setString(2, staff.getUsername());
            stmt.setString(3, staff.getPasswordHash());
            stmt.executeUpdate();
            return true;

        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("Error: Staff username already exists.");
            return false;
        } catch (SQLException e) {
            System.out.println("Error creating staff account: " + e.getMessage());
            return false;
        }
    }


    public Staff getStaffById(int staffId) {
        String sql = "SELECT * FROM StaffAccount WHERE staff_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, staffId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }

        } catch (SQLException e) {
            System.out.println("Error finding staff account: " + e.getMessage());
        }
        return null;
    }


    public Staff getStaffByUsername(String username) {
        String sql = "SELECT * FROM StaffAccount WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }

        } catch (SQLException e) {
            System.out.println("Error finding staff account: " + e.getMessage());
        }
        return null;
    }


    public List<Staff> getAllStaff() {
        List<Staff> list = new ArrayList<>();
        String sql = "SELECT * FROM StaffAccount ORDER BY name";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) list.add(mapRow(rs));

        } catch (SQLException e) {
            System.out.println("Error fetching staff accounts: " + e.getMessage());
        }
        return list;
    }


    public boolean updateStaff(int staffId, String name, String username) {
        String sql = "UPDATE StaffAccount SET name = ?, username = ? WHERE staff_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, username);
            stmt.setInt(3, staffId);
            return stmt.executeUpdate() > 0;

        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("Error: Staff username already in use.");
            return false;
        } catch (SQLException e) {
            System.out.println("Error updating staff account: " + e.getMessage());
            return false;
        }
    }


    public boolean updatePassword(int staffId, String newPasswordHash) {
        String sql = "UPDATE StaffAccount SET password_hash = ? WHERE staff_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, staffId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Error updating staff password: " + e.getMessage());
            return false;
        }
    }


    public boolean setActive(int staffId, boolean active) {
        String sql = "UPDATE StaffAccount SET active = ? WHERE staff_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, active);
            stmt.setInt(2, staffId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Error changing staff status: " + e.getMessage());
            return false;
        }
    }


    public boolean deleteStaff(int staffId) {
        String sql = "DELETE FROM StaffAccount WHERE staff_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, staffId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Error deleting staff account: " + e.getMessage());
            return false;
        }
    }


    private Staff mapRow(ResultSet rs) throws SQLException {
        Staff s = new Staff();
        s.setStaffId(rs.getInt("staff_id"));
        s.setName(rs.getString("name"));
        s.setUsername(rs.getString("username"));
        s.setPasswordHash(rs.getString("password_hash"));
        s.setActive(rs.getBoolean("active"));
        return s;
    }
}