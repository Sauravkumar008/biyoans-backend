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
@RequestMapping("/api/superusers") // 👈 Ise wapas 'superusers' kiya taaki AuthController se clash na ho
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
        String code = body.containsKey("code") ? body.get("code") : body.get("otp");

        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email and code required"));
        }
        String normalized = email.trim().toLowerCase();
        OtpEntry entry = otpStore.get(normalized);
        
        if (entry == null) return ResponseEntity.status(404).body(Map.of("message", "OTP not found."));
        
        long now = Instant.now().getEpochSecond();
        if (now - entry.ts > OTP_TTL_SECONDS) {
            otpStore.remove(normalized);
            return ResponseEntity.status(410).body(Map.of("message", "OTP expired."));
        }
        
        if (!entry.code.equals(code.trim())) return ResponseEntity.status(401).body(Map.of("message", "Invalid OTP code"));
        
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
            File