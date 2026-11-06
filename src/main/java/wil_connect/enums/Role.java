package wil_connect.enums;

import java.util.Locale;

public enum Role {
    STUDENT,
    SUPERVISOR,
    EMPLOYER,
    COORDINATOR;

    public static Role fromString(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role cannot be empty");
        }

        return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
    }
}