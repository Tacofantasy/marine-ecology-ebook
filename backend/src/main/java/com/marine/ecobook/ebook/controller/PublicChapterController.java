package com.marine.ecobook.ebook.controller;

import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.dto.ChapterDetail;
import com.marine.ecobook.ebook.dto.ChapterItem;
import com.marine.ecobook.ebook.service.ChapterService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ebooks/{ebookId}/chapters")
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class PublicChapterController {

    private final ChapterService chapterService;

    public PublicChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    public ApiResponse<List<ChapterItem>> list(@PathVariable long ebookId) {
        return ApiResponse.success(chapterService.listPublicChapters(ebookId));
    }

    @GetMapping("/{chapterId}")
    public ApiResponse<ChapterDetail> detail(@PathVariable long ebookId, @PathVariable long chapterId) {
        return ApiResponse.success(chapterService.getPublicChapter(ebookId, chapterId));
    }

    @PostMapping("/{chapterId}/read")
    public ApiResponse<Void> recordRead(
            @PathVariable long ebookId, @PathVariable long chapterId, HttpServletRequest request) {
        chapterService.recordRead(ebookId, chapterId, request);
        return ApiResponse.success(null);
    }
}
