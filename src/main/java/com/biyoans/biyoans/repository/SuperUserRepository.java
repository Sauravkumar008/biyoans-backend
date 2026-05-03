package com.biyoans.biyoans.repository;

import com.biyoans.biyoans.model.SuperUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuperUserRepository extends JpaRepository<SuperUser, Long> {
    Optional<SuperUser> findByUsername(String username);

    Optional<SuperUser> findByEmail(String email);
    Optional<SuperUser> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    List<SuperUser> findByRole(String role);
}
