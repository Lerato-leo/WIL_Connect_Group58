/* Users (registerUser, 4.6.2): the coordinator creates accounts for other coordinators.
   Checks follow Table 4.7.4 of the Phase 4 document. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('COORDINATOR');
  if (!user) return;
  Layout.mount(user);

  const form = document.getElementById('user-form');
  const button = document.getElementById('create-button');
  const NAME = /^[A-Za-z][A-Za-z ']{0,49}$/;

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    UI.clearErrors(form);
    const f = form;
    if (!NAME.test(f.firstName.value.trim())) UI.fieldError(f.firstName, 'Enter your first name');
    if (!NAME.test(f.lastName.value.trim())) UI.fieldError(f.lastName, 'Enter your last name');
    if (!f.email.value.trim() || !f.email.checkValidity()) UI.fieldError(f.email, 'Enter a valid email address');
    const pw = f.password.value;
    if (pw.length < API.RULES.MIN_PASSWORD_LENGTH || !/[A-Za-z]/.test(pw) || !/\d/.test(pw)) {
      UI.fieldError(f.password, 'Password must have at least 8 characters, a letter and a number');
    }
    if (f.confirm.value !== pw) UI.fieldError(f.confirm, 'Passwords do not match');
    if (UI.focusFirstError(f)) return;

    UI.setBusy(button, true, 'Creating');
    try {
      await API.registerUser({
        email: f.email.value.trim(),
        password: pw,
        role: 'COORDINATOR',
        name: `${f.firstName.value.trim()} ${f.lastName.value.trim()}`
      });
      UI.toast('Account created. The new coordinator can now log in');
      form.reset();
    } catch (err) {
      if (err.status === 409) UI.fieldError(f.email, 'This email is already registered');
      else UI.formAlert(f, err.message);
    } finally {
      UI.setBusy(button, false);
    }
  });
});
