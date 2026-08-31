package com.smartdesk.repo;

import com.smartdesk.domain.AppUser;
import com.smartdesk.domain.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepo extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    List<AppUser> findByDepartmentIdAndActiveTrue(Long departmentId);
    List<AppUser> findByRoleAndDepartmentIdAndActiveTrue(Enums.Role role, Long departmentId);
    List<AppUser> findByRoleAndActiveTrue(Enums.Role role);
}
