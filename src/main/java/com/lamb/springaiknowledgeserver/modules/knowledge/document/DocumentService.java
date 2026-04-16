package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentChunkPreviewResponse;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentCreateRequest;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentReindexResponse;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentUpdateRequest;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.Document;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentChunk;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentStatus;
import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentChunkRepository;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentRepository;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentProcessorHelper.PageText;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentProcessorHelper.ParsedContent;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentProcessorHelper.DocumentFileType;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleRepository;
import java.io.ByteArrayInputStream;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.lamb.springaiknowledgeserver.modules.system.config.SystemConfigService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String SUPPORTED_UPLOAD_TYPES = "PDF/DOCX/PPTX/XLSX/TXT/MD/HTML/CSV";
    
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RoleRepository roleRepository;
    private final VectorStore vectorStore;
    private final SystemConfigService systemConfigService;
    private final DocumentAsyncService documentAsyncService;
    private final DocumentProcessorHelper documentProcessorHelper;

    @Value("${app.document.storage-path}")
    private String storagePath;

    @Value("${app.document.chunk-size}")
    private int chunkSize;

    @Value("${app.document.chunk-overlap}")
    private int chunkOverlap;

    @Value("${app.document.embedding-safe-chunk-size:320}")
    private int embeddingSafeChunkSize;

    @Transactional
    public Document createText(DocumentCreateRequest request) {
        Set<Role> roles = resolveRoles(request.getAllowedRoles());
        Document document = new Document();
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setFileName(request.getTitle());
        document.setContentType("text/plain");
        document.setFileSize(request.getContent() == null ? 0 : request.getContent().getBytes(StandardCharsets.UTF_8).length);
        document.setStatus(DocumentStatus.PARSING);
        document.setAllowedRoles(roles);
        Document saved = documentRepository.save(document);
        
        documentAsyncService.reindexAsync(saved.getId());
        return saved;
    }

    @Transactional
    public Document upload(MultipartFile file, String title, Collection<String> roleNames) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未检测到文件，请先选择需要上传的文件");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\\\/]+", "_");
        
        DocumentFileType fileType = documentProcessorHelper.resolveUploadFileType(file.getContentType(), safeName);
        Path target = storeFile(file, safeName);

        Document document = new Document();
        document.setTitle((title == null || title.isBlank()) ? safeName : title);
        document.setFileName(safeName);
        document.setContentType(fileType.defaultContentType);
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStatus(DocumentStatus.PARSING);
        document.setAllowedRoles(resolveRoles(roleNames));
        
        Document saved = documentRepository.save(document);
        try {
            documentAsyncService.processDocumentAsync(saved.getId(), file.getBytes(), file.getContentType(), safeName);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取上传的文件内容", e);
        }
        return saved;
    }

    @Transactional
    public Document update(Long id, DocumentUpdateRequest request) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取文档，它可能已被删除或移除"));
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
            documentProcessorHelper.rebuildChunks(saved);
        } else if (rolesChanged || titleChanged) {
            documentProcessorHelper.refreshVectorMetadata(saved);
        }
        return saved;
    }

    @Transactional
    public Document replaceFile(Long id, MultipartFile file, String title, Collection<String> roleNames) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取文档，它可能已被删除或移除"));
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未检测到文件，请先选择需要上传的文件");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\\\/]+", "_");
        
        DocumentFileType fileType = documentProcessorHelper.resolveUploadFileType(file.getContentType(), safeName);
        Path target = storeFile(file, safeName);
        String oldPath = document.getStoragePath();

        if (title != null && !title.isBlank()) {
            document.setTitle(title);
        }
        if (roleNames != null && !roleNames.isEmpty()) {
            document.setAllowedRoles(resolveRoles(roleNames));
        }
        document.setFileName(safeName);
        document.setContentType(fileType.defaultContentType);
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStatus(DocumentStatus.PARSING);

        Document saved = documentRepository.save(document);
        try {
            documentAsyncService.processDocumentAsync(saved.getId(), file.getBytes(), file.getContentType(), safeName);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取上传的文件内容", e);
        }
        if (oldPath != null && !oldPath.isBlank()) {
            deleteFileIfExists(oldPath);
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取文档，它可能已被删除或移除"));
        deleteFileIfExists(document.getStoragePath());
        List<DocumentChunk> existing = documentChunkRepository.findByDocumentIdOrderByChunkIndex(id);
        documentProcessorHelper.deleteVectors(id, existing);
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取该文档，可能已被删除"));
        if (hasRoleAccess(document, roleName)) {
            return document;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "抱歉，您当前没有访问该文档的权限");
    }

    @Transactional(readOnly = true)
    public DocumentChunkPreviewResponse getChunkPreview(Long chunkId, String roleName) {
        DocumentChunk chunk = documentChunkRepository.findById(chunkId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无权限访问，或者该知识片段已被删除"));
        Document document = chunk.getDocument();
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "抱歉，您当前没有访问该文档的权限");
        }
        if (hasRoleAccess(document, roleName)) {
            return DocumentChunkPreviewResponse.from(chunk);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "抱歉，您当前没有访问该文档的权限");
    }

    public org.springframework.core.io.Resource getFileAsResource(Long id, String roleName) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取该文档"));
        
        if (!hasRoleAccess(document, roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "抱歉，您当前没有访问该文档的权限");
        }

        String storedPath = document.getStoragePath();
        if (storedPath == null || storedPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该文档没有关联的原始文件");
        }

        Path path = Paths.get(storedPath);
        if (!Files.exists(path)) {
            log.warn("File not found at stored path: {}. Attempting fallback to configured storage path.", storedPath);
            try {
                String fileName = path.getFileName().toString();
                Path safeStoragePath = Paths.get(storagePath).toAbsolutePath().normalize();
                Path fallbackPath = safeStoragePath.resolve(fileName);
                if (Files.exists(fallbackPath)) {
                    log.info("Found file at fallback path: {}", fallbackPath);
                    return new org.springframework.core.io.FileSystemResource(fallbackPath);
                }
            } catch (Exception e) {
                log.error("Fallback path resolution failed", e);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原始文件已丢失，请尝试重新上传。路径：" + storedPath);
        }

        return new org.springframework.core.io.FileSystemResource(path);
    }

    @Transactional
    public DocumentReindexResponse reindexAll() {
        List<Document> documents = documentRepository.findAll();
        int total = documents.size();
        int success = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Document document : documents) {
            try {
                document.setStatus(DocumentStatus.PARSING);
                documentRepository.save(document);
                documentAsyncService.reindexAsync(document.getId());
                success++;
            } catch (Exception ex) {
                log.debug("Failed to reindex document {}", document.getId(), ex);
                document.setStatus(DocumentStatus.FAILED);
                document.setErrorMessage(ex.getMessage());
                documentRepository.save(document);
                failedIds.add(document.getId());
            }
        }
        int failed = total - success;
        return new DocumentReindexResponse(total, success, failed, failedIds);
    }

    public DocumentReindexResponse reindexOne(Long id) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取文档，它可能已被删除或移除"));
        document.setStatus(DocumentStatus.PARSING);
        documentRepository.save(document);
        documentAsyncService.reindexAsync(id);
        return new DocumentReindexResponse(1, 1, 0, List.of());
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "服务器在保存文件时遇到问题，请稍后重试", ex);
        }
    }

    private void deleteFileIfExists(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "服务器在删除文件时遇到问题", ex);
        }
    }



    private Set<Role> resolveRoles(Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须为该文档至少分配一个可访问的角色");
        }
        Set<String> normalized = new HashSet<>();
        for (String name : roleNames) {
            if (name != null && !name.isBlank()) {
                normalized.add(name);
            }
        }
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须为该文档至少分配一个可访问的角色");
        }
        Collection<Role> roles = roleRepository.findByNameIn(normalized);
        if (roles.size() != normalized.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分配了无效的角色，请检查角色是否已被删除");
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




