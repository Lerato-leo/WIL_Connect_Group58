package wil_connect.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.entity.Employer;

import wil_connect.generated.placement.api.EmployerSearchApi;
import wil_connect.generated.placement.model.EmployerSummary;

import wil_connect.service.PlacementRequestService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EmployerSearchController implements EmployerSearchApi {

    private final PlacementRequestService placementRequestService;

    /**
     * Searches approved employers.
     *
     * @param search optional employer name search keyword
     * @return list of approved employers
     */
    @Override
    public ResponseEntity<List<EmployerSummary>> searchApprovedEmployers(String search) {
        List<EmployerSummary> responses = placementRequestService.searchEmployers(search)
                        .stream()
                        .map(this::toEmployerSummary)
                        .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Converts an employer entity into its generated API response.
     *
     * @param employer employer entity
     * @return employer summary
     */
    private EmployerSummary toEmployerSummary(Employer employer) {
        EmployerSummary response = new EmployerSummary();
        response.setEmployerId(employer.getEmployerId());
        response.setEmployerName(employer.getEmployerName());
        response.setAddress(employer.getAddress());
        response.setContactEmail(employer.getContactEmail());
        response.setContactNumber(employer.getContactNumber());
        response.setStatus(EmployerSummary.StatusEnum.fromValue(employer.getStatus()));
        return response;
    }
}