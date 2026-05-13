package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.StudentOfBiyoans;
import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.repository.StudentOfBiyoansRepository;
import com.biyoans.biyoans.repository.SuperUserRepository;
import com.biyoans.biyoans.service.OtpService; // Naya import
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth/password")
@CrossOrigin(origins = "*")
public class PasswordResetController {

    private final OtpService otpService; // JavaMailSender ki jagah OtpService
    private final StudentOfBiyoansRepository studentRepo;
    private final SuperUserRepository superRepo;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetController(OtpService otpService, // Constructor update
                                   StudentOfBiyoansRepository studentRepo,
                                   SuperUserRepository superRepo,
                                   PasswordEncoder passwordEncoder) {
        this.otpService = otpService;
        this.studentRepo = studentRepo;
        this.superRepo = superRepo;
        this.passwordEncoder = passwordEncoder;
    }

    private static class OtpEntry {
        final String code;
        final long ts; 
        OtpEntry(String code, long ts) { this.code = code; this.ts = ts; }
    }

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> verified = new ConcurrentHashMap<>();
    private static final long OTP_TTL_SECONDS = 10 * 60;

    // 1) initiate
    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "email required"));
        String em = email.trim().toLowerCase();
        
        // OTP Generate
        String code = String.format("%06d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000000));
        long now = Instant.now().getEpochSecond();
        otpStore.put(em, new OtpEntry(code, now));
        verified.remove(em);

        // Send mail via our new API-based OtpService
        try {
            otpService.sendOtpEmail(em, code); // Brevo API call
            logInfo("Password reset OTP sent to: " + em);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send OTP", "detail", ex.getMessage()));
        }

        return ResponseEntity.ok(Map.of("message", "OTP sent", "debug_code", code));
    }

    // Helper method for logging since we don't have @Slf4j here
    private void logInfo(String msg) {
        System.out.println("[PasswordReset] " + msg);
    }

    // 2) verify
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.containsKey("code") ? body.get("code") : body.get("otp");
        if (email == null || code == null || email.isBlank() || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email and code required"));
        }
        String em = email.trim().toLowerCase();
        OtpEntry entry = otpStore.get(em);
        if (entry == null) return ResponseEntity.status(404).body(Map.of("message", "OTP not found"));
        long now = Instant.now().getEpochSecond();
        if (now - entry.ts > OTP_TTL_SECONDS) {
            otpStore.remove(em);
            return ResponseEntity.status(410).body(Map.of("message", "OTP expired"));
        }
        if (!entry.code.equals(code.trim())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid OTP"));
        }
        verified.put(em, true);
        otpStore.remove(em);
        return ResponseEntity.ok(Map.of("message", "OTP verified"));
    }

    // 3) complete
    @PostMapping("/complete")
    public ResponseEntity<?> complete(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.containsKey("code") ? body.get("code") : body.get("otp");
        String newPassword = body.get("newPassword");
        if (email == null || code == null || newPassword == null || email.isBlank() || code.isBlank() || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email, code and newPassword required"));
        }
        String em = email.trim().toLowerCase();

        if (!Boolean.TRUE.equals(verified.get(em))) {
            OtpEntry entry = otpStore.get(em);
            if (entry == null || !entry.code.equals(code.trim()) || (Instant.now().getEpochSecond() - entry.ts > OTP_TTL_SECONDS)) {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired OTP"));
            }
        }

        Optional<StudentOfBiyoans> optStudent = studentRepo.findByEmail(em);
        if (optStudent.isPresent()) {
            StudentOfBiyoans s = optStudent.get();
            s.setUserPass(passwordEncoder.encode(newPassword));
            studentRepo.save(s);
            verified.remove(em);
            otpStore.remove(em);
            return ResponseEntity.ok(Map.of("message", "Password updated"));
        }

        Optional<SuperUser> optSuper = superRepo.findByEmail(em);
        if (optSuper.isPresent()) {
            SuperUser u = optSuper.get();
            u.setPassword(passwordEncoder.encode(newPassword));
            superRepo.save(u);
            verified.remove(em);
            otpStore.remove(em);
            return ResponseEntity.ok(Map.of("message", "Password updated"));
        }

        return ResponseEntity.status(404).body(Map.of("message", "User not found for this email"));
    }
}