package wil_connect.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DutyLogRequest {

    @NotNull(message = "Placement ID is required")
    private Long placementId;

    @NotNull(message = "Log date is required")
    private LocalDate logDate;

    @NotNull(message = "Hours worked is required")
    @DecimalMin(value = "0.5", message = "Hours must be at least 0.5")
    @DecimalMax(value = "12.0", message = "Hours may not exceed 12")
    private BigDecimal hoursWorked;

    @NotBlank(message = "Task description is required")
    @Size(min = 10, max = 1000, message = "Task description must be 10 to 1000 characters")
    private String taskDescription;

    @Size(max = 500, message = "Skills learnt may not exceed 500 characters")
    private String skillsLearnt;

    public Long getPlacementId() {
        return placementId;
    }

    public void setPlacementId(Long placementId) {
        this.placementId = placementId;
    }

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
