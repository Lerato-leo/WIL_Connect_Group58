# WIL Connect and Sign Off Portal

Group 58, Richfield, IT Project Year 3.

WIL Connect replaces paper logbooks for Work Integrated Learning. Students find an
approved employer and request a placement, log their daily duties with proof of work,
and supervisors sign each day off with a PIN or a drawn signature. Hours count only
once they are signed, and the coordinator follows every student and creates the
compliance report.

## What each role can do

| Role | Main tasks |
| --- | --- |
| Student | Search approved employers, send placement requests, log daily duties with up to 3 proof files, correct rejected logs, follow signed hours, download the compliance report |
| Supervisor | Review the pending logs of their own students, sign off by PIN or signature, reject with a reason, see signed logs and each student's hours |
| Employer | Accept, reject or ask for a meeting on placement requests (choosing the supervisor and dates), see placements and supervisors, edit company details |
| Coordinator | Follow every student's progress, approve or deactivate employers, read the sign off audit log, create coordinator accounts, create compliance reports |

Everyone gets notifications. Messages that ask for an action open the page that answers them.

## How the parts fit together

| Part | Technology | Port | Folder |
| --- | --- | --- | --- |
| Database | MySQL 8 | 3306 | `src/main/resources/db/` |
| Backend (REST API) | Spring Boot 3.5, Java 17 | 8080 | `src/` |
| Frontend | HTML, CSS and JavaScript (no build step) | 8080, served by the backend | `frontend/` |

The Spring Boot application serves both the REST API (`/api/...`) and the web pages
(`/pages/...`) on port 8080, so the browser talks to one address and needs no cross
origin setup. The pages are packaged into the application when it is built, and read
straight from `frontend/` while it runs from the project folder, so page edits show on
refresh.

## Getting started

### 1. Prerequisites

- Java JDK 17. All group members should use Java 17 (check with `java -version`).
  Newer JDKs such as 25 do not work with the Lombok version in this project.
- MySQL 8.0.16 or newer. MySQL Workbench is recommended.
  MariaDB users must change the collation in `schema.sql` to `utf8mb4_general_ci`.
- Maven is not needed: use IntelliJ's bundled Maven or the `mvnw` wrapper.

### 2. Database

> Warning: running `schema.sql` drops and recreates the `wil_connect` database.

1. Open MySQL Workbench and connect to your local MySQL server.
2. Run `src/main/resources/db/schema.sql`.
3. Run `src/main/resources/db/seed.sql`.
4. Put your MySQL root password in `spring.datasource.password` in
   `src/main/resources/application.properties`, or set it when you start the backend (see Configuration).

Run both files again whenever you need a clean demonstration database.

### 3. Backend

In IntelliJ, run `WilConnectApplication`.

On Windows, or from PowerShell in the project folder, run:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux, run:

```bash
./mvnw spring-boot:run
```

### 4. Open WIL Connect

Open **http://localhost:8080** in the browser. It goes straight to the login page and
uses the real database. Swagger UI is at http://localhost:8080/swagger-ui/index.html.
The OpenAPI specifications are in src/main/resources/static/openapi/.

If port 8080 is already in use, start the backend on port 8081.

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

On macOS/Linux:

```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
```

Then open http://localhost:8081. Nothing else needs changing because the pages call the API on their own address.

### Designing pages without the backend

The pages can also be opened from a separate file server such as VS Code Live Server
(right click `frontend/index.html`, Open with Live Server) or `python -m http.server 5500`
inside `frontend/`. Opened that way, every page runs on built in sample data that matches
`seed.sql`, so no backend or database is needed. `frontend/js/api.js` explains how this is
chosen and how to override it.

## Test accounts

| Role | Login email | Password |
| --- | --- | --- |
| Coordinator | coordinator@richfield.co.za | password |
| Student | john.smith@richfield.co.za | password |
| Student | sarah.jones@richfield.co.za | password |
| Supervisor | michael.brown@richfield.co.za | password |
| Supervisor | linda.davis@richfield.co.za | password |
| Employer (Approved) | hr.nexus@richfield.co.za | password |
| Employer (Pending) | hr.brightworks@richfield.co.za | password |

Supervisor demo PIN: **1234**. These passwords and the PIN are for demonstration and testing only.

## Seed data

- One coordinator, two students, two supervisors and two employers (one approved, one pending)
- Three placement requests and one active placement
- Twelve signed duty logs, plus one rejected and one pending log.
- PIN and drawn signature sign off records, proof of work records and notifications

The active placement: John Smith (20240123) at Nexus Tech Solutions, supervised by
Michael Brown, 1 September 2026 to 30 November 2026, 96 signed hours of the 150 required.

## Proof files

Uploaded proof of work is saved in `uploads/proof/` (ignored by git). The seed rows
point at five files that are not in the repository, so on start up the backend
creates a labelled sample file for each one that is missing. Real uploads are never
touched. Turn this off with `wil.upload.create-missing-samples=false`.

## Configuration

Settings live in `src/main/resources/application.properties`. Any of them can be
overridden when starting the backend, for example `SERVER_PORT=8081` or
`SPRING_DATASOURCE_PASSWORD=yourpassword`, without editing the file.

| Setting | Default | Meaning |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/wil_connect` | The database |
| `spring.datasource.username` | `root` | Database username |
| `spring.datasource.password` | Your local MySQL password | Database password |
| `server.port` | `8080` | Port of the API and the web pages |
| `spring.web.resources.static-locations` | `file:frontend/,classpath:/static/` | Where the web pages are read from |
| `wil.upload.dir` | `uploads/proof` | Folder for proof of work files |
| `wil.upload.create-missing-samples` | `true` | Create sample files for seed proofs that are missing |
| `wil.cors.allowed-origins` | `http://localhost:*,http://127.0.0.1:*` | Other browser addresses allowed to call the API (only needed when pages are opened from a separate server) |
| `spring.servlet.multipart.max-file-size` | `5MB` | Largest proof file |

## API

Every endpoint, who may call it and what it returns is listed in [docs/api.md](docs/api.md).

- Login uses HTTP Basic: the login call checks the password, and every later request
  sends the same email and password. Passwords and PINs are stored as BCrypt hashes.
- Each role can only reach its own part of the API (`/api/students`, `/api/supervisors`,
  `/api/employers`, `/api/coordinator`). The logged in person is always found from the
  login, never from an ID in the request.
- 5 wrong passwords lock a login for 15 minutes, and 5 wrong PINs lock signing for 15 minutes.

### OpenAPI specification

The login, register and placement endpoints are defined first in OpenAPI and their
models are generated at build time:

- `src/main/resources/static/openapi/wil-connect-auth-api.yaml`
- `src/main/resources/static/openapi/wil-connect-placement-api.yaml`

Update the specification when you change those endpoints. The other endpoints are
described in [docs/api.md](docs/api.md).

## Running the tests

The tests run against MySQL, so load `schema.sql` and `seed.sql` first. The tests are designed to roll back their database changes so the seed rows remain unchanged and the suite can run again.

### Windows PowerShell

```powershell
.\mvnw.cmd test
```

### macOS/Linux

```bash
./mvnw test
```

To use another database or password without editing
`application.properties`:

### Windows PowerShell

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3306/wil_connect"
$env:SPRING_DATASOURCE_PASSWORD = "yourpassword"
.\mvnw.cmd test
```

### macOS/Linux

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/wil_connect SPRING_DATASOURCE_PASSWORD=yourpassword ./mvnw test
```

The suite currently contains 119 tests. What they cover is in [docs/testing/test-summary.md](docs/testing/test-summary.md).

## Project structure

```
src/main/java/wil_connect/
  config/        security, password hashing, sample proof files
  controller/    REST endpoints
  service/       business rules (registration, duty logs, sign off, attendance, notifications, report)
  security/      the logged in user and the lockout counter
  entity/        JPA entities (users, students, employers, supervisors, placements, requests)
  repository/    Spring Data repositories
src/main/resources/
  db/            schema.sql and seed.sql
  static/openapi OpenAPI specifications
src/test/java/   tests (see docs/testing)
frontend/        the web pages (see frontend/README.md)
docs/            API reference, testing and screenshots
```

## IntelliJ setup

1. Open the project folder in IntelliJ IDEA.
2. File, Project Structure, Project: set Project SDK and Language Level to 17.
   Under Modules, set the module SDK to 17 as well.
3. Settings, Build, Execution, Deployment, Build Tools, Maven:
   - Maven home path: Bundled (Maven 3); user settings file and local repository: Default.
   - Importing: set the JDK for importer to Java 17.
   - Runner: set the JRE to Java 17.
4. Open the Maven tool window and click Reload All Maven Projects.
