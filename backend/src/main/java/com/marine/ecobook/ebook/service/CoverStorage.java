package com.marine.ecobook.ebook.service;

import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
     * JPEG 和 PNG 使用标准 ImageIO 解码器完整解码验证。
     * WebP 由于 JDK 标准库不含解码器，通过验证 RIFF 容器结构完整性
     * （RIFF 头 + 文件大小 + WEBP FourCC + 至少一个有效 chunk）来确保内容合法。
     * 伪造前几个字节但后续内容无法解析的文件将被拒绝。
     */
    private String detectedExtension(byte[] content) {
        if (content.length < 12) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面仅支持 JPEG、PNG 或 WebP 图片");
        }

        // JPEG: FF D8 FF
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return validateJpeg(content);
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G' && content[4] == '\r' && content[5] == '\n'
                && content[6] == 0x1A && content[7] == '\n') {
            return validatePng(content);
        }
        // WebP: RIFF....WEBP
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return validateWebp(content);
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "封面仅支持 JPEG、PNG 或 WebP 图片");
    }

    private String validateJpeg(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
            }
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
        }
        return ".jpg";
    }

    private String validatePng(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
            }
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
        }
        return ".png";
    }

    /**
     * 验证 WebP 文件的 RIFF 容器结构完整性。
     * <p>
     * RIFF 容器格式：'RIFF' + 4 字节文件大小（小端） + 'WEBP' + 一系列 chunk。
     * 每个 chunk：4 字节 FourCC + 4 字节 chunk 大小（小端） + chunk 数据。
     * 文件大小应与实际内容一致，且至少包含一个有效 chunk（如 VP8/VP8L/VP8X/ALPH）。
     */
    private String validateWebp(byte[] content) {
        // RIFF 头：4 字节 'RIFF' + 4 字节文件大小 + 4 字节 'WEBP' = 12 字节最小
        if (content.length < 12) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
        }
        ByteBuffer buffer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        // 跳过 'RIFF' (4) 读取文件大小
        int riffSize = buffer.getInt(4);
        // riffSize 不包含 RIFF 头和大小字段本身（8 字节）
        if (riffSize < 4 || (long) riffSize + 8 > content.length) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
        }
        // 检查 'WEBP' FourCC
        if (buffer.getInt(8) != 0x50424557) { // 'WEBP' in little-endian: P=0x50, B=0x42, E=0x45, W=0x57
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
        }
        // 验证至少一个有效 chunk
        int offset = 12;
        boolean foundValidChunk = false;
        while (offset + 8 <= content.length) {
            int chunkSize = buffer.getInt(offset + 4);
            if (chunkSize < 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
            }
            // chunk 数据以 padding 对齐到偶数
            int paddedSize = chunkSize + (chunkSize % 2);
            offset += 8 + paddedSize;
            foundValidChunk = true;
            if (offset >= content.length) {
                break;
            }
        }
        if (!foundValidChunk) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面图片内容无效或已损坏");
        }
        return ".webp";
    }
}
