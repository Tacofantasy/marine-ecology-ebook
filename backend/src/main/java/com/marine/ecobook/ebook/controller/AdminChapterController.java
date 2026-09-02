package com.marine.ecobook.ebook.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.dto.ChapterDetail;
import com.marine.ecobook.ebook.dto.ChapterItem;
import com.marine.ecobook.ebook.dto.ChapterReorderRequest;
import com.marine.ecobook.ebook.dto.ChapterUpsertRequest;
import com.marine.ecobook.ebook.service.ChapterService;
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
@RequestMapping("/api/admin/ebooks/{ebookId}/chapters")
@SaCheckRole(value = {"ADMIN", "SUPER_ADMIN"}, mode = SaMode.OR)
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class AdminChapterController {

    private final ChapterService chapterService;

    public AdminChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    public ApiResponse<List<ChapterItem>> list(@PathVariable long ebookId) {
        return ApiResponse.success(chapterService.listAdminChapters(ebookId));
    }

    @GetMapping("/{chapterId}")
    public ApiResponse<ChapterDetail> detail(@PathVariable long ebookId, @PathVariable long chapterId) {
        return ApiResponse.success(chapterService.getAdminChapter(ebookId, chapterId));
    }

    @PostMapping
    public ApiResponse<ChapterItem> create(
            @PathVariable long ebookId, @Valid @RequestBody ChapterUpsertRequest request) {
        return ApiResponse.success(chapterService.create(ebookId, request));
    }

    @PutMapping("/{chapterId}")
    public ApiResponse<ChapterItem> update(
            @PathVariable long ebookId, @PathVariable long chapterId,
            @Valid @RequestBody ChapterUpsertRequest request) {
        return ApiResponse.success(chapterService.update(ebookId, chapterId, request));
    }

    @DeleteMapping("/{chapterId}")
    public ApiResponse<Void> delete(@PathVariable long ebookId, @PathVariable long chapterId) {
        chapterService.delete(ebookId, chapterId);
        return ApiResponse.success(null);
    }

    @PutMapping("/order")
    public ApiResponse<List<ChapterItem>> reorder(
            @PathVariable long ebookId, @Valid @RequestBody ChapterReorderRequest request) {
        return ApiResponse.success(chapterService.reorder(ebookId, request));
    }
}
