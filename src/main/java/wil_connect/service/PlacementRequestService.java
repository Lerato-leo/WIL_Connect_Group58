
package wil_connect.service;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import wil_connect.entity.*;
import wil_connect.enums.EStatus;
import wil_connect.repository.*;

import wil_connect.generated.placement.model.MeetingRequest;
import wil_connect.generated.placement.model.AcceptPlacementRequest;
import wil_connect.validation.ErrorMessages;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlacementRequestService {

    private final PlacementRequestRepository requestRepository;
    private final PlacementRepository placementRepository;
    private final StudentRepository studentRepository;
    private final EmployerRepository employerRepository;
    private final SupervisorRepository supervisorRepository;

    @Transactional(readOnly = true)
    public List<Employer> searchEmployers(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return employerRepository.findByStatusIgnoreCase(EStatus.APPROVED.getValue());
        }
        return employerRepository.findByEmployerNameContainingIgnoreCaseAndStatusIgnoreCase(keyword.trim(), EStatus.APPROVED.getValue());
    }

    @Transactional
    public PlacementRequest createRequest(Long authenticatedUserId, Integer employerId) {
        Student student = studentRepository.findByUserAccount_UserId(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.STUDENT_NOT_FOUND));

        Employer employer = employerRepository.findById(employerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.EMPLOYER_NOT_FOUND));

        ValidateStudentRequest(employerId, employer, student);
        PlacementRequest request = new PlacementRequest();
        request.setStudent(student);
        request.setEmployer(employer);
        request.setRequestDate(LocalDate.now());
        request.setStatus(EStatus.PENDING.getValue());

        return requestRepository.save(request);
    }

    @Transactional
    public Placement acceptRequest(Integer requestId, Long authenticatedUserId, AcceptPlacementRequest details) {
        PlacementRequest request = getEmployerRequest(requestId, authenticatedUserId);
        Supervisor supervisor = validateSupervisor(details, request);
        Placement placement = new Placement();
        placement.setStudent(request.getStudent());
        placement.setEmployer(request.getEmployer());
        placement.setSupervisor(supervisor);
        placement.setStartDate(details.getStartDate());
        placement.setEndDate(details.getEndDate());
        placement.setStatus(EStatus.ACTIVE.getValue());
        request.setStatus(EStatus.ACCEPTED.getValue());

        requestRepository.save(request);
        return placementRepository.save(placement);
    }



    // Employer rejects a request
    @Transactional
    public PlacementRequest rejectRequest(Integer requestId, Long authenticatedUserId) {
        PlacementRequest request = getEmployerRequest(requestId, authenticatedUserId);
        if (!EStatus.PENDING.getValue().equals(request.getStatus()) && !EStatus.MEETING_REQUESTED.getValue().equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.REQUEST_ALREADY_PROCESSED);
        }
        request.setStatus(EStatus.REJECTED.getValue());
        return requestRepository.save(request);
    }


    @Transactional
    public PlacementRequest requestMeeting(Integer requestId, Long authenticatedUserId, MeetingRequest details) {
        PlacementRequest request = getEmployerRequest(requestId, authenticatedUserId);
        if (!EStatus.PENDING.getValue().equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.MEETING_NOT_ALLOWED
            );
        }

        if (details == null || details.getMessage() == null || details.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorMessages.MEETING_MESSAGE_REQUIRED);
        }

        // Update placement request status
        request.setStatus(EStatus.MEETING_REQUESTED.getValue());
        return requestRepository.save(request);
    }

    /**
     * Retrieves all placement requests submitted by the authenticated student.
     *
     * @param authenticatedUserId ID of the authenticated user
     * @return List of the student's placement requests
     */
    @Transactional(readOnly = true)
    public List<PlacementRequest> getMyPlacementRequests(Long authenticatedUserId) {
        studentRepository.findByUserAccount_UserId(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.STUDENT_NOT_FOUND));

        return requestRepository.findByStudent_UserAccount_UserId(authenticatedUserId);
    }

    /**
     * Retrieves placement requests received by the authenticated employer.
     * An optional status can be supplied to filter the results.
     *
     * @param authenticatedUserId ID of the authenticated user
     * @param status Optional placement request status
     * @return List of the employer's placement requests
     */
    @Transactional(readOnly = true)
    public List<PlacementRequest> getEmployerPlacementRequests(Long authenticatedUserId, String status) {

        employerRepository.findByUserAccount_UserId(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.EMPLOYER_NOT_FOUND));

        List<PlacementRequest> requests = requestRepository.findByEmployer_UserAccount_UserId(authenticatedUserId);
        if (status == null || status.isBlank()) {
            return requests;
        }

        String requestedStatus = status.trim();
        return requests.stream()
                .filter(request -> request.getStatus() != null && request.getStatus().equalsIgnoreCase(requestedStatus))
                .toList();
    }


    private PlacementRequest getEmployerRequest(Integer requestId, Long authenticatedUserId) {
        PlacementRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.REQUEST_NOT_FOUND));

        Employer employer = employerRepository.findByUserAccount_UserId(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.EMPLOYER_NOT_FOUND));

        if (!request.getEmployer().getEmployerId().equals(employer.getEmployerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ErrorMessages.UNAUTHORIZED_EMPLOYER_REQUEST);
        }
        return request;
    }

    private void ValidateStudentRequest (Integer employerId, Employer employer, Student student) {
        if (!EStatus.APPROVED.getValue().equalsIgnoreCase(employer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorMessages.EMPLOYER_NOT_APPROVED);
        }
        List<String> activeRequestStatuses = List.of(EStatus.PENDING.getValue(), EStatus.MEETING_REQUESTED.getValue());
        boolean isActiveRequest = requestRepository.existsByStudent_StudentIdAndEmployer_EmployerIdAndStatusIn(student.getStudentId(), employerId, activeRequestStatuses);
        if (isActiveRequest) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.DUPLICATE_REQUEST);
        }
        boolean isActivePlacement = placementRepository.existsByStudent_StudentIdAndEmployer_EmployerIdAndStatus(student.getStudentId(), employerId, EStatus.ACTIVE.getValue());
        if (isActivePlacement) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.ACTIVE_PLACEMENT_EXISTS);
        }
    }

    private Supervisor validateSupervisor (AcceptPlacementRequest details, PlacementRequest request) {
        if (!EStatus.PENDING.getValue().equals(request.getStatus()) && !EStatus.MEETING_REQUESTED.getValue().equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.REQUEST_ALREADY_PROCESSED);
        }
        if (details == null || details.getSupervisorId() == null || details.getStartDate() == null || details.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorMessages.REQUIRED_ACCEPTANCE_DETAILS);
        }
        if (details.getEndDate().isBefore(details.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_DATE_RANGE);
        }
        Supervisor supervisor = supervisorRepository.findById(details.getSupervisorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.SUPERVISOR_NOT_FOUND));

        if (supervisor.getEmployer() == null || !supervisor.getEmployer().getEmployerId().equals(request.getEmployer().getEmployerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorMessages.SUPERVISOR_EMPLOYER_MISMATCH);
        }
        boolean isaActivePlacementWithEmployer = placementRepository
                .existsByStudent_StudentIdAndEmployer_EmployerIdAndStatus(request.getStudent().getStudentId(), request.getEmployer().getEmployerId(), EStatus.ACTIVE.getValue());
        if (isaActivePlacementWithEmployer) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.ACTIVE_PLACEMENT_EXISTS);
        }
        return supervisor;
    }
}
