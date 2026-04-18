package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import com.lamb.springaiknowledgeserver.modules.system.config.SystemConfigService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class DocumentProcessorHelper {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessorHelper.class);
    private static final String SUPPORTED_UPLOAD_TYPES = "PDF/DOCX/PPTX/XLSX/TXT/MD/HTML/CSV";
    
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorStore vectorStore;
    private final SystemConfigService systemConfigService;

    @Value("${app.document.chunk-size:500}")
    private int chunkSize;

    @Value("${app.document.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${app.document.embedding-safe-chunk-size:320}")
    private int embeddingSafeChunkSize;

    public void processAndIndex(Document document, byte[] fileBytes, String contentType, String fileName) throws IOException {
        log.info("[解析基础] 准备提取内容: {} (类型: {}, 大小: {} bytes)", fileName, contentType, fileBytes.length);
        DocumentFileType fileType = resolveUploadFileType(contentType, fileName);
        ParsedContent parsed = extractContent(fileBytes, fileType);
        
        log.info("[解析基础] 内容提取完成，文本长度: {} 字符", parsed.text != null ? parsed.text.length() : 0);
        document.setContent(parsed.text);
        document.setContentType(fileType.defaultContentType);
        
        rebuildChunks(document, parsed.pages);
    }

    public void rebuildChunks(Document document) {
        if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            rebuildChunks(document, null);
            return;
        }
        
        try {
            DocumentFileType fileType = resolveUploadFileType(document.getContentType(), document.getFileName());
            byte[] bytes = Files.readAllBytes(Paths.get(document.getStoragePath()));
            ParsedContent parsed = extractContent(bytes, fileType);
            document.setContent(parsed.text);
            rebuildChunks(document, parsed.pages);
        } catch (IOException e) {
            log.error("Failed to read stored file for document {}", document.getId(), e);
            rebuildChunks(document, null);
        }
    }

    public void rebuildChunks(Document document, List<PageText> pages) {
        log.info("[分段操作] 开始为文档 ID: {} 重建分段...", document.getId());
        List<DocumentChunk> existing = documentChunkRepository.findByDocumentIdOrderByChunkIndex(document.getId());
        if (!existing.isEmpty()) {
            log.info("[分段操作] 正在清理旧的向量数据 (数量: {})", existing.size());
            deleteVectors(document.getId(), existing);
            documentChunkRepository.deleteByDocumentId(document.getId());
        }
        
        List<DocumentChunk> chunks = (pages == null || pages.isEmpty())
            ? splitToChunks(document)
            : splitToChunks(document, pages);
            
        if (chunks.isEmpty()) {
            log.warn("[分段操作] 文档内未发现可分段的内容 (ID: {})", document.getId());
            return;
        }
        
        log.info("[分段操作] 正在保存 {} 个新分段并同步至向量库", chunks.size());
        List<DocumentChunk> saved = documentChunkRepository.saveAll(chunks);
        vectorStore.add(toVectorDocuments(document, saved));
        log.info("[分段操作] 同步完成 (文档 ID: {})", document.getId());
    }

    public void refreshVectorMetadata(Document document) {
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndex(document.getId());
        if (chunks.isEmpty()) {
            return;
        }
        vectorStore.add(toVectorDocuments(document, chunks));
    }

    public ParsedContent extractContent(byte[] bytes, DocumentFileType fileType) {
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
        log.info("[PDF解析] 正在加载 PDF 文档...");
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = doc.getNumberOfPages();
            log.info("[PDF解析] 文档加载完成，总页数: {}", totalPages);
            List<PageText> pages = new ArrayList<>();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                pages.add(new PageText(page, text == null ? "" : text));
                if (page % 5 == 0 || page == totalPages) {
                    log.debug("[PDF解析] 已提取 {}/{} 页", page, totalPages);
                }
            }
            return new ParsedContent(joinPages(pages), pages);
        } catch (IOException ex) {
            log.error("[PDF解析] 解析过程中发生异常", ex);
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

    public void deleteVectors(Long documentId, List<DocumentChunk> chunks) {
        if (documentId == null || chunks == null || chunks.isEmpty()) return;
        List<String> ids = chunks.stream().map(c -> "doc-" + documentId + "-chunk-" + c.getId()).toList();
        vectorStore.delete(ids);
    }

    public List<org.springframework.ai.document.Document> toVectorDocuments(Document document, List<DocumentChunk> chunks) {
        List<String> roleNames = document.getAllowedRoles().stream().map(Role::getName).toList();
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
            result.add(new org.springframework.ai.document.Document("doc-" + document.getId() + "-chunk-" + chunk.getId(), chunk.getContent(), metadata));
        }
        return result;
    }

    public String vectorId(Long documentId, Long chunkId) {
        return "doc-" + documentId + "-chunk-" + chunkId;
    }

    public DocumentFileType resolveUploadFileType(String contentType, String fileName) {
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

    private int resolveChunkSize() {
        int configuredSize = Math.max(1, systemConfigService.getInt("chunk.size", chunkSize));
        int safeSize = Math.max(1, systemConfigService.getInt("chunk.embeddingSafeSize", embeddingSafeChunkSize));
        return Math.min(configuredSize, safeSize);
    }

    private int resolveChunkOverlap() {
        return systemConfigService.getInt("chunk.overlap", chunkOverlap);
    }

    public static final class ParsedContent {
        public final String text;
        public final List<PageText> pages;
        public ParsedContent(String text, List<PageText> pages) { this.text = text; this.pages = pages; }
    }

    public static final class PageText {
        public final int pageNumber;
        public final String text;
        public PageText(int pageNumber, String text) { this.pageNumber = pageNumber; this.text = text; }
    }

    public enum DocumentFileType {
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

        public final String defaultContentType;
        private final Set<String> extensions;
        private final Set<String> contentTypes;

        DocumentFileType(String defaultContentType, Set<String> extensions, Set<String> contentTypes) {
            this.defaultContentType = defaultContentType;
            this.extensions = extensions;
            this.contentTypes = contentTypes;
        }

        public boolean matchesExtension(String extension) {
            return extension != null && extensions.contains(extension.toLowerCase());
        }

        public boolean matchesContentType(String contentType) {
            return contentType != null && contentTypes.contains(contentType);
        }

        public boolean isGenericTextType() {
            return this == TXT;
        }
    }
}
