package wil_connect.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "duty_log",
       uniqueConstraints = @UniqueConstraint(columnNames = {"placement_id", "log_date"}))
public class DutyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "placement_id", nullable = false)
    private Placement placement;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "task_description", nullable = false, columnDefinition = "TEXT")
    private String taskDescription;

    @Column(name = "skills_learnt", columnDefinition = "TEXT")
    private String skillsLearnt;

    @Column(name = "hours_worked", nullable = false, precision = 5, scale = 2)
    private BigDecimal hoursWorked;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // PENDING | SIGNED | REJECTED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "dutyLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProofOfWork> proofs = new ArrayList<>();

    @OneToMany(mappedBy = "dutyLog", cascade = CascadeType.ALL)
    private List<SignOff> signOffs = new ArrayList<>();

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Placement getPlacement() {
        return placement;
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
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

    public BigDecimal getHoursWorked() {
        return hoursWorked;
    }

    public void setHoursWorked(BigDecimal hoursWorked) {
        this.hoursWorked = hoursWorked;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ProofOfWork> getProofs() {
        return proofs;
    }

    public void setProofs(List<ProofOfWork> proofs) {
        this.proofs = proofs;
    }

    public List<SignOff> getSignOffs() {
        return signOffs;
    }

    public void setSignOffs(List<SignOff> signOffs) {
        this.signOffs = signOffs;
    }
}
