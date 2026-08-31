package com.smartdesk.repo;

import com.smartdesk.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepo extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrue();
    Optional<Category> findByNameIgnoreCase(String name);
}
