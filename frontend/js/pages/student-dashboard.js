/* Student dashboard (Table 4.7.12): progress card, today's action, recent logs and notifications. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('STUDENT');
  if (!user) return;
  Layout.mount(user);

  const hoursEl = document.getElementById('hours');
  const todayEl = document.getElementById('today');
  const recentEl = document.getElementById('recent');
  const notesEl = document.getElementById('notes');
  const placementLine = document.getElementById('placement-line');

  // "Good morning, John"
  const hour = new Date().getHours();
  const part = hour < 12 ? 'morning' : hour < 17 ? 'afternoon' : 'evening';
  document.getElementById('greeting').textContent = `Good ${part}, ${user.name.split(' ')[0]}`;
  document.getElementById('today-title').textContent = UI.formatLongDate(new Date()).replace(/ \d{4}$/, '');

  async function load() {
    UI.loading(hoursEl, 'Loading hours', 2);
    UI.loading(todayEl, 'Loading today', 1);
    UI.loading(recentEl, 'Loading recent logs', 4);
    loadNotifications();

    // 1. Find the active placement.
    let placement;
    try {
      const placements = await API.getMyPlacements();
      placement = placements.find((p) => p.status === 'Active') || placements[0];
    } catch (err) {
      placementLine.textContent = 'Your placement could not load.';
      [hoursEl, todayEl, recentEl].forEach((el) => UI.error(el, err, load));
      return;
    }

    if (!placement) {
      placementLine.textContent = 'You do not have a placement yet.';
      UI.empty(hoursEl, 'No placement yet', 'Your hours start counting once an employer accepts one of your placement requests.', '<a class="btn btn-primary btn-sm" href="employers.html">Find Employers</a>');
      UI.empty(todayEl, 'Nothing to log yet', 'You can log work once your placement starts.');
      UI.empty(recentEl, 'You have no logs yet', 'Logs appear here once your placement starts.');
      return;
    }

    placementLine.textContent = `${placement.employerName}. Placement from ${UI.formatDayMonth(placement.startDate)} to ${UI.formatDate(placement.endDate)}.`;

    // 2. Attendance (calculateAttendance) and the logs load side by side.
    const [attendance, logs] = await Promise.allSettled([API.calculateAttendance(placement.placementId), API.getMyDutyLogs()]);
    renderHours(attendance, placement);
    renderToday(logs);
    renderRecent(logs);
  }

  function renderHours(result, placement) {
    if (result.status === 'rejected') return UI.error(hoursEl, result.reason, load);
    const a = result.value;
    UI.done(hoursEl, `
      <div class="hours-top">
        <p class="hours-total">${Math.round(a.signedHours * 10) / 10} <span>/ ${API.REQUIRED_HOURS} hours</span></p>
        ${UI.pill(a.status)}
      </div>
      ${UI.progressBar(a.percentage, 'Hours signed off')}
      <p class="small muted">${UI.formatHours(a.percentage)}% complete. ${UI.formatHours(a.remainingHours)} hours remaining.</p>
      <div class="stats">
        <div><strong>${a.daysCompleted}</strong><span>Days completed</span></div>
        <div><strong>${UI.formatHours(a.pendingHours)}</strong><span>Hours waiting for sign off</span></div>
        <div><strong>${UI.esc(UI.formatDayMonth(placement.endDate))}</strong><span>Placement ends</span></div>
      </div>`);
  }

  // Log Today's Duty if there is no log for today, otherwise its status.
  function renderToday(result) {
    if (result.status === 'rejected') return UI.error(todayEl, result.reason, load);
    const todays = result.value.find((l) => l.logDate === UI.todayString());
    if (todays) {
      UI.done(todayEl, `<p class="small muted">Today's log has been sent.</p><div style="margin-top:10px">${UI.pill(todays.status)}</div>
        <a class="btn btn-block" href="daily-log.html#logs">View My Logs</a>`);
    } else {
      UI.done(todayEl, `<p class="small muted">You have not logged today yet.</p>
        <a class="btn btn-primary btn-block" href="daily-log.html">${UI.icon('pen')}Log Today's Duty</a>`);
    }
  }

  // The last 5 logs with date, hours, status and the supervisor's comment.
  function renderRecent(result) {
    if (result.status === 'rejected') return UI.error(recentEl, result.reason, load);
    const list = result.value.slice(0, 5);
    if (!list.length) {
      return UI.empty(recentEl, 'You have no logs yet.', "Select Log Today's Duty to start.", '<a class="btn btn-primary btn-sm" href="daily-log.html">Log Today\'s Duty</a>');
    }
    UI.done(recentEl, `<div class="table-wrap"><table class="table">
      <thead><tr><th>Date</th><th class="num">Hours</th><th>Status</th><th>Supervisor comment</th></tr></thead>
      <tbody>${list.map((l) => `
        <tr>
          <td class="nowrap">${UI.esc(UI.formatDate(l.logDate))}</td>
          <td class="num">${UI.formatHours(l.hoursWorked)}</td>
          <td>${UI.pill(l.status)}</td>
          <td class="muted">${UI.esc(l.supervisorComment || 'None')}</td>
        </tr>`).join('')}</tbody></table></div>`);
  }

  // The 3 latest messages with a link to see all.
  async function loadNotifications() {
    UI.loading(notesEl, 'Loading notifications', 3);
    try {
      const inbox = await API.getNotifications();
      const items = (inbox.items || []).slice(0, 3);
      if (!items.length) return UI.empty(notesEl, 'No notifications yet', 'Updates about your logs and requests will show here.');
      UI.done(notesEl, `<ul class="notify-list">${UI.notificationItems(items, 'STUDENT')}</ul>`);
    } catch (err) {
      UI.error(notesEl, err, loadNotifications);
    }
  }

  // A rejected log opens My Logs so the student can correct it.
  UI.bindNotificationLinks(notesEl);
  notesEl.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-read]');
    if (!button) return;
    await API.markNotificationRead(Number(button.dataset.read)).catch(() => {});
    window.dispatchEvent(new CustomEvent('wil:refresh-bell'));
    loadNotifications();
  });

  load();
});
