package wil_connect.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import wil_connect.enums.Role;
import wil_connect.repository.UserAccountRepository;
import wil_connect.entity.UserAccount;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccount register(String email, String password) {

        if (userAccountRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.STUDENT);

        return userAccountRepository.save(user);
    }
}