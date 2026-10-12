package com.smartqueue.service;

import com.smartqueue.dao.DepartmentDAO;
import com.smartqueue.dao.PatientDAO;
import com.smartqueue.dao.QueueDAO;
import com.smartqueue.model.Department;
import com.smartqueue.model.Patient;
import com.smartqueue.model.QueueEntry;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;
import java.util.UUID;


public class WalkInService {

    private final PatientDAO    patientDAO    = new PatientDAO();
    private final DepartmentDAO departmentDAO = new DepartmentDAO();
    private final QueueDAO      queueDAO      = new QueueDAO();

    private static final int AVG_WAIT_PER_PATIENT = 10;

    public WalkInResult registerWalkIn(String name, String contact, String email,
                                       int departmentId, int priorityId) {

        if (name == null || name.isBlank()) {
            return WalkInResult.fail("Patient name is required.");
        }

        Department dept = departmentDAO.getDepartmentById(departmentId);
        if (dept == null) {
            return WalkInResult.fail("Department not found.");
        }

        int todayCount = departmentDAO.getTodayPatientCount(departmentId);
        if (todayCount >= dept.getDailyLimit()) {
            return WalkInResult.fail("This department has reached its daily patient limit.");
        }

        // Find or create the patient record
        Patient patient;
        if (email != null && !email.isBlank()) {
            patient = patientDAO.getPatientByEmail(email);
            if (patient == null) {
                patient = createPatientRecord(name, contact, email);
                if (patient == null) {
                    return WalkInResult.fail("Could not register patient.");
                }
            }
        } else {
            // No email given — generate a unique placeholder so the
            // email column's uniqueness constraint isn't violated.
            String placeholderEmail = "walkin-" + UUID.randomUUID().toString().substring(0, 8) + "@smartqueue.local";
            patient = createPatientRecord(name, contact, placeholderEmail);
            if (patient == null) {
                return WalkInResult.fail("Could not register patient.");
            }
        }

        // Generate queue number and add to queue
        String prefix = dept.getName().substring(0, Math.min(3, dept.getName().length())).toUpperCase();
        String queueNumber = queueDAO.generateQueueNumber(prefix, departmentId);

        QueueEntry entry = new QueueEntry(
                patient.getPatientId(), departmentId, priorityId, queueNumber, LocalDate.now()
        );
        entry.setEstimatedWaitMin(todayCount * AVG_WAIT_PER_PATIENT);

        boolean added = queueDAO.addToQueue(entry);
        if (!added) {
            return WalkInResult.fail("Could not add patient to the queue.");
        }

        return WalkInResult.success(queueNumber, patient.getPatientId(), patient.getName());
    }

    private Patient createPatientRecord(String name, String contact, String email) {
        // Walk-ins don't set their own password at registration time.
        // A random one is generated and hashed; they can use "Forgot
        // password" later if they ever want to log in online.
        String tempPassword = UUID.randomUUID().toString();
        String passwordHash = BCrypt.hashpw(tempPassword, BCrypt.gensalt());

        Patient patient = new Patient();
        patient.setName(name);
        patient.setContact(contact != null ? contact : "");
        patient.setEmail(email);
        patient.setPasswordHash(passwordHash);

        boolean ok = patientDAO.registerPatient(patient);
        if (!ok) {
            return null;
        }

        return patientDAO.getPatientByEmail(email);
    }

    public static class WalkInResult {
        private final boolean success;
        private final String  message;
        private final String  queueNumber;
        private final Integer patientId;
        private final String  patientName;

        private WalkInResult(boolean success, String message, String queueNumber,
                             Integer patientId, String patientName) {
            this.success     = success;
            this.message     = message;
            this.queueNumber = queueNumber;
            this.patientId   = patientId;
            this.patientName = patientName;
        }

        public static WalkInResult success(String queueNumber, int patientId, String patientName) {
            return new WalkInResult(true, "Walk-in patient registered.", queueNumber, patientId, patientName);
        }

        public static WalkInResult fail(String message) {
            return new WalkInResult(false, message, null, null, null);
        }

        public boolean isSuccess()      { return success; }
        public String  getMessage()     { return message; }
        public String  getQueueNumber() { return queueNumber; }
        public Integer getPatientId()   { return patientId; }
        public String  getPatientName() { return patientName; }
    }
}