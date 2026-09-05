package com.marine.ecobook.ebook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import com.marine.ecobook.ebook.dto.EbookItem;
import com.marine.ecobook.ebook.dto.EbookUpsertRequest;
import com.marine.ecobook.ebook.dto.PageData;
import com.marine.ecobook.ebook.mapper.ChapterMapper;
import com.marine.ecobook.ebook.mapper.EbookLikeCount;
import com.marine.ecobook.ebook.mapper.EbookLikeMapper;
import com.marine.ecobook.ebook.mapper.EbookMapper;
import com.marine.ecobook.ebook.model.Ebook;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class EbookService {

    private final EbookMapper ebookMapper;
    private final ChapterMapper chapterMapper;
    private final CategoryMapper categoryMapper;
    private final CoverStorage coverStorage;
    private final EbookLikeMapper likeMapper;

    public EbookService(
            EbookMapper ebookMapper,
            ChapterMapper chapterMapper,
            CategoryMapper categoryMapper,
            CoverStorage coverStorage,
            EbookLikeMapper likeMapper) {
        this.ebookMapper = ebookMapper;
        this.chapterMapper = chapterMapper;
        this.categoryMapper = categoryMapper;
        this.coverStorage = coverStorage;
        this.likeMapper = likeMapper;
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
        return toItem(ebook, categoriesById(List.of(ebook)), likeMapper.countByEbookId(ebookId));
    }

    public EbookItem getAdmin(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        return toItem(ebook, categoriesById(List.of(ebook)), null);
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
        return toItem(ebook, Map.of(category.getId(), category), null);
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
        return toItem(ebook, Map.of(category.getId(), category), null);
    }

    @Transactional
    public String replaceCover(long ebookId, MultipartFile file) {
        Ebook ebook = requiredEbook(ebookId);
        assertDraft(ebook);
        String newCoverUrl = coverStorage.save(file);
        String oldCoverUrl = ebook.getCoverUrl();
        registerPostCommitCleanup(newCoverUrl, oldCoverUrl);
        ebook.setCoverUrl(newCoverUrl);
        ebookMapper.updateById(ebook);
        return newCoverUrl;
    }

    @Transactional
    public EbookItem publish(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        assertDraft(ebook);
        assertPublishable(ebook);
        ebook.setStatus("PUBLISHED");
        ebook.setPublishedAt(LocalDateTime.now());
        ebookMapper.updateById(ebook);
        return toItem(ebook, categoriesById(List.of(ebook)), null);
    }

    @Transactional
    public EbookItem unpublish(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        if (!"PUBLISHED".equals(ebook.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "电子书当前不是已发布状态");
        }
        ebook.setStatus("DRAFT");
        ebookMapper.updateById(ebook);
        return toItem(ebook, categoriesById(List.of(ebook)), null);
    }

    @Transactional
    public void delete(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        assertDraft(ebook);
        chapterMapper.deleteByEbookId(ebookId);
        ebookMapper.deleteById(ebookId);
        // 封面删除放到事务提交后执行，避免删除记录失败时文件已被删除。
        registerPostCommitCleanup(null, ebook.getCoverUrl());
    }

    private PageData<EbookItem> list(Long categoryId, String keyword, int page, int pageSize, boolean publicOnly) {
        if (categoryId != null) {
            requiredSecondLevelCategory(categoryId);
        }
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
        // 用 long 计算偏移量：page 仅有下限，int 乘法在超大页码时会溢出为负 OFFSET。
        long offset = (long) (page - 1) * pageSize;
        query.orderByDesc(publicOnly, Ebook::getPublishedAt)
                .orderByDesc(!publicOnly, Ebook::getUpdatedAt)
                .orderByDesc(Ebook::getId)
                .last("LIMIT %d OFFSET %d".formatted(pageSize, offset));
        List<Ebook> ebooks = ebookMapper.selectList(query);
        Map<Long, Category> categories = categoriesById(ebooks);
        // 仅公开列表填充点赞聚合；管理端响应保持原有字段，不暴露 likeCount。
        Map<Long, Long> likeCounts = publicOnly ? likeCountsByEbookIds(ebooks) : Map.of();
        return new PageData<>(total, ebooks.stream()
                .map(ebook -> toItem(ebook, categories, publicOnly ? likeCounts.getOrDefault(ebook.getId(), 0L) : null))
                .toList());
    }

    private void assertPublishable(Ebook ebook) {
        if (normalizedOptional(ebook.getTitle()) == null) {
            throw new BusinessException(ResultCode.CONFLICT, "发布前请填写电子书名称");
        }
        String summary = normalizedOptional(ebook.getSummary());
        if (summary == null || summary.length() < 20 || summary.length() > 500) {
            throw new BusinessException(ResultCode.CONFLICT, "发布前请填写 20–500 字简介");
        }
        if (normalizedOptional(ebook.getCoverUrl()) == null) {
            throw new BusinessException(ResultCode.CONFLICT, "发布前请上传封面图片");
        }
        if (normalizedOptional(ebook.getSourceNote()) == null) {
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

    private EbookItem toItem(Ebook ebook, Map<Long, Category> categories, Long likeCount) {
        Category category = categories.get(ebook.getCategoryId());
        return new EbookItem(ebook.getId(), ebook.getCategoryId(), category == null ? "" : category.getName(), ebook.getTitle(),
                ebook.getCoverUrl(), ebook.getSummary(), ebook.getSourceNote(), ebook.getStatus(), ebook.getPublishedAt(), ebook.getUpdatedAt(), likeCount);
    }

    /**
     * 一次批量聚合查询填充本页所有电子书的点赞数，避免列表页 N+1。
     */
    private Map<Long, Long> likeCountsByEbookIds(List<Ebook> ebooks) {
        if (ebooks.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = ebooks.stream().map(Ebook::getId).distinct().toList();
        Map<Long, Long> result = new HashMap<>();
        for (EbookLikeCount row : likeMapper.countByEbookIds(ids)) {
            result.put(row.getEbookId(), row.getLikeCount());
        }
        return result;
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

    /**
     * 注册事务提交后的封面文件清理回调。
     * <p>
     * 事务提交成功：删除旧封面文件（oldCoverUrl）。
     * 事务回滚：补偿删除新封面文件（newCoverUrl），避免无主文件遗留磁盘。
     * 若未处于事务上下文（如非事务调用），立即执行清理。
     *
     * @param newCoverUrl 新保存的封面 URL，事务回滚时补偿删除；null 表示无新文件
     * @param oldCoverUrl 旧封面 URL，事务提交后删除；null 表示无旧文件
     */
    private void registerPostCommitCleanup(String newCoverUrl, String oldCoverUrl) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (oldCoverUrl != null) {
                        coverStorage.deleteQuietly(oldCoverUrl);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK && newCoverUrl != null) {
                        coverStorage.deleteQuietly(newCoverUrl);
                    }
                }
            });
        } else {
            if (oldCoverUrl != null) {
                coverStorage.deleteQuietly(oldCoverUrl);
            }
        }
    }
}
