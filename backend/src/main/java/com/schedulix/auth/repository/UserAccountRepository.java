package com.schedulix.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import com.schedulix.auth.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserAccount> findByIdAndDepartmentId(UUID id, UUID departmentId);
}