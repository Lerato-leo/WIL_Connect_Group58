package wil_connect.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The form fields of a duty log (submitDutyLog, 4.6.5). Sent as multipart form data
 * with up to 3 files in the "proof" field. The placement is the student's active
 * placement, found on the server.
 */
public class DutyLogRequest {

    @NotNull(message = "Enter a valid date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate logDate;

    @NotNull(message = "Hours must be between 0.5 and 12")
    @DecimalMin(value = "0.5", message = "Hours must be between 0.5 and 12")
    @DecimalMax(value = "12.0", message = "Hours must be between 0.5 and 12")
    private BigDecimal hoursWorked;

    @NotBlank(message = "Describe the task in 10 to 1000 characters")
    @Size(min = 10, max = 1000, message = "Describe the task in 10 to 1000 characters")
    private String taskDescription;

    // Optional. Saved as an empty text when left out, because the column is NOT NULL.
    @Size(max = 500, message = "Skills learnt may not exceed 500 characters")
    private String skillsLearnt;

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
    }

    public BigDecimal getHoursWorked() {
        return hoursWorked;
    }

    public void setHoursWorked(BigDecimal hoursWorked) {
        this.hoursWorked = hoursWorked;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getSkillsLearnt() {
        return skillsLearnt;
    }

    public void setSkillsLearnt(String skillsLearnt) {
        this.skillsLearnt = skillsLearnt;
    }
}
