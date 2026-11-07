
package wil_connect.controller.service;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import wil_connect.entity.Employer;
import wil_connect.entity.Placement;
import wil_connect.entity.PlacementRequest;
import wil_connect.entity.Student;
import wil_connect.entity.Supervisor;

import wil_connect.enums.EStatus;

import wil_connect.generated.placement.model.AcceptPlacementRequest;
import wil_connect.generated.placement.model.MeetingRequest;
import wil_connect.repository.EmployerRepository;
import wil_connect.repository.PlacementRepository;
import wil_connect.repository.PlacementRequestRepository;
import wil_connect.repository.StudentRepository;
import wil_connect.repository.SupervisorRepository;

import wil_connect.service.PlacementRequestService;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PlacementRequestServiceTest {

    @Autowired
    private PlacementRequestService placementRequestService;

    @Autowired
    private PlacementRequestRepository requestRepository;

    @Autowired
    private PlacementRepository placementRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EmployerRepository employerRepository;

    @Autowired
    private SupervisorRepository supervisorRepository;


    // ============================================================
    // TC-001: Student searches for approved employers
    // Expected: Only Nexus Tech Solutions is returned.
    // ============================================================

    @Test
    void shouldReturnOnlyApprovedEmployers() {

        List<Employer> result = placementRequestService.searchEmployers(null);
        assertNotNull(result);
        assertEquals(1, result.size());

        assertEquals("Nexus Tech Solutions", result.get(0).getEmployerName());
        assertEquals(EStatus.APPROVED.getValue(), result.get(0).getStatus());
    }


    // ============================================================
    // TC-002: Student successfully submits a placement request
    // Student: Sarah Jones (User ID 3)
    // Employer: Nexus Tech Solutions (Employer ID 1)
    // Expected: Placement request is created with Pending status.
    // ============================================================

    @Test
    void shouldSuccessfullyCreatePlacementRequest() {

        Student student = studentRepository.findByUserAccount_UserId(3L).orElseThrow();
        Employer employer = employerRepository.findById(1).orElseThrow();

        // Sarah already has a Meeting Requested request for Nexus.
        // Change its status so that a new request can be submitted.
        List<PlacementRequest> existingRequests = requestRepository.findByStudent_UserAccount_UserId(3L);
        existingRequests.forEach(request -> request.setStatus(EStatus.REJECTED.getValue()));

        requestRepository.saveAll(existingRequests);
        PlacementRequest result = placementRequestService.createRequest(3L, 1);

        assertNotNull(result);
        assertSame(student, result.getStudent());
        assertSame(employer, result.getEmployer());
        assertEquals(EStatus.PENDING.getValue(), result.getStatus());
        assertEquals(LocalDate.now(), result.getRequestDate());
        assertTrue(requestRepository.existsById(result.getRequestId()));
    }


    // ============================================================
    // TC-003: Employer accepts a placement request and creates
    // a placement.
    //
    // Student: Sarah Jones
    // Employer: Nexus Tech Solutions
    // Supervisor: Michael Brown (Supervisor ID 1)
    // Request: ID 2 (Meeting Requested)
    //
    // Expected:
    // 1. Request status changes to Accepted.
    // 2. A placement is created.
    // 3. Placement status is Active.
    // ============================================================

    @Test
    void shouldAcceptPlacementRequestAndCreatePlacement() {
        PlacementRequest request = requestRepository.findById(2).orElseThrow();
        Employer employer = employerRepository.findByUserAccount_UserId(6L).orElseThrow();
        Supervisor supervisor = supervisorRepository.findById(1).orElseThrow();

        AcceptPlacementRequest details = new AcceptPlacementRequest();
        details.setSupervisorId(1);
        details.setStartDate(LocalDate.of(2026, 12, 1));
        details.setEndDate(LocalDate.of(2027, 2, 28));

        Placement result = placementRequestService.acceptRequest(2, 6L, details);
        assertNotNull(result);
        assertEquals(EStatus.ACCEPTED.getValue(), request.getStatus());
        assertSame(request.getStudent(), result.getStudent());
        assertSame(employer, result.getEmployer());
        assertSame(supervisor, result.getSupervisor());
        assertEquals(details.getStartDate(), result.getStartDate());
        assertEquals(details.getEndDate(), result.getEndDate());
        assertEquals(EStatus.ACTIVE.getValue(), result.getStatus());
        assertTrue(placementRepository.existsById(result.getPlacementId()));
    }

    // ============================================================
    // TC-004: Employer rejects a placement request
    //
    // Student: John Smith
    // Employer: Nexus Tech Solutions
    // Request: A Pending request
    //
    // Expected:
    // 1. Request status changes to Rejected.
    // 2. No placement is created.
    // ============================================================

    @Test
    void shouldRejectPlacementRequestSuccessfully() {

        // Given
        PlacementRequest request = new PlacementRequest();
        request.setStudent(studentRepository.findByUserAccount_UserId(2L).orElseThrow());
        request.setEmployer(employerRepository.findById(1).orElseThrow());
        request.setRequestDate(LocalDate.now());
        request.setStatus(EStatus.PENDING.getValue());
        request = requestRepository.saveAndFlush(request);

        Integer requestId = request.getRequestId();
        placementRequestService.rejectRequest(requestId, 6L);

        PlacementRequest updatedRequest = requestRepository.findById(requestId).orElseThrow();
        assertEquals(EStatus.REJECTED.getValue(), updatedRequest.getStatus());
        assertFalse(placementRepository.existsById(requestId));
    }

    // ============================================================
    // TC-005: Employer requests a meeting with a student
    //
    // Student: John Smith
    // Employer: Nexus Tech Solutions
    // Request: A Pending request
    //
    // Expected:
    // 1. Request status changes to Meeting Requested.
    // ============================================================

    @Test
    void shouldRequestMeetingSuccessfully() {

        PlacementRequest request = new PlacementRequest();
        request.setStudent(studentRepository.findByUserAccount_UserId(2L).orElseThrow());
        request.setEmployer(employerRepository.findById(1).orElseThrow());
        request.setRequestDate(LocalDate.now());
        request.setStatus(EStatus.PENDING.getValue());

        request = requestRepository.saveAndFlush(request);
        Integer requestId = request.getRequestId();
        MeetingRequest details = new MeetingRequest();
        details.setMessage("We would like to arrange an interview.");

        placementRequestService.requestMeeting(requestId, 6L, details);
        PlacementRequest updatedRequest = requestRepository.findById(requestId).orElseThrow();
        assertEquals(EStatus.MEETING_REQUESTED.getValue(), updatedRequest.getStatus());
    }

}
