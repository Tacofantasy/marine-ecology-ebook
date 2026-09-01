package com.marine.ecobook.category.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.marine.ecobook.category.dto.CategoryCreateRequest;
import com.marine.ecobook.category.dto.CategoryTreeItem;
import com.marine.ecobook.category.dto.CategoryUpdateRequest;
import com.marine.ecobook.category.service.CategoryService;
import com.marine.ecobook.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
@SaCheckRole(value = {"ADMIN", "SUPER_ADMIN"}, mode = SaMode.OR)
@ConditionalOnProperty(name = "app.category.enabled", havingValue = "true", matchIfMissing = true)
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryTreeItem>> list() {
        return ApiResponse.success(categoryService.listTree());
    }

    @PostMapping
    public ApiResponse<CategoryTreeItem> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.success(categoryService.create(request));
    }

    @PutMapping("/{categoryId}")
    public ApiResponse<CategoryTreeItem> update(
            @PathVariable long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.update(categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> delete(@PathVariable long categoryId) {
        categoryService.delete(categoryId);
        return ApiResponse.success(null);
    }
}
