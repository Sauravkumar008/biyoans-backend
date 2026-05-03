package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.model.StudentOfBiyoans;
import com.biyoans.biyoans.repository.SuperUserRepository;
import com.biyoans.biyoans.repository.StudentOfBiyoansRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * ProfileController
 * - GET  /api/profile?identifier=...
 * - POST /api/profile/update  (multipart/form-data)
 * - POST /api/profile/logout
 *
 * Notes:
 *  - Students are looked up by email only (per your requirement).
 *  - SuperUsers are looked up by username OR email.
 *  - Uploaded photos are stored under uploads/profile and returned as "/uploads/profile/..."
 */
@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class ProfileController {

    @Autowired
    private SuperUserRepository superUserRepo;

    @Autowired
    private StudentOfBiyoansRepository studentRepo;

    private final Path uploadDir = Paths.get("uploads/profile").toAbsolutePath().normalize();

    public ProfileController() throws Exception {
        // ensure directory exists
        Files.createDirectories(uploadDir);
    }

    // --- helpers to make a uniform JSON response ---
    private Map<String, Object> toResponseForSuper(SuperUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "SUPERUSER");
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("userName", u.getName());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phoneNumber", u.getPhoneNumber());
        m.put("role", u.getRole());
        m.put("photoUrl", u.getPhotoUrl());
        return m;
    }

    private Map<String, Object> toResponseForStudent(StudentOfBiyoans s) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "STUDENT");
        m.put("id", s.getId());
        m.put("username", null); // students don't have username in your model
        m.put("userName", s.getUserName());
        m.put("name", s.getUserName());
        m.put("email", s.getEmail());
        // best-effort phone field - adapt if your field name differs
        m.put("phoneNumber", s.getWhatsAppNumber());
        m.put("role", s.getRole());
        m.put("photoUrl", s.getPhotoUrl());
        return m;
    }

    /**
     * GET profile by identifier.
     * identifier can be:
     * - numeric id -> will try superuser id then student id
     * - username or email for superuser
     * - email for student
     */
    @GetMapping
    public ResponseEntity<?> getProfile(@RequestParam String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "identifier is required"));
        }

        // try numeric id lookup first
        if (identifier.matches("\\d+")) {
            Long id = Long.valueOf(identifier);
            Optional<SuperUser> suById = superUserRepo.findById(id);
            if (suById.isPresent()) return ResponseEntity.ok(toResponseForSuper(suById.get()));
            Optional<StudentOfBiyoans> stById = studentRepo.findById(id);
            if (stById.isPresent()) return ResponseEntity.ok(toResponseForStudent(stById.get()));
        }

        // 1) superuser by username or email
        Optional<SuperUser> maybeSuper = superUserRepo.findByUsernameOrEmail(identifier, identifier);
        if (maybeSuper.isPresent()) {
            return ResponseEntity.ok(toResponseForSuper(maybeSuper.get()));
        }

        // 2) student by email (students use email only)
        Optional<StudentOfBiyoans> maybeStudentByEmail = studentRepo.findByEmail(identifier);
        if (maybeStudentByEmail.isPresent()) {
            return ResponseEntity.ok(toResponseForStudent(maybeStudentByEmail.get()));
        }

        return ResponseEntity.status(404).body(Map.of("message", "User not found"));
    }

    /**
     * Update profile (multipart/form-data)
     * required: identifier (username/email/id)
     * optional: name, phone, photo (file)
     *
     * Student updates: identified by email only (per your requirement).
     */
    // inside ProfileController.java

    @PostMapping("/update")
    public ResponseEntity<?> updateProfile(
            @RequestParam String identifier,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String fatherName,
            @RequestParam(required = false) String motherName,
            @RequestParam(required = false) String dob,               // expected "yyyy-MM-dd"
            @RequestParam(required = false) MultipartFile photo
    ) {
        try {
            // -- Try SuperUser first (if you still want to support superuser updates) --
            Optional<SuperUser> maybeSuper = superUserRepo.findByUsernameOrEmail(identifier, identifier);
            if (maybeSuper.isPresent()) {
                SuperUser u = maybeSuper.get();

                if (name != null) u.setName(name);
                if (phone != null) u.setPhoneNumber(phone);

                if (photo != null && !photo.isEmpty()) {
                    String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename();
                    Path target = uploadDir.resolve(fileName);
                    Files.copy(photo.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                    u.setPhotoUrl("/uploads/profile/" + fileName);
                }

                superUserRepo.save(u);
                return ResponseEntity.ok(Map.of("message", "Profile updated", "user", u));
            }

            // -- Student path (match by email only as requested) --
            Optional<StudentOfBiyoans> maybeStudent = studentRepo.findByEmail(identifier);
            if (maybeStudent.isPresent()) {
                StudentOfBiyoans s = maybeStudent.get();

                // update name
                if (name != null) s.setUserName(name);

                // phone
                if (phone != null) s.setWhatsAppNumber(phone);

                // father/mother names (student-only fields)
                if (fatherName != null) s.setFatherName(fatherName);
                if (motherName != null) s.setMotherName(motherName);

                // dob parsing into LocalDate. Expect frontend to send yyyy-MM-dd
                if (dob != null && !dob.isBlank()) {
                    try {
                        LocalDate parsed = LocalDate.parse(dob); // uses ISO yyyy-MM-dd
                        s.setDob(parsed);
                    } catch (Exception ex) {
                        // invalid format: return 400 with guidance
                        return ResponseEntity.badRequest().body(Map.of("message", "Invalid dob format. Use yyyy-MM-dd"));
                    }
                }

                // photo handling
                if (photo != null && !photo.isEmpty()) {
                    String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename();
                    Path target = uploadDir.resolve(fileName);
                    Files.copy(photo.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                    s.setPhotoUrl("/uploads/profile/" + fileName);
                }

                studentRepo.save(s);
                return ResponseEntity.ok(Map.of("message", "Profile updated", "student", s));
            }

            return ResponseEntity.status(404).body(Map.of("message", "User not found (student or superuser)"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error updating profile",
                    "detail", e.getMessage()
            ));
        }
    }

    /**
     * Logout endpoint — stateless; frontend should clear localStorage/sessionStorage
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}