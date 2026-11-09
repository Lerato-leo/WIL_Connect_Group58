/* Daily Log and My Logs (submitDutyLog, 4.6.5): log a day with proof, list the logs and correct them. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('STUDENT');
  if (!user) return;
  Layout.mount(user);

  const R = API.RULES;
  const HOUR = 60 * 60 * 1000;
  const form = document.getElementById('log-form');
  const fields = {
    logDate: form.logDate,
    hoursWorked: form.hoursWorked,
    taskDescription: form.taskDescription,
    skillsLearnt: form.skillsLearnt,
    proof: form.proof
  };
  const saveButton = document.getElementById('save-button');
  const chipsEl = document.getElementById('chips');
  const fileCount = document.getElementById('file-count');
  const listEl = document.getElementById('log-list');

  let placement = null;
  let logs = [];
  let files = [];          // new files chosen in this form
  let editing = null;      // the log being corrected, or null

  // Warns before leaving the page with a log that was typed but not submitted.
  let dirty = false;
  form.addEventListener('input', () => { dirty = true; });
  window.addEventListener('beforeunload', (event) => {
    if (!dirty) return;
    event.preventDefault();
    event.returnValue = '';
  });

  const updateTaskCount = UI.counter(fields.taskDescription, document.getElementById('task-count'));
  const updateSkillsCount = UI.counter(fields.skillsLearnt, document.getElementById('skills-count'));
  fields.logDate.max = UI.todayString();
  fields.logDate.value = UI.todayString();
  // The cursor starts in the hours box (Table 4.7.8), unless My Logs was opened.
  if (window.location.hash !== '#logs') fields.hoursWorked.focus();

  // ---------- Correction rules (24 hour window, section 4.5.3) ----------
  function correctionState(log) {
    if (log.status === 'Pending') return { allowed: true, label: 'Edit' };
    if (log.status === 'Rejected') return { allowed: true, label: 'Correct and send again' };
    const until = log.signedAt ? new Date(log.signedAt).getTime() + R.SIGN_OFF_WINDOW_HOURS * HOUR : 0;
    if (Date.now() < until) return { allowed: true, label: 'Correct', until };
    return { allowed: false, label: 'Locked after 24 hours' };
  }

  // ---------- Proof files: chips with name, size and a remove button ----------
  function sizeLabel(bytes) {
    return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  function existingProofs() {
    return editing ? editing.proofs || [] : [];
  }

  function renderChips() {
    const kept = existingProofs().map((p) => `<span class="chip">${UI.icon(String(p.fileType).includes('pdf') ? 'file' : 'image')}${UI.esc(p.fileName)} <small>attached</small></span>`);
    const added = files.map((f, i) => `<span class="chip">${UI.icon(f.type.includes('pdf') ? 'file' : 'image')}${UI.esc(f.name)} <small>${sizeLabel(f.size)}</small><button type="button" data-remove="${i}" aria-label="Remove ${UI.esc(f.name)}">${UI.icon('close')}</button></span>`);
    chipsEl.innerHTML = kept.concat(added).join('');
    fileCount.textContent = `${existingProofs().length + files.length} of ${R.MAX_FILES_PER_LOG} files`;
  }

  fields.proof.addEventListener('change', () => {
    files = files.concat([...fields.proof.files]);
    fields.proof.value = '';
    dirty = true;
    UI.clearErrors(form);
    renderChips();
  });

  chipsEl.addEventListener('click', (event) => {
    const button = event.target.closest('[data-remove]');
    if (!button) return;
    files.splice(Number(button.dataset.remove), 1);
    renderChips();
  });

  // ---------- Browser checks from Table 4.7.8 ----------
  function validate() {
    UI.clearErrors(form);
    const date = fields.logDate.value;
    const hours = Number(fields.hoursWorked.value);
    const task = fields.taskDescription.value.trim();

    if (!date) UI.fieldError(fields.logDate, 'Enter a valid date');
    else if (date > UI.todayString()) UI.fieldError(fields.logDate, 'The date cannot be in the future');
    else if (placement && (date < placement.startDate || date > placement.endDate)) UI.fieldError(fields.logDate, 'The date is outside your placement period');
    else if (logs.some((l) => l.logDate === date && (!editing || l.logId !== editing.logId))) UI.fieldError(fields.logDate, 'You already have a log for this date');

    if (fields.hoursWorked.value === '' || !(hours >= R.MIN_HOURS_PER_LOG && hours <= R.MAX_HOURS_PER_LOG) || (hours * 2) % 1 !== 0) {
      UI.fieldError(fields.hoursWorked, 'Hours must be between 0.5 and 12');
    }

    if (task.length < R.TASK_MIN || task.length > R.TASK_MAX) UI.fieldError(fields.taskDescription, 'Describe the task in 10 to 1000 characters');
    if (fields.skillsLearnt.value.length > R.SKILLS_MAX) UI.fieldError(fields.skillsLearnt, 'Skills learnt is too long');

    if (existingProofs().length + files.length > R.MAX_FILES_PER_LOG) {
      UI.fieldError(fields.proof, 'You can attach up to 3 files');
    } else if (files.some((f) => !isAllowed(f))) {
      UI.fieldError(fields.proof, 'Only JPG, PNG or PDF files up to 5 MB are allowed');
    }
    return !UI.focusFirstError(form);
  }

  function isAllowed(file) {
    const name = file.name.toLowerCase();
    const typeOk = R.FILE_TYPES.includes(file.type) || R.FILE_EXTENSIONS.some((ext) => name.endsWith(ext));
    return typeOk && file.size <= R.MAX_FILE_BYTES;
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!validate()) return;

    const data = new FormData();
    data.append('logDate', fields.logDate.value);
    data.append('hoursWorked', fields.hoursWorked.value);
    data.append('taskDescription', fields.taskDescription.value.trim());
    data.append('skillsLearnt', fields.skillsLearnt.value.trim());
    files.forEach((file) => data.append('proof', file));

    UI.setBusy(saveButton, true, 'Submitting');
    try {
      if (editing) await API.correctDutyLog(editing.logId, data);
      else await API.submitDutyLog(data);
      UI.toast('Your log was saved and sent to your supervisor');
      resetForm();
      loadAll();
    } catch (err) {
      UI.formAlert(form, err.message);
    } finally {
      UI.setBusy(saveButton, false);
    }
  });

  // ---------- Correction mode ----------
  function startEdit(log) {
    editing = log;
    files = [];
    fields.logDate.value = log.logDate;
    fields.hoursWorked.value = log.hoursWorked;
    fields.taskDescription.value = log.taskDescription;
    fields.skillsLearnt.value = log.skillsLearnt || '';
    updateTaskCount();
    updateSkillsCount();
    document.getElementById('crumb').textContent = 'Correct log';
    document.getElementById('form-title').textContent = `Correct the log for ${UI.formatDate(log.logDate)}`;
    document.getElementById('form-sub').textContent = 'Submitting sends the log back to Pending, so your supervisor signs it again.';
    saveButton.textContent = 'Submit correction';
    UI.clearErrors(form);
    renderChips();
    window.scrollTo({ top: 0, behavior: 'smooth' });
    fields.taskDescription.focus({ preventScroll: true });
  }

  function resetForm() {
    dirty = false;
    editing = null;
    files = [];
    form.reset();
    fields.logDate.value = UI.todayString();
    updateTaskCount();
    updateSkillsCount();
    document.getElementById('crumb').textContent = 'New log';
    document.getElementById('form-title').textContent = "Log today's duty";
    document.getElementById('form-sub').textContent = 'One log for each day. Your supervisor is told as soon as you submit.';
    saveButton.textContent = 'Submit log';
    UI.clearErrors(form);
    renderChips();
  }

  document.getElementById('cancel-button').addEventListener('click', resetForm);

  // ---------- Side cards and My Logs ----------
  async function loadAll() {
    const placementEl = document.getElementById('placement');
    const progressEl = document.getElementById('progress');
    UI.loading(placementEl, 'Loading placement', 2);
    UI.loading(progressEl, 'Loading progress', 1);
    UI.loading(listEl, 'Loading your logs', 4);

    try {
      const placements = await API.getMyPlacements();
      placement = placements.find((p) => p.status === 'Active') || null;
    } catch (err) {
      UI.error(placementEl, err, loadAll);
      UI.error(progressEl, err, loadAll);
      UI.error(listEl, err, loadAll);
      return;
    }

    if (!placement) {
      UI.empty(placementEl, 'No active placement', 'You can log work once an employer accepts your request.');
      UI.empty(progressEl, 'No hours yet', '');
      UI.empty(listEl, 'You have no logs yet.', 'Logs appear here once your placement starts.');
      saveButton.disabled = true;
      return;
    }

    UI.done(placementEl, `<dl class="detail-list">
      <div><dt>Employer</dt><dd>${UI.esc(placement.employerName)}</dd></div>
      <div><dt>Supervisor</dt><dd>${UI.esc(placement.supervisorName)}</dd></div>
      <div><dt>Period</dt><dd>${UI.esc(UI.formatDayMonth(placement.startDate))} to ${UI.esc(UI.formatDate(placement.endDate))}</dd></div>
    </dl>`);
    fields.logDate.min = placement.startDate;
    if (placement.endDate < UI.todayString()) fields.logDate.max = placement.endDate;

    const [attendance, list] = await Promise.allSettled([API.calculateAttendance(placement.placementId), API.getMyDutyLogs()]);
    if (attendance.status === 'fulfilled') {
      const a = attendance.value;
      UI.done(progressEl, `<p class="hours-total" style="font-size:1.6rem">${Math.round(a.signedHours * 10) / 10} <span>/ ${API.REQUIRED_HOURS} h</span></p>
        <p class="small muted" style="margin:2px 0 10px">signed off</p>${UI.progressBar(a.percentage, 'Hours signed off')}`);
    } else {
      UI.error(progressEl, attendance.reason, loadAll);
    }

    if (list.status === 'rejected') return UI.error(listEl, list.reason, loadAll);
    logs = list.value;
    renderLogs();
  }

  // My Logs: date, hours, status and the supervisor's comment, 10 to a page.
  function renderLogs() {
    if (!logs.length) return UI.empty(listEl, 'You have no logs yet.', "Select Log Today's Duty to start.");
    UI.paged(listEl, logs, (rows) => `<ul class="list">${rows.map((l) => {
      const state = correctionState(l);
      const proofs = (l.proofs || []).map((p) => UI.esc(p.fileName)).join(', ');
      return `
        <li class="list-item">
          <div>
            <h3>${UI.esc(UI.formatDate(l.logDate))} · <span class="num">${UI.formatHours(l.hoursWorked)}</span> hours</h3>
            <p>${UI.esc(l.taskDescription)}</p>
            <div class="list-meta">
              ${l.skillsLearnt ? `<span>Skills: ${UI.esc(l.skillsLearnt)}</span>` : ''}
              <span>${proofs ? `Proof: ${proofs}` : 'No proof attached'}</span>
              ${l.signedAt ? `<span>Signed with ${UI.esc(l.verificationMethod)} on ${UI.esc(UI.formatDateTime(l.signedAt))}</span>` : ''}
            </div>
            ${l.status === 'Rejected' && l.supervisorComment ? `<p class="note">Supervisor comment: ${UI.esc(l.supervisorComment)}</p>` : ''}
            ${state.until ? `<p class="small muted" style="margin-top:6px">You can correct this until ${UI.esc(UI.formatDateTime(state.until))}.</p>` : ''}
          </div>
          <div class="list-actions">
            ${UI.pill(l.status)}
            <button type="button" class="btn btn-sm" data-edit="${l.logId}" ${state.allowed ? '' : 'disabled aria-disabled="true"'}>${UI.esc(state.label)}</button>
          </div>
        </li>`;
    }).join('')}</ul>`);
  }

  listEl.addEventListener('click', (event) => {
    const button = event.target.closest('[data-edit]');
    if (!button || button.disabled) return;
    const log = logs.find((l) => l.logId === Number(button.dataset.edit));
    if (log) startEdit(log);
  });

  renderChips();
  loadAll();
});
