package com.lamb.springaiknowledgeserver.modules.system.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "application/pdf"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf"
    );

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    @Value("${app.upload.path}")
    private String uploadPath;

    @PostMapping
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件不能为空"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件大小不能超过 10MB"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            return ResponseEntity.badRequest().body(Map.of("message", "不支持的文件类型"));
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(Map.of("message", "不支持的文件扩展名"));
        }

        try {
            java.nio.file.Path rootPath = java.nio.file.Paths.get(uploadPath).toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(rootPath)) {
                java.nio.file.Files.createDirectories(rootPath);
            }

            String newFilename = UUID.randomUUID().toString() + extension;
            java.nio.file.Path targetLocation = rootPath.resolve(newFilename).normalize();
            if (!targetLocation.startsWith(rootPath)) {
                return ResponseEntity.badRequest().body(Map.of("message", "非法的文件名"));
            }

            java.nio.file.Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(newFilename)
                    .toUriString();

            return ResponseEntity.ok(Map.of("url", fileUrl));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "文件上传失败"));
        }
    }
}
