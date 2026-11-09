/* Login page (login module, 4.6.3): checks the form, logs in and opens the dashboard for the role. */
document.addEventListener('DOMContentLoaded', () => {
  if (Auth.redirectIfLoggedIn()) return;

  const form = document.getElementById('login-form');
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const button = document.getElementById('login-button');
  document.getElementById('brand').insertAdjacentHTML('afterbegin', UI.LOGO);

  // Messages after the session ended (4.6.3) or after a new account was created (4.6.2).
  const params = new URLSearchParams(window.location.search);
  if (params.get('reason') === 'expired') UI.formAlert(form, 'Your session has ended. Please log in again', 'info');
  if (params.get('registered') === '1') UI.formAlert(form, 'Account created. You can now log in', 'info');
  if (params.get('registered') === 'employer') {
    UI.formAlert(form, 'Account created. You can now log in. Students can find your company once a coordinator approves it.', 'info');
  }

  // The test account list helps during the demo with fake data.
  if (API.USE_FAKE_DATA) {
    const box = document.getElementById('demo-box');
    box.classList.remove('hidden');
    box.addEventListener('click', (event) => {
      const pick = event.target.closest('[data-email]');
      if (!pick) return;
      email.value = pick.dataset.email;
      password.value = 'password';
      password.focus();
    });
  }

  // Show or hide the password.
  const toggle = document.getElementById('toggle-password');
  toggle.addEventListener('click', () => {
    const show = password.type === 'password';
    password.type = show ? 'text' : 'password';
    toggle.textContent = show ? 'Hide' : 'Show';
    toggle.setAttribute('aria-pressed', String(show));
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    UI.clearErrors(form);

    // Browser checks from Table 4.7.5 before anything is sent.
    if (!email.value.trim()) UI.fieldError(email, 'Enter your email');
    else if (!email.checkValidity()) UI.fieldError(email, 'Enter a valid email address');
    if (!password.value) UI.fieldError(password, 'Enter your password');
    if (UI.focusFirstError(form)) return;

    UI.setBusy(button, true, 'Logging in');
    try {
      const user = await Auth.login(email.value, password.value);
      window.location.href = Auth.dashboardFor(user.role);
    } catch (err) {
      UI.setBusy(button, false);
      // The same message for an unknown email and a wrong password (4.6.3).
      UI.formAlert(form, err.message);
      if (err.status === 401) password.select();
    }
  });
});
