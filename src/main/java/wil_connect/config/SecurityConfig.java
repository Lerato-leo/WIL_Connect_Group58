package wil_connect.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Security for the API (checkAccess, 4.6.3).
 *
 * <p>Every request after login sends HTTP Basic credentials. Each role can only
 * reach its own part of the API. Shared endpoints (attendance, notifications,
 * proof files, reports) also check ownership in their own code.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String SESSION_ENDED = "Your session has ended. Please log in again.";

    /** Browser origins allowed to call the API, comma separated. */
    @Value("${wil.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // A JSON 401 without the WWW-Authenticate header, so the browser never shows its own login box.
        AuthenticationEntryPoint jsonEntryPoint = (request, response, exception) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, SESSION_ENDED);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/employers",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/openapi/**",
                                // Spring's error page, so a real error is not hidden behind a 401
                                "/error").permitAll()
                        .requestMatchers("/api/students/**").hasRole("STUDENT")
                        .requestMatchers("/api/supervisors/**").hasRole("SUPERVISOR")
                        .requestMatchers("/api/employers/**").hasRole("EMPLOYER")
                        .requestMatchers("/api/coordinator/**").hasRole("COORDINATOR")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(jsonEntryPoint))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(jsonEntryPoint)
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "You do not have access to this page.")));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
