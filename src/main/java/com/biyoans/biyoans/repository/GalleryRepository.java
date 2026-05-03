package com.biyoans.biyoans.repository;


import com.biyoans.biyoans.model.GalleryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryRepository extends JpaRepository<GalleryItem, Long> { }