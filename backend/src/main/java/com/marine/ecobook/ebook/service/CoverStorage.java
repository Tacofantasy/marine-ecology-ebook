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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class CoverStorage {

    private static final Logger log = LoggerFactory.getLogger(CoverStorage.class);
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

        byte[] content;
        try (InputStream input = file.getInputStream()) {
            content = input.readAllBytes();
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法读取封面图片");
        }

        String extension = detectedExtension(content);
        try {
            Files.createDirectories(coversDirectory);
            String filename = UUID.randomUUID() + extension;
            Path target = coversDirectory.resolve(filename).normalize();
            Files.write(target, content);
            return "/uploads/covers/" + filename;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "封面保存失败，请稍后重试");
        }
    }

    /**
     * 静默删除封面文件，失败时仅记录日志不抛异常。
     * 用于事务提交后的旧封面清理，避免删除失败导致数据库回滚而产生新文件遗留问题。
     */
    public void deleteQuietly(String coverUrl) {
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
            log.warn("旧封面清理失败：{}", coverUrl, exception);
        }
    }

    /**
     * 通过完整解码图片内容验证其有效性，而非仅检查魔数。
     * <p>
     * JPEG、PNG 和 WebP 都使用 ImageIO 的实际解码结果验证。
     * WebP 解码器由 webp-imageio 依赖提供。
     */
    private String detectedExtension(byte[] content) {
        if (content.length < 12) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面仅支持 JPEG、PNG 或 WebP 图片");
        }

        // JPEG: FF D8 FF
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return validateImage(content, ".jpg");
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G' && content[4] == '\r' && content[5] == '\n'
                && content[6] == 0x1A && content[7] == '\n') {
            return validateImage(content, ".png");
        }
        // WebP: RIFF....WEBP
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return validateImage(content, ".webp");
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "封面仅支持 JPEG、PNG 或 WebP 图片");
    }

    private String validateImage(byte[] content, String extension) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
            }
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
        }
        return extension;
    }
}
