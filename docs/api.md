# WIL Connect API

Base address: `http://localhost:8080`. All bodies are JSON unless stated otherwise.

## Login and errors

- `POST /api/auth/login` checks the email and password. Every other call (except the
  public ones) sends the same email and password with HTTP Basic:
  `Authorization: Basic base64(email:password)`.
- Each role can only call its own part of the API. Shared endpoints check that the
  record belongs to the caller.
- Every error has the same shape: `{ "message": "a sentence for the user" }`.

| Status | Meaning |
| --- | --- |
| 400 | A value breaks a rule (the message says which), or a wrong PIN |
| 401 | Not logged in, or the email and password do not match |
| 403 | The role or the person may not do this |
| 404 | Not found, or not visible to the caller |
| 409 | Clashes with the current state (duplicate, already answered, locked log) |
| 429 | Locked for 15 minutes after 5 wrong passwords or PINs |

## Public

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | see below | 201 `{ userId, email, role, message }` |
| POST | `/api/auth/login` | `{ email, password }` | 200 `{ userId, email, role, name, authenticated, message }` |
| GET | `/api/auth/employers` | | Approved employers for the supervisor sign up list: `[{ employerId, employerName }]` |

**Register** (registerUser, 4.6.2) saves the account and its role row in one transaction.

| Role | Fields besides `email`, `password` (8 to 72, a letter and a number) and `role` |
| --- | --- |
| `STUDENT` | `firstName`, `lastName`, `studentNumber` (6 to 10 digits, unique) |
| `SUPERVISOR` | `firstName`, `lastName`, `phone` (10 digits starting with 0), `pin` (4 to 6 digits, stored hashed), `employerId` (an approved employer) |
| `EMPLOYER` | `employerName`, `address`, `contactEmail`, `contactNumber`. Starts as Pending; the coordinators are notified |
| `COORDINATOR` | Only when the call is made by a logged in coordinator |

Errors: 400 for a broken rule, 403 for a coordinator account without a coordinator login,
409 when the email or student number is already registered.

## Student (`/api/students`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| GET | `/api/students/employers?search=` | | Approved employers matching the name: `[{ employerId, employerName, address, contactEmail, contactNumber, status }]` |
| POST | `/api/students/placement-requests` | `{ employerId }` | 201 request. 409 if a request is already open or a placement is active with that employer |
| GET | `/api/students/placement-requests` | | `[{ requestId, employerId, employerName, requestDate, status }]` |
| GET | `/api/students/placements` | | `[Placement]` |
| GET | `/api/students/duty-logs` | | `[Log]`, newest first |
| POST | `/api/students/duty-logs` | multipart form, see below | 201 `Log` |
| PUT | `/api/students/duty-logs/{logId}` | multipart form, see below | 200 `Log` |

**Duty log form** (submitDutyLog, 4.6.5): `logDate` (yyyy-mm-dd), `hoursWorked` (0.5 to 12),
`taskDescription` (10 to 1000 characters), `skillsLearnt` (optional, up to 500) and up to 3
files in `proof` (JPG, PNG or PDF, 5 MB each, checked by their content).

Rules: the student needs an active placement (409); the date is inside the placement and not
in the future (400); one log per day (409). A correction is allowed for a Pending or Rejected
log, and for a Signed log within 24 hours of its sign off (409 after that). A correction goes
back to Pending, clears the supervisor comment, keeps the earlier files (3 in total) and
notifies the supervisor.

## Supervisor (`/api/supervisors`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| GET | `/api/supervisors/duty-logs?status=Pending` | | Logs of the supervisor's students with that status, oldest first |
| GET | `/api/supervisors/duty-logs/{logId}` | | `Log`. 403 if the student is not theirs |
| POST | `/api/supervisors/duty-logs/{logId}/sign-off` | `{ verificationMethod: "PIN", pin }` or `{ verificationMethod: "Signature", signatureData: "data:image/png;base64,..." }` | 200 `Log` (Signed) |
| PUT | `/api/supervisors/duty-logs/{logId}/reject` | `{ supervisorComment }` (5 to 300 characters) | 200 `Log` (Rejected) |
| GET | `/api/supervisors/sign-offs` | | Signed Logs: `[{ signoffId, signoffDate, verificationMethod, logId, logDate, hoursWorked, taskDescription, status, studentName, studentNumber }]`, newest first |
| GET | `/api/supervisors/placements` | | My Students: `[Placement + attendance]` |

Sign off (signOffLog, 4.6.6) works only on Pending logs (409 otherwise). A wrong PIN gives
400 "Incorrect PIN" (not 401, so the supervisor stays logged in); 5 wrong PINs lock signing for
15 minutes (429). A signature must fit the database column (65,000 characters). The student
is notified of every sign off and rejection.

## Employer (`/api/employers`)

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| GET | `/api/employers/placement-requests?status=` | | Requests to this employer, with `studentId`, `studentNumber`, `studentFirstName`, `studentLastName` |
| PUT | `/api/employers/placement-requests/{requestId}/accept` | `{ supervisorId, startDate, endDate }` | 200 `Placement` (Active). The supervisor must belong to this employer |
| PUT | `/api/employers/placement-requests/{requestId}/reject` | | 200 request |
| PUT | `/api/employers/placement-requests/{requestId}/meeting` | `{ message }` | 200 request. The message goes to the student |
| GET | `/api/employers/placements` | | `[Placement]` |
| GET | `/api/employers/me` | | `{ employerId, employerName, address, contactEmail, contactNumber, status }` |
| PUT | `/api/employers/me` | `{ employerName, address, contactEmail, contactNumber }` | 200 company details. The status cannot be changed here |
| GET | `/api/employers/supervisors` | | `[{ supervisorId, firstName, lastName, phone, email, activeStudents }]` |

## Coordinator (`/api/coordinator`)

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/coordinator/student-progress` | `[{ placementId, studentNumber, studentName, employerName, supervisorName, attendance, unverifiedLogs, logsWaitingOver3Days, startDate, endDate }]` |
| GET | `/api/coordinator/employers` | `[{ employerId, employerName, status }]` |
| PATCH | `/api/coordinator/employers/{employerId}/approve` | 204. Pending to Approved (409 otherwise, 404 if unknown) |
| PATCH | `/api/coordinator/employers/{employerId}/deactivate` | 204. Approved to Inactive; the employer leaves the search |
| GET | `/api/coordinator/sign-offs` | Sign Off Audit Log: `[{ signoffId, logId, logDate, studentName, studentNumber, supervisorName, verificationMethod, signoffDate }]`, newest first |

"Logs waiting over 3 days" counts pending logs whose log date is more than 3 days ago,
because DUTY_LOG stores no submission time.

## Shared (any logged in user, checked per record)

| Method | Path | Who | Returns |
| --- | --- | --- | --- |
| GET | `/api/placements/{placementId}/attendance` | the student, their supervisor, coordinators | `{ signedHours, daysCompleted, pendingHours, percentage, remainingHours, status }` (calculateAttendance, 4.6.7) |
| GET | `/api/placements/{placementId}/duty-logs` | the student, their supervisor, coordinators | `[Log]`, oldest first |
| GET | `/api/placements/{placementId}/compliance-report` | the student, coordinators | PDF (generateComplianceReport, 4.6.9). 409 when there are no signed logs |
| GET | `/api/proofs/{proofId}/file` | the student, their supervisor, coordinators | The file, shown in the browser. 404 for anyone else |
| GET | `/api/notifications` | everyone | `{ items: [{ notificationId, message, read, createdAt }], unreadCount }`, newest first |
| PATCH | `/api/notifications/{notificationId}/read` | everyone | 204. Only changes the caller's own messages |

## Objects

**Placement**: `placementId, studentId, studentName, studentNumber, employerId, employerName,
supervisorId, supervisorName, startDate, endDate, status` (Pending, Active, Completed, Cancelled).

**Log**: `logId, placementId, logDate, hoursWorked, taskDescription, skillsLearnt, status`
(Pending, Signed, Rejected), `supervisorComment, submittedAt` (always null, not stored),
`signedAt, verificationMethod` (from the latest sign off of a signed log),
`signOffs: [{ signoffId, logId, supervisorId, supervisorName, signoffDate, verificationMethod }]`,
`proofs: [{ proofId, fileName, fileType, uploadDate }]`,
`studentName, studentNumber, employerName, supervisorName`.

## Notifications sent (Table 4.7.14)

| When | To | Message |
| --- | --- | --- |
| An employer registers | coordinators | `{employer} is waiting for approval.` |
| A student sends a placement request | the employer | `New placement request from {student}.` |
| The employer accepts or rejects | the student | `Your placement request was updated: Accepted.` / `Rejected.` |
| The employer asks for a meeting | the student | `Your placement request was updated: Meeting Requested. {message}` |
| A log is sent or corrected | the supervisor | `A new log from {student} needs sign off.` |
| A log is signed | the student | `Your log for {27 October 2026} was signed off.` |
| A log is rejected | the student | `Your log for {27 October 2026} was rejected: {reason}` |
