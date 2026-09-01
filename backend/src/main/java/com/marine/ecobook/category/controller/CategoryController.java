package com.marine.ecobook.category.controller;

import com.marine.ecobook.category.dto.CategoryTreeItem;
import com.marine.ecobook.category.service.CategoryService;
import com.marine.ecobook.common.api.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@ConditionalOnProperty(name = "app.category.enabled", havingValue = "true", matchIfMissing = true)
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryTreeItem>> list() {
        return ApiResponse.success(categoryService.listTree());
    }
}
