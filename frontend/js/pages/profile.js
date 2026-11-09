/* Profile: the details of the logged in account. Employers edit company details on their dashboard. */
document.addEventListener('DOMContentLoaded', () => {
  const user = Auth.requireRole('STUDENT', 'SUPERVISOR', 'EMPLOYER', 'COORDINATOR');
  if (!user) return;
  Layout.mount(user);

  const el = document.getElementById('profile');
  const extra = {
    STUDENT: '<a class="btn btn-sm" href="requests.html">My Requests</a>',
    SUPERVISOR: '<a class="btn btn-sm" href="supervisor-pending.html#students">My Students</a>',
    EMPLOYER: '<a class="btn btn-sm" href="employer-requests.html#company">Edit company details</a>',
    COORDINATOR: '<a class="btn btn-sm" href="coordinator-users.html">Users</a>'
  };

  UI.done(el, `
    <div class="row" style="margin-bottom:20px">
      <span class="avatar" style="width:44px;height:44px;font-size:14px" aria-hidden="true">${UI.esc(UI.initials(user.name))}</span>
      <div><h2 style="font-size:var(--fs-lg)">${UI.esc(user.name)}</h2><p class="small muted">${UI.esc(Auth.roleLabel(user.role))}</p></div>
    </div>
    <dl class="detail-list">
      <div><dt>Name</dt><dd>${UI.esc(user.name)}</dd></div>
      <div><dt>Email</dt><dd>${UI.esc(user.email)}</dd></div>
      <div><dt>Role</dt><dd>${UI.esc(Auth.roleLabel(user.role))}</dd></div>
    </dl>
    <p class="small muted" style="margin:20px 0 12px">To change your name or email, contact your WIL coordinator. For your safety, you are logged out after 30 minutes without activity.</p>
    <div class="row">${extra[user.role] || ''}</div>`);
});
