/* ==========================================================
   WIL Connect: layout.js
   Builds the shared layout of section 4.7.2 for the logged in role:
   the top bar (WIL Connect, notification bell with unread count,
   user name and role, Log out) and the side menu of Table 4.7.2,
   with Profile at the bottom. On a phone the menu folds into a button.
   Every logged in page calls Layout.mount(user) once.
   ========================================================== */
const Layout = (() => {
  // Menu items per role from Table 4.7.2: [label, link, icon].
  const NAV = {
    STUDENT: [
      ['Dashboard', 'student-dashboard.html', 'grid'],
      ['Find Employers', 'employers.html', 'search'],
      ['My Requests', 'requests.html', 'send'],
      ['Daily Log', 'daily-log.html', 'pen'],
      ['My Logs', 'daily-log.html#logs', 'list'],
      ['Attendance', 'student-dashboard.html#attendance', 'clock'],
      ['My Report', 'report.html', 'file'],
      ['Notifications', 'notifications.html', 'bell']
    ],
    SUPERVISOR: [
      ['Dashboard', 'supervisor-pending.html', 'grid'],
      ['Pending Sign Offs', 'supervisor-pending.html#pending', 'check'],
      ['Signed Logs', 'supervisor-pending.html#history', 'list'],
      ['My Students', 'supervisor-pending.html#students', 'users'],
      ['Notifications', 'notifications.html', 'bell']
    ],
    EMPLOYER: [
      ['Dashboard', 'employer-requests.html', 'grid'],
      ['Placement Requests', 'employer-requests.html#requests', 'inbox'],
      ['Students on Placement', 'employer-requests.html#placements', 'users'],
      ['Supervisors', 'employer-requests.html#supervisors', 'user'],
      ['Notifications', 'notifications.html', 'bell']
    ],
    COORDINATOR: [
      ['Dashboard', 'coordinator-dashboard.html', 'grid'],
      ['Employers', 'coordinator-employers.html', 'building'],
      ['Students and Progress', 'coordinator-dashboard.html#students', 'users'],
      ['Reports', 'report.html', 'file'],
      ['Sign Off Audit Log', 'coordinator-audit.html', 'shield'],
      ['Users', 'coordinator-users.html', 'user'],
      ['Notifications', 'notifications.html', 'bell']
    ]
  };

  // Pages that are not in the menu but belong to one of its items.
  const PARENT_PAGE = { 'supervisor-signoff.html': 'supervisor-pending.html#pending' };

  const currentFile = () => window.location.pathname.split('/').pop() || '';

  // Highlights the menu item for the page (and section) that is open.
  function markActive(nav) {
    const file = currentFile();
    const hash = window.location.hash;
    const links = [...nav.querySelectorAll('a[data-nav]')];
    const withHash = file + hash;
    const target = PARENT_PAGE[file] || (hash && links.some((a) => a.getAttribute('href') === withHash) ? withHash : file);
    links.forEach((a) => {
      if (a.getAttribute('href') === target) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
  }

  function mount(user) {
    const app = document.getElementById('app');
    const main = document.getElementById('main');
    const items = NAV[user.role] || [];

    // ---------- Top bar ----------
    const header = document.createElement('header');
    header.className = 'topbar';
    header.innerHTML = `
      <button type="button" class="icon-btn menu-btn" id="menu-btn" aria-controls="sidebar" aria-expanded="false" aria-label="Open the menu">${UI.icon('menu')}</button>
      <a class="brand" href="${Auth.dashboardFor(user.role)}" aria-label="WIL Connect, go to your dashboard">
        ${UI.LOGO}<span class="brand-name">WIL <span class="brand-accent">Connect</span></span>
      </a>
      <div class="topbar-right">
        <div class="notify">
          <button type="button" class="icon-btn" id="bell" aria-haspopup="true" aria-expanded="false" aria-controls="notify-panel" aria-label="Notifications">
            ${UI.icon('bell')}<span class="badge hidden" id="bell-count"></span>
          </button>
          <div class="notify-panel hidden" id="notify-panel" role="region" aria-label="Latest notifications">
            <div class="notify-head">
              <h2>Notifications</h2>
              <button type="button" class="link-btn hidden" id="mark-all">Mark all as read</button>
            </div>
            <div id="notify-body"></div>
            <a class="notify-all" href="notifications.html">See all notifications</a>
          </div>
        </div>
        <span class="avatar" aria-hidden="true">${UI.esc(UI.initials(user.name))}</span>
        <div class="user-chip"><strong>${UI.esc(user.name)}</strong><span>${UI.esc(Auth.roleLabel(user.role))}</span></div>
        <button type="button" class="btn btn-light btn-sm" id="logout">Log out</button>
      </div>`;

    // ---------- Side menu ----------
    const aside = document.createElement('nav');
    aside.className = 'sidebar';
    aside.id = 'sidebar';
    aside.setAttribute('aria-label', 'Main menu');
    aside.innerHTML = `
      <ul class="nav">${items.map(([label, href, iconName]) => `
        <li><a href="${href}" data-nav>${UI.icon(iconName)}<span>${UI.esc(label)}</span>${iconName === 'bell' ? '<span class="nav-count hidden" data-count="notifications"></span>' : ''}${href.endsWith('#pending') ? '<span class="nav-count hidden" data-count="pending"></span>' : ''}</a></li>`).join('')}
      </ul>
      <a class="nav-profile" href="profile.html" data-nav>${UI.icon('user')}<span>Profile</span></a>`;

    // Dark layer behind the phone menu; tapping it closes the menu.
    const backdrop = document.createElement('div');
    backdrop.className = 'menu-backdrop';
    backdrop.setAttribute('aria-hidden', 'true');

    app.insertBefore(header, main);
    app.insertBefore(aside, main);
    app.appendChild(backdrop);
    markActive(aside);
    window.addEventListener('hashchange', () => markActive(aside));

    header.querySelector('#logout').addEventListener('click', () => Auth.logout());
    setUpMenuButton(header, aside, backdrop);
    setUpBell(header, aside, user);
    if (user.role === 'SUPERVISOR') showPendingCount(aside);
  }

  // On a phone the menu is hidden behind the menu button.
  function setUpMenuButton(header, aside, backdrop) {
    const button = header.querySelector('#menu-btn');
    const open = (show) => {
      document.body.classList.toggle('menu-open', show);
      button.setAttribute('aria-expanded', String(show));
      button.setAttribute('aria-label', show ? 'Close the menu' : 'Open the menu');
      button.innerHTML = UI.icon(show ? 'close' : 'menu');
      if (show) (aside.querySelector('[aria-current="page"]') || aside.querySelector('a')).focus();
    };
    button.addEventListener('click', () => open(!document.body.classList.contains('menu-open')));
    aside.addEventListener('click', (event) => { if (event.target.closest('a')) open(false); });
    backdrop.addEventListener('click', () => open(false));
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && document.body.classList.contains('menu-open')) { open(false); button.focus(); }
    });
    // Turning a tablet to landscape shows the side menu again, so the phone menu closes.
    window.matchMedia('(min-width: 861px)').addEventListener('change', (event) => { if (event.matches) open(false); });
  }

  // Number of logs waiting, next to Pending Sign Offs.
  async function showPendingCount(aside) {
    try {
      const logs = await API.getPendingLogs();
      setCount(aside, 'pending', logs.length);
    } catch (err) { /* the count is optional */ }
  }

  function setCount(aside, name, value) {
    const el = aside.querySelector(`[data-count="${name}"]`);
    if (!el) return;
    el.textContent = value > 99 ? '99+' : String(value);
    el.classList.toggle('hidden', !value);
  }

  // ---------- Notification bell ----------
  function setUpBell(header, aside, user) {
    const bell = header.querySelector('#bell');
    const panel = header.querySelector('#notify-panel');
    const body = header.querySelector('#notify-body');
    const count = header.querySelector('#bell-count');
    const markAll = header.querySelector('#mark-all');
    let items = [];

    function showCount(unread) {
      count.textContent = unread > 9 ? '9+' : String(unread);
      count.classList.toggle('hidden', unread === 0);
      bell.setAttribute('aria-label', unread ? `Notifications, ${unread} unread` : 'Notifications');
      markAll.classList.toggle('hidden', unread === 0);
      setCount(aside, 'notifications', unread);
    }

    function render() {
      if (!items.length) {
        UI.empty(body, 'No notifications yet', 'Updates about your logs and requests will show here.');
        body.firstElementChild.style.padding = '16px';
        return;
      }
      UI.done(body, `<ul class="notify-list">${UI.notificationItems(items.slice(0, 5), user.role)}</ul>`);
    }

    // silent: refresh in the background without the loading skeleton.
    async function load(silent) {
      if (!silent) UI.loading(body, 'Loading notifications', 2);
      try {
        const inbox = await API.getNotifications();
        items = inbox.items || [];
        showCount(inbox.unreadCount ?? items.filter((n) => !n.read).length);
        render();
      } catch (err) {
        if (silent) return; // keep what is shown when a background refresh fails
        showCount(0);
        UI.error(body, err, () => load());
        body.firstElementChild.style.padding = '16px';
      }
    }

    async function markRead(ids) {
      try {
        await Promise.all(ids.map((id) => API.markNotificationRead(id)));
        items.forEach((n) => { if (ids.includes(n.notificationId)) n.read = true; });
        showCount(items.filter((n) => !n.read).length);
        render();
        window.dispatchEvent(new CustomEvent('wil:notifications-read'));
      } catch (err) {
        UI.toast(err.message, 'error');
      }
    }

    function open(show) {
      panel.classList.toggle('hidden', !show);
      bell.setAttribute('aria-expanded', String(show));
    }

    bell.addEventListener('click', () => open(panel.classList.contains('hidden')));
    body.addEventListener('click', (event) => {
      const button = event.target.closest('[data-read]');
      if (button) markRead([Number(button.dataset.read)]);
    });
    markAll.addEventListener('click', () => markRead(items.filter((n) => !n.read).map((n) => n.notificationId)));
    document.addEventListener('click', (event) => {
      if (!header.querySelector('.notify').contains(event.target)) open(false);
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !panel.classList.contains('hidden')) { open(false); bell.focus(); }
    });
    // Opening a notification marks it as read and goes to the page that answers it.
    UI.bindNotificationLinks(body);
    body.addEventListener('click', (event) => { if (event.target.closest('a.notify-link')) open(false); });

    // Other pages tell the bell when something was marked as read.
    window.addEventListener('wil:refresh-bell', () => load(true));

    // New messages show up without reloading: every minute while the tab is open,
    // and straight away when the user comes back to the tab.
    setInterval(() => { if (!document.hidden) load(true); }, 60000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) load(true); });

    load();
  }

  return { mount };
})();

window.Layout = Layout;
