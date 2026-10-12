package com.smartqueue.service;

import com.smartqueue.dao.AppointmentDAO;
import com.smartqueue.dao.DepartmentDAO;
import com.smartqueue.dao.PatientDAO;
import com.smartqueue.model.Appointment;
import com.smartqueue.model.Department;
import com.smartqueue.model.Patient;

import java.util.List;


public class ReminderService {

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final PatientDAO     patientDAO     = new PatientDAO();
    private final DepartmentDAO  departmentDAO  = new DepartmentDAO();
    private final MailService    mailService    = new MailService();

    public void sendReminders() {
        List<Appointment> dueForReminder = appointmentDAO.getAppointmentsNeedingReminder();

        if (dueForReminder.isEmpty()) {
            return;
        }

        System.out.println("Sending " + dueForReminder.size() + " appointment reminder(s)...");

        for (Appointment appt : dueForReminder) {
            try {
                Patient patient = patientDAO.getPatientById(appt.getPatientId());
                Department dept = departmentDAO.getDepartmentById(appt.getDepartmentId());

                if (patient == null || patient.getEmail() == null || dept == null) {
                    continue;
                }

                String subject = "SmartQueue: Appointment Reminder";
                String body =
                        "Hi " + patient.getName() + ",\n\n" +
                                "This is a reminder that you have an appointment tomorrow.\n\n" +
                                "Department: " + dept.getName() + "\n" +
                                "Date: " + appt.getAppointmentDate() + "\n" +
                                "Time: " + appt.getAppointmentTime() + "\n\n" +
                                "Please arrive 15 minutes early.\n\n" +
                                "- SmartQueue";

                mailService.sendSimpleMail(patient.getEmail(), subject, body);
                appointmentDAO.markReminderSent(appt.getAppointmentId());

                System.out.println("Reminder sent to " + patient.getEmail()
                        + " for appointment #" + appt.getAppointmentId());

            } catch (Exception e) {
                // One failed reminder should never block the rest of the batch.
                System.out.println("Could not send reminder for appointment #"
                        + appt.getAppointmentId() + ": " + e.getMessage());
            }
        }
    }
}