package wil_connect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import wil_connect.dto.DutyLogRequest;
import wil_connect.dto.DutyLogResponse;
import wil_connect.entity.DutyLog;
import wil_connect.entity.Placement;
import wil_connect.entity.ProofOfWork;
import wil_connect.repository.DutyLogRepository;
import wil_connect.repository.PlacementRepository;
import wil_connect.repository.ProofOfWorkRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * C4 – Daily duty log submission, proof upload and 24-hour correction.
 */
@Service
public class DutyLogService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "application/pdf");
    private static final long MAX_FILE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final int MAX_FILES = 3;

    private final DutyLogRepository dutyLogRepo;
    private final ProofOfWorkRepository proofRepo;
    private final PlacementRepository placementRepo;
    private final NotificationService notificationService;

    @Value("${wil.upload.dir:uploads/proof}")
    private String uploadDir;

    public DutyLogService(DutyLogRepository dutyLogRepo,
                          ProofOfWorkRepository proofRepo,
                          PlacementRepository placementRepo,
                          NotificationService notificationService) {
        this.dutyLogRepo = dutyLogRepo;
        this.proofRepo = proofRepo;
        this.placementRepo = placementRepo;
        this.notificationService = notificationService;
    }

    /**
     * Submit a new duty log or correct an existing PENDING log within 24 hours.
     */
    @Transactional
    public DutyLogResponse submitDutyLog(Long studentId,
                                         DutyLogRequest req,
                                         List<MultipartFile> files) {

        Placement placement = placementRepo.findById(req.getPlacementId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement not found"));

        // Ownership check
        if (!placement.getStudent().getStudentId().equals(studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your placement");
        }
        if (!"ACTIVE".equalsIgnoreCase(placement.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Placement is not active");
        }

        LocalDate logDate = req.getLogDate();
        if (logDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date cannot be in the future");
        }
        if (logDate.isBefore(placement.getStartDate()) || logDate.isAfter(placement.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date outside placement period");
        }

        // Look for existing log on that date
        DutyLog log = dutyLogRepo
                .findByPlacementPlacementIdAndLogDate(placement.getPlacementId(), logDate)
                .orElse(null);

        if (log == null) {
            // Brand-new log
            log = new DutyLog();
            log.setPlacement(placement);
            log.setLogDate(logDate);
            log.setStatus("PENDING");
            log.setCreatedAt(LocalDateTime.now());
        } else {
            // Correction path – only allowed while PENDING and inside 24 h
            if (!"PENDING".equals(log.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only PENDING logs can be corrected");
            }
            if (log.getCreatedAt().plusHours(24).isBefore(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "24-hour correction window has closed");
            }
            // Clear previous proofs so the new set replaces them
            log.getProofs().clear();
        }

        log.setHoursWorked(req.getHoursWorked());
        log.setTaskDescription(req.getTaskDescription());
        log.setSkillsLearnt(req.getSkillsLearnt());
        log.setUpdatedAt(LocalDateTime.now());
        log.setStatus("PENDING"); // always returns to PENDING on correction

        dutyLogRepo.save(log);

        // File handling
        if (files != null && !files.isEmpty()) {
            if (files.size() > MAX_FILES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Maximum " + MAX_FILES + " proof files allowed");
            }
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                storeProof(log, file);
            }
        }

        // Notify supervisor
        notificationService.notifySupervisorNewLog(
                placement.getSupervisor().getSupervisorId(),
                placement.getStudent().getFirstName() + " " + placement.getStudent().getLastName(),
                log.getLogDate());

        return toResponse(log);
    }

    private void storeProof(DutyLog log, MultipartFile file) {
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPG, PNG or PDF files are allowed");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each file must be 5 MB or smaller");
        }

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String safeName = log.getLogDate() + "_" + UUID.randomUUID()
                    + getExtension(file.getOriginalFilename());
            Path target = dir.resolve(safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            ProofOfWork proof = new ProofOfWork();
            proof.setDutyLog(log);
            proof.setFilePath(safeName); // relative path stored
            proof.setOriginalFilename(file.getOriginalFilename());
            proof.setMimeType(mime);
            proof.setFileSizeBytes(file.getSize());
            proof.setUploadDate(LocalDateTime.now());
            log.getProofs().add(proof);
            proofRepo.save(proof);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store proof file");
        }
    }

    private String getExtension(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i) : "";
    }

    public List<DutyLogResponse> getLogsForStudent(Long studentId) {
        return dutyLogRepo.findByStudentId(studentId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private DutyLogResponse toResponse(DutyLog log) {
        DutyLogResponse r = new DutyLogResponse();
        r.setLogId(log.getLogId());
        r.setPlacementId(log.getPlacement().getPlacementId());
        r.setLogDate(log.getLogDate());
        r.setHoursWorked(log.getHoursWorked());
        r.setTaskDescription(log.getTaskDescription());
        r.setSkillsLearnt(log.getSkillsLearnt());
        r.setStatus(log.getStatus());
        r.setCreatedAt(log.getCreatedAt());
        r.setEditableWithin24h(
                "PENDING".equals(log.getStatus())
                && log.getCreatedAt().plusHours(24).isAfter(LocalDateTime.now()));

        // Map proofs
        if (log.getProofs() != null) {
            r.setProofs(log.getProofs().stream().map(p -> {
                DutyLogResponse.ProofDto dto = new DutyLogResponse.ProofDto();
                dto.setProofId(p.getProofId());
                dto.setFilePath(p.getFilePath());
                dto.setOriginalFilename(p.getOriginalFilename());
                dto.setMimeType(p.getMimeType());
                dto.setFileSizeBytes(p.getFileSizeBytes());
                dto.setUploadDate(p.getUploadDate());
                return dto;
            }).collect(Collectors.toList()));
        }

        // Map sign-off history
        if (log.getSignOffs() != null) {
            r.setSignOffHistory(log.getSignOffs().stream().map(s -> {
                DutyLogResponse.SignOffDto dto = new DutyLogResponse.SignOffDto();
                dto.setSignoffId(s.getSignoffId());
                dto.setSignoffDate(s.getSignoffDate());
                dto.setDecision(s.getDecision());
                dto.setVerificationMethod(s.getVerificationMethod());
                dto.setRejectionReason(s.getRejectionReason());
                return dto;
            }).collect(Collectors.toList()));
        }

        return r;
    }
}
