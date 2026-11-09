/* Supervisor page: Pending Sign Offs, Signed Logs and My Students, switched by tab (#pending, #history, #students). */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('SUPERVISOR');
  if (!user) return;
  Layout.mount(user);

  const NAMES = ['pending', 'history', 'students'];
  const tabs = Object.fromEntries(NAMES.map((n) => [n, document.getElementById(`tab-${n}`)]));
  const panels = Object.fromEntries(NAMES.map((n) => [n, document.getElementById(`panel-${n}`)]));
  const loaders = { pending: loadPending, history: loadHistory, students: loadStudents };
  const loaded = {};

  const fromHash = () => (NAMES.includes(window.location.hash.slice(1)) ? window.location.hash.slice(1) : 'pending');

  // Shows one tab. The hash keeps the menu items working.
  function show(name) {
    NAMES.forEach((key) => {
      tabs[key].setAttribute('aria-selected', String(key === name));
      panels[key].classList.toggle('hidden', key !== name);
    });
    if (!loaded[name]) { loaded[name] = true; loaders[name](); }
  }

  NAMES.forEach((name) => tabs[name].addEventListener('click', () => {
    history.replaceState(null, '', `#${name}`);
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  }));
  window.addEventListener('hashchange', () => show(fromHash()));

  // Days since the log date: "Today", "1 day", "5 days".
  function waitingSince(logDate) {
    const [y, m, d] = String(logDate).split('-').map(Number);
    const start = new Date(y, m - 1, d);
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const days = Math.max(0, Math.round((today - start) / 86400000));
    return days === 0 ? 'Today' : days === 1 ? '1 day' : `${days} days`;
  }

  // Pending Sign Offs: oldest log first (Table 4.7.13).
  async function loadPending() {
    const el = panels.pending;
    UI.loading(el, 'Loading pending sign offs', 4);
    try {
      const logs = await API.getPendingLogs();
      const hours = logs.reduce((sum, l) => sum + Number(l.hoursWorked), 0);
      document.getElementById('fact-count').textContent = logs.length;
      document.getElementById('fact-hours').textContent = UI.formatHours(hours);
      document.getElementById('fact-oldest').textContent = logs.length ? UI.formatDayMonth(logs[0].logDate) : 'None';

      if (!logs.length) return UI.empty(el, 'Nothing to sign off', 'New logs from your students will show here.');
      UI.paged(el, logs, (rows) => `<div class="table-wrap"><table class="table">
        <thead><tr><th>Student name</th><th>Student number</th><th>Log date</th><th class="num">Hours</th><th>Waiting</th><th class="right"><span class="visually-hidden">Action</span></th></tr></thead>
        <tbody>${rows.map((l) => `
          <tr>
            <td class="cell-strong">${UI.esc(l.studentName)}</td>
            <td class="num">${UI.esc(l.studentNumber)}</td>
            <td class="nowrap">${UI.esc(UI.formatDate(l.logDate))}</td>
            <td class="num">${UI.formatHours(l.hoursWorked)}</td>
            <td class="nowrap muted">${UI.esc(waitingSince(l.logDate))}</td>
            <td class="right"><a class="btn btn-primary btn-sm" href="supervisor-signoff.html?log=${l.logId}">Review</a></td>
          </tr>`).join('')}</tbody></table></div>`);
    } catch (err) {
      UI.error(el, err, loadPending);
    }
  }

  // Signed Logs: every sign off by this supervisor, newest first.
  async function loadHistory() {
    const el = panels.history;
    UI.loading(el, 'Loading signed logs', 4);
    try {
      const rows = await API.getSignOffHistory();
      if (!rows.length) return UI.empty(el, 'No signed logs yet', 'Logs you sign off will be listed here.');
      UI.paged(el, rows, (page) => `<div class="table-wrap"><table class="table">
        <thead><tr><th>Student</th><th>Log date</th><th class="num">Hours</th><th>Method</th><th>Signed on</th></tr></thead>
        <tbody>${page.map((l) => `
          <tr>
            <td><span class="cell-strong">${UI.esc(l.studentName)}</span><span class="cell-sub">${UI.esc(l.taskDescription)}</span></td>
            <td class="nowrap">${UI.esc(UI.formatDate(l.logDate))}</td>
            <td class="num">${UI.formatHours(l.hoursWorked)}</td>
            <td>${UI.esc(l.verificationMethod)}</td>
            <td class="nowrap">${UI.esc(UI.formatDateTime(l.signoffDate))}</td>
          </tr>`).join('')}</tbody></table></div>`);
    } catch (err) {
      UI.error(el, err, loadHistory);
    }
  }

  // My Students: students and their hours (FR7).
  async function loadStudents() {
    const el = panels.students;
    UI.loading(el, 'Loading your students', 3);
    try {
      const rows = await API.getSupervisorStudents();
      if (!rows.length) return UI.empty(el, 'No students yet', 'Students appear here when an employer places them with you.');
      UI.done(el, `<div class="table-wrap"><table class="table">
        <thead><tr><th>Student number</th><th>Name</th><th>Placement</th><th class="num">Hours</th><th class="num">Days</th><th>Progress</th><th>Status</th></tr></thead>
        <tbody>${rows.map((r) => {
          const a = r.attendance || {};
          return `
          <tr>
            <td class="num">${UI.esc(r.studentNumber)}</td>
            <td class="cell-strong">${UI.esc(r.studentName)}</td>
            <td class="nowrap muted">${UI.esc(UI.formatDayMonth(r.startDate))} to ${UI.esc(UI.formatDate(r.endDate))}</td>
            <td class="num">${UI.formatHours(a.signedHours)}</td>
            <td class="num">${a.daysCompleted ?? ''}</td>
            <td><div class="row" style="gap:8px;flex-wrap:nowrap"><div class="progress progress-sm">${'<span style="width:' + Math.min(Number(a.percentage) || 0, 100) + '%"></span>'}</div><span class="num small">${UI.formatHours(a.percentage)}%</span></div></td>
            <td>${UI.pill(a.status)}</td>
          </tr>`;
        }).join('')}</tbody></table></div>`);
    } catch (err) {
      UI.error(el, err, loadStudents);
    }
  }

  // The summary cards need the pending list even when another tab opens first.
  show(fromHash());
  if (!loaded.pending) { loaded.pending = true; loadPending(); }
});
