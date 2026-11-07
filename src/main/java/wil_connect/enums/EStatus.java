package wil_connect.enums;

public enum EStatus {

    APPROVED("Approved"),
    PENDING("Pending"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    MEETING_REQUESTED("Meeting Requested"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private final String value;

    EStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
