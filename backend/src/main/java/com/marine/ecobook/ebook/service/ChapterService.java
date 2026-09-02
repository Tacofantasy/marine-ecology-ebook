package com.marine.ecobook.ebook.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import com.marine.ecobook.ebook.dto.ChapterDetail;
import com.marine.ecobook.ebook.dto.ChapterItem;
import com.marine.ecobook.ebook.dto.ChapterReorderRequest;
import com.marine.ecobook.ebook.dto.ChapterUpsertRequest;
import com.marine.ecobook.ebook.mapper.ChapterMapper;
import com.marine.ecobook.ebook.mapper.EbookMapper;
import com.marine.ecobook.ebook.model.Chapter;
import com.marine.ecobook.ebook.model.Ebook;
import com.marine.ecobook.stats.service.StatsService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class ChapterService {

    private static final String READ_DEDUP_KEY_PREFIX = "chapter:read:";
    private static final Duration READ_DEDUP_TTL = Duration.ofMinutes(30);

    private final ChapterMapper chapterMapper;
    private final EbookMapper ebookMapper;
    private final HtmlSanitizer htmlSanitizer;
    private final StringRedisTemplate redisTemplate;
    private final StatsService statsService;

    public ChapterService(
            ChapterMapper chapterMapper,
            EbookMapper ebookMapper,
            HtmlSanitizer htmlSanitizer,
            StringRedisTemplate redisTemplate,
            StatsService statsService) {
        this.chapterMapper = chapterMapper;
        this.ebookMapper = ebookMapper;
        this.htmlSanitizer = htmlSanitizer;
        this.redisTemplate = redisTemplate;
        this.statsService = statsService;
    }

    @Transactional
    public ChapterItem create(long ebookId, ChapterUpsertRequest request) {
        requiredDraftEbook(ebookId);
        String title = normalizeTitle(request.title());
        String sanitizedContent = sanitizeAndValidateContent(request.content());
        String sourceNote = normalizeOptional(request.sourceNote());

        int maxSort = chapterMapper.selectMaxSortOrder(ebookId);
        Chapter chapter = new Chapter();
        chapter.setEbookId(ebookId);
        chapter.setTitle(title);
        chapter.setContent(sanitizedContent);
        chapter.setSortOrder(maxSort + 1);
        chapter.setStatus("PUBLISHED");
        chapter.setSourceNote(sourceNote);
        chapter.setViewCount(0L);
        chapter.setWordCount((long) htmlSanitizer.toPlainText(sanitizedContent).length());
        chapterMapper.insert(chapter);
        return toItem(chapter);
    }

    @Transactional
    public ChapterItem update(long ebookId, long chapterId, ChapterUpsertRequest request) {
        requiredDraftEbook(ebookId);
        Chapter chapter = requireChapterBelongToEbook(ebookId, chapterId);
        String title = normalizeTitle(request.title());
        String sanitizedContent = sanitizeAndValidateContent(request.content());
        String sourceNote = normalizeOptional(request.sourceNote());

        chapter.setTitle(title);
        chapter.setContent(sanitizedContent);
        chapter.setSourceNote(sourceNote);
        chapter.setWordCount((long) htmlSanitizer.toPlainText(sanitizedContent).length());
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterMapper.updateById(chapter);
        return toItem(chapter);
    }

    @Transactional
    public void delete(long ebookId, long chapterId) {
        requiredDraftEbook(ebookId);
        Chapter chapter = requireChapterBelongToEbook(ebookId, chapterId);
        chapterMapper.deleteById(chapterId);
        LambdaUpdateWrapper<Chapter> updateWrapper = new LambdaUpdateWrapper<Chapter>()
                .eq(Chapter::getEbookId, ebookId)
                .gt(Chapter::getSortOrder, chapter.getSortOrder())
                .setSql("sort_order = sort_order - 1");
        chapterMapper.update(null, updateWrapper);
    }

    @Transactional
    public List<ChapterItem> reorder(long ebookId, ChapterReorderRequest request) {
        requiredDraftEbook(ebookId);
        List<Chapter> chapters = listChaptersByEbook(ebookId);
        if (request.chapterIds().size() != chapters.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "章节数量与该书现有章节数不一致");
        }
        Set<String> idSet = new HashSet<>(request.chapterIds());
        if (idSet.size() != request.chapterIds().size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "排序数组包含重复的章节 ID");
        }
        Set<Long> existingIds = new HashSet<>(chapters.stream().map(Chapter::getId).toList());
        for (String idStr : request.chapterIds()) {
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "章节 ID 格式不正确: " + idStr);
            }
            if (!existingIds.contains(id)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "章节不存在或不属于该电子书: " + idStr);
            }
        }

        LambdaUpdateWrapper<Chapter> tempWrapper = new LambdaUpdateWrapper<Chapter>()
                .eq(Chapter::getEbookId, ebookId)
                .setSql("sort_order = -sort_order");
        chapterMapper.update(null, tempWrapper);

        int order = 1;
        for (String idStr : request.chapterIds()) {
            long id = Long.parseLong(idStr);
            LambdaUpdateWrapper<Chapter> updateWrapper = new LambdaUpdateWrapper<Chapter>()
                    .eq(Chapter::getId, id)
                    .eq(Chapter::getEbookId, ebookId)
                    .set(Chapter::getSortOrder, order);
            chapterMapper.update(null, updateWrapper);
            order++;
        }

        return listAdminChapters(ebookId);
    }

    public List<ChapterItem> listAdminChapters(long ebookId) {
        requiredEbook(ebookId);
        List<Chapter> chapters = listChaptersByEbook(ebookId);
        return chapters.stream().map(this::toItem).toList();
    }

    public ChapterDetail getAdminChapter(long ebookId, long chapterId) {
        requiredEbook(ebookId);
        Chapter chapter = requireChapterBelongToEbook(ebookId, chapterId);
        return toDetail(chapter);
    }

    public List<ChapterItem> listPublicChapters(long ebookId) {
        requiredPublishedEbook(ebookId);
        List<Chapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getEbookId, ebookId)
                        .eq(Chapter::getStatus, "PUBLISHED")
                        .orderByAsc(Chapter::getSortOrder));
        return chapters.stream().map(this::toItem).toList();
    }

    public ChapterDetail getPublicChapter(long ebookId, long chapterId) {
        requiredPublishedEbook(ebookId);
        Chapter chapter = chapterMapper.selectOne(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getId, chapterId)
                        .eq(Chapter::getEbookId, ebookId)
                        .eq(Chapter::getStatus, "PUBLISHED"));
        if (chapter == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "章节不存在或尚未发布");
        }
        return toDetail(chapter);
    }

    public void recordRead(long ebookId, long chapterId, HttpServletRequest request) {
        requiredPublishedEbook(ebookId);
        Chapter chapter = chapterMapper.selectOne(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getId, chapterId)
                        .eq(Chapter::getEbookId, ebookId)
                        .eq(Chapter::getStatus, "PUBLISHED"));
        if (chapter == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "章节不存在或尚未发布");
        }

        String subject = resolveSubject(request);
        String redisKey = READ_DEDUP_KEY_PREFIX + chapterId + ":" + subject;

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", READ_DEDUP_TTL);
        if (Boolean.TRUE.equals(isNew)) {
            chapterMapper.incrementViewCount(chapterId);
            ebookMapper.incrementViewCount(ebookId);
            // 当日阅读量日计数（Redis），供首页“今日阅读”卡片与每日快照使用
            statsService.incrementReadCount(java.time.LocalDate.now());
        }
    }

    private Ebook requiredEbook(long ebookId) {
        Ebook ebook = ebookMapper.selectById(ebookId);
        if (ebook == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "电子书不存在");
        }
        return ebook;
    }

    private Ebook requiredDraftEbook(long ebookId) {
        Ebook ebook = requiredEbook(ebookId);
        if (!"DRAFT".equals(ebook.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "请先撤回电子书后再修改章节");
        }
        return ebook;
    }

    private Ebook requiredPublishedEbook(long ebookId) {
        Ebook ebook = ebookMapper.selectById(ebookId);
        if (ebook == null || !"PUBLISHED".equals(ebook.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "电子书不存在或尚未发布");
        }
        return ebook;
    }

    private Chapter requireChapterBelongToEbook(long ebookId, long chapterId) {
        Chapter chapter = chapterMapper.selectOne(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getId, chapterId)
                        .eq(Chapter::getEbookId, ebookId));
        if (chapter == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "章节不存在");
        }
        return chapter;
    }

    private List<Chapter> listChaptersByEbook(long ebookId) {
        return chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getEbookId, ebookId)
                        .orderByAsc(Chapter::getSortOrder));
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请输入章节标题");
        }
        if (normalized.length() > 200) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "章节标题不能超过 200 个字符");
        }
        return normalized;
    }

    private String sanitizeAndValidateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请输入章节正文");
        }
        String sanitized = htmlSanitizer.sanitize(content);
        String plainText = htmlSanitizer.toPlainText(content);
        if (plainText.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "章节正文不能为空");
        }
        return sanitized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveSubject(HttpServletRequest request) {
        if (StpUtil.isLogin()) {
            return "u:" + StpUtil.getLoginIdAsString();
        }
        String ip = extractClientIp(request);
        return "ip:" + sha256Hex(ip);
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }

    private ChapterItem toItem(Chapter chapter) {
        return new ChapterItem(
                chapter.getId(),
                chapter.getEbookId(),
                chapter.getTitle(),
                chapter.getSortOrder(),
                chapter.getStatus(),
                chapter.getSourceNote(),
                chapter.getViewCount(),
                chapter.getUpdatedAt());
    }

    private ChapterDetail toDetail(Chapter chapter) {
        return new ChapterDetail(
                chapter.getId(),
                chapter.getEbookId(),
                chapter.getTitle(),
                chapter.getSortOrder(),
                chapter.getStatus(),
                chapter.getSourceNote(),
                chapter.getViewCount(),
                chapter.getContent(),
                chapter.getUpdatedAt());
    }
}
