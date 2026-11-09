/* My requests: status of each placement request and the placement it led to. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('STUDENT');
  if (!user) return;
  Layout.mount(user);

  const requestsEl = document.getElementById('requests');
  const placementsEl = document.getElementById('placements');

  // What each status means for the student.
  const MEANING = {
    Pending: 'Waiting for the employer to answer.',
    'Meeting Requested': 'The employer would like to meet you first. Check your notifications for the details.',
    Accepted: 'The employer accepted. Your placement has been created.',
    Rejected: 'The employer did not accept this request. You can apply to other employers.'
  };

  async function loadRequests() {
    UI.loading(requestsEl, 'Loading requests', 3);
    try {
      const requests = await API.getMyPlacementRequests();
      if (!requests.length) {
        return UI.empty(requestsEl, 'No requests yet', 'Find an approved employer and send your first placement request.', '<a class="btn btn-primary btn-sm" href="employers.html">Find Employers</a>');
      }
      UI.done(requestsEl, `<div class="table-wrap"><table class="table">
        <thead><tr><th>Employer</th><th>Sent on</th><th>Status</th><th>What it means</th></tr></thead>
        <tbody>${requests.map((r) => `
          <tr>
            <td class="cell-strong">${UI.esc(r.employerName)}</td>
            <td>${UI.esc(UI.formatDate(r.requestDate))}</td>
            <td>${UI.pill(r.status)}</td>
            <td class="muted">${UI.esc(MEANING[r.status] || '')}</td>
          </tr>`).join('')}</tbody></table></div>`);
    } catch (err) {
      UI.error(requestsEl, err, loadRequests);
    }
  }

  async function loadPlacements() {
    UI.loading(placementsEl, 'Loading placement', 2);
    try {
      const placements = await API.getMyPlacements();
      if (!placements.length) {
        return UI.empty(placementsEl, 'No placement yet', 'A placement is created when an employer accepts one of your requests.');
      }
      UI.done(placementsEl, placements.map((p) => `
        <dl class="detail-list">
          <div><dt>Employer</dt><dd>${UI.esc(p.employerName)}</dd></div>
          <div><dt>Supervisor</dt><dd>${UI.esc(p.supervisorName)}</dd></div>
          <div><dt>Period</dt><dd>${UI.esc(UI.formatDate(p.startDate))} to ${UI.esc(UI.formatDate(p.endDate))}</dd></div>
          <div><dt>Status</dt><dd>${UI.pill(p.status)}</dd></div>
        </dl>`).join('<hr style="border:0;border-top:1px solid var(--line);margin:16px 0">'));
    } catch (err) {
      UI.error(placementsEl, err, loadPlacements);
    }
  }

  loadRequests();
  loadPlacements();
});
