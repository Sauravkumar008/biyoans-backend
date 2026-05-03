package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.model.StudentOfBiyoans;
import com.biyoans.biyoans.repository.SuperUserRepository;
import com.biyoans.biyoans.repository.StudentOfBiyoansRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/superusers")
@CrossOrigin(origins = "*")
public class SuperUserController {

    private final SuperUserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final StudentOfBiyoansRepository studentRepo;

    // in-memory OTP store: email -> (code,timestampSecs)
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    // verified flag after OTP verify: email -> true
    private final ConcurrentHashMap<String, Boolean> verifiedEmails = new ConcurrentHashMap<>();

    private static final long OTP_TTL_SECONDS = 10 * 60; // 10 minutes

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public SuperUserController(SuperUserRepository repo,
                               PasswordEncoder passwordEncoder,
                               JavaMailSender mailSender,
                               StudentOfBiyoansRepository studentRepo) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.studentRepo = studentRepo;
    }

    private static class OtpEntry {
        final String code;
        final long ts;
        OtpEntry(String code, long ts) { this.code = code; this.ts = ts; }
    }

    // ---------- Send OTP ----------
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "email required"));
        String normalized = email.trim().toLowerCase();

        // generate 6-digit numeric code
        String code = String.format("%06d", (int)(Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1000000));
        long now = Instant.now().getEpochSecond();
        otpStore.put(normalized, new OtpEntry(code, now));
        verifiedEmails.remove(normalized);

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(normalized);
            msg.setSubject("Your Biyoans verification code");
            msg.setText("Your verification code is: " + code + " (valid for " + (OTP_TTL_SECONDS/60) + " minutes)");
            if (mailFrom != null && !mailFrom.isBlank()) msg.setFrom(mailFrom);
            mailSender.send(msg);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send OTP", "detail", ex.getMessage()));
        }

        // DEV: return code in response to ease testing. Remove in production.
        return ResponseEntity.ok(Map.of("message", "OTP sent", "debug_code", code));
    }

    // ---------- Verify OTP ----------
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email and code required"));
        }
        String normalized = email.trim().toLowerCase();
        OtpEntry entry = otpStore.get(normalized);
        if (entry == null) {
            return ResponseEntity.status(404).body(Map.of("message", "OTP not found. Please request a new code."));
        }
        long now = Instant.now().getEpochSecond();
        if (now - entry.ts > OTP_TTL_SECONDS) {
            otpStore.remove(normalized);
            return ResponseEntity.status(410).body(Map.of("message", "OTP expired. Please request a new code."));
        }
        if (!entry.code.equals(code.trim())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid OTP code"));
        }
        // success
        verifiedEmails.put(normalized, true);
        otpStore.remove(normalized);
        return ResponseEntity.ok(Map.of("message", "OTP verified"));
    }

    // ---------- Create SuperUser (Teacher/Admin) ----------
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createSuperUser(
            @RequestParam String name,
            @RequestParam(required = false) String gender,
            @RequestParam String phoneNumber,
            @RequestParam String email,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, name = "photo") MultipartFile photo
    ) throws IOException {

        String un = username.trim();
        String em = email.trim().toLowerCase();
        String phone = phoneNumber.trim();

        // OTP verification required
        if (!Boolean.TRUE.equals(verifiedEmails.get(em))) {
            return ResponseEntity.status(403).body(Map.of("message", "Email not verified with OTP. Please verify OTP before creating user."));
        }

        if (repo.existsByUsername(un)) {
            return ResponseEntity.status(409).body(Map.of("message", "Username already exists"));
        }
        if (repo.existsByEmail(em)) {
            return ResponseEntity.status(409).body(Map.of("message", "Email already exists"));
        }
        if (repo.existsByPhoneNumber(phone)) {
            return ResponseEntity.status(409).body(Map.of("message", "Phone number already exists"));
        }

        SuperUser su = new SuperUser();
        su.setName(name);
        su.setGender(gender);
        su.setPhoneNumber(phone);
        su.setEmail(em);
        su.setUsername(un);
        su.setPassword(passwordEncoder.encode(password));
        su.setRole((role == null || role.isBlank()) ? "TEACHER" : role.toUpperCase());

        // Handle photo upload
        if (photo != null && !photo.isEmpty()) {
            // Save uploads to a fixed folder relative to your project
            File uploadDir = new File("uploads");
            if (!uploadDir.exists()) {
                uploadDir.mkdirs(); // create uploads directory if not exists
            }

            String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename().replaceAll("\\s+", "_");
            File dest = new File(uploadDir, fileName);  // <-- safe path outside Tomcat temp
            photo.transferTo(dest);

            // Save relative path to DB
            su.setPhotoUrl("/uploads/" + fileName);
        }

        repo.save(su);
        verifiedEmails.remove(em);
        return ResponseEntity.ok(Map.of("message", "SuperUser created successfully", "user", su));
    }

    // ---------- Create Student (uses same OTP verification) ----------
    // Endpoint: POST /api/superusers/create-student  (multipart/form-data)
    @PostMapping(value = "/create-student", consumes = {"multipart/form-data"})
    public ResponseEntity<?> createStudent(
            @RequestParam String userName,
            @RequestParam String qualification,
            @RequestParam String fatherName,
            @RequestParam(required = false) String motherName,
            @RequestParam(required = false) String aadharNumber,
            @RequestParam(required = false) String dob, // yyyy-MM-dd
            @RequestParam String email,
            @RequestParam String whatsAppNumber,
            @RequestParam String userPass,
            @RequestParam String gender,
            @RequestParam(required = false, name = "photo") MultipartFile photo
    ) {
        try {
            String em = email.trim().toLowerCase();

            if (!Boolean.TRUE.equals(verifiedEmails.get(em))) {
                return ResponseEntity.status(403).body(Map.of("message", "Email not verified with OTP. Please verify OTP before creating account."));
            }

            if (studentRepo.findByEmail(em).isPresent()) {
                return ResponseEntity.status(409).body(Map.of("message", "Email already registered as student"));
            }
            if (studentRepo.existsByWhatsAppNumber(whatsAppNumber)) {
                return ResponseEntity.status(409).body(Map.of("message", "Phone number already registered as student"));
            }

            StudentOfBiyoans s = new StudentOfBiyoans();
            s.setUserName(userName);
            s.setQualification(qualification);
            s.setFatherName(fatherName);
            s.setMotherName(motherName);
            if (aadharNumber != null && !aadharNumber.isBlank()) s.setAadharNumber(aadharNumber);

            if (dob != null && !dob.isBlank()) {
                try {
                    LocalDate ld = LocalDate.parse(dob);
                    s.setDob(ld);
                } catch (DateTimeParseException ex) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Invalid dob format. Use yyyy-MM-dd"));
                }
            }

            s.setEmail(em);
            s.setWhatsAppNumber(whatsAppNumber);
            s.setUserPass(passwordEncoder.encode(userPass));
            s.setGender(gender);
            s.setRole("STUDENT");

            // Handle photo upload
            if (photo != null && !photo.isEmpty()) {
                // Save uploads to a fixed folder relative to your project
                File uploadDir = new File("uploads");
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs(); // create uploads directory if not exists
                }

                String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename().replaceAll("\\s+", "_");
                File dest = new File(uploadDir, fileName);  // <-- safe path outside Tomcat temp
                photo.transferTo(dest);

                // Save relative path to DB
                s.setPhotoUrl("/uploads/" + fileName);
            }

            studentRepo.save(s);
            verifiedEmails.remove(em);
            return ResponseEntity.ok(Map.of("message", "Student created successfully", "student", s));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Failed to create student", "detail", ex.getMessage()));
        }
    }

    // ---------- Login (SuperUser) ----------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String identifier = req.get("username");
        String password = req.get("password");

        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username/email and password required"));
        }

        String idTrim = identifier.trim();
        Optional<SuperUser> opt = repo.findByUsernameOrEmail(idTrim, idTrim);
        if (opt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        SuperUser su = opt.get();
        if (!passwordEncoder.matches(password, su.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("message", "Login successful");
        resp.put("type", "SUPERUSER");
        resp.put("id", su.getId());
        resp.put("username", su.getUsername());
        resp.put("role", su.getRole());
        resp.put("name", su.getName());
        resp.put("email", su.getEmail());
        resp.put("phoneNumber", su.getPhoneNumber());
        resp.put("photoUrl", su.getPhotoUrl());

        return ResponseEntity.ok(resp);
    }
}