package wil_connect.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DutyLogResponse {

    private Long logId;
    private Long placementId;
    private LocalDate logDate;
    private BigDecimal hoursWorked;
    private String taskDescription;
    private String skillsLearnt;
    private String status;
    private LocalDateTime createdAt;
    private boolean editableWithin24h;
    private List<ProofDto> proofs = new ArrayList<>();
    private List<SignOffDto> signOffHistory = new ArrayList<>();

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

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

    public boolean isEditableWithin24h() {
        return editableWithin24h;
    }

    public void setEditableWithin24h(boolean editableWithin24h) {
        this.editableWithin24h = editableWithin24h;
    }

    public List<ProofDto> getProofs() {
        return proofs;
    }

    public void setProofs(List<ProofDto> proofs) {
        this.proofs = proofs;
    }

    public List<SignOffDto> getSignOffHistory() {
        return signOffHistory;
    }

    public void setSignOffHistory(List<SignOffDto> signOffHistory) {
        this.signOffHistory = signOffHistory;
    }

    public static class ProofDto {
        private Long proofId;
        private String filePath;
        private String originalFilename;
        private String mimeType;
        private Long fileSizeBytes;
        private LocalDateTime uploadDate;

        public Long getProofId() {
            return proofId;
        }

        public void setProofId(Long proofId) {
            this.proofId = proofId;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public void setOriginalFilename(String originalFilename) {
            this.originalFilename = originalFilename;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public Long getFileSizeBytes() {
            return fileSizeBytes;
        }

        public void setFileSizeBytes(Long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
        }

        public LocalDateTime getUploadDate() {
            return uploadDate;
        }

        public void setUploadDate(LocalDateTime uploadDate) {
            this.uploadDate = uploadDate;
        }
    }

    public static class SignOffDto {
        private Long signoffId;
        private LocalDateTime signoffDate;
        private String decision;
        private String verificationMethod;
        private String rejectionReason;

        public Long getSignoffId() {
            return signoffId;
        }

        public void setSignoffId(Long signoffId) {
            this.signoffId = signoffId;
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

        public String getRejectionReason() {
            return rejectionReason;
        }

        public void setRejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
        }
    }
}
