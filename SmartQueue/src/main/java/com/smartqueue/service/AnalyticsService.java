package com.smartqueue.service;

import com.smartqueue.dao.AppointmentDAO;
import com.smartqueue.dao.DepartmentDAO;
import com.smartqueue.dao.PriorityDAO;
import com.smartqueue.dao.QueueDAO;
import com.smartqueue.model.Appointment;
import com.smartqueue.model.Department;
import com.smartqueue.model.Priority;
import com.smartqueue.model.QueueRecord;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class AnalyticsService {

    private final QueueDAO       queueDAO       = new QueueDAO();
    private final DepartmentDAO  departmentDAO  = new DepartmentDAO();
    private final PriorityDAO    priorityDAO    = new PriorityDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();



    public Map<String, Object> buildReport(LocalDate from, LocalDate to, Integer departmentId) {
        return compute(
                queueDAO.getQueueRecords(from, to, departmentId),
                departmentDAO.getAllDepartments(),
                priorityDAO.getAllPriorities(),
                appointmentDAO.getAllAppointments(),
                from, to, departmentId);
    }




    public static Map<String, Object> compute(List<QueueRecord> records,
                                              List<Department> departments,
                                              List<Priority> priorities,
                                              List<Appointment> appointments,
                                              LocalDate from, LocalDate to,
                                              Integer departmentId) {

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("from", from.toString());
        report.put("to", to.toString());
        report.put("departmentId", departmentId);

        // ── totals ───────────────────────────────────────
        int served = 0, noShow = 0, inQueue = 0;
        List<Double> waits = new ArrayList<>();
        List<Double> services = new ArrayList<>();

        for (QueueRecord r : records) {
            String st = r.getStatus();
            if ("Done".equals(st))         served++;
            else if ("No-show".equals(st)) noShow++;
            else                           inQueue++;

            Double w = waitMinutes(r);
            if (w != null) waits.add(w);
            Double sv = serviceMinutes(r);
            if (sv != null) services.add(sv);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("patients", records.size());
        totals.put("served", served);
        totals.put("noShow", noShow);
        totals.put("inQueue", inQueue);
        totals.put("noShowRate", (served + noShow) > 0
                ? (Object) round1(100.0 * noShow / (served + noShow)) : null);
        report.put("totals", totals);

        report.put("wait", stats(waits));
        report.put("service", stats(services));

        // ── by department ────────────────────────────────
        List<Map<String, Object>> byDepartment = new ArrayList<>();
        for (Department d : departments) {
            if (departmentId != null && d.getDepartmentId() != departmentId) continue;

            int patients = 0, dServed = 0, dNoShow = 0;
            List<Double> dWaits = new ArrayList<>();
            List<Double> dServices = new ArrayList<>();

            for (QueueRecord r : records) {
                if (r.getDepartmentId() != d.getDepartmentId()) continue;
                patients++;
                if ("Done".equals(r.getStatus()))         dServed++;
                else if ("No-show".equals(r.getStatus())) dNoShow++;
                Double w = waitMinutes(r);
                if (w != null) dWaits.add(w);
                Double sv = serviceMinutes(r);
                if (sv != null) dServices.add(sv);
            }

            Map<String, Object> waitStats = stats(dWaits);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("departmentId", d.getDepartmentId());
            row.put("name", d.getName());
            row.put("patients", patients);
            row.put("served", dServed);
            row.put("noShow", dNoShow);
            row.put("avgWaitMin", waitStats.get("average"));
            row.put("maxWaitMin", waitStats.get("max"));
            row.put("avgServiceMin", stats(dServices).get("average"));
            row.put("waitSample", dWaits.size());
            byDepartment.add(row);
        }
        report.put("byDepartment", byDepartment);

        // ── by priority ──────────────────────────────────
        List<Map<String, Object>> byPriority = new ArrayList<>();
        for (Priority p : priorities) {
            int patients = 0;
            List<Double> pWaits = new ArrayList<>();
            for (QueueRecord r : records) {
                if (r.getPriorityId() != p.getPriorityId()) continue;
                patients++;
                Double w = waitMinutes(r);
                if (w != null) pWaits.add(w);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("priorityId", p.getPriorityId());
            row.put("category", p.getCategory());
            row.put("patients", patients);
            row.put("avgWaitMin", stats(pWaits).get("average"));
            row.put("waitSample", pWaits.size());
            byPriority.add(row);
        }
        report.put("byPriority", byPriority);

        // ── by day ───────────────────────────────────────
        TreeMap<LocalDate, List<QueueRecord>> days = new TreeMap<>();
        for (QueueRecord r : records) {
            if (r.getQueueDate() == null) continue;
            days.computeIfAbsent(r.getQueueDate(), k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> byDay = new ArrayList<>();
        for (Map.Entry<LocalDate, List<QueueRecord>> e : days.entrySet()) {
            int dayServed = 0;
            List<Double> dayWaits = new ArrayList<>();
            for (QueueRecord r : e.getValue()) {
                if ("Done".equals(r.getStatus())) dayServed++;
                Double w = waitMinutes(r);
                if (w != null) dayWaits.add(w);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey().toString());
            row.put("patients", e.getValue().size());
            row.put("served", dayServed);
            row.put("avgWaitMin", stats(dayWaits).get("average"));
            byDay.add(row);
        }
        report.put("byDay", byDay);

        // ── arrivals by hour of day (for planning staffing) ──
        int[] perHour = new int[24];
        for (QueueRecord r : records) {
            if (r.getCreatedAt() != null) perHour[r.getCreatedAt().getHour()]++;
        }
        List<Map<String, Object>> byHour = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hour", h);
            row.put("patients", perHour[h]);
            byHour.add(row);
        }
        report.put("byHour", byHour);

        // ── appointments in the same period ──────────────
        int total = 0, pending = 0, confirmed = 0, done = 0, cancelled = 0;
        for (Appointment a : appointments) {
            LocalDate d = a.getAppointmentDate();
            if (d == null || d.isBefore(from) || d.isAfter(to)) continue;
            if (departmentId != null && a.getDepartmentId() != departmentId) continue;
            total++;
            String st = a.getStatus();
            if ("Pending".equals(st))        pending++;
            else if ("Confirmed".equals(st)) confirmed++;
            else if ("Done".equals(st))      done++;
            else if ("Cancelled".equals(st)) cancelled++;
        }
        Map<String, Object> appts = new LinkedHashMap<>();
        appts.put("total", total);
        appts.put("pending", pending);
        appts.put("confirmed", confirmed);
        appts.put("done", done);
        appts.put("cancelled", cancelled);
        report.put("appointments", appts);

        return report;
    }



    public static Double averageWait(List<QueueRecord> records) {
        double sum = 0;
        int n = 0;
        for (QueueRecord r : records) {
            Double w = waitMinutes(r);
            if (w != null) { sum += w; n++; }
        }
        return n == 0 ? null : round1(sum / n);
    }



    public static Map<Integer, Double> averageWaitByDepartment(List<QueueRecord> records) {
        Map<Integer, double[]> acc = new LinkedHashMap<>(); // id -> {sum, count}
        for (QueueRecord r : records) {
            Double w = waitMinutes(r);
            if (w == null) continue;
            double[] a = acc.computeIfAbsent(r.getDepartmentId(), k -> new double[2]);
            a[0] += w;
            a[1] += 1;
        }
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, double[]> e : acc.entrySet()) {
            result.put(e.getKey(), round1(e.getValue()[0] / e.getValue()[1]));
        }
        return result;
    }



    public static Double waitMinutes(QueueRecord r) {
        if (r.getCreatedAt() == null || r.getCalledAt() == null) return null;
        long seconds = Duration.between(r.getCreatedAt(), r.getCalledAt()).getSeconds();
        return seconds < 0 ? null : seconds / 60.0;
    }



    public static Double serviceMinutes(QueueRecord r) {
        if (r.getStartedAt() == null || r.getCompletedAt() == null) return null;
        long seconds = Duration.between(r.getStartedAt(), r.getCompletedAt()).getSeconds();
        return seconds < 0 ? null : seconds / 60.0;
    }


    private static Map<String, Object> stats(List<Double> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", values.size());

        if (values.isEmpty()) {
            m.put("average", null);
            m.put("median", null);
            m.put("max", null);
            return m;
        }

        double sum = 0, max = values.get(0);
        for (double v : values) {
            sum += v;
            if (v > max) max = v;
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        double median = (n % 2 == 1)
                ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;

        m.put("average", round1(sum / n));
        m.put("median", round1(median));
        m.put("max", round1(max));
        return m;
    }


    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}