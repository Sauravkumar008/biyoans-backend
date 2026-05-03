package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.Batch;
import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.repository.BatchRepository;
import com.biyoans.biyoans.repository.SuperUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/batches")
@CrossOrigin(origins = "*")
public class BatchController {

    private final BatchRepository repo;
    private final SuperUserRepository superUserRepository;
    private final PasswordEncoder passwordEncoder;

    public BatchController(BatchRepository repo,
                           SuperUserRepository superUserRepository,
                           PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.superUserRepository = superUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<List<Batch>> listAll() {
        List<Batch> batches = repo.findAll();
        return ResponseEntity.ok(batches);
    }

    @PostMapping
    public ResponseEntity<?> createBatch(@RequestBody Map<String, Object> body) {
        try {
            String name = Objects.toString(body.get("batchName"), "").trim();
            String timing = Objects.toString(body.get("timing"), null);
            String mode = Objects.toString(body.get("mode"), null);
            String runningFrom = Objects.toString(body.get("runningFrom"), null);

            if (name.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "batchName is required"));
            }

            Batch b = new Batch();
            b.setBatchName(name);
            b.setTiming(timing);
            b.setMode(mode);
            b.setRunningFrom(runningFrom);

            Batch saved = repo.save(b);
            return ResponseEntity.ok(Map.of("message", "Batch created", "batch", saved));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Server error while creating batch", "error", ex.getClass().getSimpleName(), "detail", ex.getMessage()));
        }
    }

    // Update — requires adminIdentifier & adminPassword as query params (same pattern as courses)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBatch(
            @PathVariable Long id,
            @RequestParam(name = "adminIdentifier", required = false) String adminIdentifier,
            @RequestParam(name = "adminPassword", required = false) String adminPassword,
            @RequestBody Map<String, Object> body
    ) {
        // verify admin credentials
        if (adminIdentifier == null || adminPassword == null)
            return ResponseEntity.status(401).body(Map.of("message", "adminIdentifier and adminPassword required"));

        Optional<SuperUser> maybe = superUserRepository.findByUsernameOrEmail(adminIdentifier, adminIdentifier);
        if (maybe.isEmpty())
            return ResponseEntity.status(401).body(Map.of("message", "Admin not found"));

        SuperUser admin = maybe.get();
        if (!passwordEncoder.matches(adminPassword, admin.getPassword()))
            return ResponseEntity.status(401).body(Map.of("message", "Invalid admin password"));

        String role = (admin.getRole() == null ? "" : admin.getRole().toUpperCase());
        if (!role.equals("ADMIN") && !role.equals("SUPERADMIN") && !role.equals("TEACHER"))
            return ResponseEntity.status(403).body(Map.of("message", "Forbidden: insufficient role"));

        Optional<Batch> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Batch not found"));

        Batch b = opt.get();
        if (body.containsKey("batchName")) b.setBatchName(Objects.toString(body.get("batchName"), b.getBatchName()));
        if (body.containsKey("timing")) b.setTiming(Objects.toString(body.get("timing"), b.getTiming()));
        if (body.containsKey("mode")) b.setMode(Objects.toString(body.get("mode"), b.getMode()));
        if (body.containsKey("runningFrom")) b.setRunningFrom(Objects.toString(body.get("runningFrom"), b.getRunningFrom()));

        repo.save(b);
        return ResponseEntity.ok(Map.of("message", "Batch updated", "batch", b));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBatch(
            @PathVariable Long id,
            @RequestParam(name = "adminIdentifier", required = false) String adminIdentifier,
            @RequestParam(name = "adminPassword", required = false) String adminPassword
    ) {
        if (adminIdentifier == null || adminPassword == null)
            return ResponseEntity.badRequest().body(Map.of("message", "adminIdentifier and adminPassword required"));

        Optional<SuperUser> maybe = superUserRepository.findByUsernameOrEmail(adminIdentifier, adminIdentifier);
        if (maybe.isEmpty()) return ResponseEntity.status(401).body(Map.of("message", "Admin not found"));

        SuperUser admin = maybe.get();
        if (!passwordEncoder.matches(adminPassword, admin.getPassword()))
            return ResponseEntity.status(401).body(Map.of("message", "Invalid admin password"));

        String role = (admin.getRole() == null ? "" : admin.getRole().toUpperCase());
        if (!role.equals("ADMIN") && !role.equals("SUPERADMIN") && !role.equals("TEACHER"))
            return ResponseEntity.status(403).body(Map.of("message", "Forbidden: insufficient role"));

        Optional<Batch> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Batch not found"));

        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Batch deleted"));
    }
}