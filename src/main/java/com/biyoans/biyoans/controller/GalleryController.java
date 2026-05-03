package com.biyoans.biyoans.controller;

import com.biyoans.biyoans.model.GalleryItem;
import com.biyoans.biyoans.repository.GalleryRepository;
import com.biyoans.biyoans.service.CloudinaryService; // Nayi service import ki
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/gallery")
@CrossOrigin(origins = "*")
public class GalleryController {

    private final GalleryRepository repo;
    private final CloudinaryService cloudinaryService; // Path ki jagah Service use karenge

    public GalleryController(GalleryRepository repo, CloudinaryService cloudinaryService) {
        this.repo = repo;
        this.cloudinaryService = cloudinaryService;
    }

    // GET /api/gallery -> Saari images dikhane ke liye
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<GalleryItem> items = repo.findAll();
        for (GalleryItem g : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("imageUrl", g.getImageUrl()); // Yeh ab Cloudinary ka URL return karega
            m.put("createdAt", g.getCreatedAt());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    // POST /api/gallery -> Upload to Cloudinary
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadImage(
            @RequestParam(name = "image") MultipartFile image
    ) {
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No file uploaded"));
        }

        try {
            // 1. Cloudinary par upload karke URL le rahe hain
            String secureUrl = cloudinaryService.uploadImage(image);

            // 2. Database mein wahi URL save kar rahe hain
            GalleryItem g = new GalleryItem();
            g.setImageUrl(secureUrl); 
            g.setCreatedAt(LocalDateTime.now());
            GalleryItem saved = repo.save(g);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "Uploaded to Cloudinary");
            resp.put("id", saved.getId());
            resp.put("imageUrl", saved.getImageUrl());
            return ResponseEntity.ok(resp);

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Cloudinary Upload Error", "detail", ex.getMessage()));
        }
    }

    // DELETE /api/gallery/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteImage(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found"));
        }

        // Note: Abhi hum sirf DB se record delete kar rahe hain. 
        // Cloudinary se image delete karne ka logic hum baad mein add kar sakte hain.
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted from Database"));
    }
}