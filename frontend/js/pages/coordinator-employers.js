/* Employers (coordinator): approve pending employers and deactivate approved ones. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('COORDINATOR');
  if (!user) return;
  Layout.mount(user);

  const pendingEl = document.getElementById('pending');
  const allEl = document.getElementById('all');
  let employers = [];

  async function load() {
    UI.loading(pendingEl, 'Loading employers', 2);
    UI.loading(allEl, 'Loading employers', 3);
    try {
      employers = (await API.getCoordinatorEmployers()).sort((a, b) => a.employerName.localeCompare(b.employerName));
      render();
    } catch (err) {
      UI.error(pendingEl, err, load);
      UI.error(allEl, err, load);
    }
  }

  function render() {
    const waiting = employers.filter((e) => e.status === 'Pending');
    if (!waiting.length) {
      UI.empty(pendingEl, 'No employers waiting', 'New companies that register will show here for approval.');
    } else {
      UI.done(pendingEl, `<ul class="list">${waiting.map((e) => `
        <li class="list-item">
          <div><h3>${UI.esc(e.employerName)}</h3>${e.address ? `<p>${UI.esc(e.address)}</p>` : ''}</div>
          <div class="list-actions">${UI.pill(e.status)}<button type="button" class="btn btn-primary btn-sm" data-approve="${e.employerId}">Approve</button></div>
        </li>`).join('')}</ul>`);
    }

    if (!employers.length) return UI.empty(allEl, 'No employers yet', 'Employers appear here after they register.');
    UI.paged(allEl, employers, (rows) => `<div class="table-wrap"><table class="table">
      <thead><tr><th>Employer</th><th>Status</th><th class="right"><span class="visually-hidden">Action</span></th></tr></thead>
      <tbody>${rows.map((e) => `
        <tr>
          <td class="cell-strong">${UI.esc(e.employerName)}</td>
          <td>${UI.pill(e.status)}</td>
          <td class="right">${e.status === 'Approved' ? `<button type="button" class="btn btn-danger btn-sm" data-deactivate="${e.employerId}">Deactivate</button>`
            : e.status === 'Pending' ? `<button type="button" class="btn btn-primary btn-sm" data-approve="${e.employerId}">Approve</button>` : ''}</td>
        </tr>`).join('')}</tbody></table></div>`);
  }

  async function act(button, id, action) {
    const employer = employers.find((e) => e.employerId === id);
    UI.setBusy(button, true, action === 'approve' ? 'Approving' : 'Deactivating');
    try {
      if (action === 'approve') {
        await API.approveEmployer(id);
        employer.status = 'Approved';
        UI.toast(`${employer.employerName} is approved. Students can now find it.`);
      } else {
        await API.deactivateEmployer(id);
        employer.status = 'Inactive';
        UI.toast(`${employer.employerName} is deactivated. Students can no longer find it.`);
      }
      render();
    } catch (err) {
      UI.setBusy(button, false);
      UI.toast(err.message, 'error');
    }
  }

  // Deactivate asks for a confirmation first, because students lose access to the employer.
  document.getElementById('main').addEventListener('click', (event) => {
    const approve = event.target.closest('[data-approve]');
    if (approve) return act(approve, Number(approve.dataset.approve), 'approve');
    const deactivate = event.target.closest('[data-deactivate]');
    if (!deactivate) return;
    if (deactivate.dataset.confirm) return act(deactivate, Number(deactivate.dataset.deactivate), 'deactivate');
    deactivate.dataset.confirm = '1';
    deactivate.textContent = 'Click again to deactivate';
  });

  load();
});
