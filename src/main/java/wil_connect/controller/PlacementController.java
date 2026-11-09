package wil_connect.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.entity.Placement;
import wil_connect.generated.placement.api.PlacementsApi;
import wil_connect.generated.placement.model.PlacementResponse;
import wil_connect.repository.PlacementRepository;
import wil_connect.security.CurrentUser;

import java.util.Comparator;
import java.util.List;

/**
 * Placements of the logged in student or employer.
 */
@RestController
@RequiredArgsConstructor
public class PlacementController implements PlacementsApi {

    private final PlacementRepository placementRepository;
    private final CurrentUser currentUser;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<PlacementResponse>> getEmployerPlacements() {
        return ResponseEntity.ok(toResponses(placementRepository.findByEmployer_EmployerId(currentUser.employerId())));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<PlacementResponse>> getMyPlacements() {
        return ResponseEntity.ok(toResponses(placementRepository.findByStudent_StudentId(currentUser.studentId())));
    }

    private static List<PlacementResponse> toResponses(List<Placement> placements) {
        return placements.stream()
                .sorted(Comparator.comparing(Placement::getStartDate).reversed())
                .map(PlacementController::toResponse)
                .toList();
    }

    static PlacementResponse toResponse(Placement placement) {
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
        response.setStatus(PlacementResponse.StatusEnum.fromValue(placement.getStatus()));
        return response;
    }
}
