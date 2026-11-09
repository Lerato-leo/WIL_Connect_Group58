/* Notifications (sendNotification, 4.6.8): every message for the logged in user, 10 to a page. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('STUDENT', 'SUPERVISOR', 'EMPLOYER', 'COORDINATOR');
  if (!user) return;
  Layout.mount(user);

  const listEl = document.getElementById('list');
  const markAll = document.getElementById('mark-all-page');
  let items = [];

  async function load() {
    UI.loading(listEl, 'Loading notifications', 4);
    try {
      items = (await API.getNotifications()).items || [];
      render();
    } catch (err) {
      UI.error(listEl, err, load);
    }
  }

  function render() {
    markAll.classList.toggle('hidden', !items.some((n) => !n.read));
    if (!items.length) return UI.empty(listEl, 'No notifications yet', 'Updates about your logs and requests will show here.');
    UI.paged(listEl, items, (rows) => `<ul class="notify-list notify-page">${UI.notificationItems(rows, user.role)}</ul>`);
  }

  // Messages that ask for an action open the page that answers them.
  UI.bindNotificationLinks(listEl);

  async function markRead(ids) {
    try {
      await Promise.all(ids.map((id) => API.markNotificationRead(id)));
      items.forEach((n) => { if (ids.includes(n.notificationId)) n.read = true; });
      render();
      window.dispatchEvent(new CustomEvent('wil:refresh-bell'));
    } catch (err) {
      UI.toast(err.message, 'error');
    }
  }

  listEl.addEventListener('click', (event) => {
    const button = event.target.closest('[data-read]');
    if (button) markRead([Number(button.dataset.read)]);
  });
  markAll.addEventListener('click', () => markRead(items.filter((n) => !n.read).map((n) => n.notificationId)));
  // Marking from the bell also updates this list.
  window.addEventListener('wil:notifications-read', load);

  load();
});
