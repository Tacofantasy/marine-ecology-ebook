package com.marine.ecobook.category.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marine.ecobook.category.dto.CategoryCreateRequest;
import com.marine.ecobook.category.dto.CategoryTreeItem;
import com.marine.ecobook.category.dto.CategoryUpdateRequest;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.mapper.EbookReferenceMapper;
import com.marine.ecobook.category.model.Category;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.category.enabled", havingValue = "true", matchIfMissing = true)
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final EbookReferenceMapper ebookReferenceMapper;

    public CategoryService(CategoryMapper categoryMapper, EbookReferenceMapper ebookReferenceMapper) {
        this.categoryMapper = categoryMapper;
        this.ebookReferenceMapper = ebookReferenceMapper;
    }

    public List<CategoryTreeItem> listTree() {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getCreatedAt)
                .orderByAsc(Category::getId));
        Map<Long, List<Category>> childrenByParent = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>()).add(category);
            }
        }
        return roots.stream()
                .map(root -> toTreeItem(root, childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    @Transactional
    public CategoryTreeItem create(CategoryCreateRequest request) {
        String name = normalizedName(request.name());
        validateParent(request.parentId());
        assertNameAvailable(request.parentId(), name, null);

        Category category = new Category();
        category.setParentId(request.parentId());
        category.setName(name);
        category.setSortOrder(nextSortOrder(request.parentId()));
        category.setStatus("PUBLISHED");
        try {
            categoryMapper.insert(category);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ResultCode.CONFLICT, "同级分类名称已存在");
        }
        return toTreeItem(category, List.of());
    }

    @Transactional
    public CategoryTreeItem update(long categoryId, CategoryUpdateRequest request) {
        Category category = requiredCategory(categoryId);
        String name = normalizedName(request.name());
        assertNameAvailable(category.getParentId(), name, categoryId);
        category.setName(name);
        try {
            categoryMapper.updateById(category);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ResultCode.CONFLICT, "同级分类名称已存在");
        }
        return toTreeItem(category, List.of());
    }

    @Transactional
    public void delete(long categoryId) {
        Category category = requiredCategory(categoryId);
        if (category.getParentId() == null && hasChildren(categoryId)) {
            throw new BusinessException(ResultCode.CONFLICT, "一级分类下仍有二级分类，不能删除");
        }
        if (category.getParentId() != null && ebookReferenceMapper.countByCategoryId(categoryId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "二级分类下仍有电子书，不能删除");
        }
        try {
            categoryMapper.deleteById(categoryId);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ResultCode.CONFLICT, "分类仍被引用，不能删除");
        }
    }

    private CategoryTreeItem toTreeItem(Category category, List<Category> children) {
        return new CategoryTreeItem(category.getId(), category.getParentId(), category.getName(), category.getSortOrder(), children.stream()
                .map(child -> toTreeItem(child, List.of()))
                .toList());
    }

    private void validateParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        Category parent = requiredCategory(parentId);
        if (parent.getParentId() != null) {
            throw new BusinessException(ResultCode.CONFLICT, "二级分类不能再创建下级分类");
        }
    }

    private void assertNameAvailable(Long parentId, String name, Long excludedCategoryId) {
        LambdaQueryWrapper<Category> query = new LambdaQueryWrapper<Category>().eq(Category::getName, name);
        if (parentId == null) {
            query.isNull(Category::getParentId);
        } else {
            query.eq(Category::getParentId, parentId);
        }
        if (excludedCategoryId != null) {
            query.ne(Category::getId, excludedCategoryId);
        }
        if (categoryMapper.exists(query)) {
            throw new BusinessException(ResultCode.CONFLICT, "同级分类名称已存在");
        }
    }

    private int nextSortOrder(Long parentId) {
        LambdaQueryWrapper<Category> query = new LambdaQueryWrapper<Category>()
                .orderByDesc(Category::getSortOrder)
                .orderByDesc(Category::getId)
                .last("LIMIT 1");
        if (parentId == null) {
            query.isNull(Category::getParentId);
        } else {
            query.eq(Category::getParentId, parentId);
        }
        Category lastCategory = categoryMapper.selectOne(query);
        return lastCategory == null ? 1 : lastCategory.getSortOrder() + 1;
    }

    private boolean hasChildren(long categoryId) {
        return categoryMapper.exists(new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
    }

    private Category requiredCategory(long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private String normalizedName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类名称不能为空");
        }
        return normalized;
    }
}
