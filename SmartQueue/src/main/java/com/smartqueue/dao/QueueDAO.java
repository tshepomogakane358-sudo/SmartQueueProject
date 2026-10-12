package com.smartqueue.dao;

import com.smartqueue.db.DatabaseConnection;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueRecord;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QueueDAO {

    public boolean addToQueue(QueueEntry entry) {

        String sql = "INSERT INTO Queue " +
                "(patient_id, department_id, priority_id, queue_number, status, estimated_wait_min, queue_date) " +
                "VALUES (?, ?, ?, ?, 'Waiting', ?, CURRENT_DATE)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, entry.getPatientId());
            stmt.setInt(2, entry.getDepartmentId());
            stmt.setInt(3, entry.getPriorityId());
            stmt.setString(4, entry.getQueueNumber());
            stmt.setInt(5, entry.getEstimatedWaitMin());

            stmt.executeUpdate();

            return true;

        } catch (SQLException e) {
            System.out.println("Error adding to queue: " + e.getMessage());
            return false;
        }
    }



    public List<QueueEntry> getTodayQueue(int departmentId) {

        List<QueueEntry> queue = new ArrayList<>();

        String sql = "SELECT q.* FROM Queue q " +
                "JOIN Priority p ON q.priority_id = p.priority_id " +
                "WHERE q.department_id = ? " +
                "AND q.queue_date = CURRENT_DATE " +
                "AND q.status IN ('Waiting', 'Called', 'Serving') " +
                "ORDER BY p.rank_order ASC, q.created_at ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, departmentId);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    QueueEntry entry = new QueueEntry();

                    entry.setQueueId(rs.getInt("queue_id"));
                    entry.setPatientId(rs.getInt("patient_id"));
                    entry.setDepartmentId(rs.getInt("department_id"));
                    entry.setPriorityId(rs.getInt("priority_id"));
                    entry.setQueueNumber(rs.getString("queue_number"));
                    entry.setStatus(rs.getString("status"));
                    entry.setEstimatedWaitMin(
                            rs.getInt("estimated_wait_min")
                    );

                    Date queueDate = rs.getDate("queue_date");

                    if (queueDate != null) {
                        entry.setQueueDate(queueDate.toLocalDate());
                    }

                    queue.add(entry);
                }
            }

        } catch (SQLException e) {
            System.out.println("Error fetching queue: " + e.getMessage());
        }

        return queue;
    }



    public QueueEntry callNextPatient(int departmentId) {

        String sql = "SELECT q.* FROM Queue q " +
                "JOIN Priority p ON q.priority_id = p.priority_id " +
                "WHERE q.department_id = ? " +
                "AND q.queue_date = CURRENT_DATE " +
                "AND q.status = 'Waiting' " +
                "ORDER BY p.rank_order ASC, q.created_at ASC " +
                "LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, departmentId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    QueueEntry next = new QueueEntry();

                    next.setQueueId(rs.getInt("queue_id"));
                    next.setPatientId(rs.getInt("patient_id"));
                    next.setDepartmentId(rs.getInt("department_id"));
                    next.setPriorityId(rs.getInt("priority_id"));
                    next.setQueueNumber(rs.getString("queue_number"));
                    next.setStatus("Called");
                    next.setEstimatedWaitMin(
                            rs.getInt("estimated_wait_min")
                    );

                    Date queueDate = rs.getDate("queue_date");

                    if (queueDate != null) {
                        next.setQueueDate(queueDate.toLocalDate());
                    }

                    // Update the patient's status to Called
                    updateQueueStatus(next.getQueueId(), "Called");

                    return next;
                }
            }

        } catch (SQLException e) {
            System.out.println("Error calling next patient: " + e.getMessage());
        }

        return null;
    }



    public boolean updateQueueStatus(int queueId, String newStatus) {


        String timing = "";
        if ("Called".equals(newStatus)) {
            timing = ", called_at = COALESCE(called_at, CURRENT_TIMESTAMP)";
        } else if ("Serving".equals(newStatus)) {
            timing = ", called_at = COALESCE(called_at, CURRENT_TIMESTAMP)"
                    + ", started_at = COALESCE(started_at, CURRENT_TIMESTAMP)";
        } else if ("Done".equals(newStatus)) {
            timing = ", called_at = COALESCE(called_at, CURRENT_TIMESTAMP)"
                    + ", completed_at = CURRENT_TIMESTAMP";
        }

        try {
            return runStatusUpdate(
                    "UPDATE Queue SET status = ?" + timing + " WHERE queue_id = ?",
                    newStatus, queueId);

        } catch (SQLException e) {
            if (timing.isEmpty()) {
                System.out.println("Error updating queue status: " + e.getMessage());
                return false;
            }

            System.out.println("Warning: queue timing could not be recorded (" + e.getMessage()
                    + "). Run queue_timing.sql to enable waiting-time reports.");
            try {
                return runStatusUpdate("UPDATE Queue SET status = ? WHERE queue_id = ?",
                        newStatus, queueId);
            } catch (SQLException e2) {
                System.out.println("Error updating queue status: " + e2.getMessage());
                return false;
            }
        }
    }


    private boolean runStatusUpdate(String sql, String status, int queueId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setInt(2, queueId);
            return stmt.executeUpdate() > 0;
        }
    }

    public String generateQueueNumber(String deptPrefix, int departmentId) {


        String sql = "SELECT queue_number FROM Queue " +
                "WHERE department_id = ? " +
                "AND queue_date = CURRENT_DATE";

        int highest = 0;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, departmentId);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String number = rs.getString("queue_number");
                    if (number == null) continue;

                    int dash = number.lastIndexOf('-');
                    if (dash < 0) continue;

                    try {
                        int n = Integer.parseInt(number.substring(dash + 1));
                        if (n > highest) highest = n;
                    } catch (NumberFormatException ignored) {
                        // skip numbers that don't follow the PREFIX-001 pattern
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println("Error generating queue number: " + e.getMessage());
        }

        return deptPrefix + "-" + String.format("%03d", highest + 1);
    }



    public int countServedToday(int departmentId) {

        String sql = "SELECT COUNT(*) FROM Queue " +
                "WHERE department_id = ? " +
                "AND queue_date = CURRENT_DATE " +
                "AND status = 'Done'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, departmentId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            System.out.println("Error counting served patients: " + e.getMessage());
        }

        return 0;
    }



    public int countNoShowToday(int departmentId) {

        String sql = "SELECT COUNT(*) FROM Queue " +
                "WHERE department_id = ? " +
                "AND queue_date = CURRENT_DATE " +
                "AND status = 'No-show'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, departmentId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            System.out.println("Error counting no-shows: " + e.getMessage());
        }

        return 0;
    }



    public QueueEntry getLatestQueueEntryForPatient(int patientId) {

        String sql = "SELECT * FROM Queue " +
                "WHERE patient_id = ? " +
                "AND queue_date = CURRENT_DATE " +
                "ORDER BY queue_id DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, patientId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    QueueEntry entry = new QueueEntry();
                    entry.setQueueId(rs.getInt("queue_id"));
                    entry.setPatientId(rs.getInt("patient_id"));
                    entry.setDepartmentId(rs.getInt("department_id"));
                    entry.setPriorityId(rs.getInt("priority_id"));
                    entry.setQueueNumber(rs.getString("queue_number"));
                    entry.setStatus(rs.getString("status"));
                    entry.setEstimatedWaitMin(rs.getInt("estimated_wait_min"));

                    Date queueDate = rs.getDate("queue_date");
                    if (queueDate != null) entry.setQueueDate(queueDate.toLocalDate());

                    return entry;
                }
            }

        } catch (SQLException e) {
            System.out.println("Error fetching patient's queue entry: " + e.getMessage());
        }

        return null;
    }



    public boolean updateQueuePriority(int queueId, int priorityId) {

        String sql = "UPDATE Queue SET priority_id = ? WHERE queue_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, priorityId);
            stmt.setInt(2, queueId);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Error updating queue priority: " + e.getMessage());
            return false;
        }
    }



    public QueueEntry getQueueEntryForAppointment(int patientId, int departmentId, LocalDate date) {

        String sql = "SELECT * FROM Queue " +
                "WHERE patient_id = ? AND department_id = ? AND queue_date = ? " +
                "ORDER BY queue_id DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, patientId);
            stmt.setInt(2, departmentId);
            stmt.setDate(3, Date.valueOf(date));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    QueueEntry entry = new QueueEntry();
                    entry.setQueueId(rs.getInt("queue_id"));
                    entry.setPatientId(rs.getInt("patient_id"));
                    entry.setDepartmentId(rs.getInt("department_id"));
                    entry.setPriorityId(rs.getInt("priority_id"));
                    entry.setQueueNumber(rs.getString("queue_number"));
                    entry.setStatus(rs.getString("status"));
                    entry.setEstimatedWaitMin(rs.getInt("estimated_wait_min"));

                    Date queueDate = rs.getDate("queue_date");
                    if (queueDate != null) entry.setQueueDate(queueDate.toLocalDate());

                    return entry;
                }
            }

        } catch (SQLException e) {
            System.out.println("Error fetching queue entry for appointment: " + e.getMessage());
        }

        return null;
    }



    public QueueEntry getQueueEntryById(int queueId) {

        String sql = "SELECT * FROM Queue WHERE queue_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, queueId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    QueueEntry entry = new QueueEntry();
                    entry.setQueueId(rs.getInt("queue_id"));
                    entry.setPatientId(rs.getInt("patient_id"));
                    entry.setDepartmentId(rs.getInt("department_id"));
                    entry.setPriorityId(rs.getInt("priority_id"));
                    entry.setQueueNumber(rs.getString("queue_number"));
                    entry.setStatus(rs.getString("status"));
                    entry.setEstimatedWaitMin(rs.getInt("estimated_wait_min"));

                    Date queueDate = rs.getDate("queue_date");
                    if (queueDate != null) entry.setQueueDate(queueDate.toLocalDate());

                    return entry;
                }
            }

        } catch (SQLException e) {
            System.out.println("Error fetching queue entry: " + e.getMessage());
        }

        return null;
    }



    public boolean transferQueueEntry(int queueId, int newDepartmentId,
                                      String newQueueNumber, int estimatedWaitMin) {

        String base = "UPDATE Queue " +
                "SET department_id = ?, queue_number = ?, status = 'Waiting', " +
                "estimated_wait_min = ?, created_at = CURRENT_TIMESTAMP";
        String where = " WHERE queue_id = ? AND status IN ('Waiting', 'Called', 'Serving')";

        String withTiming = base + ", called_at = NULL, started_at = NULL, completed_at = NULL" + where;

        try {
            return runTransfer(withTiming, queueId, newDepartmentId, newQueueNumber, estimatedWaitMin);
        } catch (SQLException e) {
            System.out.println("Warning: queue timing columns not available (" + e.getMessage()
                    + "). Run queue_timing.sql. Transferring without timing.");
            try {
                return runTransfer(base + where, queueId, newDepartmentId, newQueueNumber, estimatedWaitMin);
            } catch (SQLException e2) {
                System.out.println("Error transferring queue entry: " + e2.getMessage());
                return false;
            }
        }
    }


    private boolean runTransfer(String sql, int queueId, int newDepartmentId,
                                String newQueueNumber, int estimatedWaitMin) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, newDepartmentId);
            stmt.setString(2, newQueueNumber);
            stmt.setInt(3, estimatedWaitMin);
            stmt.setInt(4, queueId);
            return stmt.executeUpdate() > 0;
        }
    }



    public List<QueueRecord> getQueueRecords(LocalDate from, LocalDate to, Integer departmentId) {

        String where = " WHERE queue_date >= ? AND queue_date <= ?"
                + (departmentId != null ? " AND department_id = ?" : "")
                + " ORDER BY queue_date, queue_id";

        String timed = "SELECT queue_id, department_id, priority_id, status, queue_date, " +
                "created_at, called_at, started_at, completed_at FROM Queue" + where;
        String basic = "SELECT queue_id, department_id, priority_id, status, queue_date, " +
                "created_at FROM Queue" + where;

        try {
            return readQueueRecords(timed, true, from, to, departmentId);
        } catch (SQLException e) {
            System.out.println("Warning: waiting times unavailable (" + e.getMessage()
                    + "). Run queue_timing.sql to enable them.");
            try {
                return readQueueRecords(basic, false, from, to, departmentId);
            } catch (SQLException e2) {
                System.out.println("Error fetching queue records: " + e2.getMessage());
                return new ArrayList<>();
            }
        }
    }


    private List<QueueRecord> readQueueRecords(String sql, boolean withTiming, LocalDate from,
                                               LocalDate to, Integer departmentId) throws SQLException {
        List<QueueRecord> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(from));
            stmt.setDate(2, Date.valueOf(to));
            if (departmentId != null) stmt.setInt(3, departmentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    QueueRecord r = new QueueRecord();
                    r.setQueueId(rs.getInt("queue_id"));
                    r.setDepartmentId(rs.getInt("department_id"));
                    r.setPriorityId(rs.getInt("priority_id"));
                    r.setStatus(rs.getString("status"));

                    Date queueDate = rs.getDate("queue_date");
                    if (queueDate != null) r.setQueueDate(queueDate.toLocalDate());

                    r.setCreatedAt(toDateTime(rs.getTimestamp("created_at")));
                    if (withTiming) {
                        r.setCalledAt(toDateTime(rs.getTimestamp("called_at")));
                        r.setStartedAt(toDateTime(rs.getTimestamp("started_at")));
                        r.setCompletedAt(toDateTime(rs.getTimestamp("completed_at")));
                    }
                    list.add(r);
                }
            }
        }
        return list;
    }


    private LocalDateTime toDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}