/* Review and sign off (signOffLog, 4.6.6): sign off with a signature or PIN, or reject with a reason. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('SUPERVISOR');
  if (!user) return;
  Layout.mount(user);

  const R = API.RULES;
  const logId = Number(new URLSearchParams(window.location.search).get('log'));
  const detailEl = document.getElementById('detail');
  const form = document.getElementById('decision-form');
  const closedEl = document.getElementById('decision-closed');
  const pin = document.getElementById('pin');
  const comment = document.getElementById('comment');
  const approveButton = document.getElementById('approve-button');
  const rejectButton = document.getElementById('reject-button');
  const signature = createSignaturePad(document.getElementById('signature'), updateApprove);
  UI.counter(comment, document.getElementById('comment-count'));

  // ---------- Load the log ----------
  async function load() {
    if (!logId) {
      form.classList.add('hidden');
      return UI.empty(detailEl, 'No log chosen', 'Open a log from Pending Sign Offs.', '<a class="btn btn-sm" href="supervisor-pending.html#pending">Pending Sign Offs</a>');
    }
    UI.loading(detailEl, 'Loading the log', 5);
    try {
      render(await API.getLogForReview(logId));
    } catch (err) {
      UI.error(detailEl, err, load);
      if (err.status === 403 || err.status === 404) form.classList.add('hidden');
    }
  }

  function render(log) {
    document.getElementById('title-pill').innerHTML = UI.pill(log.status);
    const proofs = log.proofs || [];
    // Earlier sign offs of this log stay on record (NFR6).
    const history = (log.signOffs || []).filter((s) => !(log.status === 'Signed' && s === log.signOffs[0]));

    UI.done(detailEl, `
      <dl class="facts-row">
        <div><dt>Student</dt><dd>${UI.esc(log.studentName)}<span class="num">${UI.esc(log.studentNumber)}</span></dd></div>
        <div><dt>Log date</dt><dd>${UI.esc(UI.formatLongDate(log.logDate))}</dd></div>
        <div><dt>Hours worked</dt><dd class="num">${UI.formatHours(log.hoursWorked)} hours</dd></div>
      </dl>
      <h3 class="section-title">Task done</h3>
      <p class="small">${UI.esc(log.taskDescription)}</p>
      <h3 class="section-title">Skills learnt</h3>
      <p class="small">${UI.esc(log.skillsLearnt || 'None given')}</p>
      <div class="field-label section-title"><h3 style="font-size:inherit">Proof of work</h3><span class="small muted">${proofs.length} file${proofs.length === 1 ? '' : 's'}</span></div>
      ${proofs.length ? `<div class="proof-tiles">${proofs.map((p) => `
          <button type="button" class="proof-tile" data-proof="${p.proofId}" aria-pressed="false">
            ${UI.icon(String(p.fileType).includes('pdf') ? 'file' : 'image')}<span>${UI.esc(p.fileName)}</span>
          </button>`).join('')}</div>
        <p class="small faint" style="margin-top:8px">Select a file to open it full size.</p>
        <div id="proof-view"></div>` : '<p class="small muted">The student did not attach proof for this day.</p>'}
      <h3 class="section-title" style="margin-top:24px">Sign off history</h3>
      ${history.length ? `<ul class="list">${history.map((s) => `
        <li class="list-item" style="padding:8px 0"><span class="small">${UI.esc(UI.formatDateTime(s.signoffDate))} by ${UI.esc(s.supervisorName || 'Supervisor')}</span><span class="small muted">${UI.esc(s.verificationMethod)}</span></li>`).join('')}</ul>`
        : '<p class="small muted">No earlier sign offs for this log.</p>'}`);

    // A log that is no longer pending cannot be decided again.
    if (log.status !== 'Pending') {
      form.classList.add('hidden');
      closedEl.classList.remove('hidden');
      const latest = (log.signOffs || [])[0];
      UI.done(closedEl, `<div class="state"><h3>This log is not waiting for sign off</h3><p>${log.status === 'Signed' && latest
        ? `Signed with ${UI.esc(latest.verificationMethod)} on ${UI.esc(UI.formatDateTime(latest.signoffDate))}.`
        : UI.esc(log.supervisorComment ? `Rejected: ${log.supervisorComment}` : '')}</p><a class="btn btn-sm" href="supervisor-pending.html#pending">Back to Pending Sign Offs</a></div>`);
    }
  }

  // Opens a proof file full size inside the page.
  detailEl.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-proof]');
    if (!button) return;
    detailEl.querySelectorAll('[data-proof]').forEach((b) => b.setAttribute('aria-pressed', String(b === button)));
    const view = document.getElementById('proof-view');
    UI.loading(view, 'Loading the file', 1);
    try {
      const blob = await API.getProofFile(Number(button.dataset.proof));
      const url = URL.createObjectURL(blob);
      UI.done(view, `<div class="proof-view">${blob.type === 'application/pdf' ? `<iframe src="${url}" title="Proof of work"></iframe>` : `<img src="${url}" alt="Proof of work uploaded by the student">`}</div>`);
    } catch (err) {
      UI.error(view, err);
    }
  });

  // ---------- Decision and method ----------
  // The Sign Off button stays disabled until a signature is drawn or a PIN is typed (Table 4.7.9).
  function updateApprove() {
    const method = form.method.value;
    approveButton.disabled = method === 'PIN' ? !/^\d{4,6}$/.test(pin.value) : signature.isEmpty();
  }

  form.addEventListener('change', (event) => {
    if (event.target.name === 'decision') {
      const reject = form.decision.value === 'Rejected';
      document.getElementById('signoff-part').classList.toggle('hidden', reject);
      document.getElementById('reject-part').classList.toggle('hidden', !reject);
      UI.clearErrors(form);
      if (reject) comment.focus(); else signature.resize();
    }
    if (event.target.name === 'method') {
      const usePin = form.method.value === 'PIN';
      document.getElementById('pin-field').classList.toggle('hidden', !usePin);
      document.getElementById('signature-field').classList.toggle('hidden', usePin);
      UI.clearErrors(form);
      if (usePin) pin.focus(); else signature.resize();
      updateApprove();
    }
  });
  pin.addEventListener('input', () => { pin.value = pin.value.replace(/\D/g, ''); updateApprove(); });
  document.getElementById('clear-signature').addEventListener('click', () => { signature.clear(); updateApprove(); });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    UI.clearErrors(form);
    if (form.decision.value === 'Rejected') return reject();

    const method = form.method.value;
    const details = { verificationMethod: method };
    if (method === 'PIN') {
      if (!/^\d{4,6}$/.test(pin.value)) UI.fieldError(pin, 'The PIN must be 4 to 6 digits');
      details.pin = pin.value;
    } else {
      if (signature.isEmpty()) UI.fieldError(signature.canvas, 'Please sign in the box');
      details.signatureData = signature.toDataURL();
    }
    if (UI.focusFirstError(form)) return;

    UI.setBusy(approveButton, true, 'Signing off');
    try {
      await API.signOffLog(logId, details);
      UI.toast('Log signed off');
      setTimeout(() => { window.location.href = 'supervisor-pending.html#pending'; }, 700);
    } catch (err) {
      UI.setBusy(approveButton, false);
      if (method === 'PIN' && err.status === 401) { UI.fieldError(pin, 'Incorrect PIN'); pin.select(); }
      else UI.formAlert(form, err.message);
    }
  });

  // Reject: the reason must be 5 to 300 characters.
  async function reject() {
    const reason = comment.value.trim();
    if (reason.length < R.REJECT_MIN || reason.length > R.REJECT_MAX) {
      UI.fieldError(comment, 'Give a reason for the rejection');
      comment.focus();
      return;
    }
    UI.setBusy(rejectButton, true, 'Rejecting');
    try {
      await API.rejectLog(logId, reason);
      UI.toast('Log rejected');
      setTimeout(() => { window.location.href = 'supervisor-pending.html#pending'; }, 700);
    } catch (err) {
      UI.setBusy(rejectButton, false);
      UI.formAlert(form, err.message);
    }
  }

  load();
  // Draw the pad once the layout has its final size.
  requestAnimationFrame(() => signature.resize());
});

/* Signature pad on a canvas. Pointer events cover mouse, touch and pen. */
function createSignaturePad(canvas, onChange) {
  const ctx = canvas.getContext('2d');
  let drawing = false;
  let hasInk = false;
  let last = null;

  // Matches the canvas pixels to its size on screen so lines stay sharp.
  function resize() {
    const ratio = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    if (!rect.width) return;
    canvas.width = Math.round(rect.width * ratio);
    canvas.height = Math.round(rect.height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.lineWidth = 2.4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#ffffff';
    ctx.fillStyle = '#ffffff';
    hasInk = false;
  }

  function point(event) {
    const rect = canvas.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  canvas.addEventListener('pointerdown', (event) => {
    event.preventDefault();
    try { canvas.setPointerCapture(event.pointerId); } catch (error) { /* capture is optional */ }
    drawing = true;
    last = point(event);
    ctx.beginPath();
    ctx.arc(last.x, last.y, 1.2, 0, Math.PI * 2);
    ctx.fill();
    hasInk = true;
    onChange();
  });

  canvas.addEventListener('pointermove', (event) => {
    if (!drawing) return;
    const next = point(event);
    ctx.beginPath();
    ctx.moveTo(last.x, last.y);
    ctx.lineTo(next.x, next.y);
    ctx.stroke();
    last = next;
  });

  const stop = () => { drawing = false; last = null; };
  canvas.addEventListener('pointerup', stop);
  canvas.addEventListener('pointercancel', stop);
  window.addEventListener('resize', () => { if (!hasInk) resize(); });

  return { canvas, resize, clear: resize, isEmpty: () => !hasInk, toDataURL: () => canvas.toDataURL('image/png') };
}
