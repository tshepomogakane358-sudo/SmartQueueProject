package com.smartqueue.service;

import com.smartqueue.dao.DepartmentDAO;
import com.smartqueue.dao.QueueDAO;
import com.smartqueue.model.Department;
import com.smartqueue.model.QueueEntry;

import java.time.LocalDate;
import java.util.List;


public class QueueService {


    private final QueueDAO       queueDAO      = new QueueDAO();
    private final DepartmentDAO  departmentDAO = new DepartmentDAO();


    private static final int AVG_WAIT_PER_PATIENT = 10;


    public boolean joinQueue(int patientId, int departmentId, int priorityId) {


        Department dept = departmentDAO.getDepartmentById(departmentId);
        if (dept == null) {
            System.out.println("Error: Department not found.");
            return false;
        }


        int todayCount = departmentDAO.getTodayPatientCount(departmentId);
        if (todayCount >= dept.getDailyLimit()) {
            System.out.println("Error: Department has reached its daily patient limit.");
            return false;
        }


        String prefix = dept.getName().substring(0, 3).toUpperCase();
        String queueNumber = queueDAO.generateQueueNumber(prefix, departmentId);


        int estimatedWait = todayCount * AVG_WAIT_PER_PATIENT;


        QueueEntry entry = new QueueEntry(
                patientId, departmentId, priorityId, queueNumber, LocalDate.now()
        );
        entry.setEstimatedWaitMin(estimatedWait);

        boolean success = queueDAO.addToQueue(entry);
        if (success) {
            System.out.println("Success! Your queue number is: " + queueNumber);
            System.out.println("Estimated wait time: " + estimatedWait + " minutes");
        }
        return success;
    }


    public QueueEntry callNext(int departmentId) {
        QueueEntry next = queueDAO.callNextPatient(departmentId);
        if (next == null) {
            System.out.println("No patients waiting in this department.");
        } else {
            System.out.println("Now calling: Queue #" + next.getQueueNumber());
        }
        return next;
    }


    public List<QueueEntry> viewQueue(int departmentId) {
        return queueDAO.getTodayQueue(departmentId);
    }


    public boolean markDone(int queueId) {
        return queueDAO.updateQueueStatus(queueId, "Done");
    }


    public boolean markNoShow(int queueId) {
        return queueDAO.updateQueueStatus(queueId, "No-show");
    }



    public boolean markServing(int queueId) {
        return queueDAO.updateQueueStatus(queueId, "Serving");
    }



    public boolean updatePriority(int queueId, int priorityId) {
        return queueDAO.updateQueuePriority(queueId, priorityId);
    }



    public QueueEntry getPatientQueueEntry(int patientId) {
        return queueDAO.getLatestQueueEntryForPatient(patientId);
    }



    public int getPosition(int departmentId, int queueId) {
        List<QueueEntry> queue = queueDAO.getTodayQueue(departmentId);
        int position = 0;
        for (QueueEntry q : queue) {
            if ("Waiting".equals(q.getStatus())) {
                position++;
                if (q.getQueueId() == queueId) return position;
            }
        }
        return 0;
    }



    public String getNowServing(int departmentId) {
        List<QueueEntry> queue = queueDAO.getTodayQueue(departmentId);
        for (QueueEntry q : queue) {
            if ("Called".equals(q.getStatus()) || "Serving".equals(q.getStatus())) {
                return q.getQueueNumber();
            }
        }
        return null;
    }



    public TransferResult transferPatient(int queueId, int newDepartmentId) {

        QueueEntry entry = queueDAO.getQueueEntryById(queueId);
        if (entry == null) {
            return TransferResult.fail("Queue entry not found.");
        }

        String status = entry.getStatus();
        if (!"Waiting".equals(status) && !"Called".equals(status) && !"Serving".equals(status)) {
            return TransferResult.fail("Only patients still in the queue can be transferred.");
        }

        if (entry.getDepartmentId() == newDepartmentId) {
            return TransferResult.fail("The patient is already in that department.");
        }

        Department newDept = departmentDAO.getDepartmentById(newDepartmentId);
        if (newDept == null) {
            return TransferResult.fail("Destination department not found.");
        }

        int todayCount = departmentDAO.getTodayPatientCount(newDepartmentId);
        if (todayCount >= newDept.getDailyLimit()) {
            return TransferResult.fail(newDept.getName() + " has reached its daily patient limit.");
        }

        String prefix = newDept.getName()
                .substring(0, Math.min(3, newDept.getName().length())).toUpperCase();
        String newQueueNumber = queueDAO.generateQueueNumber(prefix, newDepartmentId);

        // Estimate based on how many are currently waiting in the new department
        int waitingThere = 0;
        for (QueueEntry q : queueDAO.getTodayQueue(newDepartmentId)) {
            if ("Waiting".equals(q.getStatus())) waitingThere++;
        }
        int estimatedWait = waitingThere * AVG_WAIT_PER_PATIENT;

        boolean moved = queueDAO.transferQueueEntry(
                queueId, newDepartmentId, newQueueNumber, estimatedWait);

        if (!moved) {
            return TransferResult.fail("Could not transfer the patient.");
        }

        System.out.println("Transferred queue entry " + queueId + " to "
                + newDept.getName() + " as " + newQueueNumber);

        return TransferResult.success(newQueueNumber, newDept.getName());
    }


    public static class TransferResult {
        private final boolean success;
        private final String  message;
        private final String  queueNumber;
        private final String  departmentName;

        private TransferResult(boolean success, String message,
                               String queueNumber, String departmentName) {
            this.success        = success;
            this.message        = message;
            this.queueNumber    = queueNumber;
            this.departmentName = departmentName;
        }

        public static TransferResult success(String queueNumber, String departmentName) {
            return new TransferResult(true, "Patient transferred.", queueNumber, departmentName);
        }

        public static TransferResult fail(String message) {
            return new TransferResult(false, message, null, null);
        }

        public boolean isSuccess()        { return success; }
        public String  getMessage()       { return message; }
        public String  getQueueNumber()   { return queueNumber; }
        public String  getDepartmentName(){ return departmentName; }
    }
}