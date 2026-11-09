# Test summary

**Result: 119 tests, 119 passed, 0 failed** (9 November 2026, `mvnw test` with JDK 17 against
MySQL loaded with `schema.sql` and `seed.sql`).

The API tests call the real endpoints through Spring's MockMvc with the seed accounts and
check both the response and the database. Every test runs in a transaction that is rolled
back, so the suite can run again on the same data. Files written during a test are deleted
when its transaction rolls back.

## Tests by area

| Test class | Tests | What it proves |
| --- | --- | --- |
| `AuthRegistrationTest` | 16 | Register saves the role row with the account (student, supervisor, employer); the PIN is stored as a hash; supervisors must pick an approved employer; role details are required; duplicate email or student number gives 409; weak passwords are refused; only a coordinator can create a coordinator; the sign up list shows approved employers only; login returns the name; wrong email and wrong password give the same message; 5 wrong passwords lock the login |
| `AuthControllerTest` | 2 | Register and login through the API (team test, kept) |
| `SecurityRulesTest` | 13 | Each role reaches only its own API; 401 is JSON without the browser login box; the frontend on localhost passes CORS and other sites do not; unknown paths give 404; the web pages are served on the same port without a login while the API and uploaded files stay protected |
| `PlacementApiTest` | 9 | Employer search returns status text (was a 500); requests notify the employer; status filter; accept creates an active placement and tells the student; the meeting message reaches the student; reject; another employer cannot answer; placements for students and employers (were empty) |
| `PlacementRequestServiceTest` | 5 | Placement request rules in the service (team test, kept) |
| `DutyLogControllerTest` | 25 | Hours 0.5 to 12; task 10 to 1000; skills optional and up to 500; plain message for non numbers; no future dates; inside the placement; one log per day; no active placement; at most 3 files; only JPG, PNG or PDF; renamed files refused by content; 5 MB limit; safe file names; rejected logs can be corrected; signed logs within 24 hours can be corrected and lock after; a correction cannot move onto another day; 3 files across corrections; students cannot touch other logs; My Logs order and fields; who may read placement logs and proof files; missing files give a plain message |
| `SignOffControllerTest` | 15 | Pending list has only the supervisor's own students; other supervisors are refused; PIN sign off adds a SIGN_OFF row, notifies the student and adds the hours; drawn signatures are kept; a wrong PIN is 400; 5 wrong PINs lock for 15 minutes; empty, oversized or unknown methods are refused; only pending logs can be signed or rejected; reject reasons 5 to 300 and the student is told; history and My Students |
| `EmployerControllerTest` | 4 | Company details; updates without changing the status; invalid details refused; supervisors with their active students |
| `CoordinatorApiTest` | 11 | Progress with pending and waiting counts; the audit log order; approve only pending employers; deactivated employers leave the search; the report is a PDF for the student and the coordinator only; the report text (title, dates in words, "Page 1 of N"); no report without signed logs; notifications and unread count; mark as read only changes your own; who may read attendance |
| `AttendanceServiceTest` | 1 | Signed and pending hours are counted separately (team test, kept) |
| `GlobalExceptionHandlerTest` | 4 | Error responses for 401, 403, 404 and 500 (team test, kept) |
| `AttemptLimiterTest` | 6 | Unit test: 4 wrong tries do not lock, the 5th does; the lock ends after 15 minutes; counting restarts; a success resets; keys are separate |
| `DutyLogViewsTest` | 5 | Unit test: shown file names, file types, status text and dates in words |
| `SampleProofFilesTest` | 2 | Sample files are created for missing seed proofs and real files are never overwritten |
| `WilConnectApplicationTests` | 1 | The application starts against the schema |

## Checks outside the unit tests

- A scripted run of every endpoint against the running backend: 82 checks passed.
- The frontend against the backend with `USE_FAKE_DATA = false`, in Chrome: register,
  search and request, accept with a supervisor, meeting request, company details, a log
  with a PDF, reject, correct with a new file, sign off by drawn signature, PIN lockout
  message, approve an employer, create a coordinator, download the report, notifications
  and mark all as read, login lockout message. Every page for every role loaded with real
  data without errors.
- The layout of every page was checked at phone, tablet and laptop widths (390, 820, 1180
  and 1366 pixels) with the sample data.
