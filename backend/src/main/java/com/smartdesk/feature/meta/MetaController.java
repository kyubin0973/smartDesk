package com.smartdesk.feature.meta;

import com.smartdesk.repo.*;
import com.smartdesk.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** [보완] 드롭다운용 참조 데이터 (API 명세 누락분): 카테고리, 부서. */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final CategoryRepo categories;
    private final DepartmentRepo departments;

    public MetaController(CategoryRepo categories, DepartmentRepo departments) {
        this.categories = categories;
        this.departments = departments;
    }

    public record IdName(Long id, String name) {}

    @GetMapping("/categories")
    public List<IdName> categories() {
        CurrentUser.get();
        return categories.findByActiveTrue().stream().map(c -> new IdName(c.getId(), c.getName())).toList();
    }

    @GetMapping("/departments")
    public List<IdName> departments() {
        CurrentUser.requireSiUser();
        return departments.findAll().stream().map(d -> new IdName(d.getId(), d.getName())).toList();
    }
}
