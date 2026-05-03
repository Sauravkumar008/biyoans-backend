package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.SuperUser;
import com.biyoans.biyoans.model.StudentOfBiyoans;
import com.biyoans.biyoans.model.Course;
import com.biyoans.biyoans.model.Batch;
import com.biyoans.biyoans.repository.SuperUserRepository;
import com.biyoans.biyoans.repository.StudentOfBiyoansRepository;
import com.biyoans.biyoans.repository.CourseRepository;
import com.biyoans.biyoans.repository.BatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final SuperUserRepository superUserRepo;
    private final StudentOfBiyoansRepository studentRepo;
    private final CourseRepository courseRepo;
    private final BatchRepository batchRepo;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AdminController(SuperUserRepository superUserRepo,
                           StudentOfBiyoansRepository studentRepo,
                           CourseRepository courseRepo,
                           BatchRepository batchRepo,
                           PasswordEncoder passwordEncoder) {
        this.superUserRepo = superUserRepo;
        this.studentRepo = studentRepo;
        this.courseRepo = courseRepo;
        this.batchRepo = batchRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------ TEACHERS (SuperUsers with role TEACHER) ------------------

    // List all teachers
    @GetMapping("/teachers")
    public ResponseEntity<?> listTeachers() {
        List<SuperUser> teachers = superUserRepo.findByRole("TEACHER");
        return ResponseEntity.ok(teachers);
    }

    // Get one teacher
    @GetMapping("/teachers/{id}")
    public ResponseEntity<?> getTeacher(@PathVariable Long id) {
        Optional<SuperUser> opt = superUserRepo.findById(id);
        if (opt.isPresent()) {
            return ResponseEntity.ok(opt.get());
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "Teacher not found"));
        }
    }

    // Create teacher (multipart) - optional photo upload
    @PostMapping(path = "/teachers", consumes = {"multipart/form-data"})
    public ResponseEntity<?> createTeacher(
            @RequestParam String name,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String phoneNumber,
            @RequestParam String password,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false, name = "photo") MultipartFile photo
    ) throws IOException {
        String un = username.trim();
        String em = email.trim().toLowerCase();
        String phone = phoneNumber.trim();

        if (superUserRepo.existsByUsername(un)) return ResponseEntity.status(409).body(Map.of("message", "Username already exists"));
        if (superUserRepo.existsByEmail(em)) return ResponseEntity.status(409).body(Map.of("message", "Email already exists"));
        if (superUserRepo.existsByPhoneNumber(phone)) return ResponseEntity.status(409).body(Map.of("message", "Phone number already exists"));

        SuperUser su = new SuperUser();
        su.setName(name);
        su.setUsername(un);
        su.setEmail(em);
        su.setPhoneNumber(phone);
        su.setPassword(passwordEncoder.encode(password));
        su.setRole("TEACHER");
        su.setGender(gender);

        if (photo != null && !photo.isEmpty()) {
            String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename().replaceAll("\\s+", "_");
            File dest = new File("uploads/" + fileName);
            dest.getParentFile().mkdirs();
            photo.transferTo(dest);
            su.setPhotoUrl("/uploads/" + fileName);
        }

        superUserRepo.save(su);
        return ResponseEntity.ok(Map.of("message", "Teacher created", "teacher", su));
    }

    // Update teacher
    @PutMapping("/teachers/{id}")
    public ResponseEntity<?> updateTeacher(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<SuperUser> opt = superUserRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Teacher not found"));
        SuperUser t = opt.get();

        if (body.containsKey("name")) t.setName((String) body.get("name"));
        if (body.containsKey("username")) t.setUsername(((String) body.get("username")).trim());
        if (body.containsKey("email")) t.setEmail(((String) body.get("email")).trim().toLowerCase());
        if (body.containsKey("phoneNumber")) t.setPhoneNumber((String) body.get("phoneNumber"));
        if (body.containsKey("gender")) t.setGender((String) body.get("gender"));
        if (body.containsKey("role")) t.setRole(((String) body.get("role")).toUpperCase());
        if (body.containsKey("password")) {
            String p = (String) body.get("password");
            if (p != null && !p.isBlank()) t.setPassword(passwordEncoder.encode(p));
        }
        superUserRepo.save(t);
        return ResponseEntity.ok(Map.of("message", "Teacher updated", "teacher", t));
    }

    // Delete teacher (simple admin auth via query params — replace with proper auth)
    @DeleteMapping("/teachers/{id}")
    public ResponseEntity<?> deleteTeacher(@PathVariable Long id,
                                           @RequestParam(required = false) String adminIdentifier,
                                           @RequestParam(required = false) String adminPassword) {
        if (adminIdentifier == null || adminPassword == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Admin credentials required"));
        }
        Optional<SuperUser> adminOpt = superUserRepo.findByUsernameOrEmail(adminIdentifier, adminIdentifier);
        if (adminOpt.isEmpty()) return ResponseEntity.status(403).body(Map.of("message", "Invalid admin"));
        SuperUser admin = adminOpt.get();
        if (!"ADMIN".equalsIgnoreCase(admin.getRole()) && !"SUPERADMIN".equalsIgnoreCase(admin.getRole())) {
            return ResponseEntity.status(403).body(Map.of("message", "Not an admin"));
        }
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            return ResponseEntity.status(403).body(Map.of("message", "Invalid admin password"));
        }

        if (!superUserRepo.existsById(id)) return ResponseEntity.status(404).body(Map.of("message", "Teacher not found"));
        superUserRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Teacher deleted"));
    }

    // ------------------ STUDENTS ------------------

    // List students
    @GetMapping("/students")
    public ResponseEntity<?> listStudents() {
        List<StudentOfBiyoans> students = studentRepo.findAll();
        return ResponseEntity.ok(students);
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<?> getStudent(@PathVariable Long id) {
        Optional<StudentOfBiyoans> opt = studentRepo.findById(id);
        if (opt.isPresent()) {
            return ResponseEntity.ok(opt.get());
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "Student not found"));
        }
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<?> updateStudent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<StudentOfBiyoans> opt = studentRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Student not found"));
        StudentOfBiyoans s = opt.get();

        if (body.containsKey("userName")) s.setUserName((String) body.get("userName"));
        if (body.containsKey("fatherName")) s.setFatherName((String) body.get("fatherName"));
        if (body.containsKey("motherName")) s.setMotherName((String) body.get("motherName"));
        if (body.containsKey("whatsAppNumber")) s.setWhatsAppNumber((String) body.get("whatsAppNumber"));
        if (body.containsKey("qualification")) s.setQualification((String) body.get("qualification"));
        if (body.containsKey("dob")) {
            try {
                s.setDob(java.time.LocalDate.parse((String) body.get("dob")));
            } catch (Exception ex) {
                // ignore parse error
            }
        }
        studentRepo.save(s);
        return ResponseEntity.ok(Map.of("message", "Student updated", "student", s));
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id,
                                           @RequestParam(required = false) String adminIdentifier,
                                           @RequestParam(required = false) String adminPassword) {
        if (adminIdentifier == null || adminPassword == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Admin credentials required"));
        }
        Optional<SuperUser> adminOpt = superUserRepo.findByUsernameOrEmail(adminIdentifier, adminIdentifier);
        if (adminOpt.isEmpty()) return ResponseEntity.status(403).body(Map.of("message", "Invalid admin"));
        SuperUser admin = adminOpt.get();
        if (!"ADMIN".equalsIgnoreCase(admin.getRole()) && !"SUPERADMIN".equalsIgnoreCase(admin.getRole())) {
            return ResponseEntity.status(403).body(Map.of("message", "Not an admin"));
        }
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            return ResponseEntity.status(403).body(Map.of("message", "Invalid admin password"));
        }

        if (!studentRepo.existsById(id)) return ResponseEntity.status(404).body(Map.of("message", "Student not found"));
        studentRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Student deleted"));
    }

    // Search students by name/email
    @GetMapping("/students/search")
    public ResponseEntity<?> searchStudents(@RequestParam(required = false, name = "q") String q) {
        if (q == null || q.isBlank()) return ResponseEntity.ok(List.of());
        String term = q.trim();
        List<StudentOfBiyoans> res = studentRepo.findByUserNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term);
        return ResponseEntity.ok(res);
    }

    // ------------------ COURSES ------------------

    @GetMapping("/courses")
    public ResponseEntity<?> listCourses() {
        List<Course> list = courseRepo.findAll();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/courses")
    public ResponseEntity<?> createCourse(@RequestBody Course c) {
        Course saved = courseRepo.save(c);
        return ResponseEntity.ok(Map.of("message", "Course created", "course", saved));
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @RequestBody Course payload) {
        Optional<Course> opt = courseRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Course not found"));
        Course c = opt.get();
        c.setCourseName(payload.getCourseName());
        c.setCourseFee(payload.getCourseFee());
        c.setCourseCategory(payload.getCourseCategory());
        courseRepo.save(c);
        return ResponseEntity.ok(Map.of("message", "Course updated", "course", c));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        if (!courseRepo.existsById(id)) return ResponseEntity.status(404).body(Map.of("message", "Course not found"));
        courseRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Course deleted"));
    }

    // ------------------ BATCHES ------------------

    @GetMapping("/batches")
    public ResponseEntity<?> listBatches() {
        List<Batch> list = batchRepo.findAll();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/batches")
    public ResponseEntity<?> createBatch(@RequestBody Batch b) {
        Batch saved = batchRepo.save(b);
        return ResponseEntity.ok(Map.of("message", "Batch created", "batch", saved));
    }

    @PutMapping("/batches/{id}")
    public ResponseEntity<?> updateBatch(@PathVariable Long id, @RequestBody Batch payload) {
        Optional<Batch> opt = batchRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Batch not found"));
        Batch b = opt.get();
        b.setBatchName(payload.getBatchName());
        b.setTiming(payload.getTiming());
        b.setMode(payload.getMode());
        batchRepo.save(b);
        return ResponseEntity.ok(Map.of("message", "Batch updated", "batch", b));
    }

    @DeleteMapping("/batches/{id}")
    public ResponseEntity<?> deleteBatch(@PathVariable Long id) {
        if (!batchRepo.existsById(id)) return ResponseEntity.status(404).body(Map.of("message", "Batch not found"));
        batchRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Batch deleted"));
    }

    // ------------------ HELPERS ------------------

    // Check email across superusers and students
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        String em = email.trim().toLowerCase();
        boolean ex1 = superUserRepo.existsByEmail(em);
        boolean ex2 = studentRepo.existsByEmail(em);
        return ResponseEntity.ok(Map.of("exists", ex1 || ex2));
    }

    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        boolean exists = superUserRepo.existsByUsername(username.trim());
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}