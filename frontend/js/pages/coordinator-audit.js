/* Sign Off Audit Log (NFR6): read only list of every SIGN_OFF row. There is no edit or delete button. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('COORDINATOR');
  if (!user) return;
  Layout.mount(user);

  const tableEl = document.getElementById('table');
  const form = document.getElementById('filter-form');
  const filter = document.getElementById('filter');
  const method = document.getElementById('method');
  let rows = [];

  async function load() {
    UI.loading(tableEl, 'Loading sign offs', 5);
    try {
      rows = await API.getSignOffAudit();
      render();
    } catch (err) {
      UI.error(tableEl, err, load);
    }
  }

  function render() {
    UI.clearErrors(form);
    const term = filter.value.trim().toLowerCase();
    if (term.length > API.RULES.SEARCH_MAX) return UI.fieldError(filter, 'Search text is too long');
    const shown = rows.filter((r) =>
      (!term || `${r.studentName} ${r.studentNumber} ${r.supervisorName}`.toLowerCase().includes(term)) &&
      (!method.value || r.verificationMethod === method.value));
    if (!rows.length) return UI.empty(tableEl, 'No sign offs yet', 'Every sign off by a supervisor is listed here.');
    if (!shown.length) return UI.empty(tableEl, 'No sign offs match', 'Clear the search or choose All methods.');

    // Columns from Table 4.7.13: sign off ID, log date, student, supervisor, method and signed on.
    UI.paged(tableEl, shown, (page) => `<div class="table-wrap"><table class="table">
      <thead><tr><th class="num">Sign off ID</th><th>Log date</th><th>Student</th><th>Supervisor</th><th>Method</th><th>Signed on</th></tr></thead>
      <tbody>${page.map((r) => `
        <tr>
          <td class="num muted">${UI.esc(r.signoffId)}</td>
          <td class="nowrap">${UI.esc(UI.formatDate(r.logDate))}</td>
          <td><span class="cell-strong">${UI.esc(r.studentName)}</span><span class="cell-sub num">${UI.esc(r.studentNumber)}</span></td>
          <td>${UI.esc(r.supervisorName)}</td>
          <td>${UI.esc(r.verificationMethod)}</td>
          <td class="nowrap">${UI.esc(UI.formatDateTime(r.signoffDate))}</td>
        </tr>`).join('')}</tbody></table></div>`);
  }

  filter.addEventListener('input', render);
  method.addEventListener('change', render);
  form.addEventListener('submit', (event) => { event.preventDefault(); render(); });
  load();
});
