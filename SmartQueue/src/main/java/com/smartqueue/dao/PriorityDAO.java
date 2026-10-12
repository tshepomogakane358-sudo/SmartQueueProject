package com.smartqueue.dao;

import com.smartqueue.db.DatabaseConnection;
import com.smartqueue.model.Priority;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PriorityDAO {

    public List<Priority> getAllPriorities() {

        List<Priority> list = new ArrayList<>();

        String sql = "SELECT * FROM Priority ORDER BY rank_order ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Priority p = new Priority();
                p.setPriorityId(rs.getInt("priority_id"));
                p.setCategory(rs.getString("category"));
                p.setRankOrder(rs.getInt("rank_order"));
                list.add(p);
            }

        } catch (SQLException e) {
            System.out.println("Error fetching priorities: " + e.getMessage());
        }

        return list;
    }



    public Priority getPriorityById(int priorityId) {

        String sql = "SELECT * FROM Priority WHERE priority_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, priorityId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Priority p = new Priority();
                    p.setPriorityId(rs.getInt("priority_id"));
                    p.setCategory(rs.getString("category"));
                    p.setRankOrder(rs.getInt("rank_order"));
                    return p;
                }
            }

        } catch (SQLException e) {
            System.out.println("Error fetching priority: " + e.getMessage());
        }

        return null;
    }
}