/* Employer page (respondToRequest, 4.6.4): answer placement requests, see placed students,
   supervisors and company details. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('EMPLOYER');
  if (!user) return;
  Layout.mount(user);

  const listEl = document.getElementById('request-list');
  const tabs = document.getElementById('status-tabs');
  let status = '';
  let requests = [];
  let supervisors = null; // loaded once, used by the Accept form and the Supervisors list
  const OPEN = ['Pending', 'Meeting Requested'];

  // ---------- Placement Requests ----------
  async function loadRequests() {
    UI.loading(listEl, 'Loading requests', 3);
    try {
      requests = await API.getEmployerPlacementRequests(status || undefined);
      if (!status) updateFacts(requests);
      render();
    } catch (err) {
      UI.error(listEl, err, loadRequests);
    }
  }

  function updateFacts(all) {
    document.getElementById('fact-new').textContent = all.filter((r) => r.status === 'Pending').length;
    document.getElementById('fact-meeting').textContent = all.filter((r) => r.status === 'Meeting Requested').length;
  }

  function render() {
    if (!requests.length) {
      return UI.empty(listEl, status ? 'No requests with this status' : 'No requests yet', status ? 'Choose All to see every request.' : 'Requests from students will show here.');
    }
    UI.paged(listEl, requests, (rows) => `<ul class="list">${rows.map((r) => `
      <li class="list-item">
        <div>
          <h3>${UI.esc(r.studentFirstName)} ${UI.esc(r.studentLastName)}</h3>
          <div class="list-meta"><span class="num">Student ${UI.esc(r.studentNumber)}</span><span>Sent on ${UI.esc(UI.formatDate(r.requestDate))}</span></div>
        </div>
        <div class="list-actions">
          ${UI.pill(r.status)}
          ${OPEN.includes(r.status) ? `
            <button type="button" class="btn btn-primary btn-sm" data-action="accept" data-id="${r.requestId}">Accept</button>
            ${r.status === 'Pending' ? `<button type="button" class="btn btn-sm" data-action="meeting" data-id="${r.requestId}">Request Meeting</button>` : ''}
            <button type="button" class="btn btn-danger btn-sm" data-action="reject" data-id="${r.requestId}">Reject</button>` : ''}
        </div>
        <div class="inline-panel hidden" data-panel="${r.requestId}"></div>
      </li>`).join('')}</ul>`);
  }

  // Opens the small form under a request.
  function openPanel(requestId, html) {
    listEl.querySelectorAll('[data-panel]').forEach((p) => { p.classList.add('hidden'); p.innerHTML = ''; });
    const panel = listEl.querySelector(`[data-panel="${requestId}"]`);
    panel.innerHTML = html;
    panel.classList.remove('hidden');
    const first = panel.querySelector('input, select, textarea, button');
    if (first) first.focus();
    return panel;
  }

  async function getSupervisors() {
    if (!supervisors) supervisors = await API.getEmployerSupervisors();
    return supervisors;
  }

  // Accept: supervisor, start date and end date (Table 4.7.7).
  async function openAccept(requestId) {
    const panel = openPanel(requestId, '<p class="small muted">Loading supervisors</p>');
    let list;
    try { list = await getSupervisors(); } catch (err) { return UI.error(panel, err, () => openAccept(requestId)); }
    if (!list.length) return UI.empty(panel, 'No supervisors yet', 'A supervisor must register for your company before you can accept a student.');
    panel.innerHTML = `
      <form data-form="accept" data-id="${requestId}" novalidate>
        <div data-form-alert style="margin-bottom:12px"></div>
        <div class="form-grid">
          <div class="field field-full">
            <label for="sup-${requestId}">Supervisor</label>
            <select class="select" id="sup-${requestId}" name="supervisorId" required style="max-width:320px">
              <option value="">Choose a supervisor</option>
              ${list.map((s) => `<option value="${s.supervisorId}">${UI.esc(s.firstName)} ${UI.esc(s.lastName)}</option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label for="start-${requestId}">Start date</label>
            <input class="input" type="date" id="start-${requestId}" name="startDate" required>
          </div>
          <div class="field">
            <label for="end-${requestId}">End date</label>
            <input class="input" type="date" id="end-${requestId}" name="endDate" required>
          </div>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary btn-sm">Accept and create placement</button>
          <button type="button" class="btn btn-sm" data-close>Cancel</button>
        </div>
      </form>`;
  }

  function openMeeting(requestId) {
    openPanel(requestId, `
      <form data-form="meeting" data-id="${requestId}" novalidate>
        <div data-form-alert style="margin-bottom:12px"></div>
        <div class="field">
          <label for="msg-${requestId}">Message to the student</label>
          <textarea class="textarea" id="msg-${requestId}" name="message" maxlength="500" placeholder="For example: Can you come in on Tuesday at 10:00 for a short interview?" required></textarea>
          <span class="hint">Up to 500 characters. The student gets this as a notification.</span>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary btn-sm">Send meeting request</button>
          <button type="button" class="btn btn-sm" data-close>Cancel</button>
        </div>
      </form>`);
  }

  function openReject(requestId) {
    openPanel(requestId, `
      <form data-form="reject" data-id="${requestId}" novalidate>
        <p class="small" style="margin-bottom:12px">Reject this request? The student will be told and can apply elsewhere.</p>
        <div data-form-alert style="margin-bottom:12px"></div>
        <div class="form-actions" style="margin-top:0">
          <button type="submit" class="btn btn-danger btn-sm">Yes, reject</button>
          <button type="button" class="btn btn-sm" data-close>Keep request</button>
        </div>
      </form>`);
  }

  listEl.addEventListener('click', (event) => {
    const close = event.target.closest('[data-close]');
    if (close) {
      const panel = close.closest('[data-panel]');
      panel.classList.add('hidden');
      panel.innerHTML = '';
      return;
    }
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const id = Number(button.dataset.id);
    if (button.dataset.action === 'accept') openAccept(id);
    if (button.dataset.action === 'meeting') openMeeting(id);
    if (button.dataset.action === 'reject') openReject(id);
  });

  // One submit handler for the three small forms.
  listEl.addEventListener('submit', async (event) => {
    const form = event.target.closest('form[data-form]');
    if (!form) return;
    event.preventDefault();
    UI.clearErrors(form);
    const id = Number(form.dataset.id);
    const type = form.dataset.form;

    if (type === 'accept') {
      if (!form.supervisorId.value) UI.fieldError(form.supervisorId, 'Select a supervisor');
      if (!form.startDate.value) UI.fieldError(form.startDate, 'Enter the start date');
      if (!form.endDate.value || (form.startDate.value && form.endDate.value <= form.startDate.value)) {
        UI.fieldError(form.endDate, 'The end date must be after the start date');
      }
    }
    if (type === 'meeting' && !form.message.value.trim()) UI.fieldError(form.message, 'Write a short message for the student');
    if (UI.focusFirstError(form)) return;

    const submit = form.querySelector('[type="submit"]');
    UI.setBusy(submit, true, 'Saving');
    try {
      if (type === 'accept') {
        await API.acceptPlacementRequest(id, { supervisorId: Number(form.supervisorId.value), startDate: form.startDate.value, endDate: form.endDate.value });
        UI.toast('Request accepted. The placement was created.');
        loadPlacements();
      } else if (type === 'meeting') {
        await API.requestMeeting(id, form.message.value.trim());
        UI.toast('Meeting request sent to the student');
      } else {
        await API.rejectPlacementRequest(id);
        UI.toast('Request rejected');
      }
      if (status) loadAllForFacts();
      loadRequests();
    } catch (err) {
      UI.setBusy(submit, false);
      UI.formAlert(form, err.message);
    }
  });

  // Keeps the counts right while a status filter is on.
  async function loadAllForFacts() {
    try { updateFacts(await API.getEmployerPlacementRequests()); } catch (err) { /* counts stay as they were */ }
  }

  tabs.addEventListener('click', (event) => {
    const tab = event.target.closest('[data-status]');
    if (!tab) return;
    status = tab.dataset.status;
    tabs.querySelectorAll('[data-status]').forEach((t) => t.setAttribute('aria-selected', String(t === tab)));
    loadRequests();
  });

  // ---------- Students on Placement ----------
  async function loadPlacements() {
    const el = document.getElementById('placement-list');
    UI.loading(el, 'Loading placed students', 3);
    try {
      const rows = await API.getEmployerPlacements();
      const current = rows.filter((p) => p.status === 'Active');
      document.getElementById('fact-current').textContent = current.length;
      if (!rows.length) return UI.empty(el, 'No students on placement yet', 'Students appear here when you accept their request.');
      UI.done(el, `<div class="table-wrap"><table class="table">
        <thead><tr><th>Student number</th><th>Name</th><th>Supervisor</th><th>Start</th><th>End</th><th>Status</th></tr></thead>
        <tbody>${rows.map((p) => `
          <tr>
            <td class="num">${UI.esc(p.studentNumber)}</td>
            <td class="cell-strong">${UI.esc(p.studentName)}</td>
            <td>${UI.esc(p.supervisorName)}</td>
            <td class="nowrap">${UI.esc(UI.formatDate(p.startDate))}</td>
            <td class="nowrap">${UI.esc(UI.formatDate(p.endDate))}</td>
            <td>${UI.pill(p.status)}</td>
          </tr>`).join('')}</tbody></table></div>`);
    } catch (err) {
      UI.error(el, err, loadPlacements);
    }
  }

  // ---------- Supervisors ----------
  async function loadSupervisors() {
    const el = document.getElementById('supervisor-list');
    UI.loading(el, 'Loading supervisors', 2);
    try {
      const rows = await getSupervisors();
      if (!rows.length) return UI.empty(el, 'No supervisors yet', 'Supervisors register with your company name and appear here.');
      UI.done(el, `<div class="table-wrap"><table class="table">
        <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th class="num">Current students</th></tr></thead>
        <tbody>${rows.map((s) => `
          <tr>
            <td class="cell-strong">${UI.esc(s.firstName)} ${UI.esc(s.lastName)}</td>
            <td>${UI.esc(s.email || '')}</td>
            <td class="num">${UI.esc(s.phone || '')}</td>
            <td class="num">${s.activeStudents ?? ''}</td>
          </tr>`).join('')}</tbody></table></div>`);
    } catch (err) {
      supervisors = null;
      UI.error(el, err, loadSupervisors);
    }
  }

  // ---------- Company details ----------
  const companyForm = document.getElementById('company-form');
  const companyState = document.getElementById('company-state');

  async function loadCompany() {
    UI.loading(companyState, 'Loading company details', 2);
    try {
      const company = await API.getEmployerProfile();
      ['employerName', 'contactEmail', 'contactNumber', 'address'].forEach((name) => { companyForm[name].value = company[name] || ''; });
      if (company.employerName) {
        document.getElementById('company-line').textContent = `Students who would like to do their Work Integrated Learning at ${company.employerName}.`;
      }
      document.getElementById('approval-note').innerHTML = company.status && company.status !== 'Approved'
        ? '<div class="alert alert-warn" style="margin-bottom:20px">Your company is waiting for approval from the WIL coordinator. Students cannot find you yet.</div>'
        : '';
      UI.done(companyState, '');
      companyForm.classList.remove('hidden');
    } catch (err) {
      UI.error(companyState, err, loadCompany);
    }
  }

  // Checks from Table 4.7.4 for employer details.
  companyForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    UI.clearErrors(companyForm);
    const f = companyForm;
    const name = f.employerName.value.trim();
    const address = f.address.value.trim();
    if (name.length < 2 || name.length > 100) UI.fieldError(f.employerName, 'Enter the employer name');
    if (!f.contactEmail.value.trim() || !f.contactEmail.checkValidity()) UI.fieldError(f.contactEmail, 'Enter a valid email');
    if (!/^0\d{9}$/.test(f.contactNumber.value.trim())) UI.fieldError(f.contactNumber, 'Enter a valid phone number');
    if (address.length < 5 || address.length > 200) UI.fieldError(f.address, 'Enter the address');
    if (UI.focusFirstError(f)) return;

    const button = document.getElementById('company-save');
    UI.setBusy(button, true, 'Saving');
    try {
      await API.updateEmployerProfile({ employerName: name, contactEmail: f.contactEmail.value.trim(), contactNumber: f.contactNumber.value.trim(), address });
      UI.toast('Company details saved');
    } catch (err) {
      UI.formAlert(f, err.message);
    } finally {
      UI.setBusy(button, false);
    }
  });

  loadRequests();
  loadPlacements();
  loadSupervisors();
  loadCompany();
});
