package com.smartqueue.service;

import com.smartqueue.dao.DepartmentDAO;
import com.smartqueue.dao.PatientDAO;
import com.smartqueue.model.Appointment;
import com.smartqueue.model.Department;
import com.smartqueue.model.Patient;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueRecord;
import com.smartqueue.model.Staff;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class ApiServer {

    private final PatientService     patientService     = new PatientService();
    private final AppointmentService appointmentService = new AppointmentService();
    private final QueueService       queueService       = new QueueService();
    private final WalkInService      walkInService      = new WalkInService();
    private final StaffService       staffService       = new StaffService();
    private final AnalyticsService   analyticsService   = new AnalyticsService();
    private final ObjectMapper       jsonMapper         = new ObjectMapper();
    private final DepartmentDAO      departmentDAO      = new DepartmentDAO();
    private final PatientDAO         patientDAO         = new PatientDAO();
    private final com.smartqueue.dao.PriorityDAO priorityDAO = new com.smartqueue.dao.PriorityDAO();
    private final com.smartqueue.dao.QueueDAO queueDAO = new com.smartqueue.dao.QueueDAO();

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    rule.anyHost();
                    rule.allowCredentials = false;
                });
            });
            config.staticFiles.add("web",
                    io.javalin.http.staticfiles.Location.EXTERNAL);
        }).start(7070);

        System.out.println("SmartQueue API running at http://localhost:7070");



        // ── REGISTER (THIS WAS MISSING) ──────────────────────
        app.post("/api/register", ctx -> {
            try {
                Map body        = ctx.bodyAsClass(Map.class);
                String name     = String.valueOf(body.get("name"));
                String contact  = String.valueOf(body.get("contact"));
                String email    = String.valueOf(body.get("email"));
                String password = String.valueOf(body.get("password"));

                if (name.equals("null") || contact.equals("null") ||
                        email.equals("null") || password.equals("null") ||
                        name.isEmpty() || contact.isEmpty() ||
                        email.isEmpty() || password.isEmpty()) {
                    ctx.status(400).result("{\"error\":\"All fields are required\"}");
                    return;
                }

                boolean ok = patientService.register(name, contact, email, password);
                if (ok) {
                    ctx.status(201).result("{\"message\":\"Registered successfully\"}");
                } else {
                    ctx.status(400).result(
                            "{\"error\":\"Registration failed. Email may already be registered.\"}");
                }
            } catch (Exception e) {
                System.out.println("Register error: " + e.getMessage());
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── LOGIN ────────────────────────────────────────────
        app.post("/api/login", ctx -> {
            try {
                Map body        = ctx.bodyAsClass(Map.class);
                String email    = String.valueOf(body.get("email"));
                String password = String.valueOf(body.get("password"));

                Patient patient = patientService.login(email, password);
                if (patient != null) {
                    String json = "{"
                            + "\"patientId\":"  + patient.getPatientId() + ","
                            + "\"name\":\""     + patient.getName()      + "\","
                            + "\"contact\":\"" + patient.getContact()   + "\","
                            + "\"email\":\""    + patient.getEmail()     + "\""
                            + "}";
                    ctx.status(200).result(json);
                } else {
                    ctx.status(401).result("{\"error\":\"Invalid email or password\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── GET ALL PATIENTS ─────────────────────────────────
        app.get("/api/patients", ctx -> {
            try {
                List<Patient> patients = patientDAO.getAllPatients();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < patients.size(); i++) {
                    Patient p = patients.get(i);
                    sb.append("{")
                            .append("\"patientId\":").append(p.getPatientId()).append(",")
                            .append("\"name\":\"").append(escape(p.getName())).append("\",")
                            .append("\"contact\":\"").append(escape(p.getContact())).append("\",")
                            .append("\"email\":\"").append(escape(p.getEmail())).append("\"")
                            .append("}");
                    if (i < patients.size() - 1) sb.append(",");
                }
                sb.append("]");
                ctx.contentType("application/json").result(sb.toString());
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── UPDATE PATIENT PROFILE ───────────────────────────
        app.put("/api/patients/{id}", ctx -> {
            try {
                int patientId  = Integer.parseInt(ctx.pathParam("id"));
                Map body       = ctx.bodyAsClass(Map.class);
                String name    = String.valueOf(body.get("name"));
                String contact = String.valueOf(body.get("contact"));
                String email   = String.valueOf(body.get("email"));

                PatientService.UpdateResult result =
                        patientService.updateProfile(patientId, name, contact, email);

                if (result.isSuccess()) {
                    Patient p = result.getPatient();
                    String json = "{"
                            + "\"patientId\":"  + p.getPatientId()  + ","
                            + "\"name\":\""     + escape(p.getName())    + "\","
                            + "\"contact\":\"" + escape(p.getContact()) + "\","
                            + "\"email\":\""    + escape(p.getEmail())   + "\""
                            + "}";
                    ctx.status(200).result(json);
                } else {
                    ctx.status(400).result("{\"error\":\"" + result.getMessage() + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });



        // ── CHANGE PASSWORD ──────────────────────────────────
        app.put("/api/patients/{id}/password", ctx -> {
            try {
                int patientId      = Integer.parseInt(ctx.pathParam("id"));
                Map body           = ctx.bodyAsClass(Map.class);
                String currentPass = String.valueOf(body.get("currentPassword"));
                String newPass     = String.valueOf(body.get("newPassword"));

                PatientService.PasswordChangeResult result =
                        patientService.changePassword(patientId, currentPass, newPass);

                if (result.isSuccess()) {
                    ctx.status(200).result("{\"message\":\"" + result.getMessage() + "\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + result.getMessage() + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });



        app.get("/api/departments", ctx -> {
            try {
                List<Department> depts = departmentDAO.getAllDepartments();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < depts.size(); i++) {
                    Department d = depts.get(i);
                    sb.append("{")
                            .append("\"departmentId\":").append(d.getDepartmentId()).append(",")
                            .append("\"name\":\"").append(escape(d.getName())).append("\",")
                            .append("\"dailyLimit\":").append(d.getDailyLimit())
                            .append("}");
                    if (i < depts.size() - 1) sb.append(",");
                }
                sb.append("]");
                ctx.contentType("application/json").result(sb.toString());
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        app.put("/api/departments/{id}/limit", ctx -> {
            try {
                int deptId = Integer.parseInt(ctx.pathParam("id"));
                Map body   = ctx.bodyAsClass(Map.class);
                int limit  = Integer.parseInt(String.valueOf(body.get("limit")));
                boolean ok = departmentDAO.updateDailyLimit(deptId, limit);
                if (ok) {
                    ctx.result("{\"message\":\"Limit updated\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"Update failed\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });



        app.get("/api/stats/today", ctx -> {
            try {
                List<Department> depts = departmentDAO.getAllDepartments();
                int waiting = 0;
                int served  = 0;

                for (Department dept : depts) {
                    List<QueueEntry> todayQueue =
                            queueDAO.getTodayQueue(dept.getDepartmentId());
                    waiting += (int) todayQueue.stream()
                            .filter(q -> "Waiting".equals(q.getStatus())).count();
                    served  += queueDAO.countServedToday(dept.getDepartmentId());
                }

                String json = "{"
                        + "\"patientsToday\":" + (waiting + served) + ","
                        + "\"waiting\":"       + waiting             + ","
                        + "\"served\":"        + served
                        + "}";
                ctx.contentType("application/json").result(json);
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });



        // ── BOOK APPOINTMENT ─────────────────────────────────
        app.post("/api/appointments", ctx -> {
            try {
                Map body         = ctx.bodyAsClass(Map.class);
                int patientId    = Integer.parseInt(String.valueOf(body.get("patientId")));
                int departmentId = Integer.parseInt(String.valueOf(body.get("departmentId")));
                LocalDate date   = LocalDate.parse(String.valueOf(body.get("date")));
                LocalTime time   = LocalTime.parse(String.valueOf(body.get("time")));

                AppointmentService.BookingResult result =
                        appointmentService.bookAppointment(patientId, departmentId, date, time);

                if (result.isSuccess()) {
                    String queueNum = result.getQueueNumber() != null
                            ? "\"" + result.getQueueNumber() + "\"" : "null";
                    ctx.status(201).result("{"
                            + "\"message\":\"Appointment booked\","
                            + "\"queueNumber\":" + queueNum
                            + "}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + result.getMessage() + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── GET APPOINTMENTS FOR A PATIENT ───────────────────
        app.get("/api/appointments/{patientId}", ctx -> {
            try {
                int patientId         = Integer.parseInt(ctx.pathParam("patientId"));
                List<Appointment> list =
                        appointmentService.getMyAppointments(patientId);
                ctx.contentType("application/json")
                        .result(appointmentsWithQueueStatusToJson(list));
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── GET ALL APPOINTMENTS (staff/admin) ───────────────
        app.get("/api/appointments", ctx -> {
            try {
                String scope = ctx.queryParam("scope");
                List<Appointment> list = "today".equals(scope)
                        ? appointmentService.getTodayAppointments()
                        : appointmentService.getAllAppointments();
                ctx.contentType("application/json")
                        .result(appointmentsToJson(list));
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── CANCEL APPOINTMENT ───────────────────────────────
        app.delete("/api/appointments/{id}", ctx -> {
            try {
                int id     = Integer.parseInt(ctx.pathParam("id"));
                boolean ok = appointmentService.cancelAppointment(id);
                if (ok) {
                    ctx.result("{\"message\":\"Cancelled\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"Cancel failed\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── UPDATE APPOINTMENT STATUS ────────────────────────
        app.put("/api/appointments/{id}/status", ctx -> {
            try {
                int id        = Integer.parseInt(ctx.pathParam("id"));
                Map body      = ctx.bodyAsClass(Map.class);
                String status = String.valueOf(body.get("status"));
                boolean ok    = appointmentService.updateAppointmentStatus(id, status);
                if (ok) {
                    ctx.result("{\"message\":\"Status updated\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"Update failed\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });




        app.post("/api/contact", ctx -> {
            try {
                var body = ctx.bodyAsClass(java.util.Map.class);
                String name    = String.valueOf(body.get("name"));
                String email   = String.valueOf(body.get("email"));
                String phone   = String.valueOf(body.get("phone"));
                String subject = String.valueOf(body.get("subject"));
                String message = String.valueOf(body.get("message"));

                new MailService().sendMail(name, email, phone, subject, message);

                ctx.status(200).result("{\"success\":true}");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });



        // ── GET PRIORITY CATEGORIES ───────────────────────────
        app.get("/api/priorities", ctx -> {
            try {
                List<com.smartqueue.model.Priority> priorities = priorityDAO.getAllPriorities();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < priorities.size(); i++) {
                    com.smartqueue.model.Priority p = priorities.get(i);
                    sb.append("{")
                            .append("\"priorityId\":").append(p.getPriorityId()).append(",")
                            .append("\"category\":\"").append(escape(p.getCategory())).append("\",")
                            .append("\"rankOrder\":").append(p.getRankOrder())
                            .append("}");
                    if (i < priorities.size() - 1) sb.append(",");
                }
                sb.append("]");
                ctx.contentType("application/json").result(sb.toString());
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── UPDATE A QUEUE ENTRY'S PRIORITY ───────────────────
        app.put("/api/queue/{queueId}/priority", ctx -> {
            try {
                int queueId    = Integer.parseInt(ctx.pathParam("queueId"));
                Map body       = ctx.bodyAsClass(Map.class);
                int priorityId = Integer.parseInt(String.valueOf(body.get("priorityId")));
                boolean ok     = queueService.updatePriority(queueId, priorityId);
                if (ok) {
                    ctx.result("{\"message\":\"Priority updated\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"Could not update priority\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── TRANSFER A PATIENT TO ANOTHER DEPARTMENT ──────────
        app.put("/api/queue/{queueId}/transfer", ctx -> {
            try {
                int queueId   = Integer.parseInt(ctx.pathParam("queueId"));
                Map body      = ctx.bodyAsClass(Map.class);
                int newDeptId = Integer.parseInt(String.valueOf(body.get("departmentId")));

                QueueService.TransferResult result =
                        queueService.transferPatient(queueId, newDeptId);

                if (result.isSuccess()) {
                    ctx.result("{"
                            + "\"message\":\"Patient transferred\","
                            + "\"queueNumber\":\"" + escape(result.getQueueNumber()) + "\","
                            + "\"departmentName\":\"" + escape(result.getDepartmentName()) + "\""
                            + "}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + escape(result.getMessage()) + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        // ── REGISTER WALK-IN PATIENT ──────────────────────────
        app.post("/api/walkin", ctx -> {
            try {
                Map body         = ctx.bodyAsClass(Map.class);
                String name      = String.valueOf(body.get("name"));
                String contact   = String.valueOf(body.get("contact"));
                String email     = body.get("email") != null ? String.valueOf(body.get("email")) : null;
                int departmentId = Integer.parseInt(String.valueOf(body.get("departmentId")));
                int priorityId   = Integer.parseInt(String.valueOf(body.get("priorityId")));

                WalkInService.WalkInResult result =
                        walkInService.registerWalkIn(name, contact, email, departmentId, priorityId);

                if (result.isSuccess()) {
                    ctx.status(201).result("{"
                            + "\"message\":\"Walk-in patient registered\","
                            + "\"queueNumber\":\"" + result.getQueueNumber() + "\","
                            + "\"patientId\":" + result.getPatientId() + ","
                            + "\"patientName\":\"" + escape(result.getPatientName()) + "\""
                            + "}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + escape(result.getMessage()) + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        app.post("/api/queue/join", ctx -> {
            try {
                Map body         = ctx.bodyAsClass(Map.class);
                int patientId    = Integer.parseInt(String.valueOf(body.get("patientId")));
                int departmentId = Integer.parseInt(String.valueOf(body.get("departmentId")));
                int priorityId   = Integer.parseInt(String.valueOf(body.get("priorityId")));
                boolean ok       = queueService.joinQueue(patientId, departmentId, priorityId);
                if (ok) {
                    ctx.status(201).result("{\"message\":\"Joined queue\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"Could not join queue\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        app.get("/api/queue/{departmentId}", ctx -> {
            try {
                int deptId           = Integer.parseInt(ctx.pathParam("departmentId"));
                List<QueueEntry> queue = queueService.viewQueue(deptId);
                StringBuilder sb     = new StringBuilder("[");
                for (int i = 0; i < queue.size(); i++) {
                    QueueEntry q = queue.get(i);
                    sb.append("{")
                            .append("\"queueId\":").append(q.getQueueId()).append(",")
                            .append("\"patientId\":").append(q.getPatientId()).append(",")
                            .append("\"departmentId\":").append(q.getDepartmentId()).append(",")
                            .append("\"priorityId\":").append(q.getPriorityId()).append(",")
                            .append("\"queueNumber\":\"").append(q.getQueueNumber()).append("\",")
                            .append("\"status\":\"").append(q.getStatus()).append("\",")
                            .append("\"estimatedWaitMin\":").append(q.getEstimatedWaitMin())
                            .append("}");
                    if (i < queue.size() - 1) sb.append(",");
                }
                sb.append("]");
                ctx.contentType("application/json").result(sb.toString());
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── GET PATIENT'S OWN QUEUE STATUS (for polling) ─────
        app.get("/api/queue/patient/{patientId}", ctx -> {
            try {
                int patientId = Integer.parseInt(ctx.pathParam("patientId"));
                QueueEntry entry = queueService.getPatientQueueEntry(patientId);

                if (entry == null) {
                    ctx.status(404).result("{\"error\":\"No queue entry found for today\"}");
                    return;
                }

                int deptId = entry.getDepartmentId();
                int position = "Waiting".equals(entry.getStatus())
                        ? queueService.getPosition(deptId, entry.getQueueId()) : 0;
                String nowServing = queueService.getNowServing(deptId);

                String json = "{"
                        + "\"queueId\":" + entry.getQueueId() + ","
                        + "\"queueNumber\":\"" + entry.getQueueNumber() + "\","
                        + "\"status\":\"" + entry.getStatus() + "\","
                        + "\"departmentId\":" + deptId + ","
                        + "\"position\":" + position + ","
                        + "\"estimatedWaitMin\":" + entry.getEstimatedWaitMin() + ","
                        + "\"nowServing\":" + (nowServing != null ? "\"" + nowServing + "\"" : "null")
                        + "}";
                ctx.contentType("application/json").result(json);
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        app.post("/api/queue/{departmentId}/next", ctx -> {
            try {
                int deptId      = Integer.parseInt(ctx.pathParam("departmentId"));
                QueueEntry next = queueService.callNext(deptId);
                if (next != null) {
                    ctx.result("{"
                            + "\"queueNumber\":\"" + next.getQueueNumber() + "\","
                            + "\"patientId\":"      + next.getPatientId()
                            + "}");
                } else {
                    ctx.status(404).result("{\"error\":\"No patients waiting\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        app.put("/api/queue/{queueId}/done", ctx -> {
            try {
                int queueId = Integer.parseInt(ctx.pathParam("queueId"));
                queueService.markDone(queueId);
                ctx.result("{\"message\":\"Marked as done\"}");
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        app.put("/api/queue/{queueId}/noshow", ctx -> {
            try {
                int queueId = Integer.parseInt(ctx.pathParam("queueId"));
                queueService.markNoShow(queueId);
                ctx.result("{\"message\":\"Marked as no-show\"}");
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // ── STAFF: MARK IN SERVICE ────────────────────────────
        app.put("/api/queue/{queueId}/serving", ctx -> {
            try {
                int queueId = Integer.parseInt(ctx.pathParam("queueId"));
                queueService.markServing(queueId);
                ctx.result("{\"message\":\"Marked as in service\"}");
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });



        // ── STAFF LOGIN ──────────────────────────────────────
        app.post("/api/staff/login", ctx -> {
            try {
                Map body        = ctx.bodyAsClass(Map.class);
                String username = String.valueOf(body.get("username"));
                String password = String.valueOf(body.get("password"));

                StaffService.StaffResult result = staffService.login(username, password);
                if (result.isSuccess()) {
                    ctx.status(200).result(staffToJson(result.getStaff()));
                } else {
                    ctx.status(401).result("{\"error\":\"" + escape(result.getMessage()) + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        // ── LIST STAFF ACCOUNTS ──────────────────────────────
        app.get("/api/staff", ctx -> {
            try {
                List<Staff> list = staffService.getAllStaff();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    sb.append(staffToJson(list.get(i)));
                    if (i < list.size() - 1) sb.append(",");
                }
                sb.append("]");
                ctx.contentType("application/json").result(sb.toString());
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        // ── CREATE STAFF ACCOUNT ─────────────────────────────
        app.post("/api/staff", ctx -> {
            try {
                Map body        = ctx.bodyAsClass(Map.class);
                String name     = String.valueOf(body.get("name"));
                String username = String.valueOf(body.get("username"));
                String password = String.valueOf(body.get("password"));

                StaffService.StaffResult result = staffService.createStaff(name, username, password);
                if (result.isSuccess()) {
                    ctx.status(201).result("{\"message\":\"" + escape(result.getMessage()) + "\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + escape(result.getMessage()) + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        // ── EDIT STAFF ACCOUNT (password optional) ───────────
        app.put("/api/staff/{id}", ctx -> {
            try {
                int staffId     = Integer.parseInt(ctx.pathParam("id"));
                Map body        = ctx.bodyAsClass(Map.class);
                String name     = String.valueOf(body.get("name"));
                String username = String.valueOf(body.get("username"));
                String password = body.get("password") != null ? String.valueOf(body.get("password")) : "";

                StaffService.StaffResult result =
                        staffService.updateStaff(staffId, name, username, password);
                if (result.isSuccess()) {
                    ctx.result("{\"message\":\"" + escape(result.getMessage()) + "\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + escape(result.getMessage()) + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        // ── ENABLE / DISABLE STAFF ACCOUNT ───────────────────
        app.put("/api/staff/{id}/active", ctx -> {
            try {
                int staffId    = Integer.parseInt(ctx.pathParam("id"));
                Map body       = ctx.bodyAsClass(Map.class);
                boolean active = Boolean.parseBoolean(String.valueOf(body.get("active")));

                StaffService.StaffResult result = staffService.setActive(staffId, active);
                if (result.isSuccess()) {
                    ctx.result("{\"message\":\"" + escape(result.getMessage()) + "\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + escape(result.getMessage()) + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        // ── DELETE STAFF ACCOUNT ─────────────────────────────
        app.delete("/api/staff/{id}", ctx -> {
            try {
                int staffId = Integer.parseInt(ctx.pathParam("id"));

                StaffService.StaffResult result = staffService.deleteStaff(staffId);
                if (result.isSuccess()) {
                    ctx.result("{\"message\":\"" + escape(result.getMessage()) + "\"}");
                } else {
                    ctx.status(400).result("{\"error\":\"" + escape(result.getMessage()) + "\"}");
                }
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });



        app.get("/api/admin/overview", ctx -> {
            try {
                int totalPatients = patientDAO.getAllPatients().size();

                // Appointments: totals, today's count, and a breakdown by status
                List<Appointment> appts = appointmentService.getAllAppointments();
                LocalDate today = LocalDate.now();
                int todayAppointments = 0;
                Map<String, Integer> byStatus = new java.util.LinkedHashMap<>();
                for (String st : new String[]{"Pending", "Confirmed", "Done", "Cancelled"}) {
                    byStatus.put(st, 0);
                }
                for (Appointment a : appts) {
                    if (today.equals(a.getAppointmentDate())) todayAppointments++;
                    String st = a.getStatus() != null ? a.getStatus() : "Unknown";
                    byStatus.merge(st, 1, Integer::sum);
                }

                StringBuilder statusJson = new StringBuilder("{");
                int idx = 0;
                for (Map.Entry<String, Integer> e : byStatus.entrySet()) {
                    if (idx++ > 0) statusJson.append(",");
                    statusJson.append("\"").append(escape(e.getKey())).append("\":").append(e.getValue());
                }
                statusJson.append("}");

                // Live queue activity per department
                List<Department> depts = departmentDAO.getAllDepartments();
                StringBuilder deptJson = new StringBuilder("[");
                int waitingNow = 0, servedToday = 0, noShowToday = 0;

                // Real average waiting times for today (from recorded call times)
                List<QueueRecord> todayRecords = queueDAO.getQueueRecords(today, today, null);
                Double avgWaitToday = AnalyticsService.averageWait(todayRecords);
                Map<Integer, Double> avgWaitByDept = AnalyticsService.averageWaitByDepartment(todayRecords);

                for (int i = 0; i < depts.size(); i++) {
                    Department d = depts.get(i);
                    List<QueueEntry> tq = queueDAO.getTodayQueue(d.getDepartmentId());

                    int waiting = 0, called = 0, serving = 0;
                    for (QueueEntry q : tq) {
                        if ("Waiting".equals(q.getStatus()))      waiting++;
                        else if ("Called".equals(q.getStatus()))  called++;
                        else if ("Serving".equals(q.getStatus())) serving++;
                    }
                    int served     = queueDAO.countServedToday(d.getDepartmentId());
                    int noShow     = queueDAO.countNoShowToday(d.getDepartmentId());
                    int joinedToday = departmentDAO.getTodayPatientCount(d.getDepartmentId());

                    waitingNow  += waiting;
                    servedToday += served;
                    noShowToday += noShow;

                    deptJson.append("{")
                            .append("\"departmentId\":").append(d.getDepartmentId()).append(",")
                            .append("\"name\":\"").append(escape(d.getName())).append("\",")
                            .append("\"dailyLimit\":").append(d.getDailyLimit()).append(",")
                            .append("\"joinedToday\":").append(joinedToday).append(",")
                            .append("\"waiting\":").append(waiting).append(",")
                            .append("\"called\":").append(called).append(",")
                            .append("\"serving\":").append(serving).append(",")
                            .append("\"served\":").append(served).append(",")
                            .append("\"noShow\":").append(noShow).append(",")
                            .append("\"avgWaitMin\":").append(jsonNumber(avgWaitByDept.get(d.getDepartmentId())))
                            .append("}");
                    if (i < depts.size() - 1) deptJson.append(",");
                }
                deptJson.append("]");

                // Staff accounts
                List<Staff> allStaff = staffService.getAllStaff();
                int activeStaff = 0;
                for (Staff st : allStaff) if (st.isActive()) activeStaff++;

                String json = "{"
                        + "\"totalPatients\":"        + totalPatients      + ","
                        + "\"totalAppointments\":"    + appts.size()       + ","
                        + "\"todayAppointments\":"    + todayAppointments  + ","
                        + "\"waitingNow\":"           + waitingNow         + ","
                        + "\"avgWaitTodayMinutes\":"  + jsonNumber(avgWaitToday) + ","
                        + "\"servedToday\":"          + servedToday        + ","
                        + "\"noShowToday\":"          + noShowToday        + ","
                        + "\"totalStaff\":"           + allStaff.size()    + ","
                        + "\"activeStaff\":"          + activeStaff        + ","
                        + "\"appointmentsByStatus\":" + statusJson         + ","
                        + "\"departments\":"          + deptJson
                        + "}";
                ctx.contentType("application/json").result(json);
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });



        // /api/reports/analytics?from=2025-01-01&to=2025-01-31&departmentId=2
        // (all parameters optional: defaults to the last 30 days, all departments)
        app.get("/api/reports/analytics", ctx -> {
            try {
                LocalDate to   = parseDateParam(ctx.queryParam("to"), LocalDate.now());
                LocalDate from = parseDateParam(ctx.queryParam("from"), to.minusDays(29));

                if (from.isAfter(to)) {
                    ctx.status(400).result("{\"error\":\"The start date must not be after the end date.\"}");
                    return;
                }
                if (ChronoUnit.DAYS.between(from, to) > 366) {
                    ctx.status(400).result("{\"error\":\"Please choose a date range of one year or less.\"}");
                    return;
                }

                String deptParam = ctx.queryParam("departmentId");
                Integer departmentId = (deptParam == null || deptParam.isBlank())
                        ? null : Integer.valueOf(deptParam.trim());

                Map<String, Object> report = analyticsService.buildReport(from, to, departmentId);
                ctx.contentType("application/json").result(jsonMapper.writeValueAsString(report));

            } catch (DateTimeParseException | NumberFormatException e) {
                ctx.status(400).result("{\"error\":\"Invalid date or department.\"}");
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });



        app.get("/api/reports/staff-summary", ctx -> {
            try {
                List<Appointment> allAppts = appointmentService.getAllAppointments();
                int totalAppointments = allAppts.size();
                int completed = (int) allAppts.stream()
                        .filter(a -> "Done".equals(a.getStatus())
                                || "Confirmed".equals(a.getStatus()))
                        .count();
                int cancelled = (int) allAppts.stream()
                        .filter(a -> "Cancelled".equals(a.getStatus()))
                        .count();

                List<Department> depts = departmentDAO.getAllDepartments();
                StringBuilder deptJson = new StringBuilder("[");
                int totalNoShow = 0;

                for (int i = 0; i < depts.size(); i++) {
                    Department d        = depts.get(i);
                    List<QueueEntry> tq = queueDAO.getTodayQueue(d.getDepartmentId());
                    int waiting = (int) tq.stream()
                            .filter(q -> "Waiting".equals(q.getStatus())).count();
                    int served  = queueDAO.countServedToday(d.getDepartmentId());
                    int noShow  = queueDAO.countNoShowToday(d.getDepartmentId());
                    totalNoShow += noShow;

                    deptJson.append("{")
                            .append("\"departmentId\":").append(d.getDepartmentId()).append(",")
                            .append("\"name\":\"").append(escape(d.getName())).append("\",")
                            .append("\"waiting\":").append(waiting).append(",")
                            .append("\"served\":").append(served).append(",")
                            .append("\"noShow\":").append(noShow)
                            .append("}");
                    if (i < depts.size() - 1) deptJson.append(",");
                }
                deptJson.append("]");

                String json = "{"
                        + "\"totalAppointments\":" + totalAppointments + ","
                        + "\"completed\":"          + completed         + ","
                        + "\"cancelled\":"          + cancelled         + ","
                        + "\"noShow\":"             + totalNoShow       + ","
                        + "\"departments\":"        + deptJson
                        + "}";
                ctx.contentType("application/json").result(json);
            } catch (Exception e) {
                ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }



    private LocalDate parseDateParam(String value, LocalDate fallback) {
        return (value == null || value.isBlank()) ? fallback : LocalDate.parse(value.trim());
    }


    private String jsonNumber(Double value) {
        return value == null ? "null" : Double.toString(value);
    }


    private String staffToJson(Staff s) {
        return "{"
                + "\"staffId\":" + s.getStaffId() + ","
                + "\"name\":\"" + escape(s.getName()) + "\","
                + "\"username\":\"" + escape(s.getUsername()) + "\","
                + "\"active\":" + s.isActive()
                + "}";
    }


    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }


    private String appointmentsToJson(List<Appointment> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Appointment a = list.get(i);
            sb.append("{")
                    .append("\"appointmentId\":").append(a.getAppointmentId()).append(",")
                    .append("\"patientId\":").append(a.getPatientId()).append(",")
                    .append("\"departmentId\":").append(a.getDepartmentId()).append(",")
                    .append("\"appointmentDate\":\"").append(a.getAppointmentDate()).append("\",")
                    .append("\"appointmentTime\":\"").append(a.getAppointmentTime()).append("\",")
                    .append("\"status\":\"").append(a.getStatus()).append("\"")
                    .append("}");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }


    private String appointmentsWithQueueStatusToJson(List<Appointment> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Appointment a = list.get(i);

            com.smartqueue.model.QueueEntry qEntry = queueDAO.getQueueEntryForAppointment(
                    a.getPatientId(), a.getDepartmentId(), a.getAppointmentDate());

            String queueStatus = qEntry != null ? qEntry.getStatus() : null;
            String queueNumber = qEntry != null ? qEntry.getQueueNumber() : null;

            sb.append("{")
                    .append("\"appointmentId\":").append(a.getAppointmentId()).append(",")
                    .append("\"patientId\":").append(a.getPatientId()).append(",")
                    .append("\"departmentId\":").append(a.getDepartmentId()).append(",")
                    .append("\"appointmentDate\":\"").append(a.getAppointmentDate()).append("\",")
                    .append("\"appointmentTime\":\"").append(a.getAppointmentTime()).append("\",")
                    .append("\"status\":\"").append(a.getStatus()).append("\",")
                    .append("\"queueStatus\":").append(queueStatus != null ? "\"" + queueStatus + "\"" : "null").append(",")
                    .append("\"queueNumber\":").append(queueNumber != null ? "\"" + queueNumber + "\"" : "null")
                    .append("}");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}