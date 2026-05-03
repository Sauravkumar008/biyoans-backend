package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.StudentOfBiyoans;
import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.repository.StudentOfBiyoansRepository;
import com.biyoans.biyoans.repository.SuperUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    private final JavaMailSender mailSender;
    private final StudentOfBiyoansRepository studentRepo;
    private final SuperUserRepository superRepo;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetController(JavaMailSender mailSender,
                                   StudentOfBiyoansRepository studentRepo,
                                   SuperUserRepository superRepo,
                                   PasswordEncoder passwordEncoder) {
        this.mailSender = mailSender;
        this.studentRepo = studentRepo;
        this.superRepo = superRepo;
        this.passwordEncoder = passwordEncoder;
    }

    private static class OtpEntry {
        final String code;
        final long ts; // epoch seconds
        OtpEntry(String code, long ts) { this.code = code; this.ts = ts; }
    }

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> verified = new ConcurrentHashMap<>();
    private static final long OTP_TTL_SECONDS = 10 * 60;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    // 1) initiate
    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "email required"));
        String em = email.trim().toLowerCase();
        String code = String.format("%06d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000000));
        long now = Instant.now().getEpochSecond();
        otpStore.put(em, new OtpEntry(code, now));
        verified.remove(em);
        // send mail (best-effort)
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(em);
            msg.setSubject("Your password reset code");
            msg.setText("Your verification code: " + code + " (valid for " + (OTP_TTL_SECONDS/60) + " minutes)");
            if (mailFrom != null && !mailFrom.isBlank()) msg.setFrom(mailFrom);
            mailSender.send(msg);
        } catch (Exception ex) {
            ex.printStackTrace();
            // still return 500 so frontend knows, or you can return OK with debug_code in dev
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send OTP", "detail", ex.getMessage()));
        }

        // For dev convenience we return the code (REMOVE in production)
        return ResponseEntity.ok(Map.of("message", "OTP sent", "debug_code", code));
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

    // 3) complete: set new password (requires code too)
    @PostMapping("/complete")
    public ResponseEntity<?> complete(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.containsKey("code") ? body.get("code") : body.get("otp");
        String newPassword = body.get("newPassword");
        if (email == null || code == null || newPassword == null || email.isBlank() || code.isBlank() || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "email, code and newPassword required"));
        }
        String em = email.trim().toLowerCase();
        // Option A: check verified map (if verify was called earlier)
        if (!Boolean.TRUE.equals(verified.get(em))) {
            // fallback to verifying code here (stateless)
            OtpEntry entry = otpStore.get(em);
            if (entry == null || !entry.code.equals(code.trim()) || (Instant.now().getEpochSecond() - entry.ts > OTP_TTL_SECONDS)) {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired OTP"));
            }
        }

        // Update student or superuser by email
        Optional<StudentOfBiyoans> optStudent = studentRepo.findByEmail(em);
        if (optStudent.isPresent()) {
            StudentOfBiyoans s = optStudent.get();
            s.setUserPass(passwordEncoder.encode(newPassword));
            studentRepo.save(s);
            // clear verification
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