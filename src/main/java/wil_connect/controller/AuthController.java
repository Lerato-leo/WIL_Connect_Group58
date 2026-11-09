package wil_connect.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.generated.api.AuthenticationApi;
import wil_connect.generated.model.LoginRequest;
import wil_connect.generated.model.LoginResponse;
import wil_connect.generated.model.RegisterRequest;
import wil_connect.generated.model.RegisterResponse;
import wil_connect.entity.UserAccount;
import wil_connect.service.AuthService;
import wil_connect.service.AuthService.EmployerOption;

import java.util.List;

@RestController
public class AuthController implements AuthenticationApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<LoginResponse> loginUser(LoginRequest loginRequest) {
        UserAccount user = authService.login(loginRequest.getEmail(), loginRequest.getPassword());
        LoginResponse response = new LoginResponse();

        response.setUserId(user.getUserId());
        response.setEmail(user.getEmail());
        response.setRole(LoginResponse.RoleEnum.fromValue(user.getRole().name()));
        response.setName(authService.displayName(user));
        response.setAuthenticated(true);
        response.setMessage("Login successful");
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RegisterResponse> registerUser(RegisterRequest registerRequest) {
        UserAccount user = authService.register(registerRequest);
        RegisterResponse response = new RegisterResponse();

        response.setUserId(user.getUserId());
        response.setEmail(user.getEmail());
        response.setRole(RegisterResponse.RoleEnum.fromValue(user.getRole().name()));
        response.setMessage("Account created. You can now log in");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /** Approved employers for the supervisor sign up list. Public, like register. */
    @GetMapping("/api/auth/employers")
    public ResponseEntity<List<EmployerOption>> registerEmployers() {
        return ResponseEntity.ok(authService.approvedEmployers());
    }
}
