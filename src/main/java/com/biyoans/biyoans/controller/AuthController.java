package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.StudentOfBiyoans;
import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.repository.StudentOfBiyoansRepository;
import com.biyoans.biyoans.repository.SuperUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AuthController
 * - POST /api/auth/login
 *
 * Accepts JSON body with either:
 *  { "identifier": "...", "userPass": "..." }
 *  or
 *  { "username": "...", "password": "..." }
 *
 * SuperUser: login by username OR email
 * Student: login by email only
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    private final SuperUserRepository superUserRepo;
    private final StudentOfBiyoansRepository studentRepo;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthController(SuperUserRepository superUserRepo,
                          StudentOfBiyoansRepository studentRepo,
                          PasswordEncoder passwordEncoder) {
        this.superUserRepo = superUserRepo;
        this.studentRepo = studentRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Accepts either { identifier, userPass } or { username, password }.
     * Returns 200 with user payload on success.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        try {
            if (body == null || body.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Request body required"));
            }

            // Normalise incoming field names
            String identifier = null;
            String userPass = null;

            if (body.get("identifier") != null) identifier = String.valueOf(body.get("identifier")).trim();
            if (body.get("userPass") != null) userPass = String.valueOf(body.get("userPass"));

            // fallback names
            if (identifier == null || identifier.isEmpty()) {
                if (body.get("username") != null) identifier = String.valueOf(body.get("username")).trim();
            }
            if (userPass == null || userPass.isEmpty()) {
                if (body.get("password") != null) userPass = String.valueOf(body.get("password"));
            }

            if (identifier == null || identifier.isBlank() || userPass == null || userPass.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "identifier/username and password/userPass are required"));
            }

            // 1) Try SuperUser (username OR email)
            Optional<SuperUser> maybeSuper = superUserRepo.findByUsernameOrEmail(identifier, identifier);
            if (maybeSuper.isPresent()) {
                SuperUser su = maybeSuper.get();

                String storedHash = su.getPassword(); // ensure getter name matches your entity
                if (storedHash == null || storedHash.isBlank()) {
                    LOG.warn("SuperUser password missing for id {}", su.getId());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
                }

                if (!passwordEncoder.matches(userPass, storedHash)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("id", su.getId());
                payload.put("type", "SUPERUSER");
                payload.put("role", su.getRole());
                payload.put("username", su.getUsername());
                payload.put("email", su.getEmail());
                payload.put("name", su.getName());
                payload.put("photoUrl", su.getPhotoUrl());
                payload.put("message", "Login successful");

                return ResponseEntity.ok(payload);
            }

            // 2) Try Student by email only
            Optional<StudentOfBiyoans> maybeStudent = studentRepo.findByEmail(identifier);
            if (maybeStudent.isPresent()) {
                StudentOfBiyoans st = maybeStudent.get();

                // adjust getter if different name in entity
                String storedHash = st.getUserPass();
                if (storedHash == null || storedHash.isBlank()) {
                    LOG.warn("Student password missing for id {}", st.getId());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
                }

                if (!passwordEncoder.matches(userPass, storedHash)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("id", st.getId());
                payload.put("type", "STUDENT");
                payload.put("role", st.getRole());
                // students may not have 'username' field - include userName for compatibility
                payload.put("username", st.getUserName());
                payload.put("email", st.getEmail());
                payload.put("name", st.getUserName());
                payload.put("photoUrl", st.getPhotoUrl());
                payload.put("message", "Login successful");

                return ResponseEntity.ok(payload);
            }

            // Not found
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid username or password"));
        } catch (Exception ex) {
            LOG.error("Error during login", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Server error during login", "detail", ex.getMessage()));
        }
    }
}