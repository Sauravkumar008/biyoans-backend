package com.biyoans.biyoans.repository;

import com.biyoans.biyoans.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
    // no extra methods needed for now
}