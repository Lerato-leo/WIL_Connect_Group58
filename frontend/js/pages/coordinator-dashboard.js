/* Coordinator dashboard: summary cards and the student progress table (FR7), with a search and status filter. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('COORDINATOR');
  if (!user) return;
  Layout.mount(user);

  const tableEl = document.getElementById('table');
  const form = document.getElementById('filter-form');
  const filter = document.getElementById('filter');
  const statusSelect = document.getElementById('status');
  let rows = [];

  async function load() {
    UI.loading(tableEl, 'Loading students', 5);
    loadEmployerCount();
    try {
      rows = await API.getStudentProgress();
      const done = rows.filter((r) => r.attendance && r.attendance.status === 'COMPLETE').length;
      // logsWaitingOver3Days is not in the C7 response yet, see api.js (CHECK WITH OWNER).
      const hasWaiting = rows.some((r) => r.logsWaitingOver3Days !== undefined);
      document.getElementById('fact-students').textContent = rows.length;
      document.getElementById('fact-done').textContent = done;
      document.getElementById('fact-waiting').textContent = hasWaiting ? rows.reduce((s, r) => s + (Number(r.logsWaitingOver3Days) || 0), 0) : 'Not available';
      render();
    } catch (err) {
      UI.error(tableEl, err, load);
    }
  }

  async function loadEmployerCount() {
    try {
      const employers = await API.getCoordinatorEmployers();
      document.getElementById('fact-employers').textContent = employers.filter((e) => e.status === 'Pending').length;
    } catch (err) {
      document.getElementById('fact-employers').textContent = 'Not available';
    }
  }

  function render() {
    UI.clearErrors(form);
    const term = filter.value.trim().toLowerCase();
    // Search text is up to 50 characters (Table 4.7.10).
    if (term.length > API.RULES.SEARCH_MAX) {
      UI.fieldError(filter, 'Search text is too long');
      return;
    }
    const shown = rows.filter((r) =>
      (!term || `${r.studentName} ${r.studentNumber}`.toLowerCase().includes(term)) &&
      (!statusSelect.value || (r.attendance && r.attendance.status === statusSelect.value)));

    if (!rows.length) return UI.empty(tableEl, 'No students on placement yet', 'Students show here once an employer accepts their request.');
    if (!shown.length) return UI.empty(tableEl, 'No students match', 'Clear the search or choose All statuses.');

    UI.paged(tableEl, shown, (page) => `<div class="table-wrap"><table class="table table-tight">
      <thead><tr>
        <th>Student no.</th><th>Name</th><th>Employer</th><th>Supervisor</th><th class="num">Hours</th><th class="num">Days</th>
        <th>Progress</th><th>Status</th><th class="num" title="Logs waiting for sign off">Unverified</th><th class="right"><span class="visually-hidden">Report</span></th>
      </tr></thead>
      <tbody>${page.map((r) => {
        const a = r.attendance || {};
        const pct = Math.min(Number(a.percentage) || 0, 100);
        return `
        <tr>
          <td class="num muted">${UI.esc(r.studentNumber)}</td>
          <td class="cell-strong nowrap">${UI.esc(r.studentName)}</td>
          <td class="nowrap">${UI.esc(r.employerName)}</td>
          <td class="nowrap">${UI.esc(r.supervisorName)}</td>
          <td class="num">${UI.formatHours(a.signedHours)}</td>
          <td class="num">${a.daysCompleted ?? ''}</td>
          <td><div class="row" style="gap:8px;flex-wrap:nowrap"><div class="progress progress-sm"><span style="width:${pct}%"></span></div><span class="num small">${UI.formatHours(pct)}%</span></div></td>
          <td>${UI.pill(a.status)}</td>
          <td class="num">${r.unverifiedLogs === undefined ? '<span class="muted">Not given</span>' : (r.unverifiedLogs ? `<span style="color:var(--amber);font-weight:600">${r.unverifiedLogs}</span>` : '0')}</td>
          <td class="right"><a class="btn btn-sm" href="report.html?placement=${r.placementId}">Create Report</a></td>
        </tr>`;
      }).join('')}</tbody></table></div>`);
  }

  filter.addEventListener('input', render);
  statusSelect.addEventListener('change', render);
  form.addEventListener('submit', (event) => { event.preventDefault(); render(); });
  load();
});
