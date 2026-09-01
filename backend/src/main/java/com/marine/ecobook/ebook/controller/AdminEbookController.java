package com.marine.ecobook.ebook.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.dto.EbookItem;
import com.marine.ecobook.ebook.dto.EbookUpsertRequest;
import com.marine.ecobook.ebook.dto.PageData;
import com.marine.ecobook.ebook.service.EbookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/admin/ebooks")
@SaCheckRole(value = {"ADMIN", "SUPER_ADMIN"}, mode = SaMode.OR)
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class AdminEbookController {

    private final EbookService ebookService;

    public AdminEbookController(EbookService ebookService) {
        this.ebookService = ebookService;
    }

    @GetMapping
    public ApiResponse<PageData<EbookItem>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 50, message = "每页数量不能超过 50") int pageSize) {
        return ApiResponse.success(ebookService.listAdmin(categoryId, keyword, page, pageSize));
    }

    @PostMapping
    public ApiResponse<EbookItem> create(@Valid @RequestBody EbookUpsertRequest request) {
        return ApiResponse.success(ebookService.create(request));
    }

    @PutMapping("/{ebookId}")
    public ApiResponse<EbookItem> update(@PathVariable long ebookId, @Valid @RequestBody EbookUpsertRequest request) {
        return ApiResponse.success(ebookService.update(ebookId, request));
    }

    @PostMapping(value = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadCover(@RequestParam long ebookId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(ebookService.replaceCover(ebookId, file));
    }

    @PostMapping("/{ebookId}/publish")
    public ApiResponse<EbookItem> publish(@PathVariable long ebookId) {
        return ApiResponse.success(ebookService.publish(ebookId));
    }

    @PostMapping("/{ebookId}/unpublish")
    public ApiResponse<EbookItem> unpublish(@PathVariable long ebookId) {
        return ApiResponse.success(ebookService.unpublish(ebookId));
    }

    @DeleteMapping("/{ebookId}")
    public ApiResponse<Void> delete(@PathVariable long ebookId) {
        ebookService.delete(ebookId);
        return ApiResponse.success(null);
    }
}
