package com.marine.ecobook.ebook.service;

import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class ContentImageStorage {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private final Path contentDirectory;

    public ContentImageStorage(@Value("${app.upload.root}") String uploadRoot) {
        this.contentDirectory = Path.of(uploadRoot).toAbsolutePath().normalize().resolve("content");
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择正文图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "正文图片不能超过 5 MB");
        }

        byte[] content;
        try (InputStream input = file.getInputStream()) {
            content = input.readAllBytes();
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法读取正文图片");
        }

        String extension = detectedExtension(content);
        try {
            Files.createDirectories(contentDirectory);
            String filename = UUID.randomUUID() + extension;
            Path target = contentDirectory.resolve(filename).normalize();
            if (!target.getParent().equals(contentDirectory)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "正文图片路径无效");
            }
            Files.write(target, content);
            return "/uploads/content/" + filename;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "正文图片保存失败，请稍后重试");
        }
    }

    private String detectedExtension(byte[] content) {
        if (content.length < 12) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "正文图片仅支持 JPEG、PNG 或 WebP 图片");
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return validateImage(content, ".jpg");
        }
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G' && content[4] == '\r' && content[5] == '\n'
                && content[6] == 0x1A && content[7] == '\n') {
            return validateImage(content, ".png");
        }
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return validateImage(content, ".webp");
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "正文图片仅支持 JPEG、PNG 或 WebP 图片");
    }

    private String validateImage(byte[] content, String extension) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "正文图片内容无效或已损坏");
            }
            return extension;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ResultCode.BAD_REQUEST, "正文图片内容无效或已损坏");
        }
    }
}
