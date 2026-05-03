package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.Course;
import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.repository.CourseRepository;
import com.biyoans.biyoans.repository.SuperUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.repository.SuperUserRepository;
import org.springframework.util.StringUtils;
import java.nio.file.StandardCopyOption;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/courses")
@CrossOrigin(origins = "*")
public class CourseController {

    private final CourseRepository repo;
    private final SuperUserRepository superUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Path baseUploads;

    public CourseController(CourseRepository repo,
                            SuperUserRepository superUserRepository,
                            PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.superUserRepository = superUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.baseUploads = Paths.get("uploads").toAbsolutePath().normalize();
    }

    @GetMapping
    public ResponseEntity<List<Course>> listAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    // DELETE a course (only by ADMIN or SUPERADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(
            @PathVariable Long id,
            @RequestParam(name = "adminIdentifier") String adminIdentifier,
            @RequestParam(name = "adminPassword") String adminPassword) {

        if (adminIdentifier == null || adminIdentifier.isBlank() ||
                adminPassword == null || adminPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "adminIdentifier and adminPassword required"));
        }

        Optional<SuperUser> maybe = superUserRepository.findByUsernameOrEmail(adminIdentifier, adminIdentifier);
        if (maybe.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "Admin not found"));
        }
        SuperUser admin = maybe.get();

        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid admin password"));
        }

        String role = (admin.getRole() == null ? "" : admin.getRole().toUpperCase());
        if (!role.equals("ADMIN") && !role.equals("SUPERADMIN")) {
            return ResponseEntity.status(403).body(Map.of("message", "Forbidden: insufficient role"));
        }

        Optional<Course> courseOpt = repo.findById(id);
        if (courseOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Course not found"));
        }

        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Course deleted"));
    }

    // CREATE course (multipart/form-data)
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createCourse(
            @RequestParam String courseName,
            @RequestParam(required = false) Long courseFee,
            @RequestParam(required = false) String courseCategory,
            @RequestParam(required = false, name = "courseImage") MultipartFile courseImage
    ) {
        try {
            Course c = new Course();
            c.setCourseName(courseName);
            c.setCourseFee(courseFee);
            c.setCourseCategory(courseCategory);

            if (courseImage != null && !courseImage.isEmpty()) {
                Path coursesDir = baseUploads.resolve("courses");
                Files.createDirectories(coursesDir);

                String original = StringUtils.cleanPath(
                        Objects.requireNonNull(courseImage.getOriginalFilename()));
                String ext = "";
                int idx = original.lastIndexOf('.');
                if (idx > 0) ext = original.substring(idx);

                String fileName = UUID.randomUUID().toString() + ext;
                Path target = coursesDir.resolve(fileName);

                Files.copy(courseImage.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                c.setCourseImageUrl("/uploads/courses/" + fileName);
            }

            Course saved = repo.save(c);
            return ResponseEntity.ok(Map.of("message", "Course created", "course", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Server error while creating course",
                            "error", e.getClass().getSimpleName(),
                            "detail", e.getMessage()));
        }
    }




    //yahann se




    // put this method into the controller
    @PutMapping(value = "/{id}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> updateCourse(
            @PathVariable Long id,
            @RequestParam(name = "courseName", required = false) String courseName,
            @RequestParam(name = "courseFee", required = false) Long courseFee,
            @RequestParam(name = "courseCategory", required = false) String courseCategory,
            @RequestParam(name = "courseImage", required = false) MultipartFile courseImage,
            @RequestParam(name = "adminIdentifier", required = true) String adminIdentifier,
            @RequestParam(name = "adminPassword", required = true) String adminPassword
    ) {
        try {
            // 1) validate admin
            Optional<SuperUser> maybe = superUserRepository.findByUsernameOrEmail(adminIdentifier, adminIdentifier);
            if (maybe.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "Admin not found"));
            }
            SuperUser admin = maybe.get();
            if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid admin password"));
            }
            String role = (admin.getRole() == null ? "" : admin.getRole().toUpperCase());
            if (!role.equals("ADMIN") && !role.equals("SUPERADMIN")) {
                return ResponseEntity.status(403).body(Map.of("message", "Forbidden: insufficient role"));
            }

            // 2) find course
            Optional<Course> oc = repo.findById(id);
            if (oc.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Course not found"));
            }
            Course c = oc.get();

            // 3) update fields if provided
            if (courseName != null) c.setCourseName(courseName);
            if (courseFee != null) c.setCourseFee(courseFee);
            if (courseCategory != null) c.setCourseCategory(courseCategory);

            // 4) handle image replace
            if (courseImage != null && !courseImage.isEmpty()) {
                Path coursesDir = baseUploads.resolve("courses");
                Files.createDirectories(coursesDir);

                String original = StringUtils.cleanPath(courseImage.getOriginalFilename());
                String ext = "";
                int idx = original.lastIndexOf('.');
                if (idx > 0) ext = original.substring(idx);

                String fileName = UUID.randomUUID().toString() + ext;
                Path target = coursesDir.resolve(fileName);

                try (var in = courseImage.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }

                // Optionally delete previous file if you stored one (safe check)
                String prev = c.getCourseImageUrl();
                if (prev != null && prev.startsWith("/uploads/courses/")) {
                    try {
                        Path prevPath = baseUploads.resolve(prev.replaceFirst("^/uploads/", ""));
                        Files.deleteIfExists(prevPath);
                    } catch (Exception ignored) {}
                }

                c.setCourseImageUrl("/uploads/courses/" + fileName);
            }

            Course saved = repo.save(c);
            return ResponseEntity.ok(Map.of("message", "Course updated", "course", saved));
        } catch (IOException ex) {
            return ResponseEntity.status(500).body(Map.of("message", "File save error", "detail", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Server error", "detail", ex.getMessage()));
        }
    }
}