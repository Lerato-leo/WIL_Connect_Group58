/* ==========================================================
   WIL Connect: ui.js
   Small helpers shared by every page: formatting, status labels,
   loading, empty and error states, paging, form errors, toasts and icons.
   Output rules follow section 4.7.4 of the Phase 4 document:
   dates as "12 March 2026", hours with one decimal, 10 rows per page.
   ========================================================== */
const UI = (() => {
  // Escapes text before it goes into innerHTML.
  function esc(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  // Reads "2026-10-29" (or a full date and time) as a local date.
  function toDate(value) {
    if (!value) return null;
    if (value instanceof Date) return value;
    if (typeof value === 'number') return new Date(value);
    const text = String(value);
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
      const [y, m, d] = text.split('-').map(Number);
      return new Date(y, m - 1, d, 12);
    }
    const date = new Date(text);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  // "2026-10-29" becomes "29 October 2026".
  function formatDate(value) {
    const date = toDate(value);
    if (!date) return value ? String(value) : '';
    return `${date.getDate()} ${MONTHS[date.getMonth()]} ${date.getFullYear()}`;
  }

  // "2026-10-29" becomes "Thursday, 29 October 2026".
  function formatLongDate(value) {
    const date = toDate(value);
    return date ? `${DAYS[date.getDay()]}, ${formatDate(date)}` : '';
  }

  // "2026-11-30" becomes "30 November" (used where the year is clear).
  function formatDayMonth(value) {
    const date = toDate(value);
    return date ? `${date.getDate()} ${MONTHS[date.getMonth()]}` : '';
  }

  // A date and time becomes "28 October 2026, 08:30".
  function formatDateTime(value) {
    const date = toDate(value);
    if (!date) return value ? String(value) : '';
    const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
    return `${formatDate(date)}, ${time}`;
  }

  // Hours always show one decimal: 8 becomes "8.0".
  function formatHours(value) {
    return (Number(value) || 0).toFixed(1);
  }

  function todayString() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }

  // "John Smith" becomes "JS" for the avatar in the top bar.
  function initials(name) {
    return String(name || '').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  }

  // Status label. The text is always shown, so colour is never the only signal.
  const PILLS = {
    pending: ['pill-pending', 'Pending'],
    signed: ['pill-signed', 'Signed'],
    rejected: ['pill-rejected', 'Rejected'],
    accepted: ['pill-signed', 'Accepted'],
    approved: ['pill-signed', 'Approved'],
    completed: ['pill-signed', 'Completed'],
    complete: ['pill-signed', 'Complete'],
    active: ['pill-active', 'Active'],
    'in progress': ['pill-active', 'In progress'],
    'meeting requested': ['pill-meeting', 'Meeting requested'],
    inactive: ['', 'Inactive'],
    cancelled: ['', 'Cancelled']
  };

  function pill(status) {
    const key = String(status || '').trim().toLowerCase();
    const [cls, label] = PILLS[key] || ['', status || 'Unknown'];
    return `<span class="pill ${cls}">${esc(label)}</span>`;
  }

  // ---------- States for every fetch ----------
  function loading(el, label = 'Loading', rows = 3) {
    el.setAttribute('aria-busy', 'true');
    el.innerHTML = `<div class="skeleton" role="status"><span class="visually-hidden">${esc(label)}</span>${'<div class="skeleton-row"></div>'.repeat(rows)}</div>`;
  }

  function error(el, err, onRetry) {
    el.removeAttribute('aria-busy');
    const message = (err && err.message) || 'Something went wrong.';
    el.innerHTML = `<div class="state state-error" role="alert">
        <h3>This could not load</h3>
        <p>${esc(message)}</p>
        ${onRetry ? '<button type="button" class="btn btn-sm" data-retry>Try again</button>' : ''}
      </div>`;
    if (onRetry) el.querySelector('[data-retry]').addEventListener('click', onRetry);
  }

  function empty(el, title, text, actionHtml = '') {
    el.removeAttribute('aria-busy');
    el.innerHTML = `<div class="state"><h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ''}${actionHtml}</div>`;
  }

  function done(el, html) {
    el.removeAttribute('aria-busy');
    el.innerHTML = html;
    labelTables(el);
  }

  // Gives every table cell the text of its column heading, so narrow screens
  // can show each row as a small card with labels (see "Tables" in style.css).
  function labelTables(root) {
    root.querySelectorAll('table.table').forEach((table) => {
      const heads = [...table.querySelectorAll('thead th')].map((th) => (th.querySelector('.visually-hidden') ? '' : th.textContent.trim()));
      table.querySelectorAll('tbody tr').forEach((tr) => {
        [...tr.children].forEach((td, i) => td.setAttribute('data-label', heads[i] || ''));
      });
    });
  }

  // ---------- Paging: 10 rows per page with Previous and Next ----------
  // renderRows(rowsOnThisPage) returns the HTML for one page.
  function paged(el, items, renderRows, pageSize = 10) {
    let page = 0;
    const pages = Math.max(1, Math.ceil(items.length / pageSize));
    function draw() {
      const start = page * pageSize;
      const slice = items.slice(start, start + pageSize);
      const pager = items.length > pageSize ? `
        <div class="pager">
          <span class="small muted">Showing ${start + 1} to ${start + slice.length} of ${items.length}</span>
          <span class="spacer"></span>
          <button type="button" class="btn btn-sm" data-page="prev" ${page === 0 ? 'disabled' : ''}>Previous</button>
          <button type="button" class="btn btn-sm" data-page="next" ${page >= pages - 1 ? 'disabled' : ''}>Next</button>
        </div>` : '';
      done(el, renderRows(slice) + pager);
    }
    el.onclick = (event) => {
      const button = event.target.closest('[data-page]');
      if (!button || button.disabled) return;
      page += button.dataset.page === 'next' ? 1 : -1;
      draw();
    };
    draw();
  }

  // Disables a button while a request runs and shows a spinner.
  function setBusy(button, busy, busyText) {
    if (!button) return;
    if (busy) {
      button.dataset.label = button.textContent;
      if (busyText) button.textContent = busyText;
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
    } else {
      if (button.dataset.label) button.textContent = button.dataset.label;
      button.disabled = false;
      button.removeAttribute('aria-busy');
    }
  }

  // ---------- Form errors (shown in red next to the field) ----------
  function fieldError(input, message) {
    const id = `${input.id || input.name}-error`;
    let note = document.getElementById(id);
    if (!note) {
      note = document.createElement('p');
      note.className = 'field-error';
      note.id = id;
      (input.closest('.field') || input.parentElement).appendChild(note);
    }
    note.textContent = message;
    input.setAttribute('aria-invalid', 'true');
    const described = new Set((input.getAttribute('aria-describedby') || '').split(' ').filter(Boolean));
    described.add(id);
    input.setAttribute('aria-describedby', [...described].join(' '));
  }

  function clearErrors(form) {
    form.querySelectorAll('.field-error').forEach((note) => note.remove());
    form.querySelectorAll('[aria-invalid="true"]').forEach((input) => input.removeAttribute('aria-invalid'));
    const banner = form.querySelector('[data-form-alert]');
    if (banner) banner.innerHTML = '';
  }

  // Shows a message at the top of a form (for answers from the server).
  function formAlert(form, message, type = 'error') {
    const banner = form.querySelector('[data-form-alert]');
    if (!banner) return toast(message, type);
    banner.innerHTML = message ? `<div class="alert alert-${type}" role="alert">${esc(message)}</div>` : '';
  }

  // Moves focus to the first field with an error.
  function focusFirstError(form) {
    const first = form.querySelector('[aria-invalid="true"]');
    if (first) first.focus();
    return Boolean(first);
  }

  // Live character counter, for example "99 / 1000".
  function counter(input, output) {
    const update = () => { output.textContent = `${input.value.length} / ${input.maxLength}`; };
    input.addEventListener('input', update);
    update();
    return update;
  }

  // ---------- Toasts ----------
  function toast(message, type = 'success') {
    let region = document.querySelector('.toast-region');
    if (!region) {
      region = document.createElement('div');
      region.className = 'toast-region';
      region.setAttribute('aria-live', 'polite');
      document.body.appendChild(region);
    }
    const item = document.createElement('div');
    item.className = `toast toast-${type}`;
    item.textContent = message;
    region.appendChild(item);
    setTimeout(() => item.remove(), 4000);
  }

  // Saves a Blob as a file download.
  function downloadBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  // ---------- Icons (one stroke style, drawn as inline SVG) ----------
  const ICON_PATHS = {
    grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.5"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
    send: '<path d="M21 3 3 10.5l7.5 3L14 21l7-18Z"/><path d="m10.5 13.5 4-4"/>',
    pen: '<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z"/><path d="m13.5 6.5 4 4"/>',
    list: '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4 6h.01M4 12h.01M4 18h.01"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    file: '<path d="M14 3H6v18h12V7l-4-4Z"/><path d="M14 3v4h4"/><path d="M9 13h6M9 17h6"/>',
    bell: '<path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16Z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
    check: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="m8.5 12.5 2.5 2.5 4.5-5"/>',
    users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8"/><path d="M18 14.2a6.5 6.5 0 0 1 3.5 5.8"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    inbox: '<path d="M3 13h5l1.5 3h5L16 13h5"/><path d="M5.5 5h13L21 13v6H3v-6l2.5-8Z"/>',
    building: '<path d="M4 21V5l8-3v19"/><path d="M12 8h8v13"/><path d="M8 8v.01M8 12v.01M8 16v.01M16 12v.01M16 16v.01"/><path d="M2 21h20"/>',
    shield: '<path d="M12 3 4.5 6v6c0 4.5 3.2 7.8 7.5 9 4.3-1.2 7.5-4.5 7.5-9V6L12 3Z"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 16-5-5-9 9"/>',
    upload: '<path d="M12 16V4"/><path d="m7 9 5-5 5 5"/><path d="M5 20h14"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    arrow: '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
    prev: '<path d="m15 6-6 6 6 6"/>',
    next: '<path d="m9 6 6 6-6 6"/>'
  };

  function icon(name) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${ICON_PATHS[name] || ''}</svg>`;
  }

  // The WIL Connect mark from the mockups: a teal shield with a tick.
  const LOGO = `<svg class="brand-mark" viewBox="0 0 64 64" aria-hidden="true" focusable="false"><rect width="64" height="64" rx="14" fill="#1bbfa8"/><path d="M12 19.5 22 47 32 28.5 42 47 52 19.5" fill="none" stroke="#0a0a0b" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/><g fill="#0a0a0b"><circle cx="12" cy="19.5" r="4.8"/><circle cx="32" cy="28.5" r="4.8"/><circle cx="52" cy="19.5" r="4.8"/></g><g fill="#1bbfa8"><circle cx="12" cy="19.5" r="1.9"/><circle cx="32" cy="28.5" r="1.9"/><circle cx="52" cy="19.5" r="1.9"/></g></svg>`;

  // Progress bar, used on the dashboards and the report.
  function progressBar(percent, label) {
    const value = Math.max(0, Math.min(Number(percent) || 0, 100));
    return `<div class="progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${value}" aria-label="${esc(label || 'Progress')}"><span style="width:${value}%"></span></div>`;
  }

  // ---------- Notifications with a call to action ----------
  // The NOTIFICATION table stores only the message, so the page that answers a
  // message is worked out from its wording (Table 4.7.14) and the user's role.
  // Only messages that ask the user to do something get a link.
  function notificationAction(message, role) {
    const m = String(message || '').toLowerCase();
    if (role === 'SUPERVISOR' && m.includes('needs sign off')) return { href: 'supervisor-pending.html#pending', label: 'Review log' };
    if (role === 'STUDENT' && m.includes('was rejected')) return { href: 'daily-log.html#logs', label: 'Correct log' };
    if (role === 'STUDENT' && m.includes('meeting requested')) return { href: 'requests.html', label: 'View request' };
    if (role === 'EMPLOYER' && m.includes('placement request')) return { href: 'employer-requests.html#requests', label: 'Answer request' };
    if (role === 'COORDINATOR' && m.includes('waiting for approval')) return { href: 'coordinator-employers.html', label: 'Review employer' };
    return null;
  }

  // List items for the bell, the Notifications page and the dashboard card.
  function notificationItems(items, role) {
    return items.map((n) => {
      const action = notificationAction(n.message, role);
      return `
        <li class="notify-item ${n.read ? 'is-read' : 'is-unread'}${action ? ' has-action' : ''}">
          <span class="notify-dot" aria-hidden="true"></span>
          <div>
            ${action
              ? `<a class="notify-link" href="${action.href}" data-id="${n.notificationId}"${n.read ? '' : ' data-unread="1"'}>${esc(n.message)}</a>
                 <span class="notify-cta" aria-hidden="true">${esc(action.label)}${icon('arrow')}</span>`
              : `<p>${esc(n.message)}</p>`}
            <div class="notify-foot">
              <span>${esc(formatDateTime(n.createdAt))}${n.read ? '' : '<span class="visually-hidden">, unread</span>'}</span>
              ${n.read ? '' : `<button type="button" class="link-btn" data-read="${n.notificationId}">Mark as read</button>`}
            </div>
          </div>
        </li>`;
    }).join('');
  }

  // Opening an unread notification marks it as read, then goes to its page.
  function bindNotificationLinks(container) {
    container.addEventListener('click', async (event) => {
      const link = event.target.closest('a.notify-link');
      if (!link || !link.dataset.unread || event.ctrlKey || event.metaKey || event.shiftKey) return;
      event.preventDefault();
      try {
        await Promise.race([API.markNotificationRead(Number(link.dataset.id)), new Promise((r) => setTimeout(r, 800))]);
      } catch (err) { /* opening the page matters more than the read mark */ }
      window.dispatchEvent(new CustomEvent('wil:refresh-bell'));
      window.location.href = link.href;
    });
  }

  // ---------- Calendar for date fields ----------
  // On a mouse or trackpad, date fields get a calendar in the WIL Connect style.
  // The field stays a real date input, so typing the date and the min and max
  // rules keep working. Phones and tablets keep their own date picker.
  const DatePicker = (() => {
    const WEEKDAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
    const pad = (n) => String(n).padStart(2, '0');
    const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const parse = (v) => (/^\d{4}-\d{2}-\d{2}$/.test(v || '') ? toDate(v) : null);
    let pop = null;
    let input = null;
    let trigger = null;
    let focusDay = null;
    let limits = {};

    function enhance(el) {
      if (el.dataset.picker) return;
      el.dataset.picker = '1';
      if (window.matchMedia('(pointer: coarse)').matches) return;
      const wrap = document.createElement('div');
      wrap.className = 'date-field';
      el.parentNode.insertBefore(wrap, el);
      wrap.appendChild(el);
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'date-btn';
      button.setAttribute('aria-label', 'Open the calendar');
      button.setAttribute('aria-haspopup', 'dialog');
      button.innerHTML = icon('calendar');
      wrap.appendChild(button);
      button.addEventListener('click', () => (pop && input === el ? close(true) : open(el, button)));
      el.addEventListener('click', () => { if (!pop) open(el, button, false); });
      el.addEventListener('keydown', (event) => {
        if (event.altKey && event.key === 'ArrowDown') { event.preventDefault(); open(el, button); }
      });
    }

    function clamp(date) {
      if (limits.min && date < limits.min) return new Date(limits.min);
      if (limits.max && date > limits.max) return new Date(limits.max);
      return date;
    }

    function open(el, button, moveFocus = true) {
      close();
      input = el;
      trigger = button;
      limits = { min: parse(el.min), max: parse(el.max) };
      focusDay = clamp(parse(el.value) || toDate(todayString()));
      pop = document.createElement('div');
      pop.className = 'datepicker';
      pop.setAttribute('role', 'dialog');
      pop.setAttribute('aria-label', 'Choose a date');
      document.body.appendChild(pop);
      trigger.setAttribute('aria-expanded', 'true');
      render(moveFocus);
      place();
      pop.addEventListener('click', onClick);
      pop.addEventListener('keydown', onKey);
      document.addEventListener('pointerdown', onOutside, true);
      document.addEventListener('focusin', onFocusMove);
      window.addEventListener('resize', place);
      window.addEventListener('scroll', place, true);
    }

    // Tabbing away from the field and the calendar closes it.
    function onFocusMove(event) {
      if (pop && !pop.contains(event.target) && event.target.closest('.date-field') !== input.closest('.date-field')) close(false);
    }

    function close(returnFocus) {
      if (!pop) return;
      pop.remove();
      pop = null;
      if (trigger) trigger.setAttribute('aria-expanded', 'false');
      document.removeEventListener('pointerdown', onOutside, true);
      document.removeEventListener('focusin', onFocusMove);
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
      if (returnFocus && input) input.focus();
    }

    function inRange(d) {
      return !(limits.min && d < limits.min) && !(limits.max && d > limits.max);
    }

    function render(moveFocus = true) {
      const year = focusDay.getFullYear();
      const month = focusDay.getMonth();
      const first = new Date(year, month, 1, 12);
      const lead = (first.getDay() + 6) % 7; // weeks start on Monday
      const days = new Date(year, month + 1, 0).getDate();
      const selected = input.value;
      const today = todayString();
      const prevOk = !limits.min || new Date(year, month, 0, 12) >= limits.min;
      const nextOk = !limits.max || new Date(year, month + 1, 1, 12) <= limits.max;
      let cells = '<span></span>'.repeat(lead);
      for (let d = 1; d <= days; d++) {
        const date = new Date(year, month, d, 12);
        const value = iso(date);
        const isFocus = d === focusDay.getDate();
        cells += `<button type="button" class="dp-day${value === selected ? ' is-selected' : ''}${value === today ? ' is-today' : ''}" data-date="${value}"
          tabindex="${isFocus ? 0 : -1}" aria-label="${formatLongDate(value)}"${value === selected ? ' aria-pressed="true"' : ''}${inRange(date) ? '' : ' disabled'}>${d}</button>`;
      }
      const todayDate = toDate(today);
      pop.innerHTML = `
        <div class="dp-head">
          <button type="button" class="dp-nav" data-move="-1" aria-label="Previous month"${prevOk ? '' : ' disabled'}>${icon('prev')}</button>
          <strong aria-live="polite">${MONTHS[month]} ${year}</strong>
          <button type="button" class="dp-nav" data-move="1" aria-label="Next month"${nextOk ? '' : ' disabled'}>${icon('next')}</button>
        </div>
        <div class="dp-grid">${WEEKDAYS.map((w) => `<span class="dp-week" aria-hidden="true">${w}</span>`).join('')}${cells}</div>
        <div class="dp-foot">
          <button type="button" class="btn btn-ghost btn-sm" data-pick="${today}"${inRange(todayDate) ? '' : ' disabled'}>Today</button>
          <button type="button" class="btn btn-ghost btn-sm" data-close>Close</button>
        </div>`;
      if (moveFocus) {
        const target = pop.querySelector('.dp-day[tabindex="0"]:not([disabled])') || pop.querySelector('.dp-day:not([disabled])');
        if (target) target.focus();
      }
    }

    function place() {
      if (!pop || !input) return;
      const box = input.closest('.date-field').getBoundingClientRect();
      const w = pop.offsetWidth;
      const h = pop.offsetHeight;
      let top = box.bottom + 6;
      if (top + h > window.innerHeight - 8 && box.top - h - 6 > 8) top = box.top - h - 6;
      pop.style.top = `${Math.round(top)}px`;
      pop.style.left = `${Math.round(Math.max(8, Math.min(box.left, window.innerWidth - w - 8)))}px`;
    }

    function pick(value) {
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      close(true);
    }

    function move(days, months = 0) {
      const next = new Date(focusDay);
      if (months) next.setMonth(next.getMonth() + months, Math.min(next.getDate(), 28));
      next.setDate(next.getDate() + days);
      focusDay = clamp(next);
      render();
    }

    function onClick(event) {
      const day = event.target.closest('[data-date], [data-pick]');
      if (day && !day.disabled) return pick(day.dataset.date || day.dataset.pick);
      const nav = event.target.closest('[data-move]');
      if (nav && !nav.disabled) {
        focusDay = clamp(new Date(focusDay.getFullYear(), focusDay.getMonth() + Number(nav.dataset.move), 1, 12));
        render(false);
        nav.focus();
        return;
      }
      if (event.target.closest('[data-close]')) close(true);
    }

    function onKey(event) {
      const keys = { ArrowLeft: [-1], ArrowRight: [1], ArrowUp: [-7], ArrowDown: [7], PageUp: [0, -1], PageDown: [0, 1] };
      if (event.key === 'Escape') { event.preventDefault(); close(true); return; }
      if (!event.target.classList.contains('dp-day')) return;
      if (keys[event.key]) { event.preventDefault(); move(...keys[event.key]); return; }
      if (event.key === 'Home') { event.preventDefault(); move(-((focusDay.getDay() + 6) % 7)); }
      if (event.key === 'End') { event.preventDefault(); move(6 - ((focusDay.getDay() + 6) % 7)); }
    }

    function onOutside(event) {
      if (pop && !pop.contains(event.target) && !event.target.closest('.date-field')) close(false);
    }

    return { enhance };
  })();

  // Applies the calendar to every date field, including fields added later
  // (for example the Accept form on the employer page).
  function enhanceForms(root = document) {
    if (root.matches && root.matches('input[type="date"]')) DatePicker.enhance(root);
    root.querySelectorAll && root.querySelectorAll('input[type="date"]').forEach(DatePicker.enhance);
  }

  document.addEventListener('DOMContentLoaded', () => {
    enhanceForms(document);
    new MutationObserver((changes) => changes.forEach((c) => c.addedNodes.forEach((node) => {
      if (node.nodeType === 1) enhanceForms(node);
    }))).observe(document.body, { childList: true, subtree: true });
  });

  return {
    esc, toDate, formatDate, formatLongDate, formatDayMonth, formatDateTime, formatHours, todayString, initials, pill,
    loading, error, empty, done, paged, setBusy,
    fieldError, clearErrors, formAlert, focusFirstError, counter,
    toast, downloadBlob, icon, LOGO, progressBar,
    notificationItems, bindNotificationLinks
  };
})();

window.UI = UI;
