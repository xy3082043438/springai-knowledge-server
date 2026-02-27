package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentCreateRequest;
import com.lamb.springaiknowledgeserver.dto.DocumentUpdateRequest;
import com.lamb.springaiknowledgeserver.entity.Document;
import com.lamb.springaiknowledgeserver.entity.DocumentChunk;
import com.lamb.springaiknowledgeserver.entity.DocumentStatus;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.repository.DocumentChunkRepository;
import com.lamb.springaiknowledgeserver.repository.DocumentRepository;
import com.lamb.springaiknowledgeserver.repository.RoleRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RoleRepository roleRepository;

    @Value("${app.document.storage-path}")
    private String storagePath;

    @Value("${app.document.chunk-size}")
    private int chunkSize;

    @Value("${app.document.chunk-overlap}")
    private int chunkOverlap;

    @Transactional
    public Document createText(DocumentCreateRequest request) {
        Set<Role> roles = resolveRoles(request.getAllowedRoles());
        Document document = new Document();
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setFileName(request.getTitle());
        document.setContentType("text/plain");
        document.setFileSize(request.getContent() == null ? 0 : request.getContent().getBytes(StandardCharsets.UTF_8).length);
        document.setStatus(DocumentStatus.READY);
        document.setAllowedRoles(roles);
        Document saved = documentRepository.save(document);
        rebuildChunks(saved);
        return saved;
    }

    @Transactional
    public Document upload(MultipartFile file, String title, Collection<String> roleNames) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\\\/]+", "_");
        String contentType = file.getContentType();
        String extension = getExtension(safeName);
        boolean isPdf = isPdf(contentType, extension);
        boolean isText = isText(contentType, extension);
        if (!isPdf && !isText) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF or TXT is supported");
        }

        String extracted = isPdf ? extractPdfText(file) : extractText(file);
        Path target = storeFile(file, safeName);

        Document document = new Document();
        document.setTitle((title == null || title.isBlank()) ? safeName : title);
        document.setContent(extracted);
        document.setFileName(safeName);
        document.setContentType(contentType != null ? contentType : (isPdf ? "application/pdf" : "text/plain"));
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStatus(DocumentStatus.READY);
        document.setAllowedRoles(resolveRoles(roleNames));
        Document saved = documentRepository.save(document);
        rebuildChunks(saved);
        return saved;
    }

    @Transactional
    public Document update(Long id, DocumentUpdateRequest request) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        boolean contentChanged = false;
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            document.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            document.setContent(request.getContent());
            document.setContentType("text/plain");
            document.setFileSize(request.getContent().getBytes(StandardCharsets.UTF_8).length);
            contentChanged = true;
        }
        if (request.getAllowedRoles() != null) {
            document.setAllowedRoles(resolveRoles(request.getAllowedRoles()));
        }
        if (request.getStatus() != null) {
            document.setStatus(request.getStatus());
        }
        Document saved = documentRepository.save(document);
        if (contentChanged) {
            rebuildChunks(saved);
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        deleteFileIfExists(document.getStoragePath());
        documentChunkRepository.deleteByDocumentId(id);
        documentRepository.delete(document);
    }

    public List<Document> listVisible(String roleName) {
        return documentRepository.findVisibleByRoles(Set.of(roleName));
    }

    public List<Document> searchVisible(String roleName, String query) {
        return documentRepository.searchVisibleByRoles(Set.of(roleName), query);
    }

    public Document getVisibleById(Long id, String roleName) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!hasRoleAccess(document, roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to document");
        }
        return document;
    }

    private void rebuildChunks(Document document) {
        documentChunkRepository.deleteByDocumentId(document.getId());
        List<DocumentChunk> chunks = splitToChunks(document);
        if (!chunks.isEmpty()) {
            documentChunkRepository.saveAll(chunks);
        }
    }

    private List<DocumentChunk> splitToChunks(Document document) {
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int size = Math.max(1, chunkSize);
        int overlap = Math.max(0, chunkOverlap);
        if (overlap >= size) {
            overlap = size - 1;
        }
        int step = size - overlap;
        if (step <= 0) {
            step = size;
        }

        List<DocumentChunk> result = new ArrayList<>();
        int index = 0;
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(content.length(), start + size);
            String slice = content.substring(start, end);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(index++);
            chunk.setContent(slice);
            chunk.setStartOffset(start);
            chunk.setEndOffset(end);
            result.add(chunk);
            if (end == content.length()) {
                break;
            }
        }
        return result;
    }

    private Path storeFile(MultipartFile file, String safeName) {
        try {
            Path dir = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID() + "-" + safeName;
            Path target = dir.resolve(fileName);
            file.transferTo(target);
            return target;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", ex);
        }
    }

    private void deleteFileIfExists(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete file", ex);
        }
    }

    private String extractText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read text file", ex);
        }
    }

    private String extractPdfText(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(doc);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read PDF", ex);
        }
    }

    private boolean isPdf(String contentType, String extension) {
        return "pdf".equalsIgnoreCase(extension) || (contentType != null && contentType.contains("pdf"));
    }

    private boolean isText(String contentType, String extension) {
        return "txt".equalsIgnoreCase(extension) || (contentType != null && contentType.startsWith("text/"));
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx == -1 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1);
    }

    private Set<Role> resolveRoles(Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allowed roles required");
        }
        Set<String> normalized = new HashSet<>();
        for (String name : roleNames) {
            if (name != null && !name.isBlank()) {
                normalized.add(name);
            }
        }
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allowed roles required");
        }
        Collection<Role> roles = roleRepository.findByNameIn(normalized);
        if (roles.size() != normalized.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not found");
        }
        return new HashSet<>(roles);
    }

    private boolean hasRoleAccess(Document document, String roleName) {
        for (Role role : document.getAllowedRoles()) {
            if (role.getName().equals(roleName)) {
                return true;
            }
        }
        return false;
    }
}
