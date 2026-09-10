package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.risk.RiskLimit;
import com.hmdp.security.OpsAuthorizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "webp", "gif"));

    private final Path uploadRoot;
    private final OpsAuthorizer opsAuthorizer;

    public UploadController(@Value("${shushu.upload.directory:./uploads}") String uploadDirectory,
                            OpsAuthorizer opsAuthorizer) {
        this.uploadRoot = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        this.opsAuthorizer = opsAuthorizer;
    }

    @PostMapping("/blog")
    @RiskLimit(userLimit = 30, ipLimit = 100, deviceLimit = 50, windowSeconds = 3600)
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return Result.fail("请选择图片文件");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            return Result.fail("图片不能超过 5MB");
        }
        String extension = extensionOf(image.getOriginalFilename());
        String contentType = image.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return Result.fail("仅支持 jpg、jpeg、png、webp、gif 图片");
        }

        String id = UUID.randomUUID().toString();
        int hash = id.hashCode();
        String relative = "blogs/" + (hash & 0xF) + "/" + ((hash >> 4) & 0xF)
                + "/" + id + "." + extension;
        Path target = resolveInsideUploadRoot(relative);
        try {
            if (!hasValidImageSignature(image, extension)) {
                return Result.fail("图片内容与文件类型不匹配");
            }
            Files.createDirectories(target.getParent());
            image.transferTo(target.toFile());
            String publicPath = "/uploads/" + relative.replace('\\', '/');
            log.debug("图片上传成功，{}", publicPath);
            return Result.ok(publicPath);
        } catch (IOException exception) {
            throw new IllegalStateException("文件上传失败", exception);
        }
    }

    @DeleteMapping("/blog")
    public Result deleteBlogImage(
            @RequestHeader(value = "X-Ops-Token", required = false) String opsToken,
            @RequestParam("name") String filename) {
        opsAuthorizer.requireAuthorized(opsToken);
        if (filename == null || !filename.startsWith("/uploads/blogs/")) {
            return Result.fail("错误的文件名称");
        }
        Path target = resolveInsideUploadRoot(filename.substring("/uploads/".length()));
        try {
            if (!Files.isRegularFile(target)) {
                return Result.fail("文件不存在");
            }
            return Files.deleteIfExists(target) ? Result.ok() : Result.fail("文件不存在");
        } catch (IOException exception) {
            throw new IllegalStateException("文件删除失败", exception);
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int separator = originalFilename.lastIndexOf('.');
        return separator < 0 ? "" : originalFilename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private Path resolveInsideUploadRoot(String relative) {
        Path resolved = uploadRoot.resolve(relative).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("文件路径越界");
        }
        return resolved;
    }

    private boolean hasValidImageSignature(MultipartFile image, String extension) throws IOException {
        byte[] header = new byte[12];
        int length;
        try (InputStream input = image.getInputStream()) {
            length = input.read(header);
        }
        if (length < 3) {
            return false;
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(extension)) {
            return length >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P'
                    && header[2] == 'N' && header[3] == 'G';
        }
        if ("gif".equals(extension)) {
            return length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
        }
        return length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }
}
