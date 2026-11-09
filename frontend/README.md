# Frontend

The web pages of WIL Connect in plain HTML, CSS and JavaScript, with no build step.
The design follows section 4.7 of the Phase 4 document: dark theme, teal accent, the
Inter font, and one shared stylesheet so every screen looks the same.

## Running it

Serve this folder with any static server and open `pages/login.html`, for example
VS Code Live Server, or `python -m http.server 5500` in this folder and then
http://localhost:5500/pages/login.html. The test accounts are listed on the login page.

`js/api.js` decides where the data comes from:

| Setting | Use |
| --- | --- |
| `USE_FAKE_DATA = false` | Calls the Spring Boot backend at `API_BASE_URL` (default `http://localhost:8080`) |
| `USE_FAKE_DATA = true` | Runs every page on built in sample data that matches `seed.sql`, with no backend. Changes last until the tab is closed |

## Pages (`pages/`)

| Role | Pages |
| --- | --- |
| Everyone | `login.html`, `register.html` (Create an account), `notifications.html`, `profile.html` |
| Student | `student-dashboard.html` (Dashboard and Attendance), `employers.html` (Find Employers), `requests.html` (My Requests), `daily-log.html` (Daily Log and My Logs), `report.html` (My Report) |
| Supervisor | `supervisor-pending.html` (Pending Sign Offs, Signed Logs, My Students), `supervisor-signoff.html` (review, sign or reject one log) |
| Employer | `employer-requests.html` (Placement Requests, Students on Placement, Supervisors, Company) |
| Coordinator | `coordinator-dashboard.html` (Student progress), `coordinator-employers.html`, `coordinator-audit.html` (Sign Off Audit Log), `coordinator-users.html`, `report.html?placement=ID` |

The menu for each role follows Table 4.7.2. Opening a page of another role sends the
user to their own dashboard with a message.

## Scripts (`js/`)

| File | Job |
| --- | --- |
| `api.js` | The only place that calls the backend. Every endpoint path, the rules shared with the backend (hours, file types, lengths) and the sample data |
| `auth.js` | Login, the saved session for the tab, logging out after 30 minutes without activity, and role checks on every page |
| `layout.js` | The top bar, the menu for each role (with a phone menu) and the notification bell, refreshed every minute |
| `ui.js` | Shared helpers: dates as "29 October 2026", status pills, loading, empty and error states, paging, toasts, field errors, the themed date picker and notification links |
| `pages/*.js` | One script per page |

Styles are in `css/style.css`; the logo, favicon and the Inter font are in `assets/`.

## Rules followed on every page

- Every list has a loading, an empty and an error state with Try again.
- Forms check their fields in the browser with the same rules as the backend and show the
  message next to the field.
- No hyphens or dashes in the text shown on screen; dates are written in words.
- Works on phones, tablets and laptops: tables turn into cards on narrow screens, and tap
  targets are at least 44 pixels on touch screens.
- Notifications that ask for an action open the page that answers them.
