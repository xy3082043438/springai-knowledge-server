package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentChunkPreviewResponse;
import com.lamb.springaiknowledgeserver.dto.DocumentCreateRequest;
import com.lamb.springaiknowledgeserver.dto.DocumentReindexResponse;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RoleRepository roleRepository;
    private final VectorStore vectorStore;
    private final SystemConfigService systemConfigService;

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
        rebuildChunks(saved, null);
        return saved;
    }

    @Transactional
    public Document upload(MultipartFile file, String title, Collection<String> roleNames) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传文件");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\\\/]+", "_");
        String contentType = file.getContentType();
        String extension = getExtension(safeName);
        boolean isPdf = isPdf(contentType, extension);
        boolean isText = isText(contentType, extension);
        if (!isPdf && !isText) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PDF 或 TXT 文件");
        }

        ParsedPdf parsedPdf = isPdf ? extractPdfPages(file) : null;
        String extracted = isPdf ? parsedPdf.text : extractText(file);
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
        rebuildChunks(saved, parsedPdf == null ? null : parsedPdf.pages);
        return saved;
    }

    @Transactional
    public Document update(Long id, DocumentUpdateRequest request) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        boolean contentChanged = false;
        boolean titleChanged = false;
        boolean rolesChanged = false;
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            if (!request.getTitle().equals(document.getTitle())) {
                document.setTitle(request.getTitle());
                titleChanged = true;
            }
        }
        if (request.getContent() != null) {
            document.setContent(request.getContent());
            document.setContentType("text/plain");
            document.setFileSize(request.getContent().getBytes(StandardCharsets.UTF_8).length);
            contentChanged = true;
        }
        if (request.getAllowedRoles() != null) {
            document.setAllowedRoles(resolveRoles(request.getAllowedRoles()));
            rolesChanged = true;
        }
        if (request.getStatus() != null) {
            document.setStatus(request.getStatus());
        }
        Document saved = documentRepository.save(document);
        if (contentChanged) {
            rebuildChunks(saved, null);
        } else if (rolesChanged || titleChanged) {
            refreshVectorMetadata(saved);
        }
        return saved;
    }

    @Transactional
    public Document replaceFile(Long id, MultipartFile file, String title, Collection<String> roleNames) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传文件");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\\\/]+", "_");
        String contentType = file.getContentType();
        String extension = getExtension(safeName);
        boolean isPdf = isPdf(contentType, extension);
        boolean isText = isText(contentType, extension);
        if (!isPdf && !isText) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PDF 或 TXT 文件");
        }
        ParsedPdf parsedPdf = isPdf ? extractPdfPages(file) : null;
        String extracted = isPdf ? parsedPdf.text : extractText(file);
        Path target = storeFile(file, safeName);
        String oldPath = document.getStoragePath();

        if (title != null && !title.isBlank()) {
            document.setTitle(title);
        }
        if (roleNames != null && !roleNames.isEmpty()) {
            document.setAllowedRoles(resolveRoles(roleNames));
        }
        document.setContent(extracted);
        document.setFileName(safeName);
        document.setContentType(contentType != null ? contentType : (isPdf ? "application/pdf" : "text/plain"));
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStatus(DocumentStatus.READY);

        Document saved = documentRepository.save(document);
        rebuildChunks(saved, parsedPdf == null ? null : parsedPdf.pages);
        if (oldPath != null && !oldPath.isBlank()) {
            deleteFileIfExists(oldPath);
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        deleteFileIfExists(document.getStoragePath());
        List<DocumentChunk> existing = documentChunkRepository.findByDocumentIdOrderByChunkIndex(id);
        deleteVectors(id, existing);
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        if (hasRoleAccess(document, roleName)) {
            return document;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问文档");
    }

    @Transactional(readOnly = true)
    public DocumentChunkPreviewResponse getChunkPreview(Long chunkId, String roleName) {
        DocumentChunk chunk = documentChunkRepository.findById(chunkId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "片段不存在"));
        Document document = chunk.getDocument();
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问文档");
        }
        if (hasRoleAccess(document, roleName)) {
            return DocumentChunkPreviewResponse.from(chunk);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问文档");
    }

    @Transactional
    public DocumentReindexResponse reindexAll() {
        List<Document> documents = documentRepository.findAll();
        int total = documents.size();
        int success = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Document document : documents) {
            try {
                rebuildForDocument(document);
                success++;
            } catch (Exception ex) {
                log.debug("Failed to reindex document {}", document.getId(), ex);
                failedIds.add(document.getId());
            }
        }
        int failed = total - success;
        return new DocumentReindexResponse(total, success, failed, failedIds);
    }

    @Transactional
    public DocumentReindexResponse reindexOne(Long id) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        rebuildForDocument(document);
        return new DocumentReindexResponse(1, 1, 0, List.of());
    }

    private void rebuildChunks(Document document, List<PageText> pages) {
        List<DocumentChunk> existing = documentChunkRepository.findByDocumentIdOrderByChunkIndex(document.getId());
        deleteVectors(document.getId(), existing);
        documentChunkRepository.deleteByDocumentId(document.getId());
        List<DocumentChunk> chunks = (pages == null || pages.isEmpty())
            ? splitToChunks(document)
            : splitToChunks(document, pages);
        if (chunks.isEmpty()) {
            return;
        }
        List<DocumentChunk> saved = documentChunkRepository.saveAll(chunks);
        vectorStore.add(toVectorDocuments(document, saved));
    }

    private void rebuildForDocument(Document document) {
        List<PageText> pages = null;
        if (document.getContentType() != null && document.getContentType().contains("pdf")) {
            pages = extractPdfPages(document.getStoragePath());
        }
        rebuildChunks(document, pages);
    }

    private List<DocumentChunk> splitToChunks(Document document) {
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int size = Math.max(1, resolveChunkSize());
        int overlap = Math.max(0, resolveChunkOverlap());
        if (overlap >= size) {
            overlap = size - 1;
        }
        int step = size - overlap;

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
            chunk.setPageNumber(1);
            result.add(chunk);
            if (end == content.length()) {
                break;
            }
        }
        return result;
    }

    private List<DocumentChunk> splitToChunks(Document document, List<PageText> pages) {
        int size = Math.max(1, resolveChunkSize());
        int overlap = Math.max(0, resolveChunkOverlap());
        if (overlap >= size) {
            overlap = size - 1;
        }
        int step = size - overlap;

        List<DocumentChunk> result = new ArrayList<>();
        int index = 0;
        int offsetBase = 0;
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            PageText page = pages.get(pageIndex);
            String content = page.text;
            if (content == null || content.isBlank()) {
                offsetBase += content == null ? 0 : content.length();
                if (pageIndex < pages.size() - 1) {
                    offsetBase += 1;
                }
                continue;
            }
            for (int start = 0; start < content.length(); start += step) {
                int end = Math.min(content.length(), start + size);
                String slice = content.substring(start, end);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocument(document);
                chunk.setChunkIndex(index++);
                chunk.setContent(slice);
                chunk.setStartOffset(offsetBase + start);
                chunk.setEndOffset(offsetBase + end);
                chunk.setPageNumber(page.pageNumber);
                result.add(chunk);
                if (end == content.length()) {
                    break;
                }
            }
            offsetBase += content.length();
            if (pageIndex < pages.size() - 1) {
                offsetBase += 1;
            }
        }
        return result;
    }

    private void refreshVectorMetadata(Document document) {
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndex(document.getId());
        if (chunks.isEmpty()) {
            return;
        }
        vectorStore.add(toVectorDocuments(document, chunks));
    }

    private void deleteVectors(Long documentId, List<DocumentChunk> chunks) {
        if (documentId == null || chunks == null || chunks.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            ids.add(vectorId(documentId, chunk.getId()));
        }
        vectorStore.delete(ids);
    }

    private List<org.springframework.ai.document.Document> toVectorDocuments(Document document, List<DocumentChunk> chunks) {
        List<String> roleNames = document.getAllowedRoles().stream()
            .map(Role::getName)
            .toList();
        List<org.springframework.ai.document.Document> result = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", document.getId());
            metadata.put("chunkId", chunk.getId());
            metadata.put("chunkIndex", chunk.getChunkIndex());
            metadata.put("pageNumber", chunk.getPageNumber());
            metadata.put("startOffset", chunk.getStartOffset());
            metadata.put("endOffset", chunk.getEndOffset());
            metadata.put("roleNames", roleNames);
            metadata.put("title", document.getTitle());
            metadata.put("fileName", document.getFileName());
            metadata.put("contentType", document.getContentType());
            result.add(new org.springframework.ai.document.Document(
                vectorId(document.getId(), chunk.getId()),
                chunk.getContent(),
                metadata
            ));
        }
        return result;
    }

    private String vectorId(Long documentId, Long chunkId) {
        return "doc-" + documentId + "-chunk-" + chunkId;
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败", ex);
        }
    }

    private void deleteFileIfExists(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件删除失败", ex);
        }
    }

    private String extractText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文本失败", ex);
        }
    }

    private ParsedPdf extractPdfPages(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = doc.getNumberOfPages();
                List<PageText> pages = new ArrayList<>();
                StringBuilder full = new StringBuilder();
                for (int page = 1; page <= totalPages; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(doc);
                    if (text == null) {
                        text = "";
                    }
                    pages.add(new PageText(page, text));
                    full.append(text);
                    if (page < totalPages) {
                        full.append('\n');
                    }
                }
                return new ParsedPdf(full.toString(), pages);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 PDF 失败", ex);
        }
    }

    private List<PageText> extractPdfPages(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = doc.getNumberOfPages();
                List<PageText> pages = new ArrayList<>();
                for (int page = 1; page <= totalPages; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(doc);
                    if (text == null) {
                        text = "";
                    }
                    pages.add(new PageText(page, text));
                }
                return pages;
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 PDF 失败", ex);
        }
    }

    private static final class ParsedPdf {
        private final String text;
        private final List<PageText> pages;

        private ParsedPdf(String text, List<PageText> pages) {
            this.text = text;
            this.pages = pages;
        }
    }

    private static final class PageText {
        private final int pageNumber;
        private final String text;

        private PageText(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须指定可访问角色");
        }
        Set<String> normalized = new HashSet<>();
        for (String name : roleNames) {
            if (name != null && !name.isBlank()) {
                normalized.add(name);
            }
        }
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须指定可访问角色");
        }
        Collection<Role> roles = roleRepository.findByNameIn(normalized);
        if (roles.size() != normalized.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在");
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

    private int resolveChunkSize() {
        return systemConfigService.getInt("chunk.size", chunkSize);
    }

    private int resolveChunkOverlap() {
        return systemConfigService.getInt("chunk.overlap", chunkOverlap);
    }
}
