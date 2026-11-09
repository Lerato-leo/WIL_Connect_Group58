/* Compliance report (generateComplianceReport, 4.6.9).
   Coordinator: report.html lists the students, report.html?placement=1 shows one report.
   Student: always their own placement (My Report). Only signed logs are included. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('COORDINATOR', 'STUDENT');
  if (!user) return;
  Layout.mount(user);

  const isStudent = user.role === 'STUDENT';
  const requested = Number(new URLSearchParams(window.location.search).get('placement'));
  const reportEl = document.getElementById('report');
  const alertEl = document.getElementById('report-alert');
  let current = null; // { details, attendance, signed }

  if (isStudent) document.getElementById('page-title').textContent = 'My Report';

  // ---------- Coordinator without a placement: list of students ----------
  async function loadPicker() {
    UI.loading(reportEl, 'Loading students', 3);
    try {
      const rows = await API.getStudentProgress();
      if (!rows.length) return UI.empty(reportEl, 'No students on placement yet', 'Reports become available once students have signed logs.');
      UI.paged(reportEl, rows, (page) => `<section class="card"><div class="table-wrap"><table class="table">
        <thead><tr><th>Student no.</th><th>Name</th><th>Employer</th><th class="num">Hours</th><th>Status</th><th class="right"><span class="visually-hidden">Report</span></th></tr></thead>
        <tbody>${page.map((r) => `
          <tr>
            <td class="num muted">${UI.esc(r.studentNumber)}</td>
            <td class="cell-strong">${UI.esc(r.studentName)}</td>
            <td>${UI.esc(r.employerName)}</td>
            <td class="num">${UI.formatHours(r.attendance.signedHours)}</td>
            <td>${UI.pill(r.attendance.status)}</td>
            <td class="right"><a class="btn btn-sm" href="report.html?placement=${r.placementId}">Create Report</a></td>
          </tr>`).join('')}</tbody></table></div></section>`);
    } catch (err) {
      UI.error(reportEl, err, loadPicker);
    }
  }

  // ---------- One report ----------
  async function loadReport() {
    UI.loading(reportEl, 'Creating the report', 6);
    try {
      let details;
      let attendance;
      let logs;
      if (isStudent) {
        const placements = await API.getMyPlacements();
        const p = placements.find((x) => x.status === 'Active') || placements[0];
        if (!p) return UI.empty(reportEl, 'No placement yet', 'Your report becomes available once you have a placement with signed logs.');
        details = p;
        [attendance, logs] = await Promise.all([API.calculateAttendance(p.placementId), API.getMyDutyLogs()]);
      } else {
        const [rows, list] = await Promise.all([API.getStudentProgress(), API.getPlacementLogs(requested)]);
        details = rows.find((r) => r.placementId === requested);
        if (!details) return UI.empty(reportEl, 'Report not found', 'This placement does not exist.', '<a class="btn btn-sm" href="report.html">Choose a student</a>');
        attendance = details.attendance;
        logs = list;
      }
      const signed = logs.filter((l) => l.status === 'Signed').sort((a, b) => a.logDate.localeCompare(b.logDate));
      // Logs that are not signed off are left out (4.6.9).
      if (!signed.length) {
        return UI.empty(reportEl, 'There are no signed logs to include in the report', 'The report can be created once a supervisor signs at least one log.');
      }
      current = { details, attendance, signed };
      document.getElementById('report-sub').textContent = `${details.studentName}, ${details.employerName}`;
      document.getElementById('report-actions').classList.remove('hidden');
      if (!isStudent) {
        document.getElementById('crumbs').classList.remove('hidden');
        document.getElementById('crumb-name').textContent = details.studentName;
        document.getElementById('page-title').textContent = 'Compliance Report';
      }
      render(current);
      loadProofImages(signed);
    } catch (err) {
      UI.error(reportEl, err, loadReport);
    }
  }

  // Layout of Table 4.7.15: header, details, hours summary, signed logs, proof, closing line.
  function render({ details: d, attendance: a, signed }) {
    const proofs = signed.flatMap((l) => (l.proofs || []).map((p) => ({ ...p, logDate: l.logDate })));
    const images = proofs.filter((p) => !String(p.fileType).includes('pdf'));
    const pdfs = proofs.filter((p) => String(p.fileType).includes('pdf'));

    UI.done(reportEl, `
      <article class="report" aria-label="Work Integrated Learning Compliance Report">
        <header class="report-head">
          <span class="brand">${UI.LOGO}<span class="brand-name">WIL <span class="brand-accent">Connect</span></span></span>
          <div class="report-date">Report date<strong>${UI.esc(UI.formatDate(UI.todayString()))}</strong></div>
        </header>
        <h1>Work Integrated Learning Compliance Report</h1>
        <p class="report-sub">Created by the system from signed logs only.</p>

        <h2>Student and placement</h2>
        <dl class="report-grid">
          <div><dt>Student</dt><dd>${UI.esc(d.studentName)}</dd></div>
          <div><dt>Employer</dt><dd>${UI.esc(d.employerName)}</dd></div>
          <div><dt>Student number</dt><dd>${UI.esc(d.studentNumber)}</dd></div>
          <div><dt>Supervisor</dt><dd>${UI.esc(d.supervisorName)}</dd></div>
          ${d.startDate ? `<div><dt>Placement start</dt><dd>${UI.esc(UI.formatDate(d.startDate))}</dd></div>
          <div><dt>Placement end</dt><dd>${UI.esc(UI.formatDate(d.endDate))}</dd></div>` : ''}
        </dl>

        <h2>Hours summary</h2>
        <div class="report-boxes">
          <div><strong>${UI.formatHours(a.signedHours)}</strong><span>Total hours signed</span></div>
          <div><strong>${a.daysCompleted}</strong><span>Days completed</span></div>
          <div><strong>${API.REQUIRED_HOURS}</strong><span>Required hours</span></div>
          <div><strong>${UI.formatHours(a.percentage)}%</strong><span>Complete</span></div>
        </div>
        ${UI.progressBar(a.percentage, 'Hours complete')}

        <h2>Signed logs</h2>
        <div class="report-table"><table class="table">
          <thead><tr><th>Date</th><th>Task done</th><th class="num">Hours</th><th>Signed on</th><th>Method</th></tr></thead>
          <tbody>${signed.map((l) => `
            <tr>
              <td class="nowrap">${UI.esc(UI.formatDate(l.logDate))}</td>
              <td>${UI.esc(l.taskDescription)}</td>
              <td class="num">${UI.formatHours(l.hoursWorked)}</td>
              <td class="nowrap">${UI.esc(UI.formatDateTime(l.signedAt))}</td>
              <td>${UI.esc(l.verificationMethod || '')}</td>
            </tr>`).join('')}</tbody>
        </table></div>
        <p class="report-note">All ${signed.length} signed logs are listed.</p>

        <h2>Proof of work</h2>
        ${images.length ? `<div class="report-proofs">${images.map((p) => `
          <figure><img alt="Proof of work for ${UI.esc(UI.formatDate(p.logDate))}" data-proof-img="${p.proofId}"><figcaption>${UI.esc(UI.formatDate(p.logDate))} · ${UI.esc(p.fileName)}</figcaption></figure>`).join('')}</div>` : ''}
        ${pdfs.length ? `<ul class="report-files">${pdfs.map((p) => `<li>${UI.esc(p.fileName)}, log of ${UI.esc(UI.formatDate(p.logDate))}</li>`).join('')}</ul>` : ''}
        ${!images.length && !pdfs.length ? '<p class="report-note">No proof of work was attached to the signed logs.</p>' : ''}

        <p class="report-note" style="margin-top:22px">This report was created by WIL Connect from signed logs only.</p>
        <footer class="report-foot"><span>WIL Connect Compliance Report. ${UI.esc(d.studentName)}, ${UI.esc(d.studentNumber)}</span><span>${UI.esc(UI.formatDate(UI.todayString()))}</span></footer>
      </article>`);
  }

  // Image proof is shown under its log date; PDF proof is listed by name.
  async function loadProofImages(signed) {
    const imgs = [...reportEl.querySelectorAll('[data-proof-img]')];
    await Promise.all(imgs.map(async (img) => {
      try {
        img.src = URL.createObjectURL(await API.getProofFile(Number(img.dataset.proofImg)));
      } catch (err) {
        img.alt = 'This proof file could not load.';
      }
    }));
  }

  // ---------- Download and print ----------
  document.getElementById('print-button').addEventListener('click', () => window.print());

  document.getElementById('pdf-button').addEventListener('click', async (event) => {
    const button = event.currentTarget;
    alertEl.innerHTML = '';
    // With fake data there is no PDF service, so the browser's print to PDF is used.
    if (API.USE_FAKE_DATA) return window.print();
    UI.setBusy(button, true, 'Creating PDF');
    try {
      const blob = await API.generateComplianceReport(current.details.placementId);
      const today = UI.todayString().replace(/\D/g, '');
      UI.downloadBlob(blob, `WIL_Report_${current.details.studentNumber}_${today}.pdf`);
    } catch (err) {
      alertEl.innerHTML = `<div class="alert alert-error" role="alert">${UI.esc(err.message)}</div>`;
    } finally {
      UI.setBusy(button, false);
    }
  });

  if (isStudent || requested) loadReport();
  else loadPicker();
});
