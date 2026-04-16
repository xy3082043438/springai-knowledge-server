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
        document.setStatus(DocumentStatus.READY);
        document.setAllowedRoles(roles);
        Document saved = documentRepository.save(document);
        rebuildChunks(saved, null);
        return saved;
    }

    @Transactional
    public Document upload(MultipartFile file, String title, Collection<String> roleNames) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未检测到文件，请先选择需要上传的文件");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\\\/]+", "_");
        DocumentFileType fileType = resolveUploadFileType(file.getContentType(), safeName);
        ParsedContent parsed = extractContent(file, fileType);
        Path target = storeFile(file, safeName);

        Document document = new Document();
        document.setTitle((title == null || title.isBlank()) ? safeName : title);
        document.setContent(parsed.text);
        document.setFileName(safeName);
        document.setContentType(fileType.defaultContentType);
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStatus(DocumentStatus.READY);
        document.setAllowedRoles(resolveRoles(roleNames));
        Document saved = documentRepository.save(document);
        rebuildChunks(saved, parsed.pages);
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
            rebuildChunks(saved, null);
        } else if (rolesChanged || titleChanged) {
            refreshVectorMetadata(saved);
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
        DocumentFileType fileType = resolveUploadFileType(file.getContentType(), safeName);
        ParsedContent parsed = extractContent(file, fileType);
        Path target = storeFile(file, safeName);
        String oldPath = document.getStoragePath();

        if (title != null && !title.isBlank()) {
            document.setTitle(title);
        }
        if (roleNames != null && !roleNames.isEmpty()) {
            document.setAllowedRoles(resolveRoles(roleNames));
        }
        document.setContent(parsed.text);
        document.setFileName(safeName);
        document.setContentType(fileType.defaultContentType);
        document.setFileSize(file.getSize());
        document.setStoragePath(target.toString());
        document.setStatus(DocumentStatus.READY);

        Document saved = documentRepository.save(document);
        rebuildChunks(saved, parsed.pages);
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取文档，它可能已被删除或移除"));
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
        ParsedContent parsed = extractStoredContent(document);
        if (parsed != null) {
            document.setContent(parsed.text);
            Document saved = documentRepository.save(document);
            rebuildChunks(saved, parsed.pages);
            return;
        }
        rebuildChunks(document, null);
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

    private ParsedContent extractContent(MultipartFile file, DocumentFileType fileType) {
        try {
            return extractContent(file.getBytes(), fileType);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取您的文件内容，请检查文件是否损坏", ex);
        }
    }

    private ParsedContent extractStoredContent(Document document) {
        if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            return null;
        }
        DocumentFileType fileType = resolveStoredFileType(document.getContentType());
        if (fileType == null) {
            return null;
        }
        try {
            return extractContent(Files.readAllBytes(Paths.get(document.getStoragePath())), fileType);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取您的文件内容，请检查文件是否损坏", ex);
        }
    }

    private ParsedContent extractContent(byte[] bytes, DocumentFileType fileType) {
        return switch (fileType) {
            case PDF -> extractPdf(bytes);
            case DOCX -> extractDocx(bytes);
            case PPTX -> extractPptx(bytes);
            case XLSX -> extractXlsx(bytes);
            case HTML -> extractHtml(bytes);
            case TXT, MARKDOWN, CSV -> new ParsedContent(new String(bytes, StandardCharsets.UTF_8), null);
        };
    }

    private ParsedContent extractPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = doc.getNumberOfPages();
            List<PageText> pages = new ArrayList<>();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                pages.add(new PageText(page, text == null ? "" : text));
            }
            return new ParsedContent(joinPages(pages), pages);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 PDF 失败，文件内容格式可能异常", ex);
        }
    }

    private ParsedContent extractDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return new ParsedContent(extractor.getText(), null);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 Word 文档失败", ex);
        }
    }

    private ParsedContent extractPptx(byte[] bytes) {
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            List<PageText> pages = new ArrayList<>();
            int index = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                pages.add(new PageText(index++, extractSlideText(slide)));
            }
            return new ParsedContent(joinPages(pages), pages);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 PPT 演示文稿失败", ex);
        }
    }

    private String extractSlideText(XSLFSlide slide) {
        StringBuilder text = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
            appendSlideShapeText(shape, text);
        }
        return text.toString();
    }

    private void appendSlideShapeText(XSLFShape shape, StringBuilder text) {
        if (shape instanceof XSLFTextShape textShape) {
            appendTextSegment(text, textShape.getText());
            return;
        }
        if (shape instanceof XSLFTable table) {
            for (XSLFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XSLFTableCell cell : row.getCells()) {
                    cells.add(cell == null ? "" : cell.getText());
                }
                appendTextSegment(text, joinCellValues(cells));
            }
            return;
        }
        if (shape instanceof XSLFGroupShape groupShape) {
            for (XSLFShape child : groupShape.getShapes()) {
                appendSlideShapeText(child, text);
            }
        }
    }

    private ParsedContent extractXlsx(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<PageText> pages = new ArrayList<>();
            int index = 1;
            for (Sheet sheet : workbook) {
                pages.add(new PageText(index++, extractSheetText(sheet, formatter, evaluator)));
            }
            return new ParsedContent(joinPages(pages), pages);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 Excel 表格失败", ex);
        }
    }

    private String extractSheetText(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        StringBuilder text = new StringBuilder();
        appendTextSegment(text, "Sheet: " + sheet.getSheetName());
        for (Row row : sheet) {
            int lastCellNum = row.getLastCellNum();
            if (lastCellNum < 0) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int index = 0; index < lastCellNum; index++) {
                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                cells.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator));
            }
            appendTextSegment(text, joinCellValues(cells));
        }
        return text.toString();
    }

    private ParsedContent extractHtml(byte[] bytes) {
        org.jsoup.nodes.Document html = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8));
        html.outputSettings().prettyPrint(false);
        html.select("br").append("\\n");
        html.select("p,div,li,tr,h1,h2,h3,h4,h5,h6,section,article").prepend("\\n").append("\\n");
        html.select("td,th").append(" | ");
        String bodyText = html.body().text().replace("\\n", "\n");
        String title = html.title();
        if (!title.isBlank() && !bodyText.startsWith(title)) {
            bodyText = title + "\n" + bodyText;
        }
        return new ParsedContent(bodyText, null);
    }

    private String joinPages(List<PageText> pages) {
        StringBuilder full = new StringBuilder();
        for (int index = 0; index < pages.size(); index++) {
            full.append(pages.get(index).text);
            if (index < pages.size() - 1) {
                full.append('\n');
            }
        }
        return full.toString();
    }

    private void appendTextSegment(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(value.trim());
    }

    private String joinCellValues(List<String> cells) {
        int last = cells.size() - 1;
        while (last >= 0 && (cells.get(last) == null || cells.get(last).isBlank())) {
            last--;
        }
        if (last < 0) {
            return "";
        }
        StringBuilder row = new StringBuilder();
        for (int index = 0; index <= last; index++) {
            if (index > 0) {
                row.append('\t');
            }
            String value = cells.get(index);
            if (value != null) {
                row.append(value.trim());
            }
        }
        return row.toString();
    }

    private static final class ParsedContent {
        private final String text;
        private final List<PageText> pages;

        private ParsedContent(String text, List<PageText> pages) {
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

    private DocumentFileType resolveUploadFileType(String contentType, String fileName) {
        String normalizedContentType = normalizeContentType(contentType);
        if (normalizedContentType != null) {
            for (DocumentFileType type : DocumentFileType.values()) {
                if (type.matchesContentType(normalizedContentType) && !type.isGenericTextType()) {
                    return type;
                }
            }
        }

        String extension = getExtension(fileName);
        for (DocumentFileType type : DocumentFileType.values()) {
            if (type.matchesExtension(extension)) {
                return type;
            }
        }

        if (normalizedContentType != null) {
            if ("text/plain".equals(normalizedContentType)) {
                return DocumentFileType.TXT;
            }
            for (DocumentFileType type : DocumentFileType.values()) {
                if (type.matchesContentType(normalizedContentType)) {
                    return type;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "抱歉，当前仅支持 " + SUPPORTED_UPLOAD_TYPES + " 格式的文件");
    }

    private DocumentFileType resolveStoredFileType(String contentType) {
        String normalizedContentType = normalizeContentType(contentType);
        if (normalizedContentType == null) {
            return null;
        }
        for (DocumentFileType type : DocumentFileType.values()) {
            if (type != DocumentFileType.TXT && type.matchesContentType(normalizedContentType)) {
                return type;
            }
        }
        return null;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return normalized.trim().toLowerCase();
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx == -1 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1);
    }

    private enum DocumentFileType {
        PDF("application/pdf", Set.of("pdf"), Set.of("application/pdf")),
        DOCX(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            Set.of("docx"),
            Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        ),
        PPTX(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            Set.of("pptx"),
            Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")
        ),
        XLSX(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            Set.of("xlsx"),
            Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ),
        TXT("text/plain", Set.of("txt"), Set.of("text/plain")),
        MARKDOWN("text/markdown", Set.of("md", "markdown"), Set.of("text/markdown", "text/x-markdown")),
        HTML("text/html", Set.of("html", "htm"), Set.of("text/html", "application/xhtml+xml")),
        CSV("text/csv", Set.of("csv"), Set.of("text/csv", "application/csv", "application/vnd.ms-excel"));

        private final String defaultContentType;
        private final Set<String> extensions;
        private final Set<String> contentTypes;

        DocumentFileType(String defaultContentType, Set<String> extensions, Set<String> contentTypes) {
            this.defaultContentType = defaultContentType;
            this.extensions = extensions;
            this.contentTypes = contentTypes;
        }

        private boolean matchesExtension(String extension) {
            return extension != null && extensions.contains(extension.toLowerCase());
        }

        private boolean matchesContentType(String contentType) {
            return contentType != null && contentTypes.contains(contentType);
        }

        private boolean isGenericTextType() {
            return this == TXT;
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

    private int resolveChunkSize() {
        int configuredSize = Math.max(1, systemConfigService.getInt("chunk.size", chunkSize));
        int safeSize = Math.max(1, systemConfigService.getInt("chunk.embeddingSafeSize", embeddingSafeChunkSize));
        return Math.min(configuredSize, safeSize);
    }

    private int resolveChunkOverlap() {
        return systemConfigService.getInt("chunk.overlap", chunkOverlap);
    }
}




