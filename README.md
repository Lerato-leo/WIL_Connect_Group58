Prerequisites

Before running the project, ensure you have installed:

Java JDK 17

1. All group members should use Java 17.
2. Verify your installation using java -version.

IntelliJ Maven Configuration
This project uses IntelliJ IDEA's bundled Maven distribution.
You do not need to install Maven separately.

Configure Maven

1. Open IntelliJ IDEA.
2. Navigate to File → Settings.
3. Open Build, Execution, Deployment → Build Tools → Maven.
4. Set Maven home path to: Bundled (Maven 3)
5. Set User settings file to Default.
6. Set Local repository to Default.

Configure Maven Importer

Navigate to:
Build Tools → Maven → Importing
Set the JDK for importer to Java 17.

Configure Maven Runner
Navigate to:
Build Tools → Maven → Runner
Set the JRE to Java 17.

Reload Maven
Open the Maven tool window.
Click Reload All Maven Projects.
Allow Maven to download the project dependencies.

1. Java SDK Configuration

Ensure Java 17 is configured.
In IntelliJ:
Navigate to File → Project Structure.
Select Project.
Set Project SDK to JDK 17.
Set Language Level to 17.
Under Modules, ensure the module SDK is also JDK 17.
The Java version must be consistent with the pom.xml configuration.

OpenAPI specification location
The main API specification is maintained at:
    'src/main/resources/static/openapi/wil-connect.yaml'

All developers should update this specification when introducing or changing API endpoints.
The specification defines:
1. API endpoints and HTTP methods.
2. Request bodies.
3. Response bodies.
4. Data types and validation constraints.
5. HTTP response codes.
7. API descriptions and operation names.
# WIL Connect Database

This folder contains the database files for the WIL Connect and Sign-Off Portal.

## Requirements

- MySQL 8.0.16 or newer
- MySQL Workbench is recommended
- MariaDB users must change the collation in `schema.sql` to: `utf8mb4_general_ci`

> Warning: Running `schema.sql` drops and recreates the `wil_connect`
> database. Do not run it if you need to preserve existing data.

## Import instructions

1. Open MySQL Workbench and connect to your local MySQL instance.
2. Run `schema.sql`.
3. Run `seed.sql`.
4. The database will contain the sample users, employers, placements,
   duty logs, proof records, sign-offs and notifications.

Run `schema.sql` again whenever you need a clean demonstration database.

## Test accounts

| Role                | Login email                    | Password |
| ------------------- | ------------------------------ | -------- |
| Coordinator         | coordinator@richfield.co.za    | password |
| Student             | john.smith@richfield.co.za     | password |
| Student             | sarah.jones@richfield.co.za    | password |
| Supervisor          | michael.brown@richfield.co.za  | password |
| Supervisor          | linda.davis@richfield.co.za    | password |
| Employer (Approved) | hr.nexus@richfield.co.za       | password |
| Employer (Pending)  | hr.brightworks@richfield.co.za | password |

Supervisor demo PIN: **1234**

These passwords and the PIN are for demonstration and testing only.

## Seed data

The seed data includes:

- One coordinator account
- Two student accounts
- Two supervisor accounts
- Two employer accounts
- One approved employer and one pending employer
- Three placement requests
- One active placement
- Twelve signed duty logs
- One rejected duty log
- One pending duty log
- PIN and digital-signature sign-off records
- System notifications

The active placement is:

- Student: John Smith
- Student number: 20240123
- Employer: Nexus Tech Solutions
- Supervisor: Michael Brown
- Placement period: 1 September 2026 to 30 November 2026
- Signed hours: 96 hours

## Proof files

Before testing the report screen or proof-of-work links, the team must create
the following folder in the application project:

uploads/proof/

Add placeholder files using these exact names:
2026-09-01_site_photo.jpg
2026-09-07_timesheet.pdf
2026-10-27_test_report.pdf
2026-10-29_site_photo.jpg
2026-10-29_timesheet.pdf

## Project structure

src/ for the Spring Boot backend, frontend/ for the HTML, CSS and JavaScript pages, docs/ for the documentation and screenshots.
