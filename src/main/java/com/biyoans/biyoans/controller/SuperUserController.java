package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.model.StudentOfBiyoans;
import com.biyoans.biyoans.repository.SuperUserRepository;
import com.biyoans.biyoans.repository.StudentOfBiyoansRepository;
import com.biyoans.biyoans.service.OtpService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
// Frontend agar /api/auth use kar raha hai toh ye path best hai
@RequestMapping("/api/auth") 
@CrossOrigin(origins = "*")
public class SuperUserController {

    private final SuperUserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final StudentOfBiyoansRepository studentRepo;

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> verifiedEmails = new ConcurrentHashMap<>();

    private static final long OTP_TTL_SECONDS = 10 * 60;

    public SuperUserController(SuperUserRepository repo,
                               PasswordEncoder passwordEncoder,
                               OtpService otpService,
                               StudentOfBiyoansRepository studentRepo) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
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

        String code = String.format("%06d", (int)(Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1000000));
        long now = Instant.now().getEpochSecond();
        otpStore.put(normalized, new OtpEntry(code, now));
        verifiedEmails.remove(normalized);

        try {
            otpService.sendOtpEmail(normalized, code); 
            System.out.println("[SuperUser] OTP sent via API to: " + normalized);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send OTP", "detail", ex.getMessage()));
        }

        return ResponseEntity.ok(Map.of("message", "OTP sent", "debug_code", code));
    }

    // ---------- Verify OTP ----------
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        
        // Fix: Frontend agar "otp" bhej raha hai ya "code", dono handle honge
        String code = body.containsKey("code") ? body.get("code") : body.get("otp");

        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email and code required"));
        }
        String normalized = email.trim().toLowerCase();
        OtpEntry entry = otpStore.get(normalized);
        
        if (entry == null) {
            return ResponseEntity.status(404).body(Map.of("message", "OTP not found. Request again."));
        }
        
        long now = Instant.now().getEpochSecond();
        if (now - entry.ts > OTP_TTL_SECONDS) {
            otpStore.remove(normalized);
            return ResponseEntity.status(410).body(Map.of("message", "OTP expired."));
        }
        
        if (!entry.code.equals(code.trim())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid OTP code"));
        }
        
        verifiedEmails.put(normalized, true);
        otpStore.remove(normalized);
        return ResponseEntity.ok(Map.of("message", "OTP verified"));
    }

    // ---------- Create SuperUser ----------
    @PostMapping(value = "/create-superuser", consumes = {"multipart/form-data"})
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

        String em = email.trim().toLowerCase();
        if (!Boolean.TRUE.equals(verifiedEmails.get(em))) {
            return ResponseEntity.status(403).body(Map.of("message", "Email not verified."));
        }

        SuperUser su = new SuperUser();
        su.setName(name);
        su.setGender(gender);
        su.setPhoneNumber(phoneNumber.trim());
        su.setEmail(em);
        su.setUsername(username.trim());
        su.setPassword(passwordEncoder.encode(password));
        su.setRole((role == null || role.isBlank()) ? "TEACHER" : role.toUpperCase());

        if (photo != null && !photo.isEmpty()) {
            File uploadDir = new File("uploads");
            if (!uploadDir.exists()) uploadDir.mkdirs();
            String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename().replaceAll("\\s+", "_");
            photo.transferTo(new File(uploadDir, fileName));
            su.setPhotoUrl("/uploads/" + fileName);
        }

        repo.save(su);
        verifiedEmails.remove(em);
        return ResponseEntity.ok(Map.of("message", "SuperUser created", "user", su));
    }

    // ---------- Create Student ----------
    @PostMapping(value = "/create-student", consumes = {"multipart/form-data"})
    public ResponseEntity<?> createStudent(
            @RequestParam String userName,
            @RequestParam String qualification,
            @RequestParam String fatherName,
            @RequestParam(required = false) String motherName,
            @RequestParam(required = false) String aadharNumber,
            @RequestParam(required = false) String dob, 
            @RequestParam String email,
            @RequestParam String whatsAppNumber,
            @RequestParam String userPass,
            @RequestParam String gender,
            @RequestParam(required = false, name = "photo") MultipartFile photo
    ) {
        try {
            String em = email.trim().toLowerCase();
            if (!Boolean.TRUE.equals(verifiedEmails.get(em))) {
                return ResponseEntity.status(403).body(Map.of("message", "Email not verified."));
            }

            StudentOfBiyoans s = new StudentOfBiyoans();
            s.setUserName(userName);
            s.setQualification(qualification);
            s.setFatherName(fatherName);
            s.setMotherName(motherName);
            if (aadharNumber != null && !aadharNumber.isBlank()) s.setAadharNumber(aadharNumber);
            if (dob != null && !dob.isBlank()) s.setDob(LocalDate.parse(dob));

            s.setEmail(em);
            s.setWhatsAppNumber(whatsAppNumber);
            s.setUserPass(passwordEncoder.encode(userPass));
            s.setGender(gender);
            s.setRole("STUDENT");

            if (photo != null && !photo.isEmpty()) {
                File uploadDir = new File("uploads");
                if (!uploadDir.exists()) uploadDir.mkdirs();
                String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename().replaceAll("\\s+", "_");
                photo.transferTo(new File(uploadDir, fileName));
                s.setPhotoUrl("/uploads/" + fileName);
            }

            studentRepo.save(s);
            verifiedEmails.remove(em);
            return ResponseEntity.ok(Map.of("message", "Student created successfully"));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Error", "detail", ex.getMessage()));
        }
    }

    // ---------- Login ----------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String identifier = req.get("username");
        String password = req.get("password");
        Optional<SuperUser> opt = repo.findByUsernameOrEmail(identifier.trim(), identifier.trim());
        if (opt.isPresent() && passwordEncoder.matches(password, opt.get().getPassword())) {
            SuperUser su = opt.get();
            return ResponseEntity.ok(Map.of("id", su.getId(), "username", su.getUsername(), "role", su.getRole(), "type", "SUPERUSER"));
        }
        return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
    }
}