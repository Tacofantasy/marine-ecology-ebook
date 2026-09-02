package com.marine.ecobook.ebook.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.service.ContentImageStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/content-images")
@SaCheckRole(value = {"ADMIN", "SUPER_ADMIN"}, mode = SaMode.OR)
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class AdminContentImageController {

    private final ContentImageStorage contentImageStorage;

    public AdminContentImageController(ContentImageStorage contentImageStorage) {
        this.contentImageStorage = contentImageStorage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(contentImageStorage.save(file));
    }
}
