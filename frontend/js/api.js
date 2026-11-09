/* ==========================================================
   WIL Connect: api.js
   This one file holds every backend path and every fetch call.
   Pages only call the functions in window.API. They never write
   a URL themselves.

   Every path below is served by the Spring Boot backend
   (C2 to C7, brought together on fix/backend-integration).
   ========================================================== */

// true: every function returns the sample data at the bottom of this file.
// false: every function calls the Spring Boot backend.
// (A test page can set window.WIL_USE_FAKE_DATA before this file loads.)
const USE_FAKE_DATA = window.WIL_USE_FAKE_DATA ?? true;

// Address of the Spring Boot backend. Use '' when the pages are served by the backend itself.
const API_BASE_URL = window.WIL_API_BASE_URL ?? 'http://localhost:8080';

// Fixed values from Table 4.6.2 of the Phase 4 document.
const RULES = {
  REQUIRED_HOURS: 150,
  MIN_HOURS_PER_LOG: 0.5,
  MAX_HOURS_PER_LOG: 12,
  TASK_MIN: 10,
  TASK_MAX: 1000,
  SKILLS_MAX: 500,
  MAX_FILES_PER_LOG: 3,
  MAX_FILE_BYTES: 5 * 1024 * 1024,
  MAX_FILE_LABEL: '5 MB',
  FILE_TYPES: ['image/jpeg', 'image/png', 'application/pdf'],
  FILE_EXTENSIONS: ['.jpg', '.jpeg', '.png', '.pdf'],
  FILE_LABEL: 'JPG, PNG or PDF',
  SIGN_OFF_WINDOW_HOURS: 24,
  REJECT_MIN: 5,
  REJECT_MAX: 300,
  SEARCH_MAX: 50,
  MIN_PASSWORD_LENGTH: 8
};
const REQUIRED_HOURS = RULES.REQUIRED_HOURS;

// The logged in user is kept in sessionStorage under this key (see auth.js).
const SESSION_KEY = 'wil_session';

// ---------- Endpoint paths ----------
const PATHS = {
  // C2 login and register (public)
  LOGIN: '/api/auth/login',
  REGISTER: '/api/auth/register',
  // Approved employers for the supervisor sign up list
  REGISTER_EMPLOYERS: '/api/auth/employers',

  // C3 employer search, placement requests and placements
  EMPLOYER_SEARCH: '/api/students/employers',
  STUDENT_REQUESTS: '/api/students/placement-requests',
  EMPLOYER_REQUESTS: '/api/employers/placement-requests',
  ACCEPT_REQUEST: (requestId) => `/api/employers/placement-requests/${requestId}/accept`,
  REJECT_REQUEST: (requestId) => `/api/employers/placement-requests/${requestId}/reject`,
  MEETING_REQUEST: (requestId) => `/api/employers/placement-requests/${requestId}/meeting`,
  STUDENT_PLACEMENTS: '/api/students/placements',
  EMPLOYER_PLACEMENTS: '/api/employers/placements',

  // C6 attendance and notifications
  ATTENDANCE: (placementId) => `/api/placements/${placementId}/attendance`,
  NOTIFICATIONS: '/api/notifications',
  NOTIFICATION_READ: (notificationId) => `/api/notifications/${notificationId}/read`,

  // C7 coordinator and the compliance report
  STUDENT_PROGRESS: '/api/coordinator/student-progress',
  COORDINATOR_EMPLOYERS: '/api/coordinator/employers',
  APPROVE_EMPLOYER: (employerId) => `/api/coordinator/employers/${employerId}/approve`,
  DEACTIVATE_EMPLOYER: (employerId) => `/api/coordinator/employers/${employerId}/deactivate`,
  COMPLIANCE_REPORT: (placementId) => `/api/placements/${placementId}/compliance-report`,
  PLACEMENT_LOGS: (placementId) => `/api/placements/${placementId}/duty-logs`,
  SIGN_OFF_AUDIT: '/api/coordinator/sign-offs',

  // C4 duty logs (multipart form data) and proof files
  MY_DUTY_LOGS: '/api/students/duty-logs',
  DUTY_LOG: (logId) => `/api/students/duty-logs/${logId}`,
  PROOF_FILE: (proofId) => `/api/proofs/${proofId}/file`,

  // C5 supervisor review and sign off
  SUPERVISOR_PENDING: '/api/supervisors/duty-logs',
  SUPERVISOR_LOG: (logId) => `/api/supervisors/duty-logs/${logId}`,
  SIGN_OFF: (logId) => `/api/supervisors/duty-logs/${logId}/sign-off`,
  REJECT_LOG: (logId) => `/api/supervisors/duty-logs/${logId}/reject`,
  SIGN_OFF_HISTORY: '/api/supervisors/sign-offs',
  SUPERVISOR_STUDENTS: '/api/supervisors/placements',

  // Employer company details and supervisors
  EMPLOYER_PROFILE: '/api/employers/me',
  EMPLOYER_SUPERVISORS: '/api/employers/supervisors'
};

// ---------- Error type used by every function ----------
class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

// Plain messages for HTTP status codes when the server sends none.
function messageForStatus(status) {
  if (status === 400) return 'Some of the details are not valid. Check the form and try again.';
  if (status === 401) return 'Your session has ended. Please log in again.';
  if (status === 403) return 'You do not have access to this page.';
  if (status === 404) return 'This item could not be found.';
  if (status === 409) return 'This clashes with something that already exists.';
  return 'The server had a problem. Please try again.';
}

// Reads the saved session (written by auth.js).
function readSession() {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY)) || null;
  } catch (error) {
    return null;
  }
}

// The backend uses HTTP Basic login, so each request sends email and password.
function basicToken(email, password) {
  const bytes = new TextEncoder().encode(`${email}:${password}`);
  let binary = '';
  bytes.forEach((b) => { binary += String.fromCharCode(b); });
  return btoa(binary);
}

// ---------- The one place that calls fetch ----------
async function request(path, options = {}) {
  const { method = 'GET', body, query, asBlob = false, skipAuth = false } = options;

  const url = new URL(API_BASE_URL + path, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    });
  }

  const headers = { Accept: 'application/json' };
  const session = readSession();
  if (!skipAuth && session && session.token) headers.Authorization = `Basic ${session.token}`;

  let payload = body;
  if (body && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(url, { method, headers, body: payload });
  } catch (error) {
    throw new ApiError(`The server could not be reached. Check that the backend is running at ${API_BASE_URL}.`, 0);
  }

  if (!response.ok) {
    let message = messageForStatus(response.status);
    try {
      const text = await response.text();
      const data = text ? JSON.parse(text) : null;
      if (data && data.message) message = data.message;
    } catch (error) {
      // The body was not JSON. Keep the plain message.
    }
    // Tell auth.js the login has expired, except on the login call itself.
    if (response.status === 401 && path !== PATHS.LOGIN) {
      window.dispatchEvent(new CustomEvent('wil:unauthorized'));
    }
    throw new ApiError(message, response.status);
  }

  if (asBlob) return response.blob();
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

/* ==========================================================
   Functions used by the pages (module names from section 4.6)
   Each one returns fake data when USE_FAKE_DATA is true.
   ========================================================== */

// ----- login (4.6.3) -----
async function login(email, password) {
  if (USE_FAKE_DATA) return Fake.login(email, password);
  try {
    return await request(PATHS.LOGIN, { method: 'POST', body: { email, password }, skipAuth: true });
  } catch (error) {
    if (error.status === 401) throw new ApiError('Incorrect email or password', 401);
    throw error;
  }
}

// ----- registerUser (4.6.2), used by the coordinator Users page -----
// details: { email, password, role }. Sent with the coordinator's login, because
// only a logged in coordinator may create another coordinator.
async function registerUser(details) {
  if (USE_FAKE_DATA) return Fake.registerUser(details);
  const { email, password, role } = details;
  return request(PATHS.REGISTER, { method: 'POST', body: { email, password, role } });
}

// ----- registerUser (4.6.2), the public Create an account page -----
// details: role, email, password, firstName, lastName and the fields of that role:
//   Student: studentNumber
//   Supervisor: employerId, phone, pin
//   Employer: employerName, address, contactEmail, contactNumber
// The backend saves the account and the STUDENT, SUPERVISOR or EMPLOYER row together.
async function registerAccount(details) {
  if (USE_FAKE_DATA) return Fake.registerAccount(details);
  return request(PATHS.REGISTER, { method: 'POST', body: details, skipAuth: true });
}

// Approved employers a new supervisor can choose from (Table 4.7.4).
async function getRegisterEmployers() {
  if (USE_FAKE_DATA) return Fake.getRegisterEmployers();
  return (await request(PATHS.REGISTER_EMPLOYERS, { skipAuth: true })) || [];
}

// ----- searchEmployers, sendPlacementRequest, respondToRequest (4.6.4) -----
async function searchEmployers(search) {
  if (USE_FAKE_DATA) return Fake.searchEmployers(search);
  return (await request(PATHS.EMPLOYER_SEARCH, { query: { search } })) || [];
}

async function sendPlacementRequest(employerId) {
  if (USE_FAKE_DATA) return Fake.sendPlacementRequest(employerId);
  return request(PATHS.STUDENT_REQUESTS, { method: 'POST', body: { employerId } });
}

async function getMyPlacementRequests() {
  if (USE_FAKE_DATA) return Fake.getMyPlacementRequests();
  return (await request(PATHS.STUDENT_REQUESTS)) || [];
}

async function getMyPlacements() {
  if (USE_FAKE_DATA) return Fake.getMyPlacements();
  return (await request(PATHS.STUDENT_PLACEMENTS)) || [];
}

async function getEmployerPlacementRequests(status) {
  if (USE_FAKE_DATA) return Fake.getEmployerPlacementRequests(status);
  return (await request(PATHS.EMPLOYER_REQUESTS, { query: { status } })) || [];
}

// details: { supervisorId, startDate, endDate }
async function acceptPlacementRequest(requestId, details) {
  if (USE_FAKE_DATA) return Fake.acceptPlacementRequest(requestId, details);
  return request(PATHS.ACCEPT_REQUEST(requestId), { method: 'PUT', body: details });
}

async function rejectPlacementRequest(requestId) {
  if (USE_FAKE_DATA) return Fake.rejectPlacementRequest(requestId);
  return request(PATHS.REJECT_REQUEST(requestId), { method: 'PUT' });
}

async function requestMeeting(requestId, message) {
  if (USE_FAKE_DATA) return Fake.requestMeeting(requestId, message);
  return request(PATHS.MEETING_REQUEST(requestId), { method: 'PUT', body: { message } });
}

async function getEmployerPlacements() {
  if (USE_FAKE_DATA) return Fake.getEmployerPlacements();
  return (await request(PATHS.EMPLOYER_PLACEMENTS)) || [];
}

// ----- Employer details and supervisors (guessed) -----
async function getEmployerProfile() {
  if (USE_FAKE_DATA) return Fake.getEmployerProfile();
  return request(PATHS.EMPLOYER_PROFILE);
}

// details: { employerName, address, contactEmail, contactNumber }
async function updateEmployerProfile(details) {
  if (USE_FAKE_DATA) return Fake.updateEmployerProfile(details);
  return request(PATHS.EMPLOYER_PROFILE, { method: 'PUT', body: details });
}

async function getEmployerSupervisors() {
  if (USE_FAKE_DATA) return Fake.getEmployerSupervisors();
  return (await request(PATHS.EMPLOYER_SUPERVISORS)) || [];
}

// ----- calculateAttendance (4.6.7) and notifications (4.6.8) -----
async function calculateAttendance(placementId) {
  if (USE_FAKE_DATA) return Fake.calculateAttendance(placementId);
  return request(PATHS.ATTENDANCE(placementId));
}

async function getNotifications() {
  if (USE_FAKE_DATA) return Fake.getNotifications();
  return (await request(PATHS.NOTIFICATIONS)) || { items: [], unreadCount: 0 };
}

async function markNotificationRead(notificationId) {
  if (USE_FAKE_DATA) return Fake.markNotificationRead(notificationId);
  return request(PATHS.NOTIFICATION_READ(notificationId), { method: 'PATCH' });
}

// ----- submitDutyLog (4.6.5) -----
async function getMyDutyLogs() {
  if (USE_FAKE_DATA) return Fake.getMyDutyLogs();
  return (await request(PATHS.MY_DUTY_LOGS)) || [];
}

// formData fields: logDate, hoursWorked, taskDescription, skillsLearnt, proof (up to 3 files)
async function submitDutyLog(formData) {
  if (USE_FAKE_DATA) return Fake.submitDutyLog(null, formData);
  return request(PATHS.MY_DUTY_LOGS, { method: 'POST', body: formData });
}

// Corrects a pending or rejected log, or a signed log within 24 hours of sign off.
async function correctDutyLog(logId, formData) {
  if (USE_FAKE_DATA) return Fake.submitDutyLog(logId, formData);
  return request(PATHS.DUTY_LOG(logId), { method: 'PUT', body: formData });
}

// Returns the proof file as a Blob so it can be shown or downloaded.
async function getProofFile(proofId) {
  if (USE_FAKE_DATA) return Fake.getProofFile(proofId);
  return request(PATHS.PROOF_FILE(proofId), { asBlob: true });
}

// ----- signOffLog (4.6.6) -----
async function getPendingLogs() {
  if (USE_FAKE_DATA) return Fake.getPendingLogs();
  return (await request(PATHS.SUPERVISOR_PENDING, { query: { status: 'Pending' } })) || [];
}

async function getLogForReview(logId) {
  if (USE_FAKE_DATA) return Fake.getLogForReview(logId);
  return request(PATHS.SUPERVISOR_LOG(logId));
}

// details: { verificationMethod: 'PIN' | 'Signature', pin, signatureData }
async function signOffLog(logId, details) {
  if (USE_FAKE_DATA) return Fake.signOffLog(logId, details);
  return request(PATHS.SIGN_OFF(logId), { method: 'POST', body: details });
}

async function rejectLog(logId, supervisorComment) {
  if (USE_FAKE_DATA) return Fake.rejectLog(logId, supervisorComment);
  return request(PATHS.REJECT_LOG(logId), { method: 'PUT', body: { supervisorComment } });
}

async function getSignOffHistory() {
  if (USE_FAKE_DATA) return Fake.getSignOffHistory();
  return (await request(PATHS.SIGN_OFF_HISTORY)) || [];
}

async function getSupervisorStudents() {
  if (USE_FAKE_DATA) return Fake.getSupervisorStudents();
  return (await request(PATHS.SUPERVISOR_STUDENTS)) || [];
}

// ----- Coordinator (C7) and generateComplianceReport (4.6.9) -----
async function getStudentProgress() {
  if (USE_FAKE_DATA) return Fake.getStudentProgress();
  return (await request(PATHS.STUDENT_PROGRESS)) || [];
}

async function getCoordinatorEmployers() {
  if (USE_FAKE_DATA) return Fake.getCoordinatorEmployers();
  return (await request(PATHS.COORDINATOR_EMPLOYERS)) || [];
}

async function approveEmployer(employerId) {
  if (USE_FAKE_DATA) return Fake.setEmployerStatus(employerId, 'Pending', 'Approved');
  return request(PATHS.APPROVE_EMPLOYER(employerId), { method: 'PATCH' });
}

async function deactivateEmployer(employerId) {
  if (USE_FAKE_DATA) return Fake.setEmployerStatus(employerId, 'Approved', 'Inactive');
  return request(PATHS.DEACTIVATE_EMPLOYER(employerId), { method: 'PATCH' });
}

async function getPlacementLogs(placementId) {
  if (USE_FAKE_DATA) return Fake.getPlacementLogs(placementId);
  return (await request(PATHS.PLACEMENT_LOGS(placementId))) || [];
}

async function getSignOffAudit() {
  if (USE_FAKE_DATA) return Fake.getSignOffAudit();
  return (await request(PATHS.SIGN_OFF_AUDIT)) || [];
}

// Returns the compliance report PDF as a Blob.
async function generateComplianceReport(placementId) {
  if (USE_FAKE_DATA) return null; // the report page uses the browser's print to PDF instead
  return request(PATHS.COMPLIANCE_REPORT(placementId), { asBlob: true });
}

/* ==========================================================
   Fake backend
   Sample data that matches the seed file and the real response
   shapes. Changes are kept in sessionStorage, so a log sent by
   the student shows up for the supervisor in the same tab.
   ========================================================== */
const Fake = (() => {
  const DB_KEY = 'wil_fake_db_v2';
  const HOUR = 60 * 60 * 1000;
  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  // Small pause so loading states are visible in the demo.
  const pause = () => new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 250));
  const copy = (value) => JSON.parse(JSON.stringify(value));
  const fail = (message, status) => { throw new ApiError(message, status); };
  const today = () => toDateString(new Date());
  const longDate = (iso) => { const [y, m, d] = iso.split('-').map(Number); return `${d} ${MONTHS[m - 1]} ${y}`; };

  function toDateString(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  // Weekdays from a start date, used to build sample logs.
  function weekdays(start, count) {
    const out = [];
    const day = new Date(`${start}T12:00:00`);
    while (out.length < count) {
      if (day.getDay() !== 0 && day.getDay() !== 6) out.push(toDateString(day));
      day.setDate(day.getDate() + 1);
    }
    return out;
  }

  // The day after a log date at a given time, for sample sign off times.
  function nextDayAt(logDate, time) {
    const d = new Date(`${logDate}T12:00:00`);
    d.setDate(d.getDate() + 1);
    return `${toDateString(d)}T${time}`;
  }

  function seed() {
    const now = Date.now();
    const db = {
      users: [
        { userId: 1, email: 'coordinator@richfield.co.za', role: 'COORDINATOR', name: 'Rene Maseko' },
        { userId: 2, email: 'john.smith@richfield.co.za', role: 'STUDENT', name: 'John Smith', studentId: 1 },
        { userId: 3, email: 'sarah.jones@richfield.co.za', role: 'STUDENT', name: 'Sarah Jones', studentId: 2 },
        { userId: 4, email: 'michael.brown@richfield.co.za', role: 'SUPERVISOR', name: 'Michael Brown', supervisorId: 1 },
        { userId: 5, email: 'linda.davis@richfield.co.za', role: 'SUPERVISOR', name: 'Linda Davis', supervisorId: 2 },
        { userId: 6, email: 'hr.nexus@richfield.co.za', role: 'EMPLOYER', name: 'Nexus Tech Solutions', employerId: 1 },
        { userId: 7, email: 'hr.brightworks@richfield.co.za', role: 'EMPLOYER', name: 'BrightWorks Solutions', employerId: 2 }
      ],
      employers: [
        { employerId: 1, employerName: 'Nexus Tech Solutions', address: '12 Rivonia Road, Sandton, Johannesburg', contactEmail: 'hr@nexustech.co.za', contactNumber: '0115550100', status: 'Approved' },
        { employerId: 2, employerName: 'BrightWorks Solutions', address: '45 Loop Street, Cape Town', contactEmail: 'hr@brightworks.co.za', contactNumber: '0215550199', status: 'Pending' },
        { employerId: 3, employerName: 'Table Bay Software', address: '8 Dock Road, Waterfront, Cape Town', contactEmail: 'careers@tablebaysoft.co.za', contactNumber: '0215550142', status: 'Approved' },
        { employerId: 4, employerName: 'Umhlanga Digital', address: '21 Lighthouse Road, Umhlanga, Durban', contactEmail: 'people@umhlangadigital.co.za', contactNumber: '0315550177', status: 'Approved' },
        { employerId: 5, employerName: 'Highveld Data Systems', address: '3 Church Street, Pretoria', contactEmail: 'wil@highvelddata.co.za', contactNumber: '0125550123', status: 'Approved' },
        { employerId: 6, employerName: 'Mzansi Analytics', address: '90 Main Road, Rondebosch, Cape Town', contactEmail: 'hello@mzansianalytics.co.za', contactNumber: '0215550161', status: 'Pending' }
      ],
      students: [
        { studentId: 1, studentNumber: '20240123', firstName: 'John', lastName: 'Smith' },
        { studentId: 2, studentNumber: '20240187', firstName: 'Sarah', lastName: 'Jones' },
        { studentId: 3, studentNumber: '20240211', firstName: 'Kabelo', lastName: 'Mahlangu' },
        { studentId: 4, studentNumber: '20240302', firstName: 'Ayanda', lastName: 'Zulu' },
        { studentId: 5, studentNumber: '20240355', firstName: 'Thabo', lastName: 'Nkosi' }
      ],
      supervisors: [
        { supervisorId: 1, firstName: 'Michael', lastName: 'Brown', phone: '0821234567', employerId: 1, pin: '1234' },
        { supervisorId: 2, firstName: 'Linda', lastName: 'Davis', phone: '0837654321', employerId: 1, pin: '1234' }
      ],
      requests: [
        { requestId: 1, studentId: 1, employerId: 1, requestDate: '2026-08-10', status: 'Accepted' },
        { requestId: 2, studentId: 2, employerId: 1, requestDate: '2026-10-20', status: 'Meeting Requested' },
        { requestId: 3, studentId: 1, employerId: 2, requestDate: '2026-08-05', status: 'Rejected' },
        { requestId: 4, studentId: 3, employerId: 1, requestDate: '2026-08-12', status: 'Accepted' },
        { requestId: 5, studentId: 4, employerId: 1, requestDate: '2026-09-15', status: 'Accepted' },
        { requestId: 6, studentId: 5, employerId: 1, requestDate: '2026-11-05', status: 'Pending' }
      ],
      placements: [
        { placementId: 1, studentId: 1, employerId: 1, supervisorId: 1, startDate: '2026-09-01', endDate: '2026-11-30', status: 'Active' },
        { placementId: 2, studentId: 3, employerId: 1, supervisorId: 2, startDate: '2026-08-17', endDate: '2026-11-30', status: 'Active' },
        { placementId: 3, studentId: 4, employerId: 1, supervisorId: 1, startDate: '2026-10-01', endDate: '2026-12-18', status: 'Active' }
      ],
      logs: [],
      signOffs: [],
      notifications: [],
      nextId: { log: 1000, proof: 1000, request: 10, placement: 10, notification: 20, signOff: 1000, user: 20, student: 20 }
    };

    // Adds a sign off row (the SIGN_OFF table never changes old rows).
    const addSignOff = (log, supervisorId, signoffDate, method) => {
      db.signOffs.push({ signoffId: db.nextId.signOff++, logId: log.logId, supervisorId, signoffDate, verificationMethod: method });
    };

    // John Smith: 12 signed days of 8 hours (96 hours), 1 rejected, 2 pending (15 hours).
    const johnTasks = [
      ['2026-09-01', 'Orientation and system access set up', 'Company onboarding and development environment setup'],
      ['2026-09-02', 'Reviewed the database design document', 'Relational modelling and normalisation review'],
      ['2026-09-03', 'Wrote SQL queries for customer reports', 'SQL joins and aggregate functions'],
      ['2026-09-04', 'Fixed data entry validation errors', 'Form validation and error handling'],
      ['2026-09-07', 'Tested the report export function', 'Functional testing and test documentation'],
      ['2026-09-08', 'Added indexes to slow database tables', 'Query performance and indexing'],
      ['2026-09-09', 'Wrote test cases for the login page', 'Test case design and boundary testing'],
      ['2026-09-10', 'Fixed bugs found in testing', 'Debugging and defect resolution'],
      ['2026-10-21', 'Updated the user guide for the reporting tool', 'Technical writing and version control'],
      ['2026-10-22', 'Reviewed code changes with the team lead', 'Code review and team collaboration'],
      ['2026-10-23', 'Wrote 10 new test cases', 'Test coverage and regression planning'],
      ['2026-10-27', 'Fixed errors found in system testing', 'System testing and bug fixing']
    ];
    const proofFor = { 0: ['site_photo.jpg', 'image/jpeg'], 4: ['timesheet.pdf', 'application/pdf'], 11: ['test_report.pdf', 'application/pdf'] };

    johnTasks.forEach(([logDate, taskDescription, skillsLearnt], i) => {
      const log = {
        logId: i + 1, placementId: 1, logDate, hoursWorked: 8, taskDescription, skillsLearnt,
        status: 'Signed', supervisorComment: null, submittedAt: `${logDate}T16:20:00`,
        proofs: proofFor[i] ? [{ proofId: i + 1, fileName: proofFor[i][0], fileType: proofFor[i][1], uploadDate: `${logDate}T16:40:00` }] : []
      };
      db.logs.push(log);
      const method = i % 2 ? 'Signature' : 'PIN';
      if (i === 9) {
        // This log was corrected once, so it has two sign off rows.
        addSignOff(log, 1, nextDayAt(logDate, '08:44:00'), 'PIN');
        addSignOff(log, 1, nextDayAt(logDate, '15:10:00'), 'Signature');
      } else if (i === johnTasks.length - 1) {
        // Signed 3 hours ago, so the 24 hour correction can be shown.
        addSignOff(log, 1, new Date(now - 3 * HOUR).toISOString(), method);
      } else {
        addSignOff(log, 1, nextDayAt(logDate, `0${8 + (i % 2)}:${String(10 + i * 3).padStart(2, '0')}:00`), method);
      }
    });

    db.logs.push(
      { logId: 13, placementId: 1, logDate: '2026-10-28', hoursWorked: 8, taskDescription: 'Attended the weekly project stand up', skillsLearnt: 'Agile ceremonies and progress reporting', status: 'Rejected', supervisorComment: 'Please attach the timesheet as proof for this day.', submittedAt: '2026-10-28T16:40:00', rejectedAt: '2026-10-29T09:15:00', proofs: [] },
      { logId: 14, placementId: 1, logDate: '2026-10-29', hoursWorked: 7.5, taskDescription: 'Updated the customer database schema, wrote 12 test queries and attended the team stand up meeting.', skillsLearnt: 'SQL indexing and writing test cases.', status: 'Pending', supervisorComment: null, submittedAt: '2026-10-29T17:20:00', proofs: [{ proofId: 20, fileName: 'site_photo.jpg', fileType: 'image/jpeg', uploadDate: '2026-10-29T17:20:00' }, { proofId: 21, fileName: 'timesheet.pdf', fileType: 'application/pdf', uploadDate: '2026-10-29T17:21:00' }] },
      { logId: 15, placementId: 1, logDate: '2026-10-30', hoursWorked: 7.5, taskDescription: 'Built the monthly sales summary query and checked the totals with finance.', skillsLearnt: 'Window functions and working with business users', status: 'Pending', supervisorComment: null, submittedAt: '2026-10-30T17:05:00', proofs: [{ proofId: 22, fileName: 'query_results.png', fileType: 'image/png', uploadDate: '2026-10-30T17:05:00' }] }
    );

    // Kabelo Mahlangu: 19 days of 8 hours (152 hours, so the placement is complete).
    weekdays('2026-08-17', 19).forEach((logDate, i) => {
      const log = { logId: 200 + i, placementId: 2, logDate, hoursWorked: 8, taskDescription: 'Supported the help desk and logged user tickets', skillsLearnt: 'Customer support and ticket tracking', status: 'Signed', supervisorComment: null, submittedAt: `${logDate}T16:00:00`, proofs: [] };
      db.logs.push(log);
      addSignOff(log, 2, nextDayAt(logDate, '08:30:00'), 'PIN');
    });

    // Ayanda Zulu: 5 signed days of 8 hours (40 hours) and 1 pending.
    weekdays('2026-10-01', 6).forEach((logDate, i) => {
      const pending = i === 5;
      const log = { logId: 300 + i, placementId: 3, logDate, hoursWorked: 8, taskDescription: pending ? 'Set up test data for the new payroll module' : 'Wrote unit tests for the payroll module', skillsLearnt: 'Unit testing and test data design', status: pending ? 'Pending' : 'Signed', supervisorComment: null, submittedAt: `${logDate}T16:10:00`, proofs: [] };
      db.logs.push(log);
      if (!pending) addSignOff(log, 1, nextDayAt(logDate, '09:05:00'), 'Signature');
    });

    // Messages follow Table 4.7.14.
    db.notifications = [
      { notificationId: 1, userId: 4, message: 'A new log from John Smith needs sign off.', read: false, createdAt: '2026-10-30T17:06:00' },
      { notificationId: 2, userId: 4, message: 'A new log from John Smith needs sign off.', read: false, createdAt: '2026-10-29T17:22:00' },
      { notificationId: 3, userId: 4, message: 'A new log from Ayanda Zulu needs sign off.', read: true, createdAt: '2026-10-08T16:11:00' },
      { notificationId: 4, userId: 2, message: 'Your log for 27 October 2026 was signed off.', read: false, createdAt: new Date(now - 3 * HOUR).toISOString() },
      { notificationId: 5, userId: 2, message: 'Your log for 28 October 2026 was rejected: Please attach the timesheet as proof for this day.', read: false, createdAt: '2026-10-29T09:15:00' },
      { notificationId: 6, userId: 2, message: 'Your placement request was updated: Accepted.', read: true, createdAt: '2026-09-01T10:00:00' },
      { notificationId: 7, userId: 6, message: 'New placement request from Thabo Nkosi.', read: false, createdAt: '2026-11-05T10:12:00' },
      { notificationId: 8, userId: 6, message: 'New placement request from Sarah Jones.', read: true, createdAt: '2026-10-20T11:05:00' },
      { notificationId: 9, userId: 1, message: 'BrightWorks Solutions is waiting for approval.', read: false, createdAt: '2026-11-02T09:00:00' },
      { notificationId: 10, userId: 1, message: 'Mzansi Analytics is waiting for approval.', read: false, createdAt: '2026-11-04T14:30:00' }
    ];
    return db;
  }

  function load() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(DB_KEY));
      if (saved) return saved;
    } catch (error) {
      // Fall through and build fresh data.
    }
    const fresh = seed();
    save(fresh);
    return fresh;
  }

  function save(db) {
    try { sessionStorage.setItem(DB_KEY, JSON.stringify(db)); } catch (error) { /* storage full or blocked */ }
  }

  // The fake user making the call, from the saved session (checkAccess).
  function checkAccess(db, ...roles) {
    const session = readSession();
    const user = session && db.users.find((u) => u.userId === session.userId);
    if (!user) fail('Your session has ended. Please log in again.', 401);
    if (roles.length && !roles.includes(user.role)) fail('You do not have access to this page.', 403);
    return user;
  }

  const studentById = (db, id) => db.students.find((s) => s.studentId === id);
  const employerById = (db, id) => db.employers.find((e) => e.employerId === id);
  const supervisorById = (db, id) => db.supervisors.find((s) => s.supervisorId === id);
  const placementById = (db, id) => db.placements.find((p) => p.placementId === Number(id));
  const fullName = (person) => (person ? `${person.firstName} ${person.lastName}` : '');
  const userIdFor = (db, key, id) => { const u = db.users.find((x) => x[key] === id); return u ? u.userId : null; };

  // sendNotification (4.6.8)
  function sendNotification(db, userId, message) {
    if (!userId) return;
    db.notifications.push({ notificationId: db.nextId.notification++, userId, message, read: false, createdAt: new Date().toISOString() });
  }

  // Sign off rows of a log, newest first.
  function signOffsFor(db, logId) {
    return db.signOffs.filter((s) => s.logId === logId)
      .sort((a, b) => String(b.signoffDate).localeCompare(String(a.signoffDate)))
      .map((s) => ({ ...s, supervisorName: fullName(supervisorById(db, s.supervisorId)) }));
  }

  // A log with its latest sign off, as the pages expect it.
  function logView(db, log) {
    const rows = signOffsFor(db, log.logId);
    const latest = log.status === 'Signed' ? rows[0] : null;
    const placement = placementById(db, log.placementId);
    const student = studentById(db, placement.studentId);
    return {
      ...copy(log),
      signedAt: latest ? latest.signoffDate : null,
      verificationMethod: latest ? latest.verificationMethod : null,
      signOffs: rows,
      studentName: fullName(student),
      studentNumber: student.studentNumber,
      employerName: employerById(db, placement.employerId).employerName,
      supervisorName: fullName(supervisorById(db, placement.supervisorId))
    };
  }

  function toRequestResponse(db, r) {
    return { requestId: r.requestId, employerId: r.employerId, employerName: employerById(db, r.employerId).employerName, requestDate: r.requestDate, status: r.status };
  }

  function toPlacementResponse(db, p) {
    const student = studentById(db, p.studentId);
    return {
      placementId: p.placementId, studentId: p.studentId, studentName: fullName(student), studentNumber: student.studentNumber,
      employerId: p.employerId, employerName: employerById(db, p.employerId).employerName,
      supervisorId: p.supervisorId, supervisorName: fullName(supervisorById(db, p.supervisorId)),
      startDate: p.startDate, endDate: p.endDate, status: p.status
    };
  }

  // Same sums as calculateAttendance (4.6.7) and AttendanceService on the C6 branch.
  function attendance(db, placementId) {
    const logs = db.logs.filter((l) => l.placementId === Number(placementId));
    const signed = logs.filter((l) => l.status === 'Signed');
    const signedHours = signed.reduce((sum, l) => sum + Number(l.hoursWorked), 0);
    const pendingHours = logs.filter((l) => l.status === 'Pending').reduce((sum, l) => sum + Number(l.hoursWorked), 0);
    const remainingHours = Math.max(REQUIRED_HOURS - signedHours, 0);
    return {
      signedHours, daysCompleted: new Set(signed.map((l) => l.logDate)).size, pendingHours,
      percentage: Math.min(Math.round((signedHours * 1000) / REQUIRED_HOURS) / 10, 100),
      remainingHours, status: remainingHours === 0 ? 'COMPLETE' : 'IN PROGRESS'
    };
  }

  function activePlacementOf(db, studentId) {
    return db.placements.find((p) => p.studentId === studentId && p.status === 'Active');
  }

  return {
    async login(email, password) {
      await pause();
      const db = load();
      const user = db.users.find((u) => u.email.toLowerCase() === String(email).trim().toLowerCase());
      if (!user || password !== (user.password || 'password')) fail('Incorrect email or password', 401);
      return { userId: user.userId, email: user.email, role: user.role, authenticated: true, message: 'Login successful', name: user.name };
    },

    async registerUser(details) {
      await pause();
      const db = load();
      if (db.users.some((u) => u.email.toLowerCase() === details.email.toLowerCase())) fail('This email is already registered', 409);
      const user = { userId: db.nextId.user++, email: details.email, role: details.role, name: details.name || details.email, password: details.password };
      db.users.push(user);
      save(db);
      return { userId: user.userId, email: user.email, role: user.role, message: 'Registration successful' };
    },

    // registerUser (4.6.2): the account and the row for its role are saved together.
    async registerAccount(d) {
      await pause();
      const db = load();
      if (db.users.some((u) => u.email.toLowerCase() === String(d.email).toLowerCase())) fail('This email is already registered', 409);
      const user = { userId: db.nextId.user++, email: d.email, role: d.role, name: `${d.firstName} ${d.lastName}`, password: d.password };
      if (d.role === 'STUDENT') {
        if (db.students.some((s) => s.studentNumber === d.studentNumber)) fail('This student number is already registered', 409);
        user.studentId = db.nextId.student++;
        db.students.push({ studentId: user.studentId, studentNumber: d.studentNumber, firstName: d.firstName, lastName: d.lastName });
      } else if (d.role === 'SUPERVISOR') {
        const employer = employerById(db, Number(d.employerId));
        if (!employer || employer.status !== 'Approved') fail('Select your employer', 400);
        user.supervisorId = db.supervisors.reduce((max, s) => Math.max(max, s.supervisorId), 0) + 1;
        db.supervisors.push({ supervisorId: user.supervisorId, firstName: d.firstName, lastName: d.lastName, phone: d.phone, employerId: employer.employerId, pin: d.pin });
      } else if (d.role === 'EMPLOYER') {
        user.employerId = db.employers.reduce((max, e) => Math.max(max, e.employerId), 0) + 1;
        user.name = d.employerName;
        db.employers.push({ employerId: user.employerId, employerName: d.employerName, address: d.address, contactEmail: d.contactEmail, contactNumber: d.contactNumber, status: 'Pending' });
        const coordinator = db.users.find((u) => u.role === 'COORDINATOR');
        sendNotification(db, coordinator && coordinator.userId, `${d.employerName} is waiting for approval.`);
      } else {
        fail('Select your role', 400);
      }
      db.users.push(user);
      save(db);
      return { userId: user.userId, email: user.email, role: user.role, message: 'Account created. You can now log in' };
    },

    async getRegisterEmployers() {
      await pause();
      return load().employers.filter((e) => e.status === 'Approved')
        .map(({ employerId, employerName }) => ({ employerId, employerName }))
        .sort((a, b) => a.employerName.localeCompare(b.employerName));
    },

    async searchEmployers(search) {
      await pause();
      const term = String(search || '').trim().toLowerCase();
      return copy(load().employers.filter((e) => e.status === 'Approved' && (!term || e.employerName.toLowerCase().includes(term) || e.address.toLowerCase().includes(term))));
    },

    async sendPlacementRequest(employerId) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'STUDENT');
      const employer = employerById(db, employerId);
      if (!employer || employer.status !== 'Approved') fail('This employer is not available', 404);
      if (db.requests.some((r) => r.studentId === user.studentId && r.employerId === employerId && ['Pending', 'Meeting Requested'].includes(r.status))) {
        fail('You have already sent a request to this employer', 409);
      }
      if (db.placements.some((p) => p.studentId === user.studentId && p.employerId === employerId && p.status === 'Active')) {
        fail('You already have an active placement with this employer', 409);
      }
      const item = { requestId: db.nextId.request++, studentId: user.studentId, employerId, requestDate: today(), status: 'Pending' };
      db.requests.push(item);
      sendNotification(db, userIdFor(db, 'employerId', employerId), `New placement request from ${user.name}.`);
      save(db);
      return toRequestResponse(db, item);
    },

    async getMyPlacementRequests() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'STUDENT');
      return db.requests.filter((r) => r.studentId === user.studentId)
        .sort((a, b) => b.requestDate.localeCompare(a.requestDate))
        .map((r) => toRequestResponse(db, r));
    },

    async getMyPlacements() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'STUDENT');
      return db.placements.filter((p) => p.studentId === user.studentId).map((p) => toPlacementResponse(db, p));
    },

    async getEmployerPlacementRequests(status) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      return db.requests
        .filter((r) => r.employerId === user.employerId && (!status || r.status === status))
        .sort((a, b) => b.requestDate.localeCompare(a.requestDate))
        .map((r) => {
          const student = studentById(db, r.studentId);
          return { ...toRequestResponse(db, r), studentId: student.studentId, studentNumber: student.studentNumber, studentFirstName: student.firstName, studentLastName: student.lastName };
        });
    },

    // respondToRequest (4.6.4): accept creates the placement.
    async acceptPlacementRequest(requestId, details) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      const item = db.requests.find((r) => r.requestId === requestId && r.employerId === user.employerId);
      if (!item || !['Pending', 'Meeting Requested'].includes(item.status)) fail('This request cannot be changed', 409);
      const supervisor = supervisorById(db, Number(details.supervisorId));
      if (!supervisor || supervisor.employerId !== user.employerId) fail('Select a supervisor', 400);
      if (!details.startDate || details.endDate <= details.startDate) fail('The end date must be after the start date', 400);
      item.status = 'Accepted';
      const placement = { placementId: db.nextId.placement++, studentId: item.studentId, employerId: item.employerId, supervisorId: supervisor.supervisorId, startDate: details.startDate, endDate: details.endDate, status: 'Active' };
      db.placements.push(placement);
      sendNotification(db, userIdFor(db, 'studentId', item.studentId), 'Your placement request was updated: Accepted.');
      save(db);
      return toPlacementResponse(db, placement);
    },

    async rejectPlacementRequest(requestId) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      const item = db.requests.find((r) => r.requestId === requestId && r.employerId === user.employerId);
      if (!item || !['Pending', 'Meeting Requested'].includes(item.status)) fail('This request cannot be changed', 409);
      item.status = 'Rejected';
      sendNotification(db, userIdFor(db, 'studentId', item.studentId), 'Your placement request was updated: Rejected.');
      save(db);
      return toRequestResponse(db, item);
    },

    async requestMeeting(requestId, message) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      const item = db.requests.find((r) => r.requestId === requestId && r.employerId === user.employerId);
      if (!item || item.status !== 'Pending') fail('This request cannot be changed', 409);
      item.status = 'Meeting Requested';
      sendNotification(db, userIdFor(db, 'studentId', item.studentId), `Your placement request was updated: Meeting Requested. ${message}`);
      save(db);
      return toRequestResponse(db, item);
    },

    async getEmployerPlacements() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      return db.placements.filter((p) => p.employerId === user.employerId).map((p) => toPlacementResponse(db, p));
    },

    async getEmployerProfile() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      return copy(employerById(db, user.employerId));
    },

    async updateEmployerProfile(details) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      const employer = employerById(db, user.employerId);
      Object.assign(employer, details);
      save(db);
      return copy(employer);
    },

    async getEmployerSupervisors() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'EMPLOYER');
      return db.supervisors.filter((s) => s.employerId === user.employerId).map((s) => ({
        supervisorId: s.supervisorId, firstName: s.firstName, lastName: s.lastName, phone: s.phone,
        email: (db.users.find((u) => u.supervisorId === s.supervisorId) || {}).email || '',
        activeStudents: db.placements.filter((p) => p.supervisorId === s.supervisorId && p.status === 'Active').length
      }));
    },

    async calculateAttendance(placementId) {
      await pause();
      return attendance(load(), placementId);
    },

    async getNotifications() {
      await pause();
      const db = load();
      const user = checkAccess(db);
      const items = db.notifications.filter((n) => n.userId === user.userId)
        .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
        .map(({ notificationId, message, read, createdAt }) => ({ notificationId, message, read, createdAt }));
      return { items, unreadCount: items.filter((n) => !n.read).length };
    },

    async markNotificationRead(notificationId) {
      await pause();
      const db = load();
      const user = checkAccess(db);
      const item = db.notifications.find((n) => n.notificationId === notificationId && n.userId === user.userId);
      if (item) item.read = true;
      save(db);
      return null;
    },

    async getMyDutyLogs() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'STUDENT');
      const placement = activePlacementOf(db, user.studentId);
      if (!placement) return [];
      return db.logs.filter((l) => l.placementId === placement.placementId)
        .sort((a, b) => b.logDate.localeCompare(a.logDate))
        .map((l) => logView(db, l));
    },

    // submitDutyLog (4.6.5). logId null: new log. logId set: correction.
    async submitDutyLog(logId, formData) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'STUDENT');
      const placement = activePlacementOf(db, user.studentId);
      if (!placement) fail('No active placement found', 409);

      const logDate = formData.get('logDate');
      const hoursWorked = Number(formData.get('hoursWorked'));
      const task = String(formData.get('taskDescription') || '').trim();
      if (!logDate) fail('Enter a valid date', 400);
      if (logDate > today()) fail('The date cannot be in the future', 400);
      if (logDate < placement.startDate || logDate > placement.endDate) fail('The date is outside your placement period', 400);
      if (!(hoursWorked >= RULES.MIN_HOURS_PER_LOG && hoursWorked <= RULES.MAX_HOURS_PER_LOG)) fail('Hours must be between 0.5 and 12', 400);
      if (task.length < RULES.TASK_MIN || task.length > RULES.TASK_MAX) fail('Describe the task in 10 to 1000 characters', 400);
      const files = formData.getAll('proof').filter((f) => f && f.size);
      if (files.length > RULES.MAX_FILES_PER_LOG) fail('You can attach up to 3 files', 400);

      const clash = db.logs.find((l) => l.placementId === placement.placementId && l.logDate === logDate && l.logId !== logId);
      if (clash) fail('You already have a log for this date', 409);

      let log;
      if (logId) {
        log = db.logs.find((l) => l.logId === logId && l.placementId === placement.placementId);
        if (!log) fail('This item could not be found.', 404);
        if (log.status === 'Signed') {
          const last = signOffsFor(db, log.logId)[0];
          if (last && Date.now() - new Date(last.signoffDate).getTime() > RULES.SIGN_OFF_WINDOW_HOURS * HOUR) {
            fail('This day was signed off more than 24 hours ago and is locked', 409);
          }
        }
        if (log.proofs.length + files.length > RULES.MAX_FILES_PER_LOG) fail('You can attach up to 3 files', 400);
      } else {
        log = { logId: db.nextId.log++, placementId: placement.placementId, proofs: [] };
        db.logs.push(log);
      }
      Object.assign(log, {
        logDate, hoursWorked, taskDescription: task, skillsLearnt: String(formData.get('skillsLearnt') || '').trim(),
        status: 'Pending', supervisorComment: null, submittedAt: new Date().toISOString()
      });
      files.forEach((file) => log.proofs.push({ proofId: db.nextId.proof++, fileName: file.name, fileType: file.type, uploadDate: new Date().toISOString() }));
      sendNotification(db, userIdFor(db, 'supervisorId', placement.supervisorId), `A new log from ${user.name} needs sign off.`);
      save(db);
      return logView(db, log);
    },

    // Builds a placeholder picture so the proof preview works in the demo.
    async getProofFile(proofId) {
      await pause();
      const db = load();
      const proof = db.logs.flatMap((l) => l.proofs).find((p) => p.proofId === proofId);
      if (!proof) fail('This item could not be found.', 404);
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="450" viewBox="0 0 800 450"><rect width="800" height="450" fill="#1d1d20"/><rect x="24" y="24" width="752" height="402" rx="12" fill="none" stroke="#1bbfa8" stroke-width="2" stroke-dasharray="8 8"/><text x="400" y="210" fill="#ffffff" font-family="Segoe UI, Arial, sans-serif" font-size="28" text-anchor="middle">Sample proof of work</text><text x="400" y="252" fill="#a1a1aa" font-family="Segoe UI, Arial, sans-serif" font-size="20" text-anchor="middle">${proof.fileName}</text></svg>`;
      return new Blob([svg], { type: 'image/svg+xml' });
    },

    async getPendingLogs() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'SUPERVISOR');
      const mine = db.placements.filter((p) => p.supervisorId === user.supervisorId).map((p) => p.placementId);
      return db.logs.filter((l) => mine.includes(l.placementId) && l.status === 'Pending')
        .sort((a, b) => a.logDate.localeCompare(b.logDate))
        .map((l) => logView(db, l));
    },

    async getLogForReview(logId) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'SUPERVISOR');
      const log = db.logs.find((l) => l.logId === logId);
      if (!log || placementById(db, log.placementId).supervisorId !== user.supervisorId) fail('You can only sign logs of your own students', 403);
      return logView(db, log);
    },

    // signOffLog (4.6.6): adds a SIGN_OFF row and marks the log Signed.
    async signOffLog(logId, details) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'SUPERVISOR');
      const log = db.logs.find((l) => l.logId === logId);
      if (!log || placementById(db, log.placementId).supervisorId !== user.supervisorId) fail('You can only sign logs of your own students', 403);
      if (log.status !== 'Pending') fail('This log is not waiting for sign off', 409);
      const supervisor = supervisorById(db, user.supervisorId);
      // 400, like the backend: 401 would mean the login itself has ended.
      if (details.verificationMethod === 'PIN' && details.pin !== supervisor.pin) fail('Incorrect PIN', 400);
      if (details.verificationMethod === 'Signature' && !details.signatureData) fail('Please sign in the box', 400);
      db.signOffs.push({ signoffId: db.nextId.signOff++, logId, supervisorId: user.supervisorId, signoffDate: new Date().toISOString(), verificationMethod: details.verificationMethod });
      Object.assign(log, { status: 'Signed', supervisorComment: null });
      sendNotification(db, userIdFor(db, 'studentId', placementById(db, log.placementId).studentId), `Your log for ${longDate(log.logDate)} was signed off.`);
      save(db);
      return logView(db, log);
    },

    async rejectLog(logId, supervisorComment) {
      await pause();
      const db = load();
      const user = checkAccess(db, 'SUPERVISOR');
      const log = db.logs.find((l) => l.logId === logId);
      if (!log || placementById(db, log.placementId).supervisorId !== user.supervisorId) fail('You can only sign logs of your own students', 403);
      if (log.status !== 'Pending') fail('This log is not waiting for sign off', 409);
      if (String(supervisorComment || '').trim().length < RULES.REJECT_MIN) fail('Give a reason for the rejection', 400);
      Object.assign(log, { status: 'Rejected', supervisorComment, rejectedAt: new Date().toISOString() });
      sendNotification(db, userIdFor(db, 'studentId', placementById(db, log.placementId).studentId), `Your log for ${longDate(log.logDate)} was rejected: ${supervisorComment}`);
      save(db);
      return logView(db, log);
    },

    // Signed Logs: every log this supervisor signed, newest sign off first.
    async getSignOffHistory() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'SUPERVISOR');
      return db.signOffs.filter((s) => s.supervisorId === user.supervisorId)
        .sort((a, b) => String(b.signoffDate).localeCompare(String(a.signoffDate)))
        .map((s) => {
          const view = logView(db, db.logs.find((l) => l.logId === s.logId));
          return { ...view, signoffId: s.signoffId, signoffDate: s.signoffDate, verificationMethod: s.verificationMethod };
        });
    },

    // My Students: placements of this supervisor with their hours.
    async getSupervisorStudents() {
      await pause();
      const db = load();
      const user = checkAccess(db, 'SUPERVISOR');
      return db.placements.filter((p) => p.supervisorId === user.supervisorId)
        .map((p) => ({ ...toPlacementResponse(db, p), attendance: attendance(db, p.placementId) }));
    },

    async getStudentProgress() {
      await pause();
      const db = load();
      checkAccess(db, 'COORDINATOR');
      // Counted from the log date, as the backend does (DUTY_LOG keeps no submission time).
      const threeDaysAgo = toDateString(new Date(Date.now() - 3 * 24 * HOUR));
      return db.placements.map((p) => {
        const view = toPlacementResponse(db, p);
        const pending = db.logs.filter((l) => l.placementId === p.placementId && l.status === 'Pending');
        return {
          placementId: p.placementId, studentNumber: view.studentNumber, studentName: view.studentName,
          employerName: view.employerName, supervisorName: view.supervisorName,
          attendance: attendance(db, p.placementId),
          unverifiedLogs: pending.length,
          logsWaitingOver3Days: pending.filter((l) => l.logDate < threeDaysAgo).length,
          startDate: p.startDate, endDate: p.endDate
        };
      });
    },

    async getCoordinatorEmployers() {
      await pause();
      const db = load();
      checkAccess(db, 'COORDINATOR');
      return db.employers.map(({ employerId, employerName, status }) => ({ employerId, employerName, status }));
    },

    async setEmployerStatus(employerId, expected, next) {
      await pause();
      const db = load();
      checkAccess(db, 'COORDINATOR');
      const employer = employerById(db, employerId);
      if (!employer) fail('This item could not be found.', 404);
      if (employer.status !== expected) fail(`This employer is not ${expected.toLowerCase()}.`, 409);
      employer.status = next;
      save(db);
      return null;
    },

    async getPlacementLogs(placementId) {
      await pause();
      const db = load();
      checkAccess(db, 'COORDINATOR', 'STUDENT');
      return db.logs.filter((l) => l.placementId === Number(placementId))
        .sort((a, b) => a.logDate.localeCompare(b.logDate))
        .map((l) => logView(db, l));
    },

    // Sign Off Audit Log: every SIGN_OFF row, read only.
    async getSignOffAudit() {
      await pause();
      const db = load();
      checkAccess(db, 'COORDINATOR');
      return db.signOffs.slice()
        .sort((a, b) => String(b.signoffDate).localeCompare(String(a.signoffDate)))
        .map((s) => {
          const log = db.logs.find((l) => l.logId === s.logId);
          const student = studentById(db, placementById(db, log.placementId).studentId);
          return { signoffId: s.signoffId, logId: s.logId, logDate: log.logDate, studentName: fullName(student), studentNumber: student.studentNumber, supervisorName: fullName(supervisorById(db, s.supervisorId)), verificationMethod: s.verificationMethod, signoffDate: s.signoffDate };
        });
    }
  };
})();

// ---------- Public API used by the pages ----------
window.API = {
  USE_FAKE_DATA, API_BASE_URL, RULES, REQUIRED_HOURS, SESSION_KEY, ApiError, basicToken,
  login, registerUser, registerAccount, getRegisterEmployers,
  searchEmployers, sendPlacementRequest, getMyPlacementRequests, getMyPlacements,
  getEmployerPlacementRequests, acceptPlacementRequest, rejectPlacementRequest, requestMeeting,
  getEmployerPlacements, getEmployerProfile, updateEmployerProfile, getEmployerSupervisors,
  calculateAttendance, getNotifications, markNotificationRead,
  getMyDutyLogs, submitDutyLog, correctDutyLog, getProofFile,
  getPendingLogs, getLogForReview, signOffLog, rejectLog, getSignOffHistory, getSupervisorStudents,
  getStudentProgress, getCoordinatorEmployers, approveEmployer, deactivateEmployer,
  getPlacementLogs, getSignOffAudit, generateComplianceReport
};
