package com.smartdesk.repo;

import com.smartdesk.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepo extends JpaRepository<Department, Long> {}
