package com.smartqueue.service;

import com.smartqueue.dao.AppointmentDAO;
import com.smartqueue.dao.DepartmentDAO;
import com.smartqueue.dao.PatientDAO;
import com.smartqueue.dao.QueueDAO;
import com.smartqueue.model.Appointment;
import com.smartqueue.model.Department;
import com.smartqueue.model.Patient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;


public class AppointmentService {

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final DepartmentDAO  departmentDAO  = new DepartmentDAO();
    private final QueueDAO       queueDAO       = new QueueDAO();
    private final PatientDAO     patientDAO     = new PatientDAO();
    private final MailService    mailService    = new MailService();

    // TODO: this should be the same address you receive Contact Us messages at
    private static final String ADMIN_EMAIL = "tshepomogakane358@gmail.com";


    public BookingResult bookAppointment(int patientId, int departmentId,
                                         LocalDate date, LocalTime time) {
        // Check department exists
        Department dept = departmentDAO.getDepartmentById(departmentId);
        if (dept == null) {
            return BookingResult.fail("Department not found.");
        }

        // Date must not be in the past
        if (date.isBefore(LocalDate.now())) {
            return BookingResult.fail("Cannot book an appointment in the past.");
        }

        // Time must be within clinic hours 07:00 - 17:00
        LocalTime openTime  = LocalTime.of(7, 0);
        LocalTime closeTime = LocalTime.of(17, 0);
        if (time.isBefore(openTime) || time.isAfter(closeTime)) {
            return BookingResult.fail("Appointments must be between 07:00 and 17:00.");
        }

        // Book the appointment
        Appointment appointment = new Appointment(patientId, departmentId, date, time);
        boolean booked = appointmentDAO.bookAppointment(appointment);
        if (!booked) {
            return BookingResult.fail("That time slot may already be taken.");
        }

        // Automatically join the queue with Normal priority (5)
        String prefix      = dept.getName().substring(0, Math.min(3, dept.getName().length())).toUpperCase();
        String queueNumber = queueDAO.generateQueueNumber(prefix, departmentId);

        // Add to queue
        com.smartqueue.model.QueueEntry entry = new com.smartqueue.model.QueueEntry(
                patientId, departmentId, 5, queueNumber, date
        );
        int todayCount = departmentDAO.getTodayPatientCount(departmentId);
        entry.setEstimatedWaitMin(todayCount * 10);
        queueDAO.addToQueue(entry);

        System.out.println("Appointment booked. Queue number: " + queueNumber);

        // ── Send confirmation emails ────────────────────────
        // Wrapped in its own try/catch so a failed email never blocks
        // a successful booking.
        try {
            Patient patient = patientDAO.getPatientById(patientId);
            if (patient != null && patient.getEmail() != null) {

                String patientSubject = "SmartQueue: Appointment Confirmed";
                String patientBody =
                        "Hi " + patient.getName() + ",\n\n" +
                                "Your appointment has been booked successfully.\n\n" +
                                "Department: " + dept.getName() + "\n" +
                                "Date: " + date + "\n" +
                                "Time: " + time + "\n" +
                                "Your Queue Number: " + queueNumber + "\n\n" +
                                "Please arrive 15 minutes early.\n\n" +
                                "- SmartQueue";

                mailService.sendSimpleMail(patient.getEmail(), patientSubject, patientBody);

                String adminSubject = "SmartQueue: New Appointment Booked";
                String adminBody =
                        "A new appointment was booked.\n\n" +
                                "Patient: " + patient.getName() + " (" + patient.getEmail() + ")\n" +
                                "Department: " + dept.getName() + "\n" +
                                "Date: " + date + "\n" +
                                "Time: " + time + "\n" +
                                "Queue Number: " + queueNumber;

                mailService.sendSimpleMail(ADMIN_EMAIL, adminSubject, adminBody);
            }
        } catch (Exception e) {
            System.out.println("Could not send booking confirmation email: " + e.getMessage());
        }

        return BookingResult.success(queueNumber);
    }


    public List<Appointment> getMyAppointments(int patientId) {
        return appointmentDAO.getAppointmentsByPatient(patientId);
    }


    public List<Appointment> getTodayAppointments() {
        return appointmentDAO.getTodayAppointments();
    }


    public List<Appointment> getAllAppointments() {
        return appointmentDAO.getAllAppointments();
    }


    public boolean cancelAppointment(int appointmentId) {
        return appointmentDAO.cancelAppointment(appointmentId);
    }


    public boolean updateAppointmentStatus(int appointmentId, String status) {
        return appointmentDAO.updateAppointmentStatus(appointmentId, status);
    }

    // ── Result classes ────────────────────────────────────

    public static class BookingResult {
        private final boolean success;
        private final String  message;
        private final String  queueNumber;

        private BookingResult(boolean success, String message, String queueNumber) {
            this.success     = success;
            this.message     = message;
            this.queueNumber = queueNumber;
        }

        public static BookingResult success(String queueNumber) {
            return new BookingResult(true, "Appointment booked successfully.", queueNumber);
        }

        public static BookingResult fail(String message) {
            return new BookingResult(false, message, null);
        }

        public boolean isSuccess()    { return success; }
        public String  getMessage()   { return message; }
        public String  getQueueNumber() { return queueNumber; }
    }
}