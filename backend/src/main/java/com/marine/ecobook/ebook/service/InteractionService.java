package com.marine.ecobook.ebook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import com.marine.ecobook.ebook.dto.FavoriteEbookItem;
import com.marine.ecobook.ebook.dto.InteractionState;
import com.marine.ecobook.ebook.dto.PageData;
import com.marine.ecobook.ebook.mapper.EbookLikeCount;
import com.marine.ecobook.ebook.mapper.EbookLikeMapper;
import com.marine.ecobook.ebook.mapper.EbookMapper;
import com.marine.ecobook.ebook.mapper.FavoriteEbookRow;
import com.marine.ecobook.ebook.mapper.FavoriteMapper;
import com.marine.ecobook.ebook.model.Ebook;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 电子书互动服务：仅由本服务管理 likes / favorites 两张互动表。
 * <ul>
 *   <li>读写只面对 PUBLISHED 电子书，草稿、撤回或不存在的书统一 40401；</li>
 *   <li>创建与取消均幂等：数据库唯一键 + INSERT IGNORE / 精确条件删除；</li>
 *   <li>likeCount 始终由 likes 聚合得出，不引入缓存或冗余列；</li>
 *   <li>电子书撤回不删除互动记录，“我的收藏”只过滤未发布书，重新发布自动恢复可见。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class InteractionService {

    private final EbookMapper ebookMapper;
    private final EbookLikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;

    public InteractionService(
            EbookMapper ebookMapper,
            EbookLikeMapper likeMapper,
            FavoriteMapper favoriteMapper) {
        this.ebookMapper = ebookMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
    }

    public InteractionState getState(long ebookId, long userId) {
        requiredPublishedEbook(ebookId);
        return currentState(ebookId, userId);
    }

    @Transactional
    public InteractionState like(long ebookId, long userId) {
        requiredPublishedEbook(ebookId);
        likeMapper.insertIgnore(userId, ebookId);
        // 幂等插入后关系必然存在，不能依赖 REPEATABLE READ 快照重查（并发下会误读为 false）。
        return new InteractionState(true,
                favoriteMapper.countByUserAndEbook(userId, ebookId) > 0,
                likeMapper.countByEbookId(ebookId));
    }

    @Transactional
    public InteractionState unlike(long ebookId, long userId) {
        requiredPublishedEbook(ebookId);
        likeMapper.deleteByUserAndEbook(userId, ebookId);
        return new InteractionState(false,
                favoriteMapper.countByUserAndEbook(userId, ebookId) > 0,
                likeMapper.countByEbookId(ebookId));
    }

    @Transactional
    public InteractionState favorite(long ebookId, long userId) {
        requiredPublishedEbook(ebookId);
        favoriteMapper.insertIgnore(userId, ebookId);
        return new InteractionState(likeMapper.countByUserAndEbook(userId, ebookId) > 0,
                true,
                likeMapper.countByEbookId(ebookId));
    }

    @Transactional
    public InteractionState unfavorite(long ebookId, long userId) {
        requiredPublishedEbook(ebookId);
        favoriteMapper.deleteByUserAndEbook(userId, ebookId);
        return new InteractionState(likeMapper.countByUserAndEbook(userId, ebookId) > 0,
                false,
                likeMapper.countByEbookId(ebookId));
    }

    public PageData<FavoriteEbookItem> listFavorites(long userId, int page, int pageSize) {
        long total = favoriteMapper.countPublishedByUserId(userId);
        if (total == 0) {
            return new PageData<>(0, List.of());
        }
        // 用 long 计算偏移量：page 仅有下限，int 乘法在超大页码时会溢出为负 OFFSET。
        long offset = (long) (page - 1) * pageSize;
        if (offset >= total) {
            return new PageData<>(total, List.of());
        }
        List<FavoriteEbookRow> rows = favoriteMapper.selectPublishedFavoriteRows(userId, pageSize, offset);
        Map<Long, Long> likeCounts = likeCountsByEbookIds(
                rows.stream().map(FavoriteEbookRow::getId).distinct().toList());
        List<FavoriteEbookItem> items = rows.stream()
                .map(row -> new FavoriteEbookItem(
                        row.getId(), row.getCategoryId(), row.getCategoryName(), row.getTitle(),
                        row.getCoverUrl(), row.getSummary(), row.getStatus(), row.getPublishedAt(),
                        row.getUpdatedAt(), likeCounts.getOrDefault(row.getId(), 0L), row.getFavoritedAt()))
                .toList();
        return new PageData<>(total, items);
    }

    private InteractionState currentState(long ebookId, long userId) {
        boolean liked = likeMapper.countByUserAndEbook(userId, ebookId) > 0;
        boolean favorited = favoriteMapper.countByUserAndEbook(userId, ebookId) > 0;
        long likeCount = likeMapper.countByEbookId(ebookId);
        return new InteractionState(liked, favorited, likeCount);
    }

    private Map<Long, Long> likeCountsByEbookIds(List<Long> ebookIds) {
        if (ebookIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (EbookLikeCount row : likeMapper.countByEbookIds(ebookIds)) {
            result.put(row.getEbookId(), row.getLikeCount());
        }
        return result;
    }

    /**
     * 草稿、已撤回或不存在的电子书统一按 NOT_FOUND 拒绝，不泄露内容状态。
     */
    private void requiredPublishedEbook(long ebookId) {
        Long count = ebookMapper.selectCount(new LambdaQueryWrapper<Ebook>()
                .eq(Ebook::getId, ebookId)
                .eq(Ebook::getStatus, "PUBLISHED"));
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "电子书不存在或尚未发布");
        }
    }
}
