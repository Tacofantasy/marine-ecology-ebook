package com.marine.ecobook.ebook.controller;

import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.dto.EbookItem;
import com.marine.ecobook.ebook.dto.PageData;
import com.marine.ecobook.ebook.service.EbookService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/ebooks")
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class PublicEbookController {

    private final EbookService ebookService;

    public PublicEbookController(EbookService ebookService) {
        this.ebookService = ebookService;
    }

    @GetMapping
    public ApiResponse<PageData<EbookItem>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 50, message = "每页数量不能超过 50") int pageSize) {
        return ApiResponse.success(ebookService.listPublic(categoryId, keyword, page, pageSize));
    }

    @GetMapping("/{ebookId}")
    public ApiResponse<EbookItem> detail(@PathVariable long ebookId) {
        return ApiResponse.success(ebookService.getPublic(ebookId));
    }
}
