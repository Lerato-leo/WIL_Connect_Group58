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

7. Java SDK Configuration

Ensure Java 17 is configured.
In IntelliJ:
    Navigate to File → Project Structure.
    Select Project.
    Set Project SDK to JDK 17.
    Set Language Level to 17.
Under Modules, ensure the module SDK is also JDK 17.
The Java version must be consistent with the pom.xml configuration.
