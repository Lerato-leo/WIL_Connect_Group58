package wil_connect.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.generated.placement.api.PlacementsApi;
import wil_connect.generated.placement.model.PlacementResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlacementController implements PlacementsApi {

    @Override
    public ResponseEntity<List<PlacementResponse>> getEmployerPlacements () {
        return null;
    }

    @Override
    public ResponseEntity<List<PlacementResponse>> getMyPlacements () {
        return null;
    }
}