const API = 'http://localhost:7070/api';
let currentPatient = null;
let queuePollInterval = null;
let historyPollInterval = null;
let priorityList = []; // [{priorityId, category, rankOrder}]
let departmentList = []; // [{departmentId, name, dailyLimit}]
let currentStaff = null;      // the logged-in staff member
let adminPollInterval = null; // refreshes the admin dashboard
let staffAccounts = [];       // staff list shown on the admin screen
let editingStaffId = null;    // set while editing a staff account

// ─── Section Navigation (Home page About/Services/Contact) ─

function showSection(section) {
    // Make sure home page is visible
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.getElementById('page-home').classList.add('active');

    // Hide all toggleable sections
    const sections = ['section-about', 'section-services', 'section-contact'];
    sections.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.style.display = 'none';
    });

    // Show hero + features when going home
    const hero     = document.getElementById('section-home');
    const features = document.getElementById('section-features');
    const footer   = document.querySelector('#page-home .site-footer');

    if (section === 'home') {
        if (hero)     hero.style.display     = '';
        if (features) features.style.display = '';
        if (footer)   footer.style.display   = '';
        // Highlight Home nav link
        document.querySelectorAll('.nav-link').forEach((l, i) => {
            l.classList.toggle('active', i === 0);
        });
    } else {
        // Hide hero + features when viewing sub-sections
        if (hero)     hero.style.display     = 'none';
        if (features) features.style.display = 'none';
        if (footer)   footer.style.display   = '';

        const target = document.getElementById('section-' + section);
        if (target) target.style.display = '';

        // Highlight matching nav link
        const map = { about: 1, services: 2, contact: 3 };
        document.querySelectorAll('.nav-link').forEach((l, i) => {
            l.classList.toggle('active', i === map[section]);
        });
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// Contact form submit
async function sendContact() {
    const name    = document.getElementById('contact-name')?.value.trim();
    const email   = document.getElementById('contact-email')?.value.trim();
    const phone   = document.getElementById('contact-phone')?.value.trim();
    const subject = document.getElementById('contact-subject')?.value.trim();
    const message = document.getElementById('contact-message')?.value.trim();
    const msgEl   = document.getElementById('contact-msg');

    if (!name || !email || !message) {
        if (msgEl) { msgEl.textContent = 'Please fill in all required fields.'; msgEl.className = 'msg error'; }
        return;
    }

    try {
        const res  = await apiFetch('/contact', {
            method: 'POST',
            body: JSON.stringify({ name, email, phone, subject, message })
        });
        const data = await res.json();

        if (res.ok) {
            if (msgEl) { msgEl.textContent = 'Message sent! We will get back to you within 24 hours.'; msgEl.className = 'msg success'; }
            document.getElementById('contact-name').value    = '';
            document.getElementById('contact-email').value   = '';
            document.getElementById('contact-phone').value   = '';
            document.getElementById('contact-message').value = '';
        } else {
            if (msgEl) { msgEl.textContent = data.error || 'Could not send message.'; msgEl.className = 'msg error'; }
        }
    } catch {
        if (msgEl) { msgEl.textContent = 'Cannot connect to server.'; msgEl.className = 'msg error'; }
    }
}

// ─── Page Navigation ───────────────────────────────────────

function showPage(id) {
    if (id !== 'page-admin') stopAdminPolling();
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.getElementById(id).classList.add('active');

    if (id === 'page-home') {
        showSection('home');
        return;
    }
    if (id === 'page-patient') {
        loadDepartments();
        loadAppointments();
        updateDashboardDate();
    }
    if (id === 'page-queue-display') {
        loadQueueDisplay();
        startDisplayClock();
    }
    if (id === 'page-admin') {
        const el = document.getElementById('admin-date');
        if (el) el.textContent = todayStr() + ' | ' + nowStr();
    }
}

// ─── Patient Tab Navigation ────────────────────────────────

function showPatientTab(tabId, el) {
    document.querySelectorAll('.ptab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('#page-patient .side-link').forEach(l => l.classList.remove('active'));
    const tab = document.getElementById(tabId);
    if (tab) tab.classList.add('active');
    if (el)  el.classList.add('active');

    if (tabId === 'tab-history')      { /* handled below with polling */ }
    if (tabId === 'tab-book')         loadDepartments();
    if (tabId === 'tab-profile')      loadProfile();

    if (tabId === 'tab-history') {
        loadAppointments();
        startHistoryPolling();
    } else {
        stopHistoryPolling();
    }

    if (tabId === 'tab-queue-status') {
        loadMyQueueStatus();
        startQueuePolling();
    } else {
        stopQueuePolling();
    }
}

// ─── Staff Tab Navigation ──────────────────────────────────
function showStaffTab(tabId, el) {
    document.querySelectorAll('.stab').forEach(t => t.classList.add('hidden'));
    document.querySelectorAll('#staff-app .side-link').forEach(l => l.classList.remove('active'));
    document.getElementById(tabId).classList.remove('hidden');
    if (el) el.classList.add('active');
    if (tabId === 'stab-queue')        loadDepartments();
    if (tabId === 'stab-dashboard')    loadStaffStats();
    if (tabId === 'stab-appointments') loadStaffAppointments();
    if (tabId === 'stab-patients')     loadStaffPatients();
    if (tabId === 'stab-reports')      loadStaffReports();
}

async function loadStaffPatients() {
    const container = document.getElementById('staff-patients-list');
    if (!container) return;

    try {
        const res      = await apiFetch('/patients');
        const patients = await res.json();

        if (!patients.length) {
            container.innerHTML =
                '<div class="empty-state">No patients registered.</div>';
            return;
        }

        container.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID</th><th>Name</th>
                        <th>Contact</th><th>Email</th>
                    </tr>
                </thead>
                <tbody>
                    ${patients.map(p => `
                    <tr>
                        <td>${p.patientId}</td>
                        <td>${p.name}</td>
                        <td>${p.contact}</td>
                        <td>${p.email}</td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML =
            '<div class="empty-state">Could not load patients.</div>';
    }
}

async function loadStaffAppointments() {
    const container = document.getElementById('staff-appointments-list');
    const scopeSel  = document.getElementById('staff-appt-scope');
    const scope     = scopeSel ? scopeSel.value : 'today';
    if (!container) return;

    try {
        const res  = await apiFetch('/appointments?scope=' + scope);
        const list = await res.json();

        if (!list.length) {
            container.innerHTML =
                '<div class="empty-state">No appointments found.</div>';
            return;
        }

        container.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID</th><th>Patient ID</th><th>Department ID</th>
                        <th>Date</th><th>Time</th><th>Status</th><th>Action</th>
                    </tr>
                </thead>
                <tbody>
                    ${list.map(a => `
                    <tr>
                        <td>${a.appointmentId}</td>
                        <td>${a.patientId}</td>
                        <td>${a.departmentId}</td>
                        <td>${a.appointmentDate}</td>
                        <td>${a.appointmentTime}</td>
                        <td>${badgeHtml(a.status)}</td>
                        <td style="display:flex;gap:6px">
                            ${a.status !== 'Done' && a.status !== 'Cancelled' ? `
                                <button class="btn btn-sm btn-success"
                                    onclick="updateAppointmentStatus(${a.appointmentId}, 'Confirmed')">
                                    Confirm
                                </button>
                                <button class="btn btn-sm btn-success"
                                    onclick="updateAppointmentStatus(${a.appointmentId}, 'Done')">
                                    Done
                                </button>
                                <button class="btn btn-sm btn-danger"
                                    onclick="updateAppointmentStatus(${a.appointmentId}, 'Cancelled')">
                                    Cancel
                                </button>
                            ` : '—'}
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML =
            '<div class="empty-state">Could not load appointments.</div>';
    }
}

async function updateAppointmentStatus(id, status) {
    try {
        const res = await apiFetch('/appointments/' + id + '/status', {
            method: 'PUT',
            body: JSON.stringify({ status })
        });
        if (res.ok) {
            showMsg('staff-appt-msg', 'Appointment ' + id + ' marked as ' + status + '.', 'success');
            loadStaffAppointments();
        } else {
            showMsg('staff-appt-msg', 'Could not update appointment.', 'error');
        }
    } catch {
        showMsg('staff-appt-msg', 'Cannot connect to server.', 'error');
    }
}

async function loadStaffReports() {
    const perfEl = document.getElementById('staff-dept-performance');

    try {
        const res  = await apiFetch('/reports/staff-summary');
        const data = await res.json();

        const totalEl     = document.getElementById('rep-total');
        const completedEl = document.getElementById('rep-completed');
        const cancelledEl = document.getElementById('rep-cancelled');
        const noshowEl    = document.getElementById('rep-noshow');
        if (totalEl)     totalEl.textContent     = data.totalAppointments;
        if (completedEl) completedEl.textContent = data.completed;
        if (cancelledEl) cancelledEl.textContent = data.cancelled;
        if (noshowEl)    noshowEl.textContent    = data.noShow;

        if (!perfEl) return;

        if (!data.departments || !data.departments.length) {
            perfEl.innerHTML = '<div class="empty-state">No department data available.</div>';
            return;
        }

        perfEl.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>Department</th><th>Waiting</th><th>Served Today</th><th>No-show Today</th>
                    </tr>
                </thead>
                <tbody>
                    ${data.departments.map(d => `
                    <tr>
                        <td>${d.name}</td>
                        <td>${d.waiting}</td>
                        <td>${d.served}</td>
                        <td>${d.noShow}</td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        if (perfEl) perfEl.innerHTML = '<div class="empty-state">Could not load reports.</div>';
    }
}


// ─── Admin Tab Navigation ──────────────────────────────────
function showAdminTab(tabId, el) {
    document.querySelectorAll('.atab').forEach(t => t.classList.add('hidden'));
    document.querySelectorAll('#admin-app .side-link').forEach(l => l.classList.remove('active'));
    document.getElementById(tabId).classList.remove('hidden');
    if (el) el.classList.add('active');
    if (tabId === 'atab-users')        loadPatients();
    if (tabId === 'atab-settings')     loadAdminDepts();
    if (tabId === 'atab-staff')        loadStaffAccounts();
    if (tabId === 'atab-dashboard') {
        loadAdminStats();
        startAdminPolling();
    } else {
        stopAdminPolling();
    }
    if (tabId === 'atab-appointments') loadAdminAppointments();
    if (tabId === 'atab-reports') {
        populateReportDeptFilter();
        initReportDates();
        generateReport(true);
    }
}

async function loadAdminAppointments() {
    const container = document.getElementById('admin-appointments-list');
    const scopeSel  = document.getElementById('admin-appt-scope');
    const scope     = scopeSel ? scopeSel.value : 'today';
    if (!container) return;

    try {
        const res  = await apiFetch('/appointments?scope=' + scope);
        const list = await res.json();

        if (!list.length) {
            container.innerHTML =
                '<div class="empty-state">No appointments found.</div>';
            return;
        }

        container.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID</th><th>Patient ID</th><th>Department ID</th>
                        <th>Date</th><th>Time</th><th>Status</th><th>Action</th>
                    </tr>
                </thead>
                <tbody>
                    ${list.map(a => `
                    <tr>
                        <td>${a.appointmentId}</td>
                        <td>${a.patientId}</td>
                        <td>${a.departmentId}</td>
                        <td>${a.appointmentDate}</td>
                        <td>${a.appointmentTime}</td>
                        <td>${badgeHtml(a.status)}</td>
                        <td style="display:flex;gap:6px">
                            ${a.status !== 'Done' && a.status !== 'Cancelled' ? `
                                <button class="btn btn-sm btn-success"
                                    onclick="updateAdminAppointmentStatus(${a.appointmentId}, 'Confirmed')">
                                    Confirm
                                </button>
                                <button class="btn btn-sm btn-success"
                                    onclick="updateAdminAppointmentStatus(${a.appointmentId}, 'Done')">
                                    Done
                                </button>
                                <button class="btn btn-sm btn-danger"
                                    onclick="updateAdminAppointmentStatus(${a.appointmentId}, 'Cancelled')">
                                    Cancel
                                </button>
                            ` : '—'}
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML =
            '<div class="empty-state">Could not load appointments.</div>';
    }
}

async function updateAdminAppointmentStatus(id, status) {
    try {
        const res = await apiFetch('/appointments/' + id + '/status', {
            method: 'PUT',
            body: JSON.stringify({ status })
        });
        if (res.ok) {
            showMsg('admin-appt-msg', 'Appointment ' + id + ' marked as ' + status + '.', 'success');
            loadAdminAppointments();
        } else {
            showMsg('admin-appt-msg', 'Could not update appointment.', 'error');
        }
    } catch {
        showMsg('admin-appt-msg', 'Cannot connect to server.', 'error');
    }
}

// ─── Admin Reports ─────────────────────────────────────────

// Holds the most recently generated report data so Export can reuse it
// without making another network request.
let lastReportData = null;

function showReportTab(tab) {
    document.querySelectorAll('.report-subtab').forEach(el => el.classList.add('hidden'));
    document.querySelectorAll('.tabs-bar .tab-btn').forEach(btn => btn.classList.remove('active'));

    const target = document.getElementById('rtab-' + tab);
    const btn    = document.getElementById('rtab-btn-' + tab);
    if (target) target.classList.remove('hidden');
    if (btn)    btn.classList.add('active');
    if (tab === 'analytics' && lastAnalytics) renderAnalyticsCharts(lastAnalytics);
}

async function populateReportDeptFilter() {
    const sel = document.getElementById('report-dept');
    if (!sel || sel.dataset.loaded === 'true') return;
    try {
        const res   = await apiFetch('/departments');
        const depts = await res.json();
        depts.forEach(d => {
            const opt = document.createElement('option');
            opt.value = d.departmentId;
            opt.textContent = d.name;
            sel.appendChild(opt);
        });
        sel.dataset.loaded = 'true';
    } catch {
        console.error('Could not load department filter options');
    }
}

// The latest analytics response (waiting times, volumes, etc.)
let lastAnalytics = null;

function localDateStr(d) {
    const y  = d.getFullYear();
    const m  = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + dd;
}

// Default range = the last 30 days
function initReportDates() {
    const fromEl = document.getElementById('report-from');
    const toEl   = document.getElementById('report-to');
    const today  = new Date();
    if (toEl && !toEl.value) toEl.value = localDateStr(today);
    if (fromEl && !fromEl.value) {
        const start = new Date(today);
        start.setDate(start.getDate() - 29);
        fromEl.value = localDateStr(start);
    }
}

function fmtMin(v) {
    return (v === null || v === undefined) ? '—' : Number(v).toFixed(1) + ' min';
}

function fmtPct(v) {
    return (v === null || v === undefined) ? '—' : Number(v).toFixed(1) + '%';
}

function setTextById(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

async function generateReport(silent = false) {
    hideMsg('report-msg');

    const deptFilter = document.getElementById('report-dept')?.value || '';
    const dateFrom   = document.getElementById('report-from')?.value || '';
    const dateTo     = document.getElementById('report-to')?.value || '';

    if (dateFrom && dateTo && dateFrom > dateTo) {
        showMsg('report-msg', 'The start date must not be after the end date.', 'error');
        return;
    }

    const params = new URLSearchParams();
    if (dateFrom)   params.set('from', dateFrom);
    if (dateTo)     params.set('to', dateTo);
    if (deptFilter) params.set('departmentId', deptFilter);

    try {
        // --- Waiting times + analytics (real data from recorded timestamps) ---
        const analyticsRes = await apiFetch('/reports/analytics?' + params.toString());
        const analytics    = await analyticsRes.json();
        if (!analyticsRes.ok) {
            showMsg('report-msg', analytics.error || 'Could not generate report.', 'error');
            return;
        }

        // --- Today's live queue snapshot ---
        const summaryRes = await apiFetch('/reports/staff-summary');
        const summary    = await summaryRes.json();

        // --- Appointments (filtered by department and date range) ---
        const apptRes = await apiFetch('/appointments?scope=all');
        let appts     = await apptRes.json();

        if (deptFilter) appts = appts.filter(a => String(a.departmentId) === String(deptFilter));
        if (dateFrom)   appts = appts.filter(a => a.appointmentDate >= dateFrom);
        if (dateTo)     appts = appts.filter(a => a.appointmentDate <= dateTo);

        let deptPerf = summary.departments || [];
        if (deptFilter) {
            deptPerf = deptPerf.filter(d => String(d.departmentId) === String(deptFilter));
        }

        lastAnalytics  = analytics;
        lastReportData = { summary, appts, deptPerf, analytics };

        renderAnalyticsOverview(analytics);
        renderWaitingTimes(analytics);
        renderInsights(analytics);
        renderAnalyticsCharts(analytics);
        renderAppointmentsReport(appts);
        renderQueuePerformance(deptPerf);

        if (!silent) showMsg('report-msg', 'Report generated successfully!', 'success');
    } catch {
        showMsg('report-msg', 'Could not generate report. Cannot connect to server.', 'error');
    }
}

function renderAnalyticsOverview(a) {
    const t = a.totals, w = a.wait, sv = a.service, ap = a.appointments;

    const deptLabel = a.departmentId == null
        ? 'All departments'
        : ((a.byDepartment && a.byDepartment[0]) ? a.byDepartment[0].name : 'Selected department');
    setTextById('report-period', a.from + ' to ' + a.to + ' · ' + deptLabel);

    setTextById('arep-patients',   t.patients);
    setTextById('arep-served',     t.served);
    setTextById('arep-noshow',     t.noShow);
    setTextById('arep-noshowrate', fmtPct(t.noShowRate));

    setTextById('arep-avgwait',    fmtMin(w.average));
    setTextById('arep-medianwait', fmtMin(w.median));
    setTextById('arep-maxwait',    fmtMin(w.max));
    setTextById('arep-avgservice', fmtMin(sv.average));

    setTextById('arep-appts',     ap.total);
    setTextById('arep-scheduled', ap.pending + ap.confirmed);
    setTextById('arep-done',      ap.done);
    setTextById('arep-cancelled', ap.cancelled);

    let note = '';
    if (w.count > 0) {
        note = 'Waiting times are based on ' + w.count + ' patient' + (w.count === 1 ? '' : 's')
            + ' with recorded call times.';
    } else if (t.patients > 0) {
        note = 'No waiting-time data for this period yet. Waiting times are recorded when staff call a patient, '
            + 'so visits from before this feature was added have none.';
    }
    setTextById('report-sample-note', note);
}

function tableHtml(headers, rows) {
    return '<table><thead><tr>' + headers.map(h => '<th>' + h + '</th>').join('') + '</tr></thead>'
        + '<tbody>' + rows.map(r => '<tr>' + r.map(c => '<td>' + c + '</td>').join('') + '</tr>').join('')
        + '</tbody></table>';
}

function renderWaitingTimes(a) {
    const deptEl = document.getElementById('report-wait-dept');
    const prioEl = document.getElementById('report-wait-priority');
    const dayEl  = document.getElementById('report-wait-daily');

    if (deptEl) {
        deptEl.innerHTML = (a.byDepartment && a.byDepartment.length)
            ? tableHtml(
                ['Department', 'Patients', 'Served', 'No-shows', 'Average Wait', 'Longest Wait', 'Average Service'],
                a.byDepartment.map(d => [escapeHtml(d.name), d.patients, d.served, d.noShow,
                    fmtMin(d.avgWaitMin), fmtMin(d.maxWaitMin), fmtMin(d.avgServiceMin)]))
            : '<div class="empty-state">No departments found.</div>';
    }

    if (prioEl) {
        prioEl.innerHTML = (a.byPriority && a.byPriority.length)
            ? tableHtml(['Priority', 'Patients', 'Average Wait'],
                a.byPriority.map(p => [escapeHtml(p.category), p.patients, fmtMin(p.avgWaitMin)]))
            : '<div class="empty-state">No priority categories found.</div>';
    }

    if (dayEl) {
        dayEl.innerHTML = (a.byDay && a.byDay.length)
            ? tableHtml(['Date', 'Patients', 'Served', 'Average Wait'],
                a.byDay.map(d => [d.date, d.patients, d.served, fmtMin(d.avgWaitMin)]))
            : '<div class="empty-state">No patients in this period.</div>';
    }
}

// Plain-language findings to support planning (pure function: easy to test)
function buildInsights(a) {
    const out = [];
    const t = a.totals || {};
    if (!t.patients) return ['No queue activity was recorded in this period.'];

    const hh = h => String(h % 24).padStart(2, '0');
    const plural = n => (n === 1 ? '' : 's');

    if (!a.wait || !a.wait.count) {
        out.push('Waiting times are not available for this period (no patients with recorded call times).');
    }

    const hours = a.byHour || [];
    let peakHour = null;
    hours.forEach(h => { if (h.patients > 0 && (!peakHour || h.patients > peakHour.patients)) peakHour = h; });
    if (peakHour) {
        out.push('Busiest arrival hour: ' + hh(peakHour.hour) + ':00–' + hh(peakHour.hour + 1) + ':00 ('
            + peakHour.patients + ' patient' + plural(peakHour.patients) + ').');
    }

    const days = a.byDay || [];
    if (days.length > 1) {
        const busiest = days.reduce((best, d) => (d.patients > best.patients ? d : best), days[0]);
        out.push('Busiest day: ' + busiest.date + ' (' + busiest.patients + ' patients).');
    }

    const timed = (a.byDepartment || []).filter(d => d.avgWaitMin !== null && d.avgWaitMin !== undefined);
    if (timed.length >= 1) {
        const longest = timed.reduce((best, d) => (d.avgWaitMin > best.avgWaitMin ? d : best), timed[0]);
        out.push('Longest average wait: ' + longest.name + ' (' + fmtMin(longest.avgWaitMin) + ').');
    }
    if (timed.length >= 2) {
        const shortest = timed.reduce((best, d) => (d.avgWaitMin < best.avgWaitMin ? d : best), timed[0]);
        out.push('Shortest average wait: ' + shortest.name + ' (' + fmtMin(shortest.avgWaitMin) + ').');
    }

    if (t.served + t.noShow > 0) {
        let worst = null;
        (a.byDepartment || []).forEach(d => {
            const finished = d.served + d.noShow;
            if (finished > 0 && d.noShow > 0) {
                const rate = d.noShow / finished;
                if (!worst || rate > worst.rate) worst = { name: d.name, rate, noShow: d.noShow, finished };
            }
        });
        out.push(worst
            ? 'Highest no-show rate: ' + worst.name + ' (' + fmtPct(worst.rate * 100) + ', '
            + worst.noShow + ' of ' + worst.finished + ' patients).'
            : 'No no-shows were recorded in this period.');
    }

    const prios = a.byPriority || [];
    if (prios.length >= 2) {
        const top = prios[0], normal = prios[prios.length - 1];
        if (top.avgWaitMin !== null && top.avgWaitMin !== undefined
            && normal.avgWaitMin !== null && normal.avgWaitMin !== undefined) {
            out.push(top.category + ' patients waited ' + fmtMin(top.avgWaitMin)
                + ' on average, compared with ' + fmtMin(normal.avgWaitMin) + ' for ' + normal.category + ' patients.');
        }
    }

    if (a.service && a.service.average !== null && a.service.average !== undefined) {
        out.push('Average service time: ' + fmtMin(a.service.average) + '.');
    }
    return out;
}

function renderInsights(a) {
    const el = document.getElementById('report-insights');
    if (!el) return;
    el.innerHTML = buildInsights(a).map(text => '<li>' + escapeHtml(text) + '</li>').join('');
}

function renderAnalyticsCharts(a) {
    // Arrivals by hour (always shows at least 07h-17h; the busiest hour is highlighted)
    const hours    = a.byHour || [];
    const withData = hours.filter(h => h.patients > 0).map(h => h.hour);
    const first    = Math.min(7,  ...(withData.length ? withData : [7]));
    const last     = Math.max(17, ...(withData.length ? withData : [17]));
    const peak     = Math.max(0, ...hours.map(h => h.patients));

    const hourItems = hours
        .filter(h => h.hour >= first && h.hour <= last)
        .map(h => ({
            label: String(h.hour).padStart(2, '0') + 'h',
            value: h.patients,
            color: (peak > 0 && h.patients === peak) ? '#fa7b17' : '#1a73e8'
        }));
    drawBarChart('report-hours-chart', hourItems);

    const dayItems = (a.byDay || []).map(d => ({
        label: d.date.slice(5), value: d.patients, color: '#34a853'
    }));
    drawBarChart('report-days-chart', dayItems);
}

function renderAppointmentsReport(appts) {
    const container = document.getElementById('report-appointments-list');
    if (!container) return;

    if (!appts.length) {
        container.innerHTML = '<div class="empty-state">No appointments match the selected filters.</div>';
        return;
    }

    container.innerHTML = `
        <table>
            <thead>
                <tr>
                    <th>ID</th><th>Patient ID</th><th>Department ID</th>
                    <th>Date</th><th>Time</th><th>Status</th>
                </tr>
            </thead>
            <tbody>
                ${appts.map(a => `
                <tr>
                    <td>${a.appointmentId}</td>
                    <td>${a.patientId}</td>
                    <td>${a.departmentId}</td>
                    <td>${a.appointmentDate}</td>
                    <td>${a.appointmentTime}</td>
                    <td>${badgeHtml(a.status)}</td>
                </tr>`).join('')}
            </tbody>
        </table>`;
}

function renderQueuePerformance(deptPerf) {
    const container = document.getElementById('report-queue-list');
    if (!container) return;

    if (!deptPerf.length) {
        container.innerHTML = '<div class="empty-state">No department data available.</div>';
        return;
    }

    container.innerHTML = `
        <table>
            <thead>
                <tr>
                    <th>Department</th><th>Waiting</th><th>Served Today</th><th>No-show Today</th>
                </tr>
            </thead>
            <tbody>
                ${deptPerf.map(d => `
                <tr>
                    <td>${d.name}</td>
                    <td>${d.waiting}</td>
                    <td>${d.served}</td>
                    <td>${d.noShow}</td>
                </tr>`).join('')}
            </tbody>
        </table>`;
}

function exportReportCsv(type) {
    if (!lastReportData) {
        showMsg('report-msg', 'Please click Generate Report first.', 'error');
        showReportTab('export');
        return;
    }

    let rows = [];
    let filename = 'report.csv';

    if (type === 'appointments') {
        rows = [['Appointment ID', 'Patient ID', 'Department ID', 'Date', 'Time', 'Status']];
        lastReportData.appts.forEach(a => {
            rows.push([a.appointmentId, a.patientId, a.departmentId, a.appointmentDate, a.appointmentTime, a.status]);
        });
        filename = 'appointments_report.csv';
    } else if (type === 'waiting') {
        rows = [['Department', 'Patients', 'Served', 'No-shows', 'Average wait (min)', 'Longest wait (min)', 'Average service (min)']];
        (lastReportData.analytics.byDepartment || []).forEach(d => {
            rows.push([d.name, d.patients, d.served, d.noShow,
                d.avgWaitMin ?? '', d.maxWaitMin ?? '', d.avgServiceMin ?? '']);
        });
        filename = 'waiting_times_report.csv';
    } else if (type === 'daily') {
        rows = [['Date', 'Patients', 'Served', 'Average wait (min)']];
        (lastReportData.analytics.byDay || []).forEach(d => {
            rows.push([d.date, d.patients, d.served, d.avgWaitMin ?? '']);
        });
        filename = 'daily_summary_report.csv';
    } else if (type === 'queue') {
        rows = [['Department', 'Waiting', 'Served Today', 'No-show Today']];
        lastReportData.deptPerf.forEach(d => {
            rows.push([d.name, d.waiting, d.served, d.noShow]);
        });
        filename = 'queue_performance_report.csv';
    }

    const csvContent = rows.map(r => r.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

// ─── Helpers ───────────────────────────────────────────────

function todayStr() {
    return new Date().toLocaleDateString('en-ZA', {
        year: 'numeric', month: 'long', day: 'numeric'
    });
}

function nowStr() {
    return new Date().toLocaleTimeString('en-ZA', {
        hour: '2-digit', minute: '2-digit'
    });
}

function updateDashboardDate() {
    const d = todayStr() + ' | ' + nowStr();
    ['dash-date', 'book-date-display', 'staff-queue-date', 'staff-date'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.textContent = d;
    });
}

function showMsg(id, text, type) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    el.className   = 'msg ' + type;
}

function hideMsg(id) {
    const el = document.getElementById(id);
    if (el) el.className = 'msg hidden';
}

function badgeHtml(status) {
    const cls = {
        Waiting:      'badge-waiting',
        Called:       'badge-called',
        Serving:      'badge-serving',
        'In-Progress':'badge-serving',
        Done:         'badge-done',
        Pending:      'badge-pending',
        Confirmed:    'badge-confirmed',
        Cancelled:    'badge-cancelled'
    }[status] || '';
    return `<span class="badge ${cls}">${status}</span>`;
}

function queueStatusLabel(status) {
    return {
        Waiting: 'WAITING',
        Called: 'CALLED',
        Serving: 'IN SERVICE',
        Done: 'COMPLETED',
        'No-show': 'SKIPPED'
    }[status] || status;
}

async function apiFetch(path, options = {}) {
    const res = await fetch(API + path, {
        headers: { 'Content-Type': 'application/json' },
        ...options
    });
    return res;
}

// ─── Auth ──────────────────────────────────────────────────

async function login() {
    hideMsg('login-error');
    const email    = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value.trim();

    if (!email || !password) {
        showMsg('login-error', 'Please enter email and password.', 'error');
        return;
    }

    // Admin shortcut
    if (email === 'admin' && password === 'admin123') {
        showPage('page-admin');
        adminLogin(true);
        return;
    }

    // Wrong password for the reserved admin username
    if (email === 'admin') {
        showMsg('login-error', 'Invalid credentials.', 'error');
        return;
    }

    // Staff log in with a plain username; patients log in with their email address
    if (!email.includes('@')) {
        const result = await authenticateStaff(email, password);
        if (result.ok) {
            showPage('page-staff');
            enterStaffApp(result.data);
        } else {
            showMsg('login-error', result.error, 'error');
        }
        return;
    }

    // Patient login via API
    try {
        const res  = await apiFetch('/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();

        if (res.ok) {
            currentPatient = data;
            const firstName = data.name.split(' ')[0];
            const greeting  = document.getElementById('dash-greeting');
            if (greeting) greeting.textContent = 'Hello, ' + firstName + '!';

            const sidebarName = document.getElementById('sidebar-name');
            if (sidebarName) sidebarName.textContent = data.name;

            const initials = data.name.split(' ').map(n => n[0]).join('').toUpperCase();
            document.querySelectorAll('.user-avatar').forEach(el => {
                if (el.closest('#page-patient')) el.textContent = initials;
            });
            const pa = document.getElementById('profile-avatar');
            const pn = document.getElementById('profile-name');
            const pe = document.getElementById('profile-email');
            if (pa) pa.textContent = initials;
            if (pn) pn.textContent = data.name;
            if (pe) pe.textContent = data.email;

            showPage('page-patient');
        } else {
            showMsg('login-error', data.error || 'Login failed.', 'error');
        }
    } catch {
        showMsg('login-error', 'Cannot connect to server. Is Java running?', 'error');
    }
}

async function register() {
    hideMsg('reg-msg');
    const name     = document.getElementById('reg-name').value.trim();
    const contact  = document.getElementById('reg-contact').value.trim();
    const email    = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value.trim();
    const confirm  = document.getElementById('reg-confirm').value.trim();
    const terms    = document.getElementById('reg-terms').checked;

    if (!terms) {
        showMsg('reg-msg', 'Please accept the terms and conditions.', 'error');
        return;
    }
    if (password !== confirm) {
        showMsg('reg-msg', 'Passwords do not match.', 'error');
        return;
    }

    try {
        const res  = await apiFetch('/register', {
            method: 'POST',
            body: JSON.stringify({ name, contact, email, password })
        });
        const data = await res.json();

        if (res.ok) {
            showMsg('reg-msg', 'Account created successfully! Redirecting to login...', 'success');
            setTimeout(() => showPage('page-login'), 1800);
        } else {
            showMsg('reg-msg', data.error || 'Registration failed.', 'error');
        }
    } catch {
        showMsg('reg-msg', 'Cannot connect to server.', 'error');
    }
}

function logout() {
    stopQueuePolling();
    stopHistoryPolling();
    currentPatient = null;
    showPage('page-logout');
}

// ─── Departments ───────────────────────────────────────────

async function loadDepartments() {
    try {
        const res   = await apiFetch('/departments');
        const depts = await res.json();
        departmentList = depts;

        ['book-dept', 'qs-dept', 'staff-dept', 'walkin-dept'].forEach(id => {
            const sel = document.getElementById(id);
            if (!sel) return;
            sel.innerHTML = depts.map(d =>
                `<option value="${d.departmentId}">${d.name} (limit: ${d.dailyLimit})</option>`
            ).join('');
        });
    } catch {
        console.error('Could not load departments');
    }
}

async function loadPriorities() {
    try {
        const res = await apiFetch('/priorities');
        priorityList = await res.json();

        const walkinSel = document.getElementById('walkin-priority');
        if (walkinSel) {
            walkinSel.innerHTML = priorityList.map(p =>
                `<option value="${p.priorityId}">${p.category}</option>`
            ).join('');
            // Default to "Normal" if present, otherwise the last (lowest-rank) option
            const normal = priorityList.find(p => p.category === 'Normal');
            if (normal) walkinSel.value = normal.priorityId;
        }
    } catch {
        console.error('Could not load priorities');
    }
}

function priorityLabel(priorityId) {
    const p = priorityList.find(p => p.priorityId === priorityId);
    return p ? p.category : 'Priority ' + priorityId;
}

function priorityDropdownOptions(selectedId) {
    return priorityList.map(p =>
        `<option value="${p.priorityId}" ${p.priorityId === selectedId ? 'selected' : ''}>${p.category}</option>`
    ).join('');
}

function departmentName(departmentId) {
    const d = departmentList.find(d => d.departmentId === departmentId);
    return d ? d.name : 'Dept ' + departmentId;
}

function departmentDropdownOptions(excludeId) {
    return departmentList
        .filter(d => d.departmentId !== excludeId)
        .map(d => `<option value="${d.departmentId}">${d.name}</option>`)
        .join('');
}

async function transferPatient(queueId, selectId) {
    const sel = document.getElementById(selectId);
    if (!sel || !sel.value) return;
    const departmentId = parseInt(sel.value);
    const destination  = sel.options[sel.selectedIndex].text;

    if (!confirm('Transfer this patient to ' + destination + '?')) return;

    try {
        const res  = await apiFetch('/queue/' + queueId + '/transfer', {
            method: 'PUT',
            body: JSON.stringify({ departmentId })
        });
        const data = await res.json();

        if (res.ok) {
            showMsg('staff-msg',
                'Patient transferred to ' + data.departmentName + ' — new queue number: ' + data.queueNumber,
                'success');
            loadStaffQueue();
        } else {
            showMsg('staff-msg', data.error || 'Could not transfer patient.', 'error');
        }
    } catch {
        showMsg('staff-msg', 'Cannot connect to server.', 'error');
    }
}

async function updatePriority(queueId, selectId) {
    const sel = document.getElementById(selectId);
    if (!sel) return;
    const priorityId = parseInt(sel.value);

    try {
        const res = await apiFetch('/queue/' + queueId + '/priority', {
            method: 'PUT',
            body: JSON.stringify({ priorityId })
        });
        if (res.ok) {
            showMsg('staff-msg', 'Priority updated.', 'success');
            loadStaffQueue();
        } else {
            showMsg('staff-msg', 'Could not update priority.', 'error');
        }
    } catch {
        showMsg('staff-msg', 'Cannot connect to server.', 'error');
    }
}

function updateSummary() {
    const deptSel = document.getElementById('book-dept');
    const dateEl  = document.getElementById('book-date');
    const timeEl  = document.getElementById('book-time');
    if (!deptSel) return;
    const deptText = deptSel.options[deptSel.selectedIndex]?.text.split(' (')[0] || '—';
    const sd    = document.getElementById('sum-dept');
    const sdate = document.getElementById('sum-date');
    const stime = document.getElementById('sum-time');
    if (sd)    sd.textContent    = deptText;
    if (sdate) sdate.textContent = dateEl?.value || '—';
    if (stime) stime.textContent = timeEl?.value || '—';
}

// ─── Appointments ──────────────────────────────────────────

async function bookAppointment() {
    if (!currentPatient) return;
    hideMsg('book-msg');

    const deptId = document.getElementById('book-dept').value;
    const date   = document.getElementById('book-date').value;
    const time   = document.getElementById('book-time').value;

    if (!deptId || !date || !time) {
        showMsg('book-msg', 'Please fill in all fields.', 'error');
        return;
    }

    try {
        const res  = await apiFetch('/appointments', {
            method: 'POST',
            body: JSON.stringify({
                patientId:    currentPatient.patientId,
                departmentId: parseInt(deptId),
                date, time
            })
        });
        const data = await res.json();

        if (res.ok) {
            const deptText = document.getElementById('book-dept')
                .options[document.getElementById('book-dept').selectedIndex]
                .text.split(' (')[0];

            // Store last booked department for queue status
            try { localStorage.setItem('lastDeptId', deptId); } catch {}

            const queueNum = data.queueNumber || '—';
            const cnEl = document.getElementById('confirm-number');
            const cdEl = document.getElementById('confirm-details');
            if (cnEl) cnEl.textContent = queueNum;
            if (cdEl) cdEl.innerHTML =
                `<div>Date: ${date}</div><div>Time: ${time}</div><div>Department: ${deptText}</div>`;

            showPage('page-confirmation');
        } else {
            showMsg('book-msg', data.error || 'Booking failed.', 'error');
        }
    } catch {
        showMsg('book-msg', 'Cannot connect to server.', 'error');
    }
}

async function loadAppointments() {
    if (!currentPatient) return;
    const container = document.getElementById('appointments-list');
    if (!container) return;

    try {
        const res  = await apiFetch('/appointments/' + currentPatient.patientId);
        const list = await res.json();

        if (!list.length) {
            container.innerHTML = '<div class="empty-state">No appointments found.</div>';
            return;
        }

        const upcoming = list.find(a => a.status !== 'Cancelled');
        if (upcoming) {
            const ud = document.getElementById('upcoming-date');
            if (ud) ud.textContent = upcoming.appointmentDate + ' | ' + upcoming.appointmentTime;
        }

        container.innerHTML = `
            <table>
                <thead>
                    <tr><th>ID</th><th>Department</th><th>Date</th><th>Time</th><th>Status</th><th>Live Status</th><th>Action</th></tr>
                </thead>
                <tbody>
                    ${list.map(a => `
                    <tr>
                        <td>${a.appointmentId}</td>
                        <td>Dept ${a.departmentId}</td>
                        <td>${a.appointmentDate}</td>
                        <td>${a.appointmentTime}</td>
                        <td>${badgeHtml(a.status)}</td>
                        <td>${a.queueStatus ? badgeHtml(a.queueStatus) + ' <span style="font-size:12px;color:#5f6368">' + (a.queueNumber || '') + '</span>' : '—'}</td>
                        <td>${a.status !== 'Cancelled'
            ? `<button class="btn btn-sm btn-danger" onclick="cancelAppointment(${a.appointmentId})">Cancel</button>`
            : '—'}</td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML = '<div class="empty-state">Could not load appointments.</div>';
    }
}

async function cancelAppointment(id) {
    if (!confirm('Cancel this appointment?')) return;
    await apiFetch('/appointments/' + id, { method: 'DELETE' });
    loadAppointments();
}

// ─── Queue Status ──────────────────────────────────────────

function startQueuePolling() {
    stopQueuePolling();
    queuePollInterval = setInterval(loadMyQueueStatus, 4000);
}

function stopQueuePolling() {
    if (queuePollInterval) {
        clearInterval(queuePollInterval);
        queuePollInterval = null;
    }
}

function startHistoryPolling() {
    stopHistoryPolling();
    historyPollInterval = setInterval(loadAppointments, 4000);
}

function stopHistoryPolling() {
    if (historyPollInterval) {
        clearInterval(historyPollInterval);
        historyPollInterval = null;
    }
}

async function loadMyQueueStatus() {
    if (!currentPatient) return;

    try {
        const res = await apiFetch('/queue/patient/' + currentPatient.patientId);

        if (res.status === 404) {
            const els = ['qs-my-number','qs-position','qs-wait','qs-now-serving'];
            els.forEach(id => { const el = document.getElementById(id); if (el) el.textContent = '—'; });
            const nl = document.getElementById('qs-next-list');
            if (nl) nl.innerHTML = '<div class="empty-state" style="padding:20px">No appointment booked today</div>';
            return;
        }

        const data = await res.json();

        const mnEl  = document.getElementById('qs-my-number');
        const posEl = document.getElementById('qs-position');
        const wEl   = document.getElementById('qs-wait');
        const nsEl  = document.getElementById('qs-now-serving');

        if (mnEl) mnEl.textContent = data.queueNumber;
        if (nsEl) nsEl.textContent = data.nowServing || '—';

        if (data.status === 'Waiting') {
            if (posEl) posEl.textContent = data.position;
            const wait = data.estimatedWaitMin;
            if (wEl) wEl.textContent = wait + ' - ' + (wait + 10) + ' mins';
        } else {
            if (posEl) posEl.textContent = '0';
            if (wEl)   wEl.textContent   = queueStatusLabel(data.status);
        }

        const thanksEl = document.querySelector('#tab-queue-status .qs-thankyou');
        if (thanksEl) {
            thanksEl.textContent = data.status === 'Waiting'
                ? 'Please wait for your number to be called. Thank you!'
                : 'Status: ' + queueStatusLabel(data.status);
        }

    } catch {
        console.error('Could not load queue status');
    }
}

// ─── Staff ─────────────────────────────────────────────────

async function authenticateStaff(username, password) {
    try {
        const res  = await apiFetch('/staff/login', {
            method: 'POST',
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        if (res.ok) return { ok: true, data };
        return { ok: false, error: data.error || 'Login failed.' };
    } catch {
        return { ok: false, error: 'Cannot connect to server. Is Java running?' };
    }
}

// Login form on the staff page itself
async function staffLogin() {
    hideMsg('staff-login-msg');
    const username = document.getElementById('staff-user').value.trim();
    const password = document.getElementById('staff-pass').value.trim();

    if (!username || !password) {
        showMsg('staff-login-msg', 'Please enter your username and password.', 'error');
        return;
    }

    const result = await authenticateStaff(username, password);
    if (!result.ok) {
        showMsg('staff-login-msg', result.error, 'error');
        return;
    }

    document.getElementById('staff-pass').value = '';
    enterStaffApp(result.data);
}

function enterStaffApp(staff) {
    currentStaff = staff;

    const nameEl = document.getElementById('staff-sidebar-name');
    const avEl   = document.getElementById('staff-avatar');
    if (nameEl) nameEl.textContent = staff.name;
    if (avEl) {
        avEl.textContent = staff.name.split(' ').filter(Boolean)
            .map(n => n[0]).join('').toUpperCase().slice(0, 2);
    }

    const ls = document.getElementById('staff-login-screen');
    const sa = document.getElementById('staff-app');
    if (ls) ls.classList.add('hidden');
    if (sa) sa.classList.remove('hidden');
    loadDepartments();
    loadPriorities();
    const sd = document.getElementById('staff-date');
    if (sd) sd.textContent = todayStr() + ' | ' + nowStr();
    showStaffTab('stab-queue');
}

function staffLogout() {
    currentStaff = null;
    const su = document.getElementById('staff-user');
    const sp = document.getElementById('staff-pass');
    if (su) su.value = '';
    if (sp) sp.value = '';
    const ls = document.getElementById('staff-login-screen');
    const sa = document.getElementById('staff-app');
    if (ls) ls.classList.remove('hidden');
    if (sa) sa.classList.add('hidden');
    showPage('page-logout');
}

async function loadStaffStats() {
    try {
        const res   = await apiFetch('/departments');
        const depts = await res.json();

        // Count waiting across all departments
        let totalWaiting = 0;
        let totalDone    = 0;

        for (const d of depts) {
            const qRes  = await apiFetch('/queue/' + d.departmentId);
            const queue = await qRes.json();
            totalWaiting += queue.filter(q => q.status === 'Waiting').length;
            totalDone    += queue.filter(q => q.status === 'Done').length;
        }

        const totalEl   = document.getElementById('staff-total-patients');
        const waitingEl = document.getElementById('staff-waiting');
        const servedEl  = document.getElementById('staff-served');
        if (totalEl)   totalEl.textContent   = totalWaiting + totalDone;
        if (waitingEl) waitingEl.textContent = totalWaiting;
        if (servedEl)  servedEl.textContent  = totalDone;
    } catch {
        console.error('Could not load staff stats');
    }
}

async function loadStaffQueue() {
    const deptId    = document.getElementById('staff-dept').value;
    const container = document.getElementById('staff-queue-list');
    if (!container) return;

    try {
        const res   = await apiFetch('/queue/' + deptId);
        const queue = await res.json();

        if (!queue.length) {
            container.innerHTML = '<div class="empty-state">No patients in queue.</div>';
            return;
        }

        container.innerHTML = `
            <table>
                <thead>
                    <tr><th>Queue No.</th><th>Patient ID</th><th>Department</th><th>Status</th><th>Priority</th><th>Transfer To</th><th>Action</th></tr>
                </thead>
                <tbody>
                    ${queue.map(q => `
                    <tr>
                        <td><span class="queue-number">${q.queueNumber}</span></td>
                        <td>Patient ${q.patientId}</td>
                        <td>${departmentName(q.departmentId)}</td>
                        <td>${badgeHtml(q.status)}</td>
                        <td style="display:flex;gap:6px;align-items:center">
                            <select id="priority-select-${q.queueId}" class="form-select" style="padding:4px 8px;font-size:12px">
                                ${priorityDropdownOptions(q.priorityId)}
                            </select>
                            <button class="btn btn-sm btn-outline-blue" onclick="updatePriority(${q.queueId}, 'priority-select-${q.queueId}')">Set</button>
                        </td>
                        <td>
                            <div style="display:flex;gap:6px;align-items:center">
                                <select id="transfer-select-${q.queueId}" class="form-select" style="padding:4px 8px;font-size:12px">
                                    ${departmentDropdownOptions(q.departmentId)}
                                </select>
                                <button class="btn btn-sm btn-outline-blue" onclick="transferPatient(${q.queueId}, 'transfer-select-${q.queueId}')">Transfer</button>
                            </div>
                        </td>
                        <td style="display:flex;gap:6px">
                            <button class="btn btn-sm btn-success" onclick="markServing(${q.queueId})">Start Service</button>
                            <button class="btn btn-sm btn-success" onclick="markDone(${q.queueId})">Complete</button>
                            <button class="btn btn-sm btn-danger" onclick="markNoShow(${q.queueId})">Skip</button>
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML = '<div class="empty-state">Could not load queue.</div>';
    }
}

async function callNext() {
    const deptId = document.getElementById('staff-dept').value;
    try {
        const res  = await apiFetch('/queue/' + deptId + '/next', { method: 'POST' });
        const data = await res.json();
        if (res.ok) {
            showMsg('staff-msg', 'Now calling: Queue #' + data.queueNumber, 'success');
            loadStaffQueue();
        } else {
            showMsg('staff-msg', 'No patients waiting.', 'error');
        }
    } catch {
        showMsg('staff-msg', 'Cannot connect to server.', 'error');
    }
}

async function registerWalkIn() {
    hideMsg('walkin-msg');

    const name       = document.getElementById('walkin-name')?.value.trim();
    const contact    = document.getElementById('walkin-contact')?.value.trim();
    const email      = document.getElementById('walkin-email')?.value.trim();
    const deptId     = document.getElementById('walkin-dept')?.value;
    const priorityId = document.getElementById('walkin-priority')?.value;

    if (!name || !deptId) {
        showMsg('walkin-msg', 'Please enter a name and select a department.', 'error');
        return;
    }

    try {
        const res  = await apiFetch('/walkin', {
            method: 'POST',
            body: JSON.stringify({
                name, contact, email: email || null,
                departmentId: parseInt(deptId),
                priorityId: parseInt(priorityId)
            })
        });
        const data = await res.json();

        if (res.ok) {
            showMsg('walkin-msg',
                data.patientName + ' registered — Queue Number: ' + data.queueNumber,
                'success');
            document.getElementById('walkin-name').value    = '';
            document.getElementById('walkin-contact').value = '';
            document.getElementById('walkin-email').value   = '';

            // Refresh the queue list if staff is viewing the same department
            const staffDeptSel = document.getElementById('staff-dept');
            if (staffDeptSel && staffDeptSel.value === deptId) {
                loadStaffQueue();
            }
        } else {
            showMsg('walkin-msg', data.error || 'Could not register walk-in patient.', 'error');
        }
    } catch {
        showMsg('walkin-msg', 'Cannot connect to server.', 'error');
    }
}

async function markServing(queueId) {
    await apiFetch('/queue/' + queueId + '/serving', { method: 'PUT' });
    loadStaffQueue();
}

async function markDone(queueId) {
    await apiFetch('/queue/' + queueId + '/done', { method: 'PUT' });
    loadStaffQueue();
}

async function markNoShow(queueId) {
    await apiFetch('/queue/' + queueId + '/noshow', { method: 'PUT' });
    loadStaffQueue();
}

// ─── Admin ─────────────────────────────────────────────────

function adminLogin(skipCheck = false) {
    if (!skipCheck) {
        const user = document.getElementById('admin-user').value.trim();
        const pass = document.getElementById('admin-pass').value.trim();
        if (user !== 'admin' || pass !== 'admin123') {
            showMsg('admin-login-msg', 'Invalid credentials.', 'error');
            return;
        }
    }
    const als = document.getElementById('admin-login-screen');
    const aa  = document.getElementById('admin-app');
    if (als) als.classList.add('hidden');
    if (aa)  aa.classList.remove('hidden');
    const ad = document.getElementById('admin-date');
    if (ad) ad.textContent = todayStr() + ' | ' + nowStr();
    showAdminTab('atab-users');
}

function adminLogout() {
    const als = document.getElementById('admin-login-screen');
    const aa  = document.getElementById('admin-app');
    if (als) als.classList.remove('hidden');
    if (aa)  aa.classList.add('hidden');
    showPage('page-logout');
}

function startAdminPolling() {
    stopAdminPolling();
    adminPollInterval = setInterval(loadAdminStats, 5000);
}

function stopAdminPolling() {
    if (adminPollInterval) {
        clearInterval(adminPollInterval);
        adminPollInterval = null;
    }
}

async function loadAdminStats() {
    try {
        const res = await apiFetch('/admin/overview');
        const d   = await res.json();
        if (!res.ok) throw new Error(d.error || 'Request failed');

        const setText = (id, value) => {
            const el = document.getElementById(id);
            if (el) el.textContent = value;
        };
        setText('stat-patients',     d.totalPatients);
        setText('stat-appointments', d.totalAppointments);
        setText('stat-today',        d.todayAppointments);
        setText('stat-wait',         fmtMin(d.avgWaitTodayMinutes));
        setText('stat-wait-link',    d.waitingNow + ' waiting now · View reports');

        drawApptChart(d.appointmentsByStatus);
        renderDeptActivity(d.departments);
    } catch {
        console.error('Could not load admin stats');
    }
}

function renderDeptActivity(depts) {
    const perfEl = document.getElementById('dept-perf-list');
    if (!perfEl) return;

    if (!depts || !depts.length) {
        perfEl.innerHTML = '<div class="empty-state">No departments found.</div>';
        return;
    }

    const colors = ['#1a73e8', '#34a853', '#fa7b17', '#ea4335', '#9334e6'];
    perfEl.innerHTML =
        '<p style="font-size:12px;color:#64748b;margin:0 0 10px">Bar = share of today\'s daily limit used</p>' +
        depts.map((d, i) => {
            const pct = d.dailyLimit > 0
                ? Math.min(100, Math.round((d.joinedToday / d.dailyLimit) * 100)) : 0;
            return `<div class="dept-perf-item">
                <span style="width:120px;font-size:13px">${escapeHtml(d.name)}</span>
                <div class="perf-bar"><div class="perf-fill" style="width:${pct}%;background:${colors[i % colors.length]}"></div></div>
                <span style="font-size:12px;min-width:260px;text-align:right">${d.joinedToday}/${d.dailyLimit} · ${d.waiting} waiting · ${d.served} served${d.avgWaitMin !== null && d.avgWaitMin !== undefined ? ' · avg wait ' + fmtMin(d.avgWaitMin) : ''}</span>
            </div>`;
        }).join('');
}

// Appointments by status (dashboard)
function drawApptChart(counts) {
    if (!counts) return;
    const order  = ['Pending', 'Confirmed', 'Done', 'Cancelled'];
    const keys   = order.filter(k => k in counts)
        .concat(Object.keys(counts).filter(k => !order.includes(k)));
    const colors = { Pending: '#fa7b17', Confirmed: '#1a73e8', Done: '#34a853', Cancelled: '#ea4335' };

    drawBarChart('appt-chart', keys.map(k => ({
        label: k, value: counts[k], color: colors[k] || '#9334e6'
    })));
}

// Simple bar chart (no chart library needed). items = [{ label, value, color }]
function drawBarChart(canvasId, items, cssHeight = 160) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    canvas.style.width  = '100%';
    canvas.style.height = cssHeight + 'px';
    const cssWidth = canvas.getBoundingClientRect().width || 420;
    const dpr = window.devicePixelRatio || 1;
    canvas.width  = Math.round(cssWidth * dpr);
    canvas.height = Math.round(cssHeight * dpr);

    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssWidth, cssHeight);
    ctx.textAlign = 'center';

    if (!items.length) {
        ctx.fillStyle = '#64748b';
        ctx.font = '13px sans-serif';
        ctx.fillText('No data for this period', cssWidth / 2, cssHeight / 2);
        return;
    }

    const max       = Math.max(1, ...items.map(i => i.value));
    const topPad    = 20;
    const bottomPad = 24;
    const chartH    = cssHeight - topPad - bottomPad;
    const slot      = cssWidth / items.length;
    const barW      = Math.max(2, Math.min(56, slot * 0.6));
    const labelStep = Math.max(1, Math.ceil(36 / slot)); // skip some labels when bars are narrow

    items.forEach((item, i) => {
        const h = Math.round((item.value / max) * chartH);
        const x = slot * i + (slot - barW) / 2;
        const y = topPad + (chartH - h);

        ctx.fillStyle = item.color || '#1a73e8';
        ctx.fillRect(x, y, barW, h);

        if (slot >= 22 && (item.value > 0 || items.length <= 6)) {
            ctx.fillStyle = '#334155';
            ctx.font = '600 12px sans-serif';
            ctx.fillText(String(item.value), x + barW / 2, y - 6);
        }
        if (i % labelStep === 0) {
            ctx.fillStyle = '#64748b';
            ctx.font = '11px sans-serif';
            ctx.fillText(item.label, x + barW / 2, cssHeight - 8);
        }
    });
}

// ─── Admin: Staff Accounts ─────────────────────────────────

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

async function loadStaffAccounts() {
    const container = document.getElementById('staff-accounts-list');
    if (!container) return;

    try {
        const res  = await apiFetch('/staff');
        const list = await res.json();
        staffAccounts = list;

        if (!list.length) {
            container.innerHTML =
                '<div class="empty-state">No staff accounts yet. Create the first one above.</div>';
            return;
        }

        container.innerHTML = `
            <table>
                <thead>
                    <tr><th>ID</th><th>Name</th><th>Username</th><th>Status</th><th>Action</th></tr>
                </thead>
                <tbody>
                    ${list.map(s => `
                    <tr>
                        <td>${s.staffId}</td>
                        <td>${escapeHtml(s.name)}</td>
                        <td>${escapeHtml(s.username)}</td>
                        <td>${s.active
            ? '<span class="badge badge-confirmed">Active</span>'
            : '<span class="badge badge-cancelled">Disabled</span>'}</td>
                        <td>
                            <div style="display:flex;gap:6px">
                                <button class="btn btn-sm btn-outline-blue" onclick="editStaffAccount(${s.staffId})">Edit</button>
                                <button class="btn btn-sm ${s.active ? 'btn-danger' : 'btn-success'}"
                                    onclick="toggleStaffActive(${s.staffId}, ${!s.active})">${s.active ? 'Disable' : 'Enable'}</button>
                                <button class="btn btn-sm btn-danger" onclick="deleteStaffAccount(${s.staffId})">Delete</button>
                            </div>
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML = '<div class="empty-state">Could not load staff accounts.</div>';
    }
}

async function saveStaffAccount() {
    hideMsg('staff-form-msg');

    const name     = document.getElementById('staff-form-name').value.trim();
    const username = document.getElementById('staff-form-username').value.trim();
    const password = document.getElementById('staff-form-password').value.trim();
    const creating = editingStaffId === null;

    if (!name || !username) {
        showMsg('staff-form-msg', 'Please enter a full name and a username.', 'error');
        return;
    }
    if (creating && !password) {
        showMsg('staff-form-msg', 'Please set a password for the new account.', 'error');
        return;
    }

    try {
        const res  = await apiFetch(creating ? '/staff' : '/staff/' + editingStaffId, {
            method: creating ? 'POST' : 'PUT',
            body: JSON.stringify({ name, username, password })
        });
        const data = await res.json();

        if (res.ok) {
            resetStaffForm();
            showMsg('staff-form-msg', data.message || 'Saved.', 'success');
            loadStaffAccounts();
        } else {
            showMsg('staff-form-msg', data.error || 'Could not save the staff account.', 'error');
        }
    } catch {
        showMsg('staff-form-msg', 'Cannot connect to server.', 'error');
    }
}

function editStaffAccount(staffId) {
    const staff = staffAccounts.find(s => s.staffId === staffId);
    if (!staff) return;

    editingStaffId = staffId;
    document.getElementById('staff-form-name').value     = staff.name;
    document.getElementById('staff-form-username').value = staff.username;
    document.getElementById('staff-form-password').value = '';
    document.getElementById('staff-form-password').placeholder = 'Leave blank to keep the current password';
    document.getElementById('staff-form-title').textContent    = 'Edit Staff Account';
    document.getElementById('staff-form-password-label').textContent = 'New Password (optional)';
    document.getElementById('staff-form-submit').textContent   = 'Save Changes';
    document.getElementById('staff-form-cancel').classList.remove('hidden');
    hideMsg('staff-form-msg');
    document.getElementById('staff-form-title').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function cancelStaffEdit() {
    resetStaffForm();
}

function resetStaffForm() {
    editingStaffId = null;
    document.getElementById('staff-form-name').value     = '';
    document.getElementById('staff-form-username').value = '';
    document.getElementById('staff-form-password').value = '';
    document.getElementById('staff-form-password').placeholder = 'At least 6 characters';
    document.getElementById('staff-form-title').textContent    = 'Add Staff Account';
    document.getElementById('staff-form-password-label').textContent = 'Password';
    document.getElementById('staff-form-submit').textContent   = 'Create Account';
    document.getElementById('staff-form-cancel').classList.add('hidden');
    hideMsg('staff-form-msg');
}

async function toggleStaffActive(staffId, active) {
    try {
        const res  = await apiFetch('/staff/' + staffId + '/active', {
            method: 'PUT',
            body: JSON.stringify({ active })
        });
        const data = await res.json();
        showMsg('staff-form-msg',
            res.ok ? data.message : (data.error || 'Could not change the account status.'),
            res.ok ? 'success' : 'error');
        if (res.ok) loadStaffAccounts();
    } catch {
        showMsg('staff-form-msg', 'Cannot connect to server.', 'error');
    }
}

async function deleteStaffAccount(staffId) {
    const staff = staffAccounts.find(s => s.staffId === staffId);
    const label = staff ? staff.name + ' (' + staff.username + ')' : 'this account';
    if (!confirm('Delete the staff account for ' + label + '? This cannot be undone.')) return;

    try {
        const res  = await apiFetch('/staff/' + staffId, { method: 'DELETE' });
        const data = await res.json();

        if (res.ok) {
            if (editingStaffId === staffId) resetStaffForm();
            showMsg('staff-form-msg', data.message, 'success');
            loadStaffAccounts();
        } else {
            showMsg('staff-form-msg', data.error || 'Could not delete the staff account.', 'error');
        }
    } catch {
        showMsg('staff-form-msg', 'Cannot connect to server.', 'error');
    }
}

async function loadPatients() {
    const container = document.getElementById('patients-list');
    if (!container) return;
    try {
        const res      = await apiFetch('/patients');
        const patients = await res.json();

        if (!patients.length) {
            container.innerHTML = '<div class="empty-state">No patients registered.</div>';
            return;
        }
        container.innerHTML = `
            <table>
                <thead><tr><th>ID</th><th>Name</th><th>Contact</th><th>Email</th></tr></thead>
                <tbody>
                    ${patients.map(p => `
                    <tr>
                        <td>${p.patientId}</td>
                        <td>${p.name}</td>
                        <td>${p.contact}</td>
                        <td>${p.email}</td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML = '<div class="empty-state">Could not load patients.</div>';
    }
}

async function loadAdminDepts() {
    const container = document.getElementById('admin-depts-list');
    if (!container) return;
    try {
        const res   = await apiFetch('/departments');
        const depts = await res.json();
        container.innerHTML = `
            <table>
                <thead><tr><th>ID</th><th>Name</th><th>Daily Limit</th></tr></thead>
                <tbody>
                    ${depts.map(d => `
                    <tr><td>${d.departmentId}</td><td>${d.name}</td><td>${d.dailyLimit}</td></tr>
                    `).join('')}
                </tbody>
            </table>`;
    } catch {
        container.innerHTML = '<div class="empty-state">Could not load departments.</div>';
    }
}

async function updateLimit() {
    const deptId = document.getElementById('update-dept-id').value;
    const limit  = document.getElementById('update-limit').value;
    try {
        const res  = await apiFetch('/departments/' + deptId + '/limit', {
            method: 'PUT',
            body: JSON.stringify({ limit: parseInt(limit) })
        });
        const data = await res.json();
        if (res.ok) {
            showMsg('admin-dept-msg', 'Limit updated successfully.', 'success');
            loadAdminDepts();
        } else {
            showMsg('admin-dept-msg', data.error || 'Update failed.', 'error');
        }
    } catch {
        showMsg('admin-dept-msg', 'Cannot connect to server.', 'error');
    }
}

// ─── Queue Display ─────────────────────────────────────────

async function loadQueueDisplay() {
    try {
        const res   = await apiFetch('/departments');
        const depts = await res.json();
        if (!depts.length) return;

        const firstDept = depts[0];
        const qRes  = await apiFetch('/queue/' + firstDept.departmentId);
        const queue = await qRes.json();

        const serving = queue.find(q => q.status === 'Called' || q.status === 'Serving') || queue[0];
        const waiting = queue.filter(q => q.status === 'Waiting');

        const dnEl = document.getElementById('display-now');
        if (dnEl) dnEl.textContent = serving ? serving.queueNumber : '—';

        const nextEl = document.getElementById('display-next-list');
        if (nextEl) {
            nextEl.innerHTML = waiting.length
                ? waiting.slice(0, 5).map((q, i) => `
                    <div class="display-next-item">
                        <span class="next-num-big">${q.queueNumber}</span>
                        <span class="next-counter-text">Counter ${(i % 3) + 1}</span>
                    </div>`).join('')
                : '<div style="color:#94a3b8;padding:20px;text-align:center">No patients waiting</div>';
        }
    } catch {
        console.error('Could not load queue display');
    }
}

function startDisplayClock() {
    const el = document.getElementById('display-datetime');
    if (!el) return;
    function tick() {
        el.textContent = new Date().toLocaleDateString('en-ZA', {
            month: 'long', day: 'numeric', year: 'numeric'
        }) + ' | ' + nowStr();
    }
    tick();
    setInterval(tick, 60000);
}

// ─── Profile ───────────────────────────────────────────────

function loadProfile() {
    if (!currentPatient) return;
    const n  = document.getElementById('profile-name');
    const e  = document.getElementById('profile-email');
    const av = document.getElementById('profile-avatar');
    const initials = currentPatient.name.split(' ').map(x => x[0]).join('').toUpperCase();
    if (n)  n.textContent  = currentPatient.name;
    if (e)  e.textContent  = currentPatient.email;
    if (av) av.textContent = initials;

    const nameInput    = document.getElementById('profile-edit-name');
    const contactInput = document.getElementById('profile-edit-contact');
    const emailInput   = document.getElementById('profile-edit-email');
    if (nameInput)    nameInput.value    = currentPatient.name    || '';
    if (contactInput) contactInput.value = currentPatient.contact || '';
    if (emailInput)   emailInput.value   = currentPatient.email   || '';
    hideMsg('profile-edit-msg');
}

async function updateProfile() {
    if (!currentPatient) return;
    hideMsg('profile-edit-msg');

    const name    = document.getElementById('profile-edit-name')?.value.trim();
    const contact = document.getElementById('profile-edit-contact')?.value.trim();
    const email   = document.getElementById('profile-edit-email')?.value.trim();

    if (!name || !contact || !email) {
        showMsg('profile-edit-msg', 'Please fill in all fields.', 'error');
        return;
    }

    try {
        const res  = await apiFetch('/patients/' + currentPatient.patientId, {
            method: 'PUT',
            body: JSON.stringify({ name, contact, email })
        });
        const data = await res.json();

        if (res.ok) {
            currentPatient = { ...currentPatient, name: data.name, contact: data.contact, email: data.email };
            const initials = data.name.split(' ').map(n => n[0]).join('').toUpperCase();
            const sn = document.getElementById('sidebar-name');
            if (sn) sn.textContent = data.name;
            document.querySelectorAll('.user-avatar').forEach(el => {
                if (el.closest('#page-patient')) el.textContent = initials;
            });
            const pn = document.getElementById('profile-name');
            const pe = document.getElementById('profile-email');
            const pa = document.getElementById('profile-avatar');
            if (pn) pn.textContent = data.name;
            if (pe) pe.textContent = data.email;
            if (pa) pa.textContent = initials;
            const gr = document.getElementById('dash-greeting');
            if (gr) gr.textContent = 'Hello, ' + data.name.split(' ')[0] + '!';
            showMsg('profile-edit-msg', 'Profile updated successfully!', 'success');
        } else {
            showMsg('profile-edit-msg', data.error || 'Update failed.', 'error');
        }
    } catch {
        showMsg('profile-edit-msg', 'Cannot connect to server.', 'error');
    }
}

async function changePassword() {
    if (!currentPatient) return;
    hideMsg('profile-password-msg');

    const currentPassword = document.getElementById('profile-current-password')?.value.trim();
    const newPassword     = document.getElementById('profile-new-password')?.value.trim();
    const confirmPassword = document.getElementById('profile-confirm-password')?.value.trim();

    if (!currentPassword || !newPassword || !confirmPassword) {
        showMsg('profile-password-msg', 'Please fill in all password fields.', 'error');
        return;
    }
    if (newPassword !== confirmPassword) {
        showMsg('profile-password-msg', 'New passwords do not match.', 'error');
        return;
    }
    if (newPassword.length < 6) {
        showMsg('profile-password-msg', 'New password must be at least 6 characters.', 'error');
        return;
    }

    try {
        const res  = await apiFetch('/patients/' + currentPatient.patientId + '/password', {
            method: 'PUT',
            body: JSON.stringify({ currentPassword, newPassword })
        });
        const data = await res.json();

        if (res.ok) {
            showMsg('profile-password-msg', data.message || 'Password updated successfully!', 'success');
            ['profile-current-password','profile-new-password','profile-confirm-password']
                .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
        } else {
            showMsg('profile-password-msg', data.error || 'Could not update password.', 'error');
        }
    } catch {
        showMsg('profile-password-msg', 'Cannot connect to server.', 'error');
    }
}

// ─── Init ──────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    updateDashboardDate();
    // Ensure home page starts correctly
    showSection('home');
});
// (end of file marker)