package wil_connect.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sign_off")
public class SignOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "signoff_id")
    private Long signoffId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "log_id", nullable = false)
    private DutyLog dutyLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private Supervisor supervisor;

    @Column(name = "signoff_date", nullable = false)
    private LocalDateTime signoffDate = LocalDateTime.now();

    @Column(name = "decision", nullable = false, length = 20)
    private String decision; // SIGNED | REJECTED

    @Column(name = "verification_method", length = 20)
    private String verificationMethod; // PIN | SIGNATURE | null when rejected

    @Column(name = "signature_data", columnDefinition = "MEDIUMTEXT")
    private String signatureData;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    public Long getSignoffId() {
        return signoffId;
    }

    public void setSignoffId(Long signoffId) {
        this.signoffId = signoffId;
    }

    public DutyLog getDutyLog() {
        return dutyLog;
    }

    public void setDutyLog(DutyLog dutyLog) {
        this.dutyLog = dutyLog;
    }

    public Supervisor getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(Supervisor supervisor) {
        this.supervisor = supervisor;
    }

    public LocalDateTime getSignoffDate() {
        return signoffDate;
    }

    public void setSignoffDate(LocalDateTime signoffDate) {
        this.signoffDate = signoffDate;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getVerificationMethod() {
        return verificationMethod;
    }

    public void setVerificationMethod(String verificationMethod) {
        this.verificationMethod = verificationMethod;
    }

    public String getSignatureData() {
        return signatureData;
    }

    public void setSignatureData(String signatureData) {
        this.signatureData = signatureData;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
