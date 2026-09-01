package com.marine.ecobook.category.dto;

import java.util.List;

public record CategoryTreeItem(Long id, Long parentId, String name, int sortOrder, List<CategoryTreeItem> children) {
}
