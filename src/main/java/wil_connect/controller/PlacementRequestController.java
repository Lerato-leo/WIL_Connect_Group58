package wil_connect.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import wil_connect.entity.Placement;
import wil_connect.entity.PlacementRequest;
import wil_connect.entity.UserAccount;

import wil_connect.generated.placement.api.EmployerPlacementRequestsApi;
import wil_connect.generated.placement.api.StudentPlacementRequestsApi;

import wil_connect.generated.placement.model.AcceptPlacementRequest;
import wil_connect.generated.placement.model.CreatePlacementRequest;
import wil_connect.generated.placement.model.EmployerPlacementRequestResponse;
import wil_connect.generated.placement.model.MeetingRequest;
import wil_connect.generated.placement.model.PlacementRequestResponse;
import wil_connect.generated.placement.model.PlacementResponse;

import wil_connect.repository.UserAccountRepository;
import wil_connect.service.PlacementRequestService;
import wil_connect.validation.ErrorMessages;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlacementRequestController implements StudentPlacementRequestsApi, EmployerPlacementRequestsApi {

    private final PlacementRequestService placementRequestService;
    private final UserAccountRepository userAccountRepository;

    /**
     * Creates a placement request for the authenticated student.
     *
     * @param createPlacementRequest request details
     * @return created placement request
     */
    @Override
    public ResponseEntity<PlacementRequestResponse> createPlacementRequest(CreatePlacementRequest createPlacementRequest) {
        Long authenticatedUserId = getAuthenticatedUserId();
        PlacementRequest request = placementRequestService.createRequest(authenticatedUserId, createPlacementRequest.getEmployerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlacementRequestResponse(request));
    }

    /**
     * Retrieves all placement requests submitted by the authenticated student.
     *
     * @return student's placement requests
     */
    @Override
    public ResponseEntity<List<PlacementRequestResponse>> getMyPlacementRequests() {
        Long authenticatedUserId = getAuthenticatedUserId();
        List<PlacementRequestResponse> responses =
                placementRequestService.getMyPlacementRequests(authenticatedUserId)
                        .stream()
                        .map(this::toPlacementRequestResponse)
                        .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves placement requests received by the authenticated employer.
     *
     * @param status optional request status filter
     * @return employer's placement requests
     */
    @Override
    public ResponseEntity<List<EmployerPlacementRequestResponse>> getEmployerPlacementRequests(String status) {

        Long authenticatedUserId = getAuthenticatedUserId();
        List<EmployerPlacementRequestResponse> responses =
                placementRequestService.getEmployerPlacementRequests(authenticatedUserId, status)
                        .stream()
                        .map(this::toEmployerPlacementRequestResponse)
                        .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Accepts a student's placement request and creates a placement.
     *
     * @param requestId placement request ID
     * @param acceptPlacementRequest acceptance details
     * @return created placement
     */
    @Override
    public ResponseEntity<PlacementResponse> acceptPlacementRequest(Integer requestId, AcceptPlacementRequest acceptPlacementRequest) {
        Long authenticatedUserId = getAuthenticatedUserId();
        Placement placement = placementRequestService.acceptRequest(
                requestId,
                authenticatedUserId,
                acceptPlacementRequest
        );

        return ResponseEntity.ok(toPlacementResponse(placement));
    }

    /**
     * Rejects a student's placement request.
     *
     * @param requestId placement request ID
     * @return updated placement request
     */
    @Override
    public ResponseEntity<PlacementRequestResponse> rejectPlacementRequest(Integer requestId) {
        Long authenticatedUserId = getAuthenticatedUserId();
        PlacementRequest request = placementRequestService.rejectRequest(
                requestId,
                authenticatedUserId
        );
        return ResponseEntity.ok(toPlacementRequestResponse(request));
    }

    /**
     * Requests a meeting with the student.
     *
     * @param requestId placement request ID
     * @param meetingRequest meeting message
     * @return updated placement request
     */
    @Override
    public ResponseEntity<PlacementRequestResponse> requestMeeting(Integer requestId, MeetingRequest meetingRequest) {
        Long authenticatedUserId = getAuthenticatedUserId();
        PlacementRequest request = placementRequestService.requestMeeting(
                requestId,
                authenticatedUserId,
                meetingRequest
        );

        return ResponseEntity.ok(toPlacementRequestResponse(request));
    }

    /**
     * Retrieves the ID of the currently authenticated user.
     *
     * @return authenticated user's ID
     */
    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ErrorMessages.USER_ID_REQUIRED);
        }

        UserAccount userAccount = userAccountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.USER_NOT_FOUND));

        return Long.valueOf(userAccount.getUserId());
    }

    /**
     * Converts a placement request entity into its generated API response.
     *
     * @param request placement request entity
     * @return placement request response
     */
    private PlacementRequestResponse toPlacementRequestResponse(PlacementRequest request) {

        PlacementRequestResponse response = new PlacementRequestResponse();
        response.setRequestId(request.getRequestId());
        response.setEmployerId(request.getEmployer().getEmployerId());
        response.setEmployerName(request.getEmployer().getEmployerName());
        response.setRequestDate(request.getRequestDate());
        response.setStatus(PlacementRequestResponse.StatusEnum.valueOf(request.getStatus()));
        return response;
    }

    /**
     * Converts a placement request entity into the employer-facing response.
     *
     * @param request placement request entity
     * @return employer placement request response
     */
    private EmployerPlacementRequestResponse toEmployerPlacementRequestResponse(PlacementRequest request) {

        EmployerPlacementRequestResponse response = new EmployerPlacementRequestResponse();
        response.setRequestId(request.getRequestId());
        response.setEmployerId(request.getEmployer().getEmployerId());
        response.setEmployerName(request.getEmployer().getEmployerName());
        response.setRequestDate(request.getRequestDate());
        response.setStatus(EmployerPlacementRequestResponse.StatusEnum.valueOf(request.getStatus()));

        response.setStudentId(request.getStudent().getStudentId());
        response.setStudentNumber(request.getStudent().getStudentNumber());
        response.setStudentFirstName(request.getStudent().getFirstName());
        response.setStudentLastName(request.getStudent().getLastName());

        return response;
    }

    /**
     * Converts a placement entity into its generated API response.
     *
     * @param placement placement entity
     * @return placement response
     */
    private PlacementResponse toPlacementResponse(Placement placement) {
        PlacementResponse response = new PlacementResponse();
        response.setPlacementId(placement.getPlacementId());
        response.setStudentId(placement.getStudent().getStudentId());
        response.setStudentName(placement.getStudent().getFirstName() + " " + placement.getStudent().getLastName());
        response.setStudentNumber(placement.getStudent().getStudentNumber());
        response.setEmployerId(placement.getEmployer().getEmployerId());
        response.setEmployerName(placement.getEmployer().getEmployerName());
        response.setSupervisorId(placement.getSupervisor().getSupervisorId());
        response.setSupervisorName(placement.getSupervisor().getFirstName() + " " + placement.getSupervisor().getLastName());
        response.setStartDate(placement.getStartDate());
        response.setEndDate(placement.getEndDate());
        response.setStatus(PlacementResponse.StatusEnum.valueOf(placement.getStatus()));
        return response;
    }
}