/* Create an account (registerUser, 4.6.2). Checks and messages follow Table 4.7.4.
   Coordinators cannot register here; they are created on the coordinator Users page. */
document.addEventListener('DOMContentLoaded', () => {
  if (Auth.redirectIfLoggedIn()) return;

  const form = document.getElementById('register-form');
  const roleFields = document.getElementById('role-fields');
  const button = document.getElementById('create-button');
  const employerSelect = document.getElementById('employerId');
  const f = form.elements;
  let employersLoaded = false;
  document.getElementById('brand').insertAdjacentHTML('afterbegin', UI.LOGO);

  const NAME = /^[A-Za-z][A-Za-z ']{0,49}$/;          // 1 to 50 letters; spaces allowed
  const PHONE = /^0\d{9}$/;                            // 10 digits starting with 0
  const role = () => (form.querySelector('input[name="role"]:checked') || {}).value || '';

  // Shows only the fields of the chosen role.
  function showRole() {
    const current = role();
    roleFields.classList.toggle('hidden', !current);
    form.querySelectorAll('[data-for]').forEach((el) => el.classList.toggle('hidden', el.dataset.for !== current));
    // An employer gives the name of the contact person.
    document.getElementById('first-label').textContent = current === 'EMPLOYER' ? 'Contact person first name' : 'First name';
    document.getElementById('last-label').textContent = current === 'EMPLOYER' ? 'Contact person last name' : 'Last name';
    UI.clearErrors(form);
    if (current === 'SUPERVISOR' && !employersLoaded) loadEmployers();
  }

  // Approved employers for the supervisor list.
  async function loadEmployers() {
    employerSelect.innerHTML = '<option value="">Loading employers</option>';
    try {
      const list = await API.getRegisterEmployers();
      employersLoaded = true;
      employerSelect.innerHTML = '<option value="">Select your employer</option>' +
        list.map((e) => `<option value="${e.employerId}">${UI.esc(e.employerName)}</option>`).join('');
    } catch (err) {
      employerSelect.innerHTML = '<option value="">Employers could not load</option>';
      UI.fieldError(employerSelect, `${err.message} Choose Student or Employer, or try again later.`);
    }
  }

  form.addEventListener('change', (event) => { if (event.target.name === 'role') showRole(); });
  // Digits only in the number fields.
  ['studentNumber', 'phone', 'pin', 'contactNumber'].forEach((name) => {
    f[name].addEventListener('input', () => { f[name].value = f[name].value.replace(/\D/g, ''); });
  });

  // ---------- Browser checks from Table 4.7.4 ----------
  function validate() {
    UI.clearErrors(form);
    const current = role();
    if (!current) {
      UI.formAlert(form, 'Select your role');
      form.querySelector('input[name="role"]').focus();
      return false;
    }
    if (!NAME.test(f.firstName.value.trim())) UI.fieldError(f.firstName, 'Enter your first name');
    if (!NAME.test(f.lastName.value.trim())) UI.fieldError(f.lastName, 'Enter your last name');

    if (current === 'STUDENT' && !/^\d{6,10}$/.test(f.studentNumber.value)) UI.fieldError(f.studentNumber, 'Enter a valid student number');
    if (current === 'SUPERVISOR') {
      if (!f.employerId.value) UI.fieldError(f.employerId, 'Select your employer');
      if (!PHONE.test(f.phone.value)) UI.fieldError(f.phone, 'Enter a valid phone number');
      if (!/^\d{4,6}$/.test(f.pin.value)) UI.fieldError(f.pin, 'The PIN must be 4 to 6 digits');
    }
    if (current === 'EMPLOYER') {
      const name = f.employerName.value.trim();
      const address = f.address.value.trim();
      if (name.length < 2 || name.length > 100) UI.fieldError(f.employerName, 'Enter the employer name');
      if (address.length < 5 || address.length > 200) UI.fieldError(f.address, 'Enter the address');
      if (!f.contactEmail.value.trim() || !f.contactEmail.checkValidity()) UI.fieldError(f.contactEmail, 'Enter a valid email');
      if (!PHONE.test(f.contactNumber.value)) UI.fieldError(f.contactNumber, 'Enter a valid phone number');
    }

    if (!f.email.value.trim() || !f.email.checkValidity()) UI.fieldError(f.email, 'Enter a valid email address');
    const pw = f.password.value;
    if (pw.length < API.RULES.MIN_PASSWORD_LENGTH || !/[A-Za-z]/.test(pw) || !/\d/.test(pw)) {
      UI.fieldError(f.password, 'Password must have at least 8 characters, a letter and a number');
    }
    if (f.confirm.value !== pw) UI.fieldError(f.confirm, 'Passwords do not match');
    return !UI.focusFirstError(form);
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!validate()) return;

    const current = role();
    const details = {
      role: current,
      email: f.email.value.trim(),
      password: f.password.value,
      firstName: f.firstName.value.trim(),
      lastName: f.lastName.value.trim()
    };
    if (current === 'STUDENT') details.studentNumber = f.studentNumber.value;
    if (current === 'SUPERVISOR') Object.assign(details, { employerId: Number(f.employerId.value), phone: f.phone.value, pin: f.pin.value });
    if (current === 'EMPLOYER') {
      Object.assign(details, {
        employerName: f.employerName.value.trim(), address: f.address.value.trim(),
        contactEmail: f.contactEmail.value.trim(), contactNumber: f.contactNumber.value
      });
    }

    UI.setBusy(button, true, 'Creating account');
    try {
      await API.registerAccount(details);
      window.location.href = `login.html?registered=${current === 'EMPLOYER' ? 'employer' : '1'}`;
    } catch (err) {
      UI.setBusy(button, false);
      if (err.status === 409 && /email/i.test(err.message)) UI.fieldError(f.email, 'This email is already registered');
      else if (err.status === 409 && /student number/i.test(err.message)) UI.fieldError(f.studentNumber, 'Enter a valid student number');
      else UI.formAlert(form, err.message);
      UI.focusFirstError(form);
    }
  });
});
