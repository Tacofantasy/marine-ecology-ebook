package com.marine.ecobook.ebook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import com.marine.ecobook.ebook.dto.EbookItem;
import com.marine.ecobook.ebook.dto.EbookUpsertRequest;
import com.marine.ecobook.ebook.dto.PageData;
import com.marine.ecobook.ebook.mapper.EbookMapper;
import com.marine.ecobook.ebook.model.Ebook;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class EbookService {

    private final EbookMapper ebookMapper;
    private final CategoryMapper categoryMapper;
    private final CoverStorage coverStorage;

    public EbookService(EbookMapper ebookMapper, CategoryMapper categoryMapper, CoverStorage coverStorage) {
        this.ebookMapper = ebookMapper;
        this.categoryMapper = categoryMapper;
        this.coverStorage = coverStorage;
    }

    public PageData<EbookItem> listPublic(Long categoryId, String keyword, int page, int pageSize) {
        return list(categoryId, keyword, page, pageSize, true);
    }

    public PageData<EbookItem> listAdmin(Long categoryId, String keyword, int page, int pageSize) {
        return list(categoryId, keyword, page, pageSize, false);
    }

    public EbookItem getPublic(long ebookId) {
        Ebook ebook = ebookMapper.selectOne(new LambdaQueryWrapper<Ebook>()
                .eq(Ebook::getId, ebookId)
                .eq(Ebook::getStatus, "PUBLISHED"));
        if (ebook == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "电子书不存在或尚未发布");
        }
        return toItem(ebook, categoriesById(List.of(ebook)));
    }

    @Transactional
    public EbookItem create(EbookUpsertRequest request) {
        Category category = requiredSecondLevelCategory(request.categoryId());
        Ebook ebook = new Ebook();
        ebook.setCategoryId(category.getId());
        ebook.setTitle(normalizedRequired(request.title(), "电子书名称不能为空"));
        ebook.setSummary(normalizedOptional(request.summary()));
        ebook.setSourceNote(normalizedOptional(request.sourceNote()));
        ebook.setStatus("DRAFT");
        ebookMapper.insert(ebook);
        return toItem(ebook, Map.of(category.getId(), category));
    }

    @Transactional
    public EbookItem update(long ebookId, EbookUpsertRequest request) {
        Ebook ebook = requiredEbook(ebookId);
        assertDraft(ebook);
        Category category = requiredSecondLevelCategory(request.categoryId());
        ebook.setCategoryId(category.getId());
        ebook.setTitle(normalizedRequired(request.title(), "电子书名称不能为空"));
        ebook.setSummary(normalizedOptional(request.summary()));
        ebook.setSourceNote(normalizedOptional(request.sourceNote()));
        ebookMapper.updateById(ebook);
        return toItem(ebook, Map.of(category.getId(), category));
    }

    @Transactional
    public String replaceCover(long ebookId, MultipartFile file) {
        Ebook ebook = requiredEbook(ebookId);
        assertDraft(ebook);
        String newCoverUrl = coverStorage.save(file);
        String oldCoverUrl = ebook.getCoverUrl();
        try {
            ebook.setCoverUrl(newCoverUrl);
            ebookMapper.updateById(ebook);
        } catch (RuntimeException exception) {
            coverStorage.delete(newCoverUrl);
            throw exception;
        }
        if (oldCoverUrl != null) {
            coverStorage.delete(oldCoverUrl);
        }
        return newCoverUrl;
    }

    @Transactional
    public EbookItem publish(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        assertPublishable(ebook);
        ebook.setStatus("PUBLISHED");
        ebook.setPublishedAt(LocalDateTime.now());
        ebookMapper.updateById(ebook);
        return toItem(ebook, categoriesById(List.of(ebook)));
    }

    @Transactional
    public EbookItem unpublish(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        if (!"PUBLISHED".equals(ebook.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "电子书当前不是已发布状态");
        }
        ebook.setStatus("DRAFT");
        ebookMapper.updateById(ebook);
        return toItem(ebook, categoriesById(List.of(ebook)));
    }

    @Transactional
    public void delete(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        assertDraft(ebook);
        ebookMapper.deleteById(ebookId);
        coverStorage.delete(ebook.getCoverUrl());
    }

    private PageData<EbookItem> list(Long categoryId, String keyword, int page, int pageSize, boolean publicOnly) {
        LambdaQueryWrapper<Ebook> query = new LambdaQueryWrapper<>();
        if (publicOnly) {
            query.eq(Ebook::getStatus, "PUBLISHED");
        }
        if (categoryId != null) {
            query.eq(Ebook::getCategoryId, categoryId);
        }
        String normalizedKeyword = normalizedOptional(keyword);
        if (normalizedKeyword != null) {
            query.and(wrapper -> wrapper.like(Ebook::getTitle, normalizedKeyword)
                    .or().like(Ebook::getSummary, normalizedKeyword));
        }
        long total = ebookMapper.selectCount(query);
        int offset = (page - 1) * pageSize;
        query.orderByDesc(publicOnly, Ebook::getPublishedAt)
                .orderByDesc(!publicOnly, Ebook::getUpdatedAt)
                .orderByDesc(Ebook::getId)
                .last("LIMIT %d OFFSET %d".formatted(pageSize, offset));
        List<Ebook> ebooks = ebookMapper.selectList(query);
        Map<Long, Category> categories = categoriesById(ebooks);
        return new PageData<>(total, ebooks.stream().map(ebook -> toItem(ebook, categories)).toList());
    }

    private void assertPublishable(Ebook ebook) {
        if (ebook.getSummary() == null || ebook.getSummary().length() < 20) {
            throw new BusinessException(ResultCode.CONFLICT, "发布前请填写 20–500 字简介");
        }
        if (ebook.getCoverUrl() == null) {
            throw new BusinessException(ResultCode.CONFLICT, "发布前请上传封面图片");
        }
        if (ebook.getSourceNote() == null) {
            throw new BusinessException(ResultCode.CONFLICT, "发布前请填写内容来源说明");
        }
        requiredSecondLevelCategory(ebook.getCategoryId());
        if (ebookMapper.countNonEmptyChapters(ebook.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "发布前至少需要一篇正文非空的章节");
        }
    }

    private Map<Long, Category> categoriesById(List<Ebook> ebooks) {
        if (ebooks.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = ebooks.stream().map(Ebook::getCategoryId).distinct().toList();
        Map<Long, Category> result = new HashMap<>();
        for (Category category : categoryMapper.selectByIds(ids)) {
            result.put(category.getId(), category);
        }
        return result;
    }

    private EbookItem toItem(Ebook ebook, Map<Long, Category> categories) {
        Category category = categories.get(ebook.getCategoryId());
        return new EbookItem(ebook.getId(), ebook.getCategoryId(), category == null ? "" : category.getName(), ebook.getTitle(),
                ebook.getCoverUrl(), ebook.getSummary(), ebook.getSourceNote(), ebook.getStatus(), ebook.getPublishedAt(), ebook.getUpdatedAt());
    }

    private Category requiredSecondLevelCategory(long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getParentId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "电子书必须归属二级分类");
        }
        return category;
    }

    private Ebook requiredEbook(long ebookId) {
        Ebook ebook = ebookMapper.selectById(ebookId);
        if (ebook == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "电子书不存在");
        }
        return ebook;
    }

    private void assertDraft(Ebook ebook) {
        if (!"DRAFT".equals(ebook.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "请先撤回电子书后再编辑或删除");
        }
    }

    private String normalizedRequired(String value, String message) {
        String normalized = normalizedOptional(value);
        if (normalized == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizedOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
