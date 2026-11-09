/* ==========================================================
   WIL Connect: auth.js
   Keeps the logged in user in sessionStorage and follows the four
   navigation rules of section 4.7.2:
     1. a user who is not logged in is sent to the login page
     2. a user who opens a page of another role is told access is not allowed
     3. a user who is inactive for 30 minutes must log in again
     4. the current page is highlighted in the menu (see layout.js)
   The backend checks the role again on every request (checkAccess, 4.6.3).
   ========================================================== */
const Auth = (() => {
  const SESSION_TIMEOUT_MS = 30 * 60 * 1000; // SESSION_TIMEOUT, Table 4.6.2

  // The first page each role sees after login.
  const DASHBOARDS = {
    STUDENT: 'student-dashboard.html',
    SUPERVISOR: 'supervisor-pending.html',
    EMPLOYER: 'employer-requests.html',
    COORDINATOR: 'coordinator-dashboard.html'
  };

  const ROLE_LABELS = { STUDENT: 'Student', SUPERVISOR: 'Supervisor', EMPLOYER: 'Employer', COORDINATOR: 'Coordinator' };

  // Turns "john.smith@richfield.co.za" into "John Smith" when the backend sends no name.
  function nameFromEmail(email) {
    return String(email || '').split('@')[0].split(/[._]/).filter(Boolean)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
  }

  function getUser() {
    try {
      return JSON.parse(sessionStorage.getItem(API.SESSION_KEY)) || null;
    } catch (error) {
      return null;
    }
  }

  function saveUser(user) {
    sessionStorage.setItem(API.SESSION_KEY, JSON.stringify(user));
  }

  function dashboardFor(role) {
    return DASHBOARDS[role] || 'login.html';
  }

  // Logs in, saves the user (id, email, role, name and the login token) and returns it.
  async function login(email, password) {
    const result = await API.login(email.trim(), password);
    const role = String(result.role || '').toUpperCase();
    if (!DASHBOARDS[role]) throw new API.ApiError('This account has no role that can use the portal.', 403);
    const user = {
      userId: result.userId,
      email: result.email,
      role,
      name: result.name || nameFromEmail(result.email),
      token: API.basicToken(email.trim(), password),
      lastActive: Date.now()
    };
    saveUser(user);
    return user;
  }

  function logout(reason) {
    sessionStorage.removeItem(API.SESSION_KEY);
    window.location.href = reason ? `login.html?reason=${reason}` : 'login.html';
  }

  // Call at the top of every logged in page with the roles that may open it.
  function requireRole(...roles) {
    const user = getUser();
    if (!user) {
      window.location.replace('login.html');
      return null;
    }
    if (Date.now() - (user.lastActive || 0) > SESSION_TIMEOUT_MS) {
      logout('expired');
      return null;
    }
    if (roles.length && !roles.includes(user.role)) {
      window.location.replace(`${dashboardFor(user.role)}?denied=1`);
      return null;
    }
    keepAlive(user);
    if (new URLSearchParams(window.location.search).get('denied') === '1') {
      setTimeout(() => UI.toast('You do not have access to that page.', 'error'), 300);
    }
    return user;
  }

  // Activity on the page keeps the session open; 30 quiet minutes end it.
  function keepAlive(user) {
    let last = 0;
    const touch = () => {
      const now = Date.now();
      if (now - last < 15000) return;
      last = now;
      const current = getUser();
      if (current) { current.lastActive = now; saveUser(current); }
    };
    ['click', 'keydown', 'scroll', 'pointerdown'].forEach((type) => window.addEventListener(type, touch, { passive: true }));
    touch();
    setInterval(() => {
      const current = getUser();
      if (current && Date.now() - (current.lastActive || 0) > SESSION_TIMEOUT_MS) logout('expired');
    }, 60000);
  }

  // Used on the login page: someone already logged in goes straight to their dashboard.
  function redirectIfLoggedIn() {
    const user = getUser();
    if (user && DASHBOARDS[user.role] && Date.now() - (user.lastActive || 0) <= SESSION_TIMEOUT_MS) {
      window.location.replace(dashboardFor(user.role));
      return true;
    }
    if (user) sessionStorage.removeItem(API.SESSION_KEY);
    return false;
  }

  // api.js raises this event when the backend answers 401 (login expired).
  window.addEventListener('wil:unauthorized', () => {
    if (getUser()) logout('expired');
  });

  return { getUser, login, logout, requireRole, redirectIfLoggedIn, dashboardFor, roleLabel: (role) => ROLE_LABELS[role] || role };
})();

window.Auth = Auth;
