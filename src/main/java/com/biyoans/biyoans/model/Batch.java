package com.biyoans.biyoans.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "byns_batches")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String batchName;

    @Column(nullable = true)
    private String timing; // e.g. "6:00 AM - 9:00 AM"

    @Column(nullable = true)
    private String mode; // "Online" / "Offline"

    @Column(nullable = true)
    private String runningFrom; // simple string for UI; could be LocalDate if you prefer

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Batch() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // getters / setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchName() { return batchName; }
    public void setBatchName(String batchName) { this.batchName = batchName; }

    public String getTiming() { return timing; }
    public void setTiming(String timing) { this.timing = timing; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getRunningFrom() { return runningFrom; }
    public void setRunningFrom(String runningFrom) { this.runningFrom = runningFrom; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}