
package wil_connect.validation;

/**
 * Centralised application error messages.
 *
 * <p>This class contains reusable error messages used throughout
 * the WIL Connect application.</p>
 *
 * <p>All constants are public so they can be accessed by services,
 * controllers and exception handlers.</p>
 */
public final class ErrorMessages {


    /**
     * Prevents instantiation of this constants class.
     */
    private ErrorMessages() {throw new IllegalStateException("Constants class");}
    public static final String USER_NOT_FOUND = "user not found";
    public static final String STUDENT_NOT_FOUND = "Student not found";
    public static final String EMPLOYER_NOT_FOUND = "Employer not found";
    public static final String SUPERVISOR_NOT_FOUND = "Supervisor not found";
    public static final String REQUEST_NOT_FOUND = "Placement request not found";
    public static final String EMPLOYER_NOT_APPROVED = "Employer is not approved";
    public static final String DUPLICATE_REQUEST = "You already have an active request with this employer";
    public static final String ACTIVE_PLACEMENT_EXISTS = "Student already has an active placement with this employer";
    public static final String REQUEST_ALREADY_PROCESSED = "This request has already been processed";
    public static final String REQUIRED_ACCEPTANCE_DETAILS = "Supervisor, start date and end date are required";
    public static final String INVALID_DATE_RANGE = "End date cannot be before start date";
    public static final String SUPERVISOR_EMPLOYER_MISMATCH = "Supervisor does not belong to this employer";
    public static final String MEETING_NOT_ALLOWED = "Meeting can only be requested for a pending request";
    public static final String MEETING_MESSAGE_REQUIRED = "Meeting message is required";
    public static final String UNAUTHORIZED_EMPLOYER_REQUEST = "You cannot manage another employer's requests";
    public static final String USER_ID_REQUIRED = "Authenticated user ID is required";
    public static final String REQUEST_ID_REQUIRED = "Placement request ID is required";
    public static final String EMPLOYER_ID_REQUIRED = "Employer ID is required";
}
