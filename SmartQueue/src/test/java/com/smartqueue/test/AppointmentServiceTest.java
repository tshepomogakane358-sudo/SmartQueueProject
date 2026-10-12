package com.smartqueue.test;

import com.smartqueue.dao.AppointmentDAO;
import com.smartqueue.db.DatabaseConnection;
import com.smartqueue.service.AppointmentService;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

public class AppointmentServiceTest {
    private static final AppointmentService service = new AppointmentService();

    private static final int TEST_PATIENT_ID = 1;
    private static final int TEST_DEPARTMENT_ID = 1;
    private static final LocalDate TEST_DATE = LocalDate.now().plusDays(2);
    private static final LocalTime TEST_TIME = LocalTime.of(10, 0);

    @BeforeEach
    void cleanUpTestSlot() {
        deleteTestAppointment();
    }

    @AfterEach
    void tearDown() {
        deleteTestAppointment();
    }

    private void deleteTestAppointment() {
        String sql = "DELETE FROM Appointment " +
                "WHERE patient_id = ? AND department_id = ? " +
                "AND appointment_date = ? AND appointment_time = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, TEST_PATIENT_ID);
            stmt.setInt(2, TEST_DEPARTMENT_ID);
            stmt.setDate(3, java.sql.Date.valueOf(TEST_DATE));
            stmt.setTime(4, java.sql.Time.valueOf(TEST_TIME));
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Cleanup failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Past date booking should fail")
    void testPastDateBookingFails() {
        AppointmentService.BookingResult result = service.bookAppointment(
                TEST_PATIENT_ID, TEST_DEPARTMENT_ID,
                LocalDate.now().minusDays(1),
                LocalTime.of(9, 0)
        );
        assertFalse(result.isSuccess(), "Should not allow booking in the past");
    }

    @Test
    @DisplayName("Outside clinic hours booking should fail")
    void testOutsideHoursBookingFails() {
        AppointmentService.BookingResult result = service.bookAppointment(
                TEST_PATIENT_ID, TEST_DEPARTMENT_ID,
                LocalDate.now().plusDays(1),
                LocalTime.of(20, 0)
        );
        assertFalse(result.isSuccess(), "Should not allow booking outside 07:00-17:00");
    }

    @Test
    @DisplayName("Valid future appointment should succeed")
    void testValidBookingSucceeds() {
        AppointmentService.BookingResult result = service.bookAppointment(
                TEST_PATIENT_ID, TEST_DEPARTMENT_ID,
                TEST_DATE,
                TEST_TIME
        );
        assertTrue(result.isSuccess(), "Valid future appointment should be booked successfully");
    }
}