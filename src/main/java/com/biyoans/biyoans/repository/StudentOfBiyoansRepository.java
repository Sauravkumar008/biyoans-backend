package com.biyoans.biyoans.repository;

import com.biyoans.biyoans.model.StudentOfBiyoans;
import org.springframework.data.jpa.repository.JpaRepository;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;
import java.util.Optional;

public interface StudentOfBiyoansRepository extends JpaRepository<StudentOfBiyoans, Long> {
    boolean existsByEmail(String email);
    Optional<StudentOfBiyoans> findByEmail(String email);
    Optional<StudentOfBiyoans> findByAadharNumber(String aadharNumber);
    boolean existsByWhatsAppNumber(String whatsAppNumber);

    List<StudentOfBiyoans> findByUserNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

}



