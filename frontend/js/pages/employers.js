/* Find Employers (searchEmployers and sendPlacementRequest, 4.6.4): search, filter and send a request. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('STUDENT');
  if (!user) return;
  Layout.mount(user);

  const form = document.getElementById('search-form');
  const listEl = document.getElementById('employers');
  const search = document.getElementById('search');
  const area = document.getElementById('area');
  const countEl = document.getElementById('result-count');

  let employers = [];
  let myRequests = [];
  let timer = null;

  // The area is the last part of the address, for example "Cape Town".
  const areaOf = (employer) => String(employer.address || '').split(',').pop().trim();
  const OPEN = ['Pending', 'Meeting Requested', 'Accepted'];

  async function load() {
    UI.clearErrors(form);
    // Search text is up to 50 characters (Table 4.7.6).
    if (search.value.trim().length > API.RULES.SEARCH_MAX) {
      UI.fieldError(search, 'Search text is too long');
      return;
    }
    UI.loading(listEl, 'Loading employers', 3);
    countEl.textContent = '';
    try {
      const [found, requests] = await Promise.all([API.searchEmployers(search.value.trim()), API.getMyPlacementRequests()]);
      employers = found.sort((a, b) => a.employerName.localeCompare(b.employerName));
      myRequests = requests;
      fillAreas();
      render();
    } catch (err) {
      UI.error(listEl, err, load);
    }
  }

  function fillAreas() {
    const chosen = area.value;
    const areas = [...new Set(employers.map(areaOf).filter(Boolean))].sort();
    area.innerHTML = '<option value="">All areas</option>' + areas.map((a) => `<option ${a === chosen ? 'selected' : ''}>${UI.esc(a)}</option>`).join('');
  }

  function render() {
    const shown = employers.filter((e) => !area.value || areaOf(e) === area.value);
    countEl.textContent = `${shown.length} approved employer${shown.length === 1 ? '' : 's'}`;
    if (!shown.length) return UI.empty(listEl, 'No employers found', 'Try a different name or address, or choose All areas.');

    UI.paged(listEl, shown, (rows) => `<div class="employer-list">${rows.map((e) => {
      const existing = myRequests.find((r) => r.employerId === e.employerId && OPEN.includes(r.status));
      return `
        <article class="employer" aria-labelledby="emp-${e.employerId}">
          <div class="row"><h2 id="emp-${e.employerId}">${UI.esc(e.employerName)}</h2><span class="spacer"></span>${UI.pill(e.status)}</div>
          <dl>
            <div><dt>Address</dt><dd>${UI.esc(e.address)}</dd></div>
            ${e.contactEmail ? `<div><dt>Email</dt><dd>${UI.esc(e.contactEmail)}</dd></div>` : ''}
            ${e.contactNumber ? `<div><dt>Phone</dt><dd class="num">${UI.esc(e.contactNumber)}</dd></div>` : ''}
          </dl>
          <div class="row" data-actions="${e.employerId}">
            ${existing
              ? `<span class="small muted">Request ${UI.esc(existing.status.toLowerCase())}</span><span class="spacer"></span><a class="btn btn-sm" href="requests.html">My Requests</a>`
              : `<span class="spacer"></span><button type="button" class="btn btn-primary btn-sm" data-ask="${e.employerId}">Send Request</button>`}
          </div>
        </article>`;
    }).join('')}</div>`);
  }

  // Send Request asks for a confirmation first (Table 4.7.6).
  listEl.addEventListener('click', async (event) => {
    const ask = event.target.closest('[data-ask]');
    if (ask) {
      const employer = employers.find((e) => e.employerId === Number(ask.dataset.ask));
      ask.parentElement.innerHTML = `<span class="small">Send a request to ${UI.esc(employer.employerName)}?</span><span class="spacer"></span>
        <button type="button" class="btn btn-sm" data-cancel>Cancel</button>
        <button type="button" class="btn btn-primary btn-sm" data-send="${employer.employerId}">Yes, send</button>`;
      return;
    }
    if (event.target.closest('[data-cancel]')) return render();

    const send = event.target.closest('[data-send]');
    if (!send) return;
    UI.setBusy(send, true, 'Sending');
    try {
      const created = await API.sendPlacementRequest(Number(send.dataset.send));
      myRequests.push(created);
      UI.toast(`Request sent to ${created.employerName}`);
      render();
    } catch (err) {
      UI.setBusy(send, false);
      UI.toast(err.message, 'error');
    }
  });

  // Search as the student types, with a short pause.
  search.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(load, 350);
  });
  area.addEventListener('change', render);
  form.addEventListener('submit', (event) => { event.preventDefault(); load(); });

  load();
});
