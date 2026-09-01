package com.marine.ecobook.ebook.service;

import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class CoverStorage {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private final Path coversDirectory;

    public CoverStorage(@Value("${app.upload.root}") String uploadRoot) {
        this.coversDirectory = Path.of(uploadRoot).toAbsolutePath().normalize().resolve("covers");
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择封面图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片不能超过 5 MB");
        }

        String extension = detectedExtension(file);
        try {
            Files.createDirectories(coversDirectory);
            String filename = UUID.randomUUID() + extension;
            Path target = coversDirectory.resolve(filename).normalize();
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/covers/" + filename;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "封面保存失败，请稍后重试");
        }
    }

    public void delete(String coverUrl) {
        if (coverUrl == null || !coverUrl.startsWith("/uploads/covers/")) {
            return;
        }
        String filename = coverUrl.substring("/uploads/covers/".length());
        Path target = coversDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(coversDirectory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "旧封面清理失败，请稍后重试");
        }
    }

    private String detectedExtension(MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法识别封面图片");
        }
        if (header.length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (header.length >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N'
                && header[3] == 'G' && header[4] == '\r' && header[5] == '\n' && header[6] == 0x1A && header[7] == '\n') {
            return ".png";
        }
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ".webp";
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "封面仅支持 JPEG、PNG 或 WebP 图片");
    }
}
