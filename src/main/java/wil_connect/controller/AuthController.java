package wil_connect.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.generated.api.AuthenticationApi;
import wil_connect.generated.model.RegisterRequest;
import wil_connect.generated.model.RegisterResponse;
import wil_connect.entity.UserAccount;
import wil_connect.service.AuthService;

@RestController
public class AuthController implements AuthenticationApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<RegisterResponse> registerUser(RegisterRequest registerRequest) {
        UserAccount user = authService.register(registerRequest.getEmail(), registerRequest.getPassword());
        RegisterResponse response = new RegisterResponse();

        response.setUserId(user.getUserId());
        response.setEmail(user.getEmail());
        response.setRole(RegisterResponse.RoleEnum.STUDENT);
        response.setMessage("Registration successful");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}